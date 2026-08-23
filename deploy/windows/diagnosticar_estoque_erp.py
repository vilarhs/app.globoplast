from __future__ import annotations

"""Diagnóstico somente leitura das tabelas de estoque do DealerSystem/Firebird.

O script reutiliza a configuração protegida do sincronizador de Refugo. Sem
argumentos, imprime nomes de tabelas/colunas. Com ``--op``, mostra somente os
campos operacionais das linhas ligadas à OP informada. Nenhuma credencial é
exibida e nenhuma informação é alterada.
"""

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path


APP_DIR = Path(r"C:\ProgramData\GloboplastSync")
CONNECTOR = APP_DIR / "globoplast_sync_refugo_online.py"

if not CONNECTOR.exists():
    raise SystemExit(f"Sincronizador não encontrado: {CONNECTOR}")

sys.path.insert(0, str(APP_DIR))

from globoplast_sync_refugo_online import (  # noqa: E402
    DATABASE,
    FBCLIENT,
    HOST,
    PORT,
    carregar_config,
    connect,
    driver_config,
    tpb,
    Isolation,
    TraAccessMode,
)


KEYWORDS = (
    "ESTOQ", "SALDO", "DISPON", "ALMOX", "ARMAZ", "LOCAL",
    "ORDEM", "PRODUTO", "MATERIAL", "PLANEJ", "QTD", "QUANT",
)

DETAIL_TABLES = {
    "PLANEJAMENTO", "PROGRAMACAO", "PRODUCAO", "ITEMROMANEIO",
    "ITEM_CARR", "MOVTO", "LOG_BLOQUEIO", "REQUISICAO",
}

DETAIL_FIELDS = (
    "RECORD_ID", "REG", "ORDEM", "OP", "PRODUTO", "DESCRICAO",
    "PROCESSO", "LOTE", "LOCALIZACAO", "DIVISAO", "DATA", "DATA_PLAN",
    "DATA_PROD", "DT_PROD", "MAQUINA", "QTD_PLAN", "QTD_PROD",
    "QTD_APON", "SALDO", "QUANTIDADE", "QTD", "QTD_CX", "CONTEUDO",
    "TOTAL", "QTD_ENT", "QTD_SAI", "QTD_PERDA", "EST_ANT", "EST_ATU",
    "ATUA_EST", "CATEGORIA", "DEPTO", "SITUACAO", "STATUS", "FINALIZADO",
)


def relevant(table: str, fields: list[str]) -> bool:
    joined = " ".join([table, *fields]).upper()
    if any(word in table.upper() for word in ("ESTOQ", "SALDO", "ALMOX", "ARMAZ")):
        return True
    has_product = any("PROD" in field or "MATERIAL" in field for field in fields)
    has_quantity = any(any(word in field for word in ("QTD", "QUANT", "SALDO", "ESTOQ", "DISPON")) for field in fields)
    has_order = any("ORDEM" in field or field == "OP" or field.startswith("OP_") for field in fields)
    return has_product and has_quantity and (has_order or any(word in joined for word in KEYWORDS))


def read_schema(cur) -> dict[str, list[str]]:
    tables: dict[str, list[str]] = defaultdict(list)
    cur.execute(
        """
        SELECT TRIM(rf.RDB$RELATION_NAME), TRIM(rf.RDB$FIELD_NAME)
        FROM RDB$RELATION_FIELDS rf
        JOIN RDB$RELATIONS r
          ON r.RDB$RELATION_NAME = rf.RDB$RELATION_NAME
        WHERE COALESCE(r.RDB$SYSTEM_FLAG, 0) = 0
          AND r.RDB$VIEW_BLR IS NULL
        ORDER BY rf.RDB$RELATION_NAME, rf.RDB$FIELD_POSITION
        """
    )
    for table, field in cur:
        tables[str(table).strip()].append(str(field).strip())
    return tables


def print_schema(tables: dict[str, list[str]]) -> None:
    candidates = [(table, fields) for table, fields in tables.items() if relevant(table, fields)]
    print("TABELAS CANDIDATAS A ESTOQUE/PRODUÇÃO (somente estrutura):")
    print("=" * 78)
    if not candidates:
        print("Nenhuma candidata encontrada pelos nomes atuais.")
        return
    for table, fields in candidates:
        print(f"\n[{table}]")
        print(", ".join(fields))


def print_order_rows(cur, tables: dict[str, list[str]], order: int) -> None:
    print(f"DIAGNÓSTICO SOMENTE LEITURA DA OP {order}")
    print("=" * 78)
    found = 0
    for table, fields in tables.items():
        upper_fields = {field.upper(): field for field in fields}
        order_field = upper_fields.get("ORDEM") or upper_fields.get("OP")
        selected = [upper_fields[name] for name in DETAIL_FIELDS if name in upper_fields]
        should_query = table.upper() in DETAIL_TABLES or table.upper().startswith("TWP")
        if not should_query or not order_field or not selected:
            continue
        sql = f'SELECT FIRST 50 {", ".join(selected)} FROM {table} WHERE {order_field} = ?'
        try:
            cur.execute(sql, (order,))
            rows = cur.fetchall()
        except Exception as exc:
            print(f"\n[{table}] ERRO: {exc}")
            continue
        if not rows:
            continue
        found += len(rows)
        print(f"\n[{table}] {len(rows)} linha(s)")
        for row in rows:
            payload = {selected[i]: row[i] for i in range(len(selected))}
            print(json.dumps(payload, ensure_ascii=False, default=str))
    if found == 0:
        print("Nenhuma linha encontrada nas tabelas candidatas para esta OP.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--op", type=int, help="OP usada no diagnóstico detalhado")
    args = parser.parse_args()
    cfg = carregar_config()
    if not Path(FBCLIENT).exists():
        raise SystemExit(f"fbclient.dll não encontrado: {FBCLIENT}")

    driver_config.fb_client_library.value = FBCLIENT
    driver_config.server_defaults.host.value = HOST
    driver_config.server_defaults.port.value = PORT

    with connect(DATABASE, user=cfg["usuario"], password=cfg["senha"]) as con:
        ro = con.transaction_manager(
            tpb(Isolation.READ_COMMITTED_RECORD_VERSION, access_mode=TraAccessMode.READ)
        )
        cur = ro.cursor()
        try:
            tables = read_schema(cur)
            if args.op is None:
                print_schema(tables)
            else:
                print_order_rows(cur, tables, args.op)
        finally:
            if ro.is_active():
                ro.rollback()


if __name__ == "__main__":
    main()
