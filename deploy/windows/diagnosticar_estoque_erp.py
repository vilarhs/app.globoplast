from __future__ import annotations

"""Diagnóstico somente leitura das tabelas de estoque do DealerSystem/Firebird.

O script reutiliza a configuração protegida do sincronizador de Refugo e
imprime apenas nomes de tabelas e colunas. Nenhuma credencial nem conteúdo de
estoque é exibido ou alterado.
"""

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


def relevant(table: str, fields: list[str]) -> bool:
    joined = " ".join([table, *fields]).upper()
    if any(word in table.upper() for word in ("ESTOQ", "SALDO", "ALMOX", "ARMAZ")):
        return True
    has_product = any("PROD" in field or "MATERIAL" in field for field in fields)
    has_quantity = any(any(word in field for word in ("QTD", "QUANT", "SALDO", "ESTOQ", "DISPON")) for field in fields)
    has_order = any("ORDEM" in field or field == "OP" or field.startswith("OP_") for field in fields)
    return has_product and has_quantity and (has_order or any(word in joined for word in KEYWORDS))


def main() -> None:
    cfg = carregar_config()
    if not Path(FBCLIENT).exists():
        raise SystemExit(f"fbclient.dll não encontrado: {FBCLIENT}")

    driver_config.fb_client_library.value = FBCLIENT
    driver_config.server_defaults.host.value = HOST
    driver_config.server_defaults.port.value = PORT

    tables: dict[str, list[str]] = defaultdict(list)
    with connect(DATABASE, user=cfg["usuario"], password=cfg["senha"]) as con:
        ro = con.transaction_manager(
            tpb(Isolation.READ_COMMITTED_RECORD_VERSION, access_mode=TraAccessMode.READ)
        )
        cur = ro.cursor()
        try:
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
        finally:
            if ro.is_active():
                ro.rollback()

    candidates = [(table, fields) for table, fields in tables.items() if relevant(table, fields)]
    print("TABELAS CANDIDATAS A ESTOQUE/PRODUÇÃO (somente estrutura):")
    print("=" * 78)
    if not candidates:
        print("Nenhuma candidata encontrada pelos nomes atuais.")
        return
    for table, fields in candidates:
        print(f"\n[{table}]")
        print(", ".join(fields))


if __name__ == "__main__":
    main()
