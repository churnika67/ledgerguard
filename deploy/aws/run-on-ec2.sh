#!/usr/bin/env bash
# Runs ON the EC2 instance itself (copy it there, don't run it on your Mac). Sets up swap so a
# 1GB t3.micro can survive a Maven build without getting OOM-killed, installs Docker and git,
# clones the repo, builds the ledger-service image, and runs it as a container that restarts on
# its own if it ever crashes or the instance reboots.
#
# How to use it:
#   scp -i ledgerguard-key.pem deploy/aws/run-on-ec2.sh ec2-user@<PUBLIC_IP>:~
#   ssh -i ledgerguard-key.pem ec2-user@<PUBLIC_IP>
#   nano ledgerguard.env          # see the template this script prints below -- fill in real values
#   chmod +x run-on-ec2.sh && ./run-on-ec2.sh
#
# ledgerguard.env lives only on this instance -- it is never part of the git repo, and this
# script never prints its contents.

set -euo pipefail

REPO_URL="https://github.com/churnika67/ledgerguard.git"
REPO_DIR="$HOME/ledgerguard"
ENV_FILE="$HOME/ledgerguard.env"

if [ ! -f "$ENV_FILE" ]; then
    cat > "$ENV_FILE" <<'TEMPLATE'
# Fill these in, then re-run run-on-ec2.sh. This file stays on this instance only.
DB_URL=jdbc:postgresql://<your-neon-host>/<your-db>?sslmode=require
DB_USERNAME=<your-neon-username>
DB_PASSWORD=<your-neon-password>
STATEMENTS_BUCKET=<the-bucket-name-from-setup.sh>
AWS_REGION=us-east-1
JAVA_TOOL_OPTIONS=-Xmx384m
TEMPLATE
    chmod 600 "$ENV_FILE"
    echo "Wrote a template to $ENV_FILE -- edit it with your real Neon credentials and the"
    echo "bucket name, then run this script again."
    exit 0
fi

echo "== 1. Swap (2GB) =="
if ! swapon --show | grep -q /swapfile; then
    sudo dd if=/dev/zero of=/swapfile bs=128M count=16
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo "/swapfile swap swap defaults 0 0" | sudo tee -a /etc/fstab
fi
free -h

echo "== 2. Docker + git =="
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
# Picks up the new docker group membership for the rest of this script without a fresh login.
if ! groups | grep -q docker; then
    exec sg docker "$0"
fi

echo "== 3. Clone/update the repo =="
if [ -d "$REPO_DIR" ]; then
    git -C "$REPO_DIR" pull
else
    git clone "$REPO_URL" "$REPO_DIR"
fi

echo "== 4. Build the image =="
docker build -f "$REPO_DIR/ledger-service/Dockerfile" -t ledgerguard-ledger-service "$REPO_DIR"

echo "== 5. Run it =="
docker rm -f ledger-service 2>/dev/null || true
docker run -d \
    --name ledger-service \
    --restart unless-stopped \
    -p 8080:8080 \
    --env-file "$ENV_FILE" \
    --log-driver awslogs \
    --log-opt awslogs-region=us-east-1 \
    --log-opt awslogs-group=/ledgerguard/ledger-service \
    --log-opt awslogs-create-group=true \
    ledgerguard-ledger-service

echo ""
echo "Running. Check status with: docker ps"
echo "Check logs with:            docker logs -f ledger-service"
echo "Health check:                curl http://localhost:8080/actuator/health"
