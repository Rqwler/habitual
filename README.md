# Habitual — Habit Tracker

A production-shaped mini project: a **Java 21 / Spring Boot** REST API, a **PostgreSQL** database, and a hand-built **HTML/CSS/JavaScript** web app. It includes JWT login, per-user habits, daily check-ins, a monthly calendar, streaks, stats, importance ordering, Docker, and a GitHub Actions pipeline that publishes to GHCR and deploys to EC2 behind Caddy.

![Stack](https://img.shields.io/badge/Java-21-245b43) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-245b43) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-245b43) ![Docker](https://img.shields.io/badge/Docker-compose-245b43) ![License](https://img.shields.io/badge/license-MIT-245b43)

---

## What is included

| Area | Feature |
|---|---|
| Auth | Register, log in, JWT bearer tokens, BCrypt hashing, user profile endpoint |
| Isolation | Every habit and check-in query is scoped to the logged-in user |
| Habits | Create, edit, delete, category/tag, description, archive/unarchive |
| Frequency | Every day, selected weekdays, or “N times per week” |
| Daily tracker | Today's scheduled habits, one-tap check-in/undo, weekly progress |
| Calendar | Month view per habit; click a day to toggle a check-in; future days disabled |
| Streaks | Current and longest streak per habit with frequency-aware logic |
| Stats | Today / this week / this month completion, best streak, total check-ins, 365-day heatmap API |
| Ordering | Drag-and-drop plus accessible up/down buttons; priority persisted per user |
| Extras | Notes on check-ins (API), archived habits, domain event on completion, OpenAPI docs |
| Quality | JUnit 5 + Mockito, MockMvc API tests, H2 fast tests, Testcontainers PostgreSQL test, Spotless, JaCoCo gate |
| Runtime | Multi-stage Dockerfile, non-root container, health check, Docker Compose dev + prod stacks |
| Delivery | GitHub Actions: verify → image to GHCR → SSH deploy → health check; Caddy TLS; EC2 bootstrap script |

The UI is served by Spring Boot from `src/main/resources/static`, so the whole app is a single deployable image with no separate frontend build.

---

## Architecture at a glance

```text
Developer → git push → GitHub PR checks → merge to main
    → GitHub Actions: verify + Testcontainers → build image → push to GHCR
    → SSH deploy → EC2: docker compose pulls image
    → [Caddy :443] → [Spring Boot :8080] → [PostgreSQL (Docker volume)]
```

Inside the application:

```text
Browser (HTML/CSS/vanilla JS)
   │  fetch + Bearer JWT
   ▼
Spring Security JWT filter
   ▼
Controller  →  Service  →  Repository  →  PostgreSQL
                  │
                  ├─ HabitFrequencyStrategy (daily / weekdays / weekly target)
                  ├─ HabitCompletedEvent (extension point)
                  └─ Flyway migrations at startup
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the data model and design decisions.

---

## Quick start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker Desktop (or Docker Engine + Compose plugin)

### Option A — local Postgres in Docker, app from Maven (fastest dev loop)

```bash
# 1. Start only the database
docker compose -f docker-compose.dev.yml up -d db

# 2. Run the app (default profile is "dev")
mvn spring-boot:run
```

Open **http://localhost:8080**, create an account, and add a habit.

The app loads `application-dev.yml`, connects to `localhost:5432` with `habit / habit`, and Flyway creates the schema on first boot.

### Option B — everything in Docker

```bash
docker compose -f docker-compose.dev.yml --profile app up --build
```

Open **http://localhost:8080**. Data is stored in the `habit_pgdata_dev` volume.

Stop the stack:

```bash
docker compose -f docker-compose.dev.yml down          # keep data
docker compose -f docker-compose.dev.yml down -v       # delete data too
```

### First run checklist

1. Register at `/` (email, name, password of at least 8 characters).
2. Click **New habit**, choose a schedule, and save.
3. Use the check circle in **Today's rhythm** or click a day in the calendar.
4. Drag habits by the six-dot handle (or use the up/down arrows) to order them by importance.
5. Watch streaks and the dashboard update.

OpenAPI UI: **http://localhost:8080/swagger-ui.html**  
Health probe: **http://localhost:8080/actuator/health**

---

## Tests and quality

```bash
mvn verify                 # unit + H2 API tests + Spotless + JaCoCo (no Docker needed)
mvn verify -Pit            # additionally runs the Testcontainers PostgreSQL test
mvn spotless:apply         # format Java sources
```

| Check | Where |
|---|---|
| Streak, frequency, and metrics unit tests (JUnit 5 + Mockito) | `src/test/java/.../habit` |
| API/auth/habit integration tests (H2) | `HabitApiIntegrationTest` |
| Real PostgreSQL + Flyway test | `HabitApiIT` (`@Testcontainers`, skipped without Docker) |
| Style gate | Spotless `check` bound to `verify` |
| Coverage gate | JaCoCo line coverage ≥ 60%; report at `target/site/jacoco/index.html` |

The CI workflow runs `mvn -B -ntp verify -Pit` on every pull request.

---

## API overview

All habit and stats endpoints require `Authorization: Bearer <token>`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create account, returns JWT |
| `POST` | `/api/auth/login` | Log in, returns JWT |
| `GET` | `/api/auth/me` | Current user |
| `GET` | `/api/habits?date=YYYY-MM-DD` | Active habits, streaks and today's state |
| `POST` | `/api/habits` | Create habit |
| `GET` | `/api/habits/{id}` | One habit |
| `PUT` | `/api/habits/{id}` | Update habit |
| `DELETE` | `/api/habits/{id}` | Delete habit and its logs |
| `PUT` | `/api/habits/order` | Persist importance order (`habitIds` array) |
| `POST` | `/api/habits/{id}/check-ins` | Check in (`{"date":"YYYY-MM-DD","note":"optional"}`) |
| `DELETE` | `/api/habits/{id}/check-ins?date=YYYY-MM-DD` | Undo a check-in |
| `GET` | `/api/habits/{id}/calendar?year=&month=` | Month data for one habit |
| `POST` | `/api/habits/{id}/archive` | Archive a habit |
| `POST` | `/api/habits/{id}/unarchive` | Restore a habit |
| `GET` | `/api/stats/dashboard?date=YYYY-MM-DD` | Today/week/month summary |
| `GET` | `/api/stats/heatmap?days=365` | Per-day completed/scheduled counts |
| `GET` | `/actuator/health` | Deployment health check |

### Example

```bash
API=http://localhost:8080

TOKEN=$(curl -s -X POST "$API/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com","displayName":"Alex","password":"password123"}' \
  | jq -r .token)

curl -s -X POST "$API/api/habits" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Read 10 pages","category":"Learning","frequencyType":"DAILY"}'

curl -s -X POST "$API/api/habits/1/check-ins" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-11-14"}'
```

Habit request body:

```json
{
  "name": "Strength training",
  "description": "30 minutes",
  "category": "Fitness",
  "frequencyType": "SPECIFIC_DAYS",
  "weekdays": [1, 3, 5]
}
```

- `DAILY`: no extra fields.
- `SPECIFIC_DAYS`: `weekdays` is a non-empty list of ISO weekdays (`1` = Monday … `7` = Sunday).
- `WEEKLY`: `timesPerWeek` between 1 and 7; check-ins are allowed on any day.

---

## How streaks work

- **Daily** — consecutive completed days. An unchecked *today* does not break the streak until the day ends.
- **Specific days** — only scheduled weekdays count; missing a scheduled day in the past breaks the streak. Today is given the same end-of-day grace.
- **Weekly target** — consecutive weeks where the target was met. The current in-progress week counts if the target is already met, but failing to meet it yet does not break the streak.
- **Longest** streaks are computed from the full check-in history.
- **Completion rate** is completed check-ins ÷ expected check-ins for the period, capped at 100%. Expected counts start from the habit's creation date.

The UI always sends the browser's local date for reads and writes, so the display does not depend on the server timezone. `APP_TIMEZONE` is only the server-side fallback for defaults and future-date validation.

---

## Project structure

```text
habit-tracker/
├─ src/main/java/com/habittracker/
│  ├─ auth/                 # register, login, JWT issue
│  ├─ common/               # API errors + exception handling
│  ├─ config/               # security, Clock, OpenAPI
│  ├─ event/                # HabitCompletedEvent + listener
│  ├─ habit/                # CRUD, check-ins, calendar, DTOs
│  │  └─ frequency/         # strategy per schedule type
│  ├─ log/                  # HabitLog entity + repository
│  ├─ security/             # JWT service/filter, UserDetails
│  ├─ stats/                # dashboard + heatmap
│  └─ user/                 # User entity + repository
├─ src/main/resources/
│  ├─ static/               # index.html, app.html, CSS, JS
│  ├─ db/migration/         # Flyway V1__init.sql
│  ├─ application.yml
│  ├─ application-dev.yml
│  └─ application-prod.yml
├─ src/test/                # unit, H2 API, Testcontainers IT
├─ .github/workflows/ci.yml # PR checks, GHCR publish, EC2 deploy
├─ deploy/ec2-bootstrap.sh  # one-time Ubuntu server setup
├─ Dockerfile
├─ docker-compose.dev.yml
├─ docker-compose.yml
├─ Caddyfile
└─ docs/
   ├─ ARCHITECTURE.md
   └─ DEPLOYMENT.md
```

---

## Configuration

| Variable | Used by | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | app | JDBC URL; Compose sets it to the `db` service |
| `SPRING_DATASOURCE_USERNAME` | app | Database user |
| `SPRING_DATASOURCE_PASSWORD` | app | Database password |
| `JWT_SECRET` | app | **Required in prod**; at least 32 bytes |
| `JWT_EXPIRATION_MS` | app | Token lifetime, default 24 hours |
| `APP_TIMEZONE` | app | Default zone for “today”, default `UTC` |
| `PORT` | app | HTTP port, default `8080` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | db | Compose database settings |
| `APP_IMAGE` | prod Compose | GHCR image reference |
| `DOMAIN` | Caddy | DNS name with an A record to the EC2 Elastic IP |
| `ACME_EMAIL` | Caddy | Contact address for ACME certificate notifications |
| `SPRING_PROFILES_ACTIVE` | app | `dev` locally, `prod` in Compose |

Secrets are never committed. `application-prod.yml` deliberately has no JWT or datasource fallbacks, so the container fails fast if a secret is missing. The dev fallback exists only for local development.

---

## CI/CD workflow

Workflow file: `.github/workflows/ci.yml`

```text
Pull request to main
  └─ verify job: Spotless + unit tests + H2 API tests + Testcontainers + JaCoCo

Push/merge to main
  ├─ verify job
  ├─ docker job: build image, tag latest + sha-<commit>, push to GHCR
  └─ deploy job (when repository variable DEPLOY_ENABLED=true)
       ├─ scp docker-compose.yml + Caddyfile to EC2
       ├─ ssh: docker compose pull && up -d
       └─ poll https://YOUR_DOMAIN/actuator/health until UP
```

### Required repository secrets

| Secret | Example |
|---|---|
| `EC2_HOST` | `ec2-203-0-113-10.compute-1.amazonaws.com` or Elastic IP |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | PEM private key contents |
| `DEPLOY_PATH` | `/home/ubuntu/habit-tracker` |
| `APP_URL` | `https://habits.example.com` |

### Required repository variable

| Variable | Value |
|---|---|
| `DEPLOY_ENABLED` | `true` only after the server and secrets are ready |

Set `DEPLOY_ENABLED` last. Until then, main pushes still build and publish the image but do not attempt a deploy. Full instructions: **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

---

## EC2 / production summary

1. Launch Ubuntu 24.04 `t3.micro` (or `t3.small`), attach an Elastic IP.
2. Security group: allow `22`, `80`, `443` only. Do not open `5432` or `8080`.
3. Run `deploy/ec2-bootstrap.sh` (Docker, Compose, UFW, fail2ban, 2 GB swap).
4. Point `DOMAIN` at the Elastic IP.
5. Put `.env` (from `.env.example`) and the compose files in `~/habit-tracker`.
6. Give the server access to the GHCR package (public package or `docker login ghcr.io` once).
7. Set the GitHub secrets/variable above, then push to `main`.

Caddy obtains and renews TLS certificates automatically. PostgreSQL is only reachable on the private Compose network. See the deployment guide for backups, logs, rollback, and troubleshooting.

---

## Design choices and extension points

- **Layered packages with DTOs** keep controllers thin and make it easy to add a React/mobile client later.
- **Frequency strategy interface** means a new schedule (e.g. “every 3 days” or “twice a month”) is a new class, not a rewrite of streak code.
- **Flyway from day one** (`V1__init.sql`) plus Hibernate `validate` in prod. Add `V2__...sql` for future changes.
- **Domain event on check-in** (`HabitCompletedEvent`) is ready for reminders, badges, analytics, or activity feeds.
- **Stateless JWT** keeps deployment simple; the frontend stores it in `localStorage`. For a higher-security deployment, move the token to an HttpOnly, SameSite cookie and add refresh-token rotation.
- **No frontend build step** keeps the mini project deployable as one artifact; the API is ready for a richer client.
- **Profile separation** (`dev`, `test`, `prod`) with environment-driven secrets.
- **Non-root, multi-stage container** with an actuator health check.

### Implemented vs next

Implemented beyond the core v1: calendar data, check-in notes in the API, archive/unarchive, CSV-ready data model, 365-day heatmap endpoint.

Not yet built (clean extension points):
- CSV export endpoint
- Goals such as “20 days this month”
- Notes and archive management in the UI
- Reminders/badges consuming `HabitCompletedEvent`
- Refresh tokens and password reset

---

## License

MIT. Use it, fork it, and make it yours.
