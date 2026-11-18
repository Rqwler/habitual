# Deployment guide

This guide takes the project from a GitHub repository to a working HTTPS deployment on a single AWS EC2 instance:

```text
Internet → Caddy :443 (automatic TLS) → Spring Boot :8080 → PostgreSQL (Docker volume)
```

The GitHub Actions workflow verifies the code, publishes an image to GHCR, copies the Compose files to EC2, pulls the new image, and waits for `/actuator/health` to report `UP`.

Estimated cost: a `t3.micro` plus an Elastic IP is usually free-tier eligible for 12 months; after that, expect a few dollars per month. See [AWS EC2 pricing](https://aws.amazon.com/ec2/pricing/).

---

## 0. Prerequisites

| Requirement | Notes |
|---|---|
| AWS account | Permission to create EC2, security groups, and Elastic IPs |
| Domain name | Ability to create an `A` record; Cloudflare, Route 53, Namecheap, etc. |
| GitHub repository | The project pushed to `main` |
| SSH key pair | Created in EC2 or imported; you need the private `.pem` |
| Local tools (optional) | `ssh`, `scp`, `curl`; Docker is only needed for local testing |

The workflow publishes images to **GHCR** (`ghcr.io/<owner>/<repo>`). A public package is simplest. A private package works too, but the EC2 host must log in to GHCR once (step 4).

---

## 1. Launch EC2

1. Open **EC2 → Launch instance**.
2. Name: `habitual`.
3. AMI: **Ubuntu Server 24.04 LTS (HVM), SSD Volume Type**, 64-bit x86.
4. Instance type: **t3.micro** is enough for a demo with the 2 GB swap added by the bootstrap script. Choose **t3.small** for more headroom.
5. Key pair: create or select one and download the `.pem` file.
6. Network settings → **Create security group** with these inbound rules:

| Type | Port | Source | Why |
|---|---|---|---|
| SSH | 22 | **Your IP** (recommended) | Administration and GitHub deploy |
| HTTP | 80 | `0.0.0.0/0`, `::/0` | ACME challenge + redirect to HTTPS |
| HTTPS | 443 | `0.0.0.0/0`, `::/0` | Application traffic |

Do **not** open `5432` or `8080`. Caddy reaches the app over the private Compose network.

7. Storage: 20–30 GB gp3 is comfortable.
8. Launch the instance.

### Attach an Elastic IP

**EC2 → Elastic IPs → Allocate Elastic IP address → Associate** it with the instance. A stable public IP means the DNS record survives instance stops/starts.

---

## 2. Point DNS at the instance

Create these DNS records at your provider:

| Name | Type | Value | TTL |
|---|---|---|---|
| `habits.example.com` (or your domain) | `A` | Elastic IP | 300 s while testing |
| `www.habits.example.com` (optional) | `CNAME` | `habits.example.com` | 300 s |

Verify before continuing:

```bash
dig +short habits.example.com
```

The answer must be the Elastic IP. TLS issuance fails until DNS resolves to this instance.

> If you use Cloudflare, keep the record **DNS only** (grey cloud) for the first deploy. The orange-cloud proxy can work, but it complicates ACME and hides the origin; enable it later if you know you want it.

---

## 3. Bootstrap the server

From your local machine:

```bash
scp -i ~/path/habitual.pem deploy/ec2-bootstrap.sh ubuntu@YOUR_ELASTIC_IP:/home/ubuntu/
ssh -i ~/path/habitual.pem ubuntu@YOUR_ELASTIC_IP
chmod +x ~/ec2-bootstrap.sh
./ec2-bootstrap.sh
```

The script:

- installs Docker Engine and the Compose plugin,
- creates a 2 GB swap file if none exists (important on 1 GB instances),
- configures Docker log rotation (10 MB × 3),
- enables UFW with SSH/80/443 only,
- enables `fail2ban`,
- creates `~/habit-tracker`,
- adds your user to the `docker` group.

**Reconnect** (or run `newgrp docker`) so group membership takes effect:

```bash
exit
ssh -i ~/path/habitual.pem ubuntu@YOUR_ELASTIC_IP
docker version
docker compose version
```

---

## 4. Give the server access to GHCR

The first successful `main` build publishes a package such as `ghcr.io/your-user/habit-tracker`.

### Option A — public package (simplest)

GitHub → your profile/org → **Packages → habit-tracker → Package settings → Change visibility → Public**.

No login is needed on EC2.

### Option B — private package

Create a **classic personal access token** with the `read:packages` scope (and `repo` if the package belongs to a private repository). Then on EC2:

```bash
echo "YOUR_PAT" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

Credentials are stored in `~/.docker/config.json` for the `ubuntu` user and survive reboots. If your organization enforces SSO, authorize the token for the organization.

> `GITHUB_TOKEN` only exists inside Actions; it cannot be used on EC2.

---

## 5. Prepare the server directory and secrets

The deploy workflow copies `docker-compose.yml` and `Caddyfile` into `DEPLOY_PATH`, but **`.env` is created by you and never leaves the server**.

```bash
cd ~/habit-tracker
cp /path/to/.env.example .env    # or create it with the editor below
nano .env
chmod 600 .env
```

Example `.env`:

```dotenv
DOMAIN=habits.example.com
ACME_EMAIL=you@example.com

# Used for manual runs; CI overrides this with the sha-<commit> tag.
APP_IMAGE=ghcr.io/YOUR_GITHUB_USERNAME/habit-tracker:latest

POSTGRES_DB=habittracker
POSTGRES_USER=habit
POSTGRES_PASSWORD=<long random password>
JWT_SECRET=<output of: openssl rand -base64 48>
JWT_EXPIRATION_MS=86400000
APP_TIMEZONE=UTC
```

Generate secrets:

```bash
openssl rand -base64 48   # JWT_SECRET (at least 32 bytes)
openssl rand -base64 30   # POSTGRES_PASSWORD
```

Rules:

- `JWT_SECRET` must be at least 32 bytes and must not be reused across environments.
- `POSTGRES_PASSWORD` should be long and random; avoid `$` and backticks unless you quote carefully in `.env`.
- `DOMAIN` must match the DNS record exactly and have no `https://` prefix or trailing slash.
- Changing `JWT_SECRET` logs every user out (tokens no longer verify).
- Changing `POSTGRES_PASSWORD` after the database volume exists does **not** change the database password; see [Troubleshooting](#12-troubleshooting).

Validate the rendered configuration before starting:

```bash
cd ~/habit-tracker
docker compose config
```

This fails if a required variable is missing (`POSTGRES_PASSWORD`, `JWT_SECRET`, `DOMAIN`, `APP_IMAGE`).

---

## 6. Configure GitHub

### Secrets

Repository → **Settings → Secrets and variables → Actions → Secrets**:

| Secret | Value |
|---|---|
| `EC2_HOST` | Elastic IP or public DNS name |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Full contents of the private `.pem`, including the BEGIN/END lines |
| `DEPLOY_PATH` | `/home/ubuntu/habit-tracker` |
| `APP_URL` | `https://habits.example.com` (no trailing slash) |

### Variable

**Settings → Secrets and variables → Actions → Variables**:

| Variable | Value |
|---|---|
| `DEPLOY_ENABLED` | `true` once steps 1–5 are complete |

Leave `DEPLOY_ENABLED` unset/`false` until the server is ready. Until then, pushes to `main` still run checks and publish an image, but skip the deploy job.

### Production environment (optional but recommended)

The deploy job targets `environment: production`. Create **Settings → Environments → production** and optionally add required reviewers or restrict branches to `main`. This gives you a manual approval gate before a deploy.

### SSH host key

The workflow uses `appleboy/ssh-action` and does not persist a host key. For stricter verification, add a known-hosts step or pin the host key in a repository secret. At minimum, restrict SSH access to your IP and GitHub Actions runners, and disable password authentication.

---

## 7. First deployment

### Option A — let GitHub Actions do it

1. Commit and push to `main`.
2. Watch **Actions → CI/CD**:
   - `verify` runs Spotless, unit tests, H2 API tests, Testcontainers, and JaCoCo.
   - `docker` builds and pushes `latest` and `sha-<full-commit>` to GHCR.
   - `deploy` copies Compose files, runs `docker compose pull && up -d`, then polls the health endpoint for up to 2 minutes.
3. If the health check fails, the job prints the last 120 app log lines and exits non-zero.

### Option B — first deploy by hand

Useful for debugging the server before CI is involved. The image must already exist in GHCR, so push to `main` once first (with `DEPLOY_ENABLED` still unset).

```bash
ssh -i ~/path/habitual.pem ubuntu@YOUR_ELASTIC_IP
cd ~/habit-tracker

# If the workflow has not copied them yet:
# scp docker-compose.yml Caddyfile ubuntu@IP:~/habit-tracker/   (from your machine)

docker compose pull
docker compose up -d
docker compose ps
docker compose logs -f app
```

Open `https://habits.example.com` and register an account.

---

## 8. Verify the deployment

From your machine:

```bash
curl -fsS https://habits.example.com/actuator/health
# {"status":"UP"}

curl -I https://habits.example.com
# HTTP/2 200, strict-transport-security, x-content-type-options...

curl -I http://habits.example.com
# 308 redirect to HTTPS (Caddy)
```

Then:

1. Register a user and add a habit.
2. Check in today, refresh, and confirm the state persists.
3. Reboot the instance (or `docker compose restart`) and confirm data survives.
4. Inspect certificates: browser padlock → issued by Let's Encrypt / ZeroSSL for your domain.

---

## 9. Routine operations

All commands run on EC2 in `~/habit-tracker`.

| Task | Command |
|---|---|
| Service status | `docker compose ps` |
| Follow app logs | `docker compose logs -f --tail=200 app` |
| Follow Caddy logs | `docker compose logs -f --tail=200 caddy` |
| Restart app only | `docker compose restart app` |
| Recreate app after config change | `docker compose up -d --force-recreate app` |
| Pull latest manually | `docker compose pull && docker compose up -d` |
| Stop everything | `docker compose down` (keeps volumes) |
| Stop and delete data | `docker compose down -v` (**destroys the database**) |
| Disk usage | `docker system df` |
| Compose interpolation check | `docker compose config` |

### Deploy a new version

Push/merge to `main`. CI publishes `sha-<commit>` and `latest`, then deploys. The running container is replaced; PostgreSQL and Caddy data stay in named volumes.

To deploy a specific commit manually:

```bash
cd ~/habit-tracker
export APP_IMAGE=ghcr.io/OWNER/REPO:sha-<full-sha>
docker compose pull app
docker compose up -d --no-deps app
curl -fsS https://YOUR_DOMAIN/actuator/health
```

### Roll back

Find a previous image tag (GitHub Packages or `docker image ls`), then run the same commands with that tag:

```bash
export APP_IMAGE=ghcr.io/OWNER/REPO:sha-<previous-full-sha>
docker compose pull app
docker compose up -d --no-deps app
docker compose logs --tail=100 app
```

If a release included a new Flyway migration, rolling back the **application** may fail if the old code cannot tolerate the new schema. Prefer forward fixes, or restore the database from a backup taken before the release. Keep migrations backward-compatible for one release when possible.

---

## 10. Backups

The database lives in the `habit_pgdata` volume. Back it up off the instance.

### Manual backup

```bash
cd ~/habit-tracker
set -a; . ./.env; set +a
mkdir -p ~/backups
docker compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc \
  > ~/backups/habitual-$(date +%F-%H%M).dump
ls -lh ~/backups
```

### Daily cron with 14-day retention

```bash
crontab -e
```

Add:

```cron
15 3 * * * cd /home/ubuntu/habit-tracker && set -a && . ./.env && set +a && mkdir -p /home/ubuntu/backups && docker compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > /home/ubuntu/backups/habitual-$(date +\%F).dump && find /home/ubuntu/backups -type f -mtime +14 -delete
```

Copy backups to S3 or another host; a backup on the same disk does not survive instance loss:

```bash
aws s3 cp ~/backups/ s3://YOUR_BUCKET/habitual/ --recursive --exclude "*" --include "*.dump"
```

(Requires the AWS CLI and an instance role with `s3:PutObject`.)

### Restore

```bash
cd ~/habit-tracker
set -a; . ./.env; set +a
docker compose stop app
docker compose exec -T db pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  --clean --if-exists < ~/backups/habitual-YYYY-MM-DD.dump
docker compose start app
curl -fsS https://YOUR_DOMAIN/actuator/health
```

Test a restore into a scratch database occasionally; an untested backup is only a hope.

---

## 11. TLS, domain, and security notes

### Certificates

- Caddy obtains and renews certificates automatically.
- Ports `80` and `443` must be reachable from the internet for HTTP-01/TLS-ALPN challenges.
- Certificates and account keys live in the `caddy_data` volume. Do not delete it casually.
- To change domains: update `DOMAIN` in `.env`, point DNS at the Elastic IP, then `docker compose up -d --force-recreate caddy`. Caddy issues a new certificate; the old one remains in the volume.
- Let's Encrypt has rate limits. Fix DNS/firewall problems before retrying repeatedly.

### Hardening checklist

- [ ] Security group allows only `22` (your IP), `80`, `443`.
- [ ] UFW is active; `fail2ban` is running.
- [ ] SSH password authentication is disabled; only key auth is allowed.
- [ ] `.env` is `chmod 600` and never committed.
- [ ] `JWT_SECRET` and `POSTGRES_PASSWORD` are unique and strong.
- [ ] GHCR package is private unless you intentionally made it public.
- [ ] PostgreSQL and the app are not published on host ports.
- [ ] `unattended-upgrades` is enabled for security patches.
- [ ] Backups run daily and are copied off-instance.
- [ ] An AWS budget alarm is configured.
- [ ] Docker and the base images are updated periodically (`docker compose pull`, rebuild).
- [ ] The app currently has no login rate limiting; put it behind Cloudflare/WAF or add a limiter before public promotion.

---

## 12. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Deploy job is skipped | `DEPLOY_ENABLED` is not exactly `true`, or the run is a PR | Set the repository **variable** (not secret) to `true`; merge/push to `main` |
| `docker compose` complains about a missing variable | `.env` is missing or unreadable | `cd ~/habit-tracker && docker compose config`; check `DOMAIN`, `APP_IMAGE`, `POSTGRES_PASSWORD`, `JWT_SECRET` |
| `unauthorized` / `denied` pulling GHCR | Private package and no login on EC2 | `docker login ghcr.io` with a `read:packages` PAT, or make the package public |
| Caddy cannot get a certificate | DNS not pointing at the IP, ports 80/443 blocked, or another server on those ports | `dig +short DOMAIN`; check security group/UFW; `sudo ss -ltnp \| grep -E ':80\|:443'`; `docker compose logs caddy` |
| `502 Bad Gateway` | App is down or unhealthy | `docker compose ps`; `docker compose logs --tail=200 app`; check `JWT_SECRET`/datasource env and database health |
| App exits at startup with “Could not resolve placeholder” | Required env var missing in production | `docker compose config`; add the variable to `.env`; `docker compose up -d` |
| `password authentication failed for user "habit"` after changing the password | The volume still has the old password | Revert `.env`, or run `ALTER USER habit WITH PASSWORD '...'` inside `psql`; recreating the volume deletes data |
| Health check fails in CI though the site works | `APP_URL` wrong, TLS not ready, or path blocked | `curl -fsS https://DOMAIN/actuator/health` from your machine and from EC2; fix `APP_URL` secret |
| Flyway validation error | Entity/schema mismatch or edited migration | Never edit applied migrations; add `V2__...sql`; in dev, reset the volume if acceptable |
| Disk full | Old images/logs | `docker system df`; `docker image prune -af`; `docker builder prune -f`; keep log rotation enabled |
| Instance becomes unresponsive | Memory pressure on t3.micro | Confirm swap (`swapon --show`); consider t3.small; `docker stats` |
| Everyone is logged out | `JWT_SECRET` changed | Expected; users log in again |
| Port 80/443 already allocated | Host nginx/apache running | `sudo systemctl disable --now nginx apache2` or free the ports |

Useful log commands:

```bash
cd ~/habit-tracker
docker compose ps
docker compose logs --tail=200 app
docker compose logs --tail=200 caddy
docker compose logs --tail=100 db
journalctl -u docker --no-pager -n 100
```

---

## 13. Cost and teardown

- The Elastic IP is free while attached to a running instance; AWS may charge for unattached IPs.
- Stop the instance when unused; the Elastic IP remains associated and data stays on the EBS volume.
- To remove everything: `docker compose down -v` on the server, then terminate the instance, release the Elastic IP, delete the security group and any EBS snapshots.
- GitHub Actions minutes are free for public repositories (within limits); GHCR storage is free within the included tier for public packages.

---

## 14. Deployment checklist

Before the first deploy:

- [ ] EC2 running Ubuntu 24.04 with an Elastic IP
- [ ] Security group: 22 from your IP, 80/443 from anywhere
- [ ] `deploy/ec2-bootstrap.sh` completed; Docker works without `sudo`
- [ ] DNS `A` record points at the Elastic IP and resolves
- [ ] `~/habit-tracker/.env` created, `chmod 600`, secrets generated
- [ ] `docker compose config` succeeds on the server
- [ ] GHCR package exists (push to `main` once) and EC2 can pull it
- [ ] GitHub secrets `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `DEPLOY_PATH`, `APP_URL` set
- [ ] Repository variable `DEPLOY_ENABLED=true` set last
- [ ] `https://DOMAIN/actuator/health` returns `{"status":"UP"}`
- [ ] Register/login/check-in works in a browser
- [ ] Backups scheduled and copied off-instance
- [ ] AWS budget alarm configured

---

## Related documents

- [../README.md](../README.md) — features, local quick start, API overview
- [ARCHITECTURE.md](ARCHITECTURE.md) — code structure, data model, streak semantics, design trade-offs
