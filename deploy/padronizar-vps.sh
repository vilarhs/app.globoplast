#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_HOST="${GLOBOPLAST_DEPLOY_HOST:-ubuntu@168.138.142.85}"
SSH_KEY="${GLOBOPLAST_SSH_KEY:-$HOME/Documents/ssh-key-2026-07-30.key}"
VERSION="$(python3 -c 'import sys, xml.etree.ElementTree as ET; root=ET.parse(sys.argv[1]).getroot(); ns=root.tag.split("}")[0]+"}"; print(root.find(ns+"version").text)' "$ROOT_DIR/pom.xml")"
JAR="$ROOT_DIR/target/globoplast.jar"
REMOTE_DIR="/tmp/globoplast-padronizacao-$VERSION"

test -r "$SSH_KEY" || { echo "ERRO: chave SSH ausente: $SSH_KEY" >&2; exit 1; }

echo "[1/7] Build e preflight da versão $VERSION"
(cd "$ROOT_DIR" && bash deploy/preflight.sh)
test -f "$JAR" || { echo "ERRO: JAR ausente: $JAR" >&2; exit 1; }

SSH=(ssh -i "$SSH_KEY" -o BatchMode=yes)
SCP=(scp -i "$SSH_KEY" -o BatchMode=yes)

echo "[2/7] Preparando arquivos temporários na VPS"
"${SSH[@]}" "$DEPLOY_HOST" "mkdir -p '$REMOTE_DIR'"
"${SCP[@]}" \
  "$JAR" \
  "$ROOT_DIR/deploy/globoplast.service" \
  "$ROOT_DIR/deploy/globoplast-backup.service" \
  "$ROOT_DIR/deploy/globoplast-backup.timer" \
  "$ROOT_DIR/deploy/globoplast-backup-drive.service" \
  "$ROOT_DIR/deploy/globoplast-backup-drive.timer" \
  "$ROOT_DIR/deploy/globoplast-backup" \
  "$ROOT_DIR/deploy/globoplast-backup-check" \
  "$ROOT_DIR/deploy/globoplast-backup-drive" \
  "$DEPLOY_HOST:$REMOTE_DIR/"

echo "[3/7] Validando backup atual"
"${SSH[@]}" "$DEPLOY_HOST" \
  "sudo systemctl start globoplast-java-backup.service && sudo /usr/local/sbin/globoplast-java-backup-check --directory /var/backups/globoplast-java"

echo "[4/7] Padronizando diretórios, serviço, banco e backups"
"${SSH[@]}" "$DEPLOY_HOST" bash -s -- "$REMOTE_DIR" "$VERSION" <<'REMOTE'
set -Eeuo pipefail

STAGE="${1:?diretório temporário ausente}"
VERSION="${2:?versão ausente}"

test -d /opt/globoplast-java || { echo 'ERRO: instalação antiga não encontrada' >&2; exit 1; }
test ! -e /opt/globoplast || { echo 'ERRO: /opt/globoplast já existe' >&2; exit 1; }
test ! -e /var/lib/globoplast || { echo 'ERRO: /var/lib/globoplast já existe' >&2; exit 1; }
test ! -e /var/backups/globoplast || { echo 'ERRO: /var/backups/globoplast já existe' >&2; exit 1; }

sudo systemctl disable --now globoplast-java-backup.timer >/dev/null
sudo systemctl disable --now globoplast-java-backup-drive.timer >/dev/null 2>&1 || true
sudo systemctl stop globoplast-java

sudo mv /var/lib/globoplast-java /var/lib/globoplast
sudo mv /var/backups/globoplast-java /var/backups/globoplast
sudo mv /etc/globoplast-java.env /etc/globoplast.env

sudo install -d -o ubuntu -g ubuntu -m 0755 /opt/globoplast/releases
sudo install -o ubuntu -g ubuntu -m 0644 \
  /opt/globoplast-java/target/globoplast-java-0.0.104.backup.jar \
  /opt/globoplast/releases/globoplast-0.0.105.jar
sudo install -o ubuntu -g ubuntu -m 0644 \
  /opt/globoplast-java/target/globoplast-java.jar \
  /opt/globoplast/releases/globoplast-0.0.106.jar
sudo install -o ubuntu -g ubuntu -m 0644 \
  "$STAGE/globoplast.jar" \
  "/opt/globoplast/releases/globoplast-$VERSION.jar"
sudo ln -s "releases/globoplast-$VERSION.jar" /opt/globoplast/globoplast.jar

