#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

must_contain() {
  grep -Fq -- "$2" "$1" || {
    echo "ERRO: $3" >&2
    exit 1
  }
}

echo '=== AMBIENTE ==='
java -version
mvn -version

echo
echo '=== IDENTIDADE E VERSÃO ==='
POM_VERSION="$(python3 -c 'import sys, xml.etree.ElementTree as ET; root=ET.parse(sys.argv[1]).getroot(); ns=root.tag.split("}")[0]+"}"; print(root.find(ns+"version").text)' pom.xml)"
APP_VERSION="$(sed -nE 's/.*VERSION = "([^"]+)".*/\1/p' src/main/java/br/com/globoplast/oee/config/AppConfig.java)"

test -n "$POM_VERSION" || { echo 'ERRO: versão Maven ausente' >&2; exit 1; }
test "$POM_VERSION" = "$APP_VERSION" || {
  echo "ERRO: versões divergentes (pom.xml=$POM_VERSION, AppConfig=$APP_VERSION)" >&2
  exit 1
}

must_contain pom.xml '<artifactId>globoplast</artifactId>' 'artifactId incorreto'
must_contain pom.xml '<finalName>globoplast</finalName>' 'nome final do JAR incorreto'
must_contain src/main/resources/application.properties 'spring.application.name=globoplast' 'nome Spring incorreto'
must_contain src/main/resources/application.properties 'server.address=127.0.0.1' 'aplicação deve escutar somente no endereço local'
must_contain src/main/java/br/com/globoplast/oee/config/AppConfig.java '/var/lib/globoplast/database.db' 'caminho padrão do banco incorreto'
echo "Globoplast $POM_VERSION"

echo
echo '=== ARQUIVOS OPERACIONAIS ==='
required_files=(
  deploy/deploy-vps.sh
  deploy/globoplast.service
  deploy/globoplast-backup
  deploy/globoplast-backup-check
  deploy/globoplast-backup-drive
  deploy/globoplast-backup.service
  deploy/globoplast-backup.timer
  deploy/globoplast-backup-drive.service
  deploy/globoplast-backup-drive.timer
  deploy/globoplast-system-backup-drive
  deploy/globoplast-system-backup-drive.service
  deploy/globoplast-system-backup-drive.timer
  deploy/nginx.conf
  deploy/windows/ajustar-intervalo-erp-2min.ps1
  deploy/windows/diagnosticar_estoque_erp.py
  deploy/windows/globoplast_sync_planejamento_online.py
  deploy/windows/globoplast_sync_refugo_online.py
  deploy/windows/instalar-planejamento-erp.ps1
  src/main/resources/META-INF/resources/favicon.ico
  src/main/resources/META-INF/resources/favicon.png
  src/main/resources/META-INF/resources/images/globoplast-logo.png
  src/main/resources/META-INF/resources/images/globoplast-logo-white.png
)

for file in "${required_files[@]}"; do
  test -f "$file" || { echo "ERRO: arquivo necessário ausente: $file" >&2; exit 1; }
done

must_contain deploy/globoplast.service 'WorkingDirectory=/opt/globoplast' 'diretório do serviço incorreto'
must_contain deploy/globoplast.service 'EnvironmentFile=-/etc/globoplast.env' 'arquivo de ambiente incorreto'
must_contain deploy/globoplast.service 'GLOBOPLAST_DB=/var/lib/globoplast/database.db' 'banco do serviço incorreto'
must_contain deploy/globoplast.service '/opt/globoplast/globoplast.jar' 'JAR do serviço incorreto'
must_contain deploy/deploy-vps.sh 'health check falhou; iniciando rollback' 'deploy sem rollback automático'

echo
echo '=== SINTAXE ==='
bash -n deploy/deploy-vps.sh
bash -n deploy/preflight.sh
bash -n deploy/globoplast-backup-drive
bash -n deploy/globoplast-system-backup-drive
python3 -c 'import ast, pathlib, sys; [ast.parse(pathlib.Path(name).read_text(encoding="utf-8"), filename=name) for name in sys.argv[1:]]' \
  deploy/globoplast-backup \
  deploy/globoplast-backup-check \
  deploy/windows/diagnosticar_estoque_erp.py \
  deploy/windows/globoplast_sync_planejamento_online.py \
  deploy/windows/globoplast_sync_refugo_online.py

echo
echo '=== SEGURANÇA DO REPOSITÓRIO ==='
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if git ls-files | grep -E '(^|/)(database\.db|[^/]+\.(key|pem)|\.env)$' >/dev/null; then
    echo 'ERRO: banco, chave ou arquivo de ambiente está versionado' >&2
    exit 1
  fi
  git diff --check
fi

echo
echo '=== BUILD ==='
mvn clean package

echo
echo '=== JAR ==='
JAR=target/globoplast.jar
test -f "$JAR" || { echo "ERRO: JAR ausente: $JAR" >&2; exit 1; }
JAR_VERSION="$(unzip -p "$JAR" META-INF/maven/br.com.globoplast/globoplast/pom.properties 2>/dev/null | sed -n 's/^version=//p')"
test "$JAR_VERSION" = "$POM_VERSION" || {
  echo "ERRO: versão do JAR divergente (JAR=$JAR_VERSION, esperado=$POM_VERSION)" >&2
  exit 1
}
ls -lh "$JAR"

echo
echo 'PRE-FLIGHT OK'
