#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_HOST="${GLOBOPLAST_DEPLOY_HOST:-ubuntu@168.138.142.85}"
SSH_KEY="${GLOBOPLAST_SSH_KEY:-$HOME/Documents/ssh-key-2026-07-30.key}"
SKIP_BUILD=false

usage() {
  cat <<'EOF'
Uso: bash deploy/deploy-vps.sh [opções]

Opções:
  --host usuario@servidor   Destino SSH (padrão: ubuntu@168.138.142.85)
  --key caminho             Chave SSH privada
  --skip-build              Usa o JAR já existente em target/
  -h, --help                Mostra esta ajuda

Também aceita GLOBOPLAST_DEPLOY_HOST e GLOBOPLAST_SSH_KEY.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) DEPLOY_HOST="${2:?informe usuario@servidor}"; shift 2 ;;
    --key) SSH_KEY="${2:?informe o caminho da chave}"; shift 2 ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opção desconhecida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

test -r "$SSH_KEY" || { echo "ERRO: chave SSH ausente: $SSH_KEY" >&2; exit 1; }
VERSION="$(python3 -c 'import sys, xml.etree.ElementTree as ET; root=ET.parse(sys.argv[1]).getroot(); ns=root.tag.split("}")[0]+"}"; print(root.find(ns+"version").text)' "$ROOT_DIR/pom.xml")"
test -n "$VERSION" || { echo 'ERRO: não foi possível ler a versão do pom.xml' >&2; exit 1; }

if [[ "$SKIP_BUILD" != true ]]; then
  echo "[1/5] Validando e compilando Globoplast $VERSION"
  (cd "$ROOT_DIR" && bash deploy/preflight.sh)
else
  echo "[1/5] Build ignorado por --skip-build"
fi

JAR="$ROOT_DIR/target/globoplast.jar"
test -f "$JAR" || { echo "ERRO: JAR ausente: $JAR" >&2; exit 1; }

SSH=(ssh -i "$SSH_KEY" -o BatchMode=yes)
SCP=(scp -i "$SSH_KEY" -o BatchMode=yes)
REMOTE_STAGE="/tmp/globoplast-deploy-$VERSION"

echo "[2/5] Enviando Globoplast $VERSION para $DEPLOY_HOST"
"${SSH[@]}" "$DEPLOY_HOST" "rm -rf '$REMOTE_STAGE' && mkdir -p '$REMOTE_STAGE'"
"${SCP[@]}" \
  "$JAR" \
  "$ROOT_DIR/deploy/globoplast.service" \
  "$ROOT_DIR/deploy/globoplast-backup.service" \
  "$ROOT_DIR/deploy/globoplast-backup.timer" \
  "$ROOT_DIR/deploy/globoplast-backup-drive.service" \
  "$ROOT_DIR/deploy/globoplast-backup-drive.timer" \
  "$ROOT_DIR/deploy/globoplast-system-backup-drive.service" \
  "$ROOT_DIR/deploy/globoplast-system-backup-drive.timer" \
  "$ROOT_DIR/deploy/globoplast-backup" \
  "$ROOT_DIR/deploy/globoplast-backup-check" \
  "$ROOT_DIR/deploy/globoplast-backup-drive" \
  "$ROOT_DIR/deploy/globoplast-system-backup-drive" \
  "$DEPLOY_HOST:$REMOTE_STAGE/"

echo "[3/5] Instalando release com troca atômica"
"${SSH[@]}" "$DEPLOY_HOST" bash -s -- "$REMOTE_STAGE" "$VERSION" <<'REMOTE'
set -Eeuo pipefail

STAGE="${1:?diretório temporário ausente}"
VERSION="${2:?versão ausente}"
UPLOAD="$STAGE/globoplast.jar"
APP_DIR=/opt/globoplast
RELEASE_DIR="$APP_DIR/releases"
CURRENT_LINK="$APP_DIR/globoplast.jar"
SERVICE=globoplast
EXPECTED="version=$VERSION"
PREVIOUS=""

cleanup() {
  sudo rm -rf -- "$STAGE"
}
trap cleanup EXIT

test -f "$UPLOAD" || { echo "ERRO: upload ausente: $UPLOAD" >&2; exit 1; }
test -d "$APP_DIR" || { echo "ERRO: estrutura atual da VPS ausente: $APP_DIR" >&2; exit 1; }
systemctl cat "$SERVICE.service" >/dev/null