sudo install -o root -g root -m 0755 "$STAGE/globoplast-backup" /usr/local/sbin/globoplast-backup
sudo install -o root -g root -m 0755 "$STAGE/globoplast-backup-check" /usr/local/sbin/globoplast-backup-check
sudo install -o root -g root -m 0755 "$STAGE/globoplast-backup-drive" /usr/local/sbin/globoplast-backup-drive
sudo install -o root -g root -m 0644 "$STAGE/globoplast.service" /etc/systemd/system/globoplast.service
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup.service" /etc/systemd/system/globoplast-backup.service
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup.timer" /etc/systemd/system/globoplast-backup.timer
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup-drive.service" /etc/systemd/system/globoplast-backup-drive.service
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup-drive.timer" /etc/systemd/system/globoplast-backup-drive.timer

sudo chown -R ubuntu:ubuntu /opt/globoplast /var/lib/globoplast /var/backups/globoplast
sudo chmod 600 /etc/globoplast.env
sudo systemctl daemon-reload
sudo systemctl enable --now globoplast.service
sudo systemctl enable --now globoplast-backup.timer

HEALTH=""
for _ in $(seq 1 30); do
  HEALTH="$(curl -fsS http://127.0.0.1:8080/health 2>/dev/null || true)"
  if [[ "$HEALTH" == *'"ok":true'* && "$HEALTH" == *"\"versao\":\"$VERSION\""* ]]; then
    break
  fi
  sleep 1
done

if [[ "$HEALTH" != *'"ok":true'* || "$HEALTH" != *"\"versao\":\"$VERSION\""* ]]; then
  echo 'ERRO: nova instalação não passou no health check; usando release 0.0.106' >&2
  sudo ln -sfn releases/globoplast-0.0.106.jar /opt/globoplast/.globoplast.jar.rollback
  sudo mv -Tf /opt/globoplast/.globoplast.jar.rollback /opt/globoplast/globoplast.jar
  sudo systemctl restart globoplast
  sudo journalctl -u globoplast -n 100 --no-pager >&2
  exit 1
fi

echo "$HEALTH"
REMOTE

echo "[5/7] Criando e validando backup na estrutura nova"
"${SSH[@]}" "$DEPLOY_HOST" \
  "sudo systemctl start globoplast-backup.service && sudo /usr/local/sbin/globoplast-backup-check --directory /var/backups/globoplast"

echo "[6/7] Removendo somente estruturas antigas e temporários identificados"
"${SSH[@]}" "$DEPLOY_HOST" bash -s -- "$REMOTE_DIR" <<'REMOTE'
set -Eeuo pipefail
STAGE="${1:?diretório temporário ausente}"

systemctl is-active --quiet globoplast
curl -fsS http://127.0.0.1:8080/health | grep -q '"ok":true'

sudo systemctl disable --now globoplast-java.service >/dev/null 2>&1 || true
sudo systemctl disable --now globoplast-java-backup.timer >/dev/null 2>&1 || true
sudo systemctl disable --now globoplast-java-backup-drive.timer >/dev/null 2>&1 || true

sudo rm -f \
  /etc/systemd/system/globoplast-java.service \
  /etc/systemd/system/globoplast-java-backup.service \
  /etc/systemd/system/globoplast-java-backup.timer \
  /etc/systemd/system/globoplast-java-backup-drive.service \
  /etc/systemd/system/globoplast-java-backup-drive.timer \
  /usr/local/sbin/globoplast-java-backup \
  /usr/local/sbin/globoplast-java-backup-check \
  /usr/local/sbin/globoplast-java-backup-drive
sudo rm -rf /etc/systemd/system/globoplast-java.service.d
sudo rm -rf /opt/globoplast-java
sudo rm -rf /opt/globoplast-java-backup-20260819_035152

sudo find /var/backups/globoplast -maxdepth 1 -type f \
  \( -name '.*.db.partial-wal' -o -name '.*.db.partial-shm' -o -name '.*.db.partial-journal' \) \
  -delete
sudo find /tmp -maxdepth 1 -type f \
  \( -name 'globoplast-java-*.jar' -o -name 'globoplast-java-v*.zip' -o -name 'globoplast.zip' \) \
  -delete
sudo find /tmp -maxdepth 1 -type d -name 'globoplast-java-v*-build' -exec rm -rf -- {} +
sudo find /tmp -maxdepth 1 -type f \
  \( -name 'globoplast-verify-*.db-wal' -o -name 'globoplast-verify-*.db-shm' \) \
  -delete
sudo rm -rf -- "$STAGE"
sudo systemctl daemon-reload
sudo systemctl reset-failed
REMOTE

echo "[7/7] Auditoria final"
"${SSH[@]}" "$DEPLOY_HOST" \
  "systemctl is-active globoplast; systemctl is-enabled globoplast globoplast-backup.timer; curl -fsS http://127.0.0.1:8080/health; echo; sudo du -sh /opt/globoplast /var/lib/globoplast /var/backups/globoplast; sudo find /tmp -maxdepth 1 -name 'globoplast*' -print"
