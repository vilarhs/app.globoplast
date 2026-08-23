#!/usr/bin/env python3
import argparse, getpass, hashlib, hmac, json, os, sqlite3, time, urllib.request, urllib.error
from pathlib import Path

DEFAULT_DB = "/var/www/globoplast/app/database.db"
DEFAULT_URL = "http://168.138.142.85/java-sync/v1/catalogo"
DEFAULT_DIAG = "http://168.138.142.85/java-sync/v1/diagnostico-oee"


def read_catalog(db_path):
    p = Path(db_path)
    if not p.is_file():
        raise SystemExit(f"Banco não encontrado: {p}")
    uri = f"file:{p}?mode=ro&immutable=1"
    con = sqlite3.connect(uri, uri=True, timeout=10)
    con.row_factory = sqlite3.Row
    try:
        tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if "maquinas" not in tables or "setores" not in tables:
            raise SystemExit("O banco informado não contém as tabelas setores/maquinas do appv723.")

        setores = [str(r[0]).strip() for r in con.execute(
            "SELECT setor FROM setores WHERE TRIM(COALESCE(setor,''))<>'' ORDER BY setor COLLATE NOCASE"
        )]

        maquinas = []
        setor_por_maquina = {}
        for r in con.execute(
            "SELECT maquina,capacidade,setor FROM maquinas WHERE TRIM(COALESCE(maquina,''))<>'' ORDER BY maquina COLLATE NOCASE"
        ):
            name = str(r[0]).strip()
            cap = int(round(float(r[1] or 0)))
            setor = str(r[2] or "").strip()
            if cap <= 0:
                continue
            maquinas.append({"maquina": name, "capacidade": cap, "setor": setor})
            setor_por_maquina[name.upper()] = setor

        historicas = []
        if "historico_oee" in tables:
            cols = {r[1] for r in con.execute("PRAGMA table_info(historico_oee)")}
            if {"id", "maquina", "capacidade_24h"}.issubset(cols):
                seen = set()
                for r in con.execute(
                    """
                    SELECT maquina, capacidade_24h
                    FROM historico_oee
                    WHERE TRIM(COALESCE(maquina,''))<>''
                      AND COALESCE(capacidade_24h,0) > 0
                    ORDER BY id DESC
                    """
                ):
                    name = str(r[0]).strip()
                    key = name.upper()
                    if key in seen:
                        continue
                    seen.add(key)
                    cap = int(round(float(r[1] or 0)))
                    if cap <= 0:
                        continue
                    historicas.append({
                        "maquina": name,
                        "capacidade": cap,
                        "setor": setor_por_maquina.get(key, ""),
                    })

        return setores, maquinas, historicas
    finally:
        con.close()


def signed_post(url, payload, token):
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    ts = str(int(time.time()))
    sig = hmac.new(token.encode("utf-8"), ts.encode("ascii") + b"." + body, hashlib.sha256).hexdigest()
    req = urllib.request.Request(url, data=body, method="POST", headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}",
        "X-Globoplast-Timestamp": ts,
        "X-Globoplast-Signature": "sha256=" + sig,
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        raise SystemExit(f"HTTP {e.code}: {detail}")


def main():
    ap = argparse.ArgumentParser(
        description="Migra em SOMENTE LEITURA catálogo + capacidades históricas do appv723 para o Java."
    )
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--url", default=DEFAULT_URL)
    ap.add_argument("--diag-url", default=DEFAULT_DIAG)
    args = ap.parse_args()

    setores, maquinas, historicas = read_catalog(args.db)
    print("=" * 80)
    print("GLOBOPLAST - CATÁLOGO + CAPACIDADES HISTÓRICAS APPV723 -> JAVA")
    print("=" * 80)
    print(f"Origem SQLite (somente leitura): {args.db}")
    print(f"Setores encontrados             : {len(setores)}")
    print(f"Máquinas atuais c/ capacidade   : {len(maquinas)}")
    print(f"Máquinas históricas c/ capacidade: {len(historicas)}")
    if not maquinas and not historicas:
        raise SystemExit("Nenhuma capacidade foi encontrada no database.db informado.")

    token = os.environ.get("GLOBOPLAST_SYNC_TOKEN", "").strip()
    if not token:
        token = getpass.getpass("Token Java (64 caracteres): ").strip()
    if not token:
        raise SystemExit("Token não informado.")

    payload = {
        "connector_id": "appv723-catalog-history-migration-v002",
        "sent_at": int(time.time()),
        "setores": setores,
        "maquinas": maquinas,
        "maquinas_historicas": historicas,
    }
    result = signed_post(args.url, payload, token)
    print("\nRESULTADO CATÁLOGO/HISTÓRICO")
    print(json.dumps(result, ensure_ascii=False, indent=2))

    diag = signed_post(args.diag_url, {}, token)
    print("\nDIAGNÓSTICO OEE APÓS MIGRAÇÃO")
    print(json.dumps(diag, ensure_ascii=False, indent=2))
    print("\nCONCLUÍDO. O database.db do globoplast.app permaneceu somente leitura.")


if __name__ == "__main__":
    main()