sudo install -o root -g root -m 0755 "$STAGE/globoplast-backup" /usr/local/sbin/globoplast-backup
sudo install -o root -g root -m 0755 "$STAGE/globoplast-backup-check" /usr/local/sbin/globoplast-backup-check
sudo install -o root -g root -m 0755 "$STAGE/globoplast-backup-drive" /usr/local/sbin/globoplast-backup-drive
sudo install -o root -g root -m 0755 "$STAGE/globoplast-system-backup-drive" /usr/local/sbin/globoplast-system-backup-drive
sudo install -o root -g root -m 0644 "$STAGE/globoplast.service" /etc/systemd/system/globoplast.service
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup.service" /etc/systemd/system/globoplast-backup.service
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup.timer" /etc/systemd/system/globoplast-backup.timer
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup-drive.service" /etc/systemd/system/globoplast-backup-drive.service
sudo install -o root -g root -m 0644 "$STAGE/globoplast-backup-drive.timer" /etc/systemd/system/globoplast-backup-drive.timer
sudo install -o root -g root -m 0644 "$STAGE/globoplast-system-backup-drive.service" /etc/systemd/system/globoplast-system-backup-drive.service
sudo install -o root -g root -m 0644 "$STAGE/globoplast-system-backup-drive.timer" /etc/systemd/system/globoplast-system-backup-drive.timer
sudo install -d -o ubuntu -g ubuntu -m 0750 /var/backups/globoplast-system
sudo systemctl daemon-reload
sudo systemctl enable --now globoplast-backup.timer
if [[ -r /home/ubuntu/.config/rclone/rclone.conf ]]; then
  sudo systemctl enable --now globoplast-backup-drive.timer
  sudo systemctl enable --now globoplast-system-backup-drive.timer
else
  sudo systemctl disable --now globoplast-backup-drive.timer >/dev/null 2>&1 || true
  sudo systemctl disable --now globoplast-system-backup-drive.timer >/dev/null 2>&1 || true
fi

ACTUAL="$(unzip -p "$UPLOAD" META-INF/maven/br.com.globoplast/globoplast/pom.properties 2>/dev/null | grep '^version=' || true)"
[[ "$ACTUAL" == "$EXPECTED" ]] || {
  echo "ERRO: JAR recebido informa '$ACTUAL'; esperado '$EXPECTED'" >&2
  exit 1
}

sudo install -d -o ubuntu -g ubuntu -m 0755 "$RELEASE_DIR"
if [[ -L "$CURRENT_LINK" ]]; then
  PREVIOUS="$(readlink -f "$CURRENT_LINK")"
elif [[ -f "$CURRENT_LINK" ]]; then
  PREVIOUS="$CURRENT_LINK"
fi

RELEASE="$RELEASE_DIR/globoplast-$VERSION.jar"
sudo install -o ubuntu -g ubuntu -m 0644 "$UPLOAD" "$RELEASE"
sudo ln -sfn "releases/$(basename "$RELEASE")" "$APP_DIR/.globoplast.jar.next"
sudo mv -Tf "$APP_DIR/.globoplast.jar.next" "$CURRENT_LINK"

sudo systemctl restart "$SERVICE"

HEALTH=""
for _ in $(seq 1 30); do
  HEALTH="$(curl -fsS http://127.0.0.1:8080/health 2>/dev/null || true)"
  if [[ "$HEALTH" == *'"ok":true'* && "$HEALTH" == *"\"versao\":\"$VERSION\""* ]]; then
    break
  fi
  sleep 1
done

if [[ "$HEALTH" != *'"ok":true'* || "$HEALTH" != *"\"versao\":\"$VERSION\""* ]]; then
  echo "ERRO: health check falhou; iniciando rollback" >&2
  if [[ -n "$PREVIOUS" && -f "$PREVIOUS" ]]; then
    sudo ln -sfn "releases/$(basename "$PREVIOUS")" "$APP_DIR/.globoplast.jar.rollback"
    sudo mv -Tf "$APP_DIR/.globoplast.jar.rollback" "$CURRENT_LINK"
    sudo systemctl restart "$SERVICE"
  fi
  sudo journalctl -u "$SERVICE" -n 80 --no-pager >&2
  exit 1
fi

mapfile -t OLD_RELEASES < <(
  sudo find "$RELEASE_DIR" -maxdepth 1 -type f -name 'globoplast-*.jar' \
    -printf '%T@ %p\n' | sort -nr | awk 'NR > 4 {sub(/^[^ ]+ /, ""); print}'
)
for old in "${OLD_RELEASES[@]}"; do
  sudo rm -f -- "$old"
done

echo "$HEALTH"
systemctl is-active "$SERVICE"
REMOTE

echo "[4/5] Confirmando serviço e versão"
HEALTH="$("${SSH[@]}" "$DEPLOY_HOST" curl -fsS http://127.0.0.1:8080/health)"
[[ "$HEALTH" == *"\"versao\":\"$VERSION\""* ]] || {
  echo "ERRO: versão remota divergente: $HEALTH" >&2
  exit 1
}

echo "[5/5] Deploy concluído"
echo "$HEALTH"
