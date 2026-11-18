#!/usr/bin/env bash
#
# One-time bootstrap for an Ubuntu 24.04 EC2 instance (t3.micro or larger).
# Installs Docker Engine + Compose plugin, adds a 2 GB swap file (important on
# 1 GB instances), configures UFW and Docker log rotation.
#
# Run as the default Ubuntu user:  ./ec2-bootstrap.sh
#
set -euo pipefail

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
  DEPLOY_USER="${SUDO_USER:-root}"
else
  SUDO="sudo"
  DEPLOY_USER="${SUDO_USER:-$USER}"
fi

echo "==> Updating packages"
$SUDO apt-get update -y
$SUDO apt-get install -y ca-certificates curl gnupg ufw fail2ban

echo "==> Creating swap if none exists"
if ! swapon --show | grep -q .; then
  if ! $SUDO fallocate -l 2G /swapfile 2>/dev/null; then
    $SUDO dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  fi
  $SUDO chmod 600 /swapfile
  $SUDO mkswap /swapfile
  $SUDO swapon /swapfile
  echo '/swapfile none swap sw 0 0' | $SUDO tee -a /etc/fstab >/dev/null
  echo "Swap enabled."
else
  echo "Swap already configured."
fi

echo "==> Installing Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | $SUDO sh
fi
$SUDO usermod -aG docker "${DEPLOY_USER}" || true

echo "==> Configuring Docker log rotation"
$SUDO mkdir -p /etc/docker
if [[ ! -f /etc/docker/daemon.json ]]; then
  cat <<'EOF' | $SUDO tee /etc/docker/daemon.json >/dev/null
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF
fi
$SUDO systemctl enable docker
$SUDO systemctl restart docker

echo "==> Configuring firewall (SSH, HTTP, HTTPS)"
$SUDO ufw allow OpenSSH
$SUDO ufw allow 80/tcp
$SUDO ufw allow 443/tcp
$SUDO ufw --force enable

echo "==> Enabling fail2ban"
$SUDO systemctl enable --now fail2ban

mkdir -p "${HOME}/habit-tracker"

cat <<EOF

Bootstrap complete.

Next steps:
  1. Reconnect over SSH so your user picks up the docker group
     (or run: newgrp docker).
  2. Copy docker-compose.yml, Caddyfile and .env into ~/habit-tracker.
  3. Point the domain's A record at this instance's Elastic IP.
  4. Run: cd ~/habit-tracker && docker compose pull && docker compose up -d
  5. Check: curl -fsS https://YOUR_DOMAIN/actuator/health

Docker version:
$(docker --version)

Compose version:
$(docker compose version)

Deploy user: ${DEPLOY_USER}
Deploy directory: ${HOME}/habit-tracker
EOF