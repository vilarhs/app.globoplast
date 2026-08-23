from __future__ import annotations

"""Sincroniza PLANEJAMENTO do DealerSystem com o Globoplast.

O Firebird é aberto exclusivamente em transação de leitura. A primeira carga
abrange o histórico desde 01/01/2025; depois, somente a janela recente é relida
para capturar alterações de QTD_PROD nas OPs em andamento.
"""

import argparse
import hashlib
import json
import sys
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path


APP_DIR = Path(r"C:\ProgramData\GloboplastSync")
REFUGO_CONNECTOR = APP_DIR / "globoplast_sync_refugo_online.py"
if not REFUGO_CONNECTOR.exists():
    raise SystemExit(f"Sincronizador base não encontrado: {REFUGO_CONNECTOR}")

sys.path.insert(0, str(APP_DIR))

from globoplast_sync_refugo_online import (  # noqa: E402
    DATABASE,
    FBCLIENT,
    HOST,
    PORT,
    carregar_config,
    connect,
    driver_config,
    request_assinado,
    tpb,
    Isolation,
    TraAccessMode,
)


CONNECTOR_ID = "dealersystem-windows-planejamento-online-v1"
STATE_PATH = APP_DIR / "planejamento_online_state.json"
LOG_PATH = APP_DIR / "planejamento_online.log"
HISTORY_START = date(2025, 1, 1)
RECENT_DAYS = 180
BATCH_SIZE = 500
PROCESSES = ("770", "771", "772", "773", "775", "776")


def log(message: str) -> None:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    line = f"{datetime.now():%Y-%m-%d %H:%M:%S} | {message}"
    print(line, flush=True)
    with LOG_PATH.open("a", encoding="utf-8") as stream:
        stream.write(line + "\n")


def text(value) -> str:
    return "" if value is None else str(value).strip()


def number(value):
    return None if value is None else float(Decimal(str(value)))


def integer(value):
    return None if value is None else int(value)


def iso_date(value) -> str:
    if value is None:
        return ""
    return value.isoformat() if hasattr(value, "isoformat") else str(value)[:10]


def load_state() -> dict:
    if not STATE_PATH.exists():
        return {"hashes": {}}
    try:
        value = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {"hashes": {}}
    except Exception:
        return {"hashes": {}}


def save_state(value: dict) -> None:
    temp = STATE_PATH.with_suffix(".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
    temp.replace(STATE_PATH)


def record_hash(record: dict) -> str:
    body = json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(body.encode("utf-8")).hexdigest()


def query_records(cur, start: date) -> list[dict]:
    process_filter = " OR ".join("PRODUTO STARTING WITH ?" for _ in PROCESSES)
    cur.execute(
        f"""
        SELECT RECORD_ID, DATA_PLAN, ORDEM, PRODUTO, DESCRICAO, QTD_PLAN,
               QTD_PROD, QTD_ENT, FLAG_EXE, PROCESSO, LOTE, QTD_PERDA, CONTEUDO
        FROM PLANEJAMENTO
        WHERE DATA_PLAN >= ?
          AND ({process_filter})
        ORDER BY RECORD_ID
        """,
        (start, *PROCESSES),
    )
    records = []
    for row in cur:
        records.append({
            "erp_id": int(row[0]),
            "data_plan": iso_date(row[1]),
            "ordem": integer(row[2]),
            "produto": text(row[3]),
            "descricao": text(row[4]),
            "qtd_plan": number(row[5]),
            "qtd_prod": number(row[6]),
            "qtd_ent": number(row[7]),
            "flag_exe": text(row[8]),
            "processo": integer(row[9]),
            "lote": text(row[10]),
            "qtd_perda": number(row[11]),
            "conteudo": integer(row[12]),
        })
    return records


def chunks(values: list[dict]):
    for index in range(0, len(values), BATCH_SIZE):
        yield values[index:index + BATCH_SIZE]


def synchronize() -> None:
    cfg = carregar_config()
    if not Path(FBCLIENT).exists():
        raise RuntimeError(f"fbclient.dll não encontrado: {FBCLIENT}")
    driver_config.fb_client_library.value = FBCLIENT
    driver_config.server_defaults.host.value = HOST
    driver_config.server_defaults.port.value = PORT

    state = load_state()
    old_hashes = state.get("hashes") if isinstance(state.get("hashes"), dict) else {}
    first_load = not bool(state.get("initial_complete"))
    start = HISTORY_START if first_load else date.today() - timedelta(days=RECENT_DAYS)
    log(f"INÍCIO | PLANEJAMENTO desde {start:%d/%m/%Y}")

    with connect(DATABASE, user=cfg["usuario"], password=cfg["senha"]) as con:
        ro = con.transaction_manager(
            tpb(Isolation.READ_COMMITTED_RECORD_VERSION, access_mode=TraAccessMode.READ)
        )
        cur = ro.cursor()
        try:
            records = query_records(cur, start)
        finally:
            if ro.is_active():
                ro.rollback()

    new_hashes = dict(old_hashes)
    changed = []
    for record in records:
        key = str(record["erp_id"])
        digest = record_hash(record)
        if old_hashes.get(key) != digest:
            changed.append(record)
        new_hashes[key] = digest

    sent = 0
    for batch in chunks(changed):
        response = request_assinado(
            cfg["token"],
            "/planejamento",
            {
                "connector_id": CONNECTOR_ID,
                "sent_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "records": batch,
            },
        )
        if not response.get("ok"):
            raise RuntimeError(f"Servidor rejeitou lote: {response}")
        sent += len(batch)

    if not changed:
        response = request_assinado(
            cfg["token"],
            "/planejamento",
            {
                "connector_id": CONNECTOR_ID,
                "sent_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "records": [],
            },
        )
        if not response.get("ok"):
            raise RuntimeError(f"Servidor rejeitou heartbeat: {response}")

    save_state({
        "initial_complete": True,
        "last_success": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "hashes": new_hashes,
    })
    log(f"OK | consultados={len(records)} | alterados_enviados={sent}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--executar", action="store_true")
    parser.parse_args()
    try:
        synchronize()
    except Exception as exc:
        log(f"ERRO | {exc}")
        raise


if __name__ == "__main__":
    main()
