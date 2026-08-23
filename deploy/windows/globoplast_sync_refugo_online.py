from __future__ import annotations

import argparse
import base64
import ctypes
from ctypes import wintypes
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_EVEN
from getpass import getpass
from pathlib import Path

from firebird.driver import (
    connect,
    driver_config,
    tpb,
    Isolation,
    TraAccessMode,
)

HOST = "localhost"
PORT = "3050"
DATABASE = r"D:\Dealersystem\Db\DEALERSYSTEM.FDB"
FBCLIENT = r"C:\Program Files\Firebird\Firebird_3_0\fbclient.dll"

BASE_URL = "https://globoplast.app/sync/v1"
CONNECTOR_ID = "dealersystem-windows-refugo-online-v2"
TAMANHO_LOTE = 200
JANELA_DIAS = 7
AUDITORIA_INICIO = date(2025, 1, 1)
AUDITORIA_INTERVALO_DIAS = 31

APP_DIR = Path(os.environ.get("PROGRAMDATA", r"C:\\ProgramData")) / "GloboplastSync"
CONFIG_PATH = APP_DIR / "refugo_online_config.json"
LOG_PATH = APP_DIR / "refugo_online.log"
STATE_PATH = APP_DIR / "refugo_online_state.json"


class DATA_BLOB(ctypes.Structure):
    _fields_ = [
        ("cbData", wintypes.DWORD),
        ("pbData", ctypes.POINTER(ctypes.c_byte)),
    ]


crypt32 = ctypes.windll.crypt32
kernel32 = ctypes.windll.kernel32


def _blob_from_bytes(data: bytes):
    buf = ctypes.create_string_buffer(data)
    blob = DATA_BLOB(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_byte)))
    return blob, buf


def proteger_dpapi(texto: str) -> str:
    data = texto.encode("utf-8")
    in_blob, _buf = _blob_from_bytes(data)
    out_blob = DATA_BLOB()
    if not crypt32.CryptProtectData(
        ctypes.byref(in_blob),
        "GloboplastSync",
        None,
        None,
        None,
        0x4,  # CRYPTPROTECT_LOCAL_MACHINE
        ctypes.byref(out_blob),
    ):
        raise ctypes.WinError()
    try:
        raw = ctypes.string_at(out_blob.pbData, out_blob.cbData)
        return base64.b64encode(raw).decode("ascii")
    finally:
        kernel32.LocalFree(out_blob.pbData)


def desproteger_dpapi(valor: str) -> str:
    raw = base64.b64decode(valor)
    in_blob, _buf = _blob_from_bytes(raw)
    out_blob = DATA_BLOB()
    if not crypt32.CryptUnprotectData(
        ctypes.byref(in_blob),
        None,
        None,
        None,
        None,
        0,
        ctypes.byref(out_blob),
    ):
        raise ctypes.WinError()
    try:
        data = ctypes.string_at(out_blob.pbData, out_blob.cbData)
        return data.decode("utf-8")
    finally:
        kernel32.LocalFree(out_blob.pbData)


def log(msg: str):
    APP_DIR.mkdir(parents=True, exist_ok=True)
    linha = f"{datetime.now():%Y-%m-%d %H:%M:%S} | {msg}"
    print(linha, flush=True)
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(linha + "\n")


def txt(valor) -> str:
    return "" if valor is None else str(valor).strip()


def dec(valor) -> Decimal | None:
    if valor is None:
        return None
    return Decimal(str(valor))


def num_json(valor):
    if valor is None:
        return None
    return float(Decimal(str(valor)))


def remover_sufixo_alfabetico(codigo: str) -> str:
    codigo = txt(codigo)
    i = len(codigo)
    while i > 0 and codigo[i - 1].isalpha():
        i -= 1
    return codigo[:i]


def eh_refugo_try_out_sem_peso(codigo: str, descricao: str) -> bool:
    codigo = txt(codigo).upper()
    descricao = txt(descricao).upper()
    return codigo in {"7700222", "7760222"} and "REFUGO TRY OUT" in descricao


def qtd_itens(qtd_refugo, peso_br):
    if qtd_refugo is None or peso_br is None:
        return None
    q = Decimal(str(qtd_refugo))
    p = Decimal(str(peso_br))
    if p <= 0:
        return None
    return int(
        ((q * Decimal("1000")) / p).quantize(
            Decimal("1"),
            rounding=ROUND_HALF_EVEN,
        )
    )


def chunks(seq, tamanho):
    for i in range(0, len(seq), tamanho):
        yield seq[i:i + tamanho]


def request_assinado(token: str, endpoint: str, payload: dict) -> dict:
    body = json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")

    timestamp = str(int(time.time()))
    assinatura = hmac.new(
        token.encode("utf-8"),
        timestamp.encode("ascii") + b"." + body,
        hashlib.sha256,
    ).hexdigest()

    req = urllib.request.Request(
        BASE_URL + endpoint,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
            "X-Globoplast-Timestamp": timestamp,
            "X-Globoplast-Signature": f"sha256={assinatura}",
            "User-Agent": "Globoplast-ERP-Connector-Refugo-Online/2",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        corpo = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {corpo}") from exc


def ler_token_clipboard() -> str:
    resultado = subprocess.run(
        [
            "powershell",
            "-NoProfile",
            "-Command",
            "[Console]::OutputEncoding=[Text.UTF8Encoding]::UTF8; Get-Clipboard -Raw",
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=10,
    )
    if resultado.returncode != 0:
        raise RuntimeError(
            resultado.stderr.strip() or "Falha ao ler a área de transferência."
        )
    return resultado.stdout.strip()


def configurar():
    print("=" * 76)
    print("GLOBOPLAST - CONFIGURAÇÃO REFUGO ONLINE")
    print("=" * 76)
    print("As credenciais serão protegidas pelo Windows DPAPI desta máquina.")
    print()

    token = ler_token_clipboard()
    print(f"Token no clipboard: {len(token)} caracteres")
    if len(token) != 64 or any(c not in "0123456789abcdefABCDEF" for c in token):
        raise RuntimeError("Token inválido. Copie novamente o token de 64 caracteres.")

    usuario = input("Usuário Firebird [SYSDBA]: ").strip() or "SYSDBA"
    senha = getpass("Senha Firebird: ")

    APP_DIR.mkdir(parents=True, exist_ok=True)
    dados = {
        "usuario_fb": usuario,
        "senha_fb_dpapi": proteger_dpapi(senha),
        "token_dpapi": proteger_dpapi(token),
        "janela_dias": JANELA_DIAS,
        "auditoria_inicio": AUDITORIA_INICIO.isoformat(),
        "database": DATABASE,
        "fbclient": FBCLIENT,
    }
    CONFIG_PATH.write_text(json.dumps(dados, ensure_ascii=False, indent=2), encoding="utf-8")

    print()
    print("Configuração salva em:")
    print(CONFIG_PATH)
    print("Senha e token não foram gravados em texto aberto.")


def carregar_config():
    if not CONFIG_PATH.exists():
        raise RuntimeError(
            f"Configuração não encontrada: {CONFIG_PATH}. "
            "Execute primeiro com --configurar."
        )
    dados = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    return {
        "usuario": dados["usuario_fb"],
        "senha": desproteger_dpapi(dados["senha_fb_dpapi"]),
        "token": desproteger_dpapi(dados["token_dpapi"]),
        "janela_dias": int(dados.get("janela_dias", JANELA_DIAS)),
        "auditoria_inicio": date.fromisoformat(
            dados.get("auditoria_inicio", AUDITORIA_INICIO.isoformat())
        ),
    }


def carregar_estado() -> dict:
    if not STATE_PATH.exists():
        return {}
    try:
        value = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except Exception:
        return {}


def salvar_estado(value: dict):
    APP_DIR.mkdir(parents=True, exist_ok=True)
    temp = STATE_PATH.with_suffix(".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.replace(STATE_PATH)


def consultar_refugos(cur, inicio: date, fim: date):
    cur.execute(
        """
        SELECT
            RECORD_ID,
            DATA_APON,
            ORDEM,
            MAQUINA,
            PRODUTO,
            DESCRICAO,
            NOME_CLIENTE,
            TURNO,
            OPERADOR,
            QTD_REFUGO,
            MOTIVO
        FROM REFUGO
        WHERE DATA_APON BETWEEN ? AND ?
        ORDER BY RECORD_ID
        """,
        (inicio, fim),
    )
    return cur.fetchall()


def consultar_ids_refugo(cur, inicio: date, fim: date) -> list[int]:
    cur.execute(
        """
        SELECT RECORD_ID
        FROM REFUGO
        WHERE DATA_APON BETWEEN ? AND ?
          AND RECORD_ID IS NOT NULL
        ORDER BY RECORD_ID
        """,
        (inicio, fim),
    )
    return sorted({int(row[0]) for row in cur.fetchall()})


def periodos_auditoria(inicio: date, fim: date):
    atual = inicio
    while atual <= fim:
        limite = min(atual + timedelta(days=AUDITORIA_INTERVALO_DIAS - 1), fim)
        yield atual, limite
        atual = limite + timedelta(days=1)


def reconciliar_snapshot(token: str, inicio: date, fim: date, erp_ids: list[int]) -> int:
    resp = request_assinado(
        token,
        "/refugo",
        {
            "connector_id": CONNECTOR_ID,
            "sent_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "records": [],
            "snapshot_complete": True,
            "snapshot_start": inicio.isoformat(),
            "snapshot_end": fim.isoformat(),
            "snapshot_erp_ids": erp_ids,
        },
    )
    resultado = resp.get("resultado", {})
    if resultado.get("snapshot_reconciliado") is not True:
        raise RuntimeError(
            "Servidor ainda não confirmou a reconciliação de exclusões. "
            "Instale primeiro o Globoplast Java v076."
        )
    return int(resultado.get("excluidos") or 0)


def complementar(cur, rows_r):
    ordens = sorted({int(r[2]) for r in rows_r if r[2] is not None})
    produtos_originais = sorted({txt(r[4]) for r in rows_r if txt(r[4])})

    planejado = {}
    for grupo in chunks(ordens, 400):
        marks = ",".join("?" for _ in grupo)
        cur.execute(
            f"""
            SELECT ORDEM, PRODUTO, QTD_PLAN
            FROM PLANEJAMENTO
            WHERE ORDEM IN ({marks})
            """,
            tuple(grupo),
        )
        for ordem, produto, qtd_plan in cur:
            chave = (int(ordem), txt(produto))
            valor = dec(qtd_plan)
            anterior = planejado.get(chave)
            if valor is not None and (anterior is None or valor > anterior):
                planejado[chave] = valor

    produtos_busca = set(produtos_originais)
    for codigo in produtos_originais:
        base = remover_sufixo_alfabetico(codigo)
        if base and base != codigo:
            produtos_busca.add(base)

    pesos = {}
    lista_produtos = sorted(produtos_busca)
    for grupo in chunks(lista_produtos, 400):
        marks = ",".join("?" for _ in grupo)
        cur.execute(
            f"""
            SELECT PRODUTO, PESO2
            FROM PRODUTO
            WHERE PRODUTO IN ({marks})
            """,
            tuple(grupo),
        )
        for produto, peso2 in cur:
            pesos[txt(produto)] = dec(peso2)

    variantes_unicas = {}
    familia_consenso = {}
    processo_consenso = {}

    for codigo in produtos_originais:
        peso_exato = pesos.get(codigo)
        base = remover_sufixo_alfabetico(codigo)
        peso_base = pesos.get(base) if base and base != codigo else None

        if (peso_exato is not None and peso_exato > 0) or (
            peso_base is not None and peso_base > 0
        ):
            continue

        cur.execute(
            """
            SELECT PRODUTO, PESO2
            FROM PRODUTO
            WHERE PRODUTO STARTING WITH ?
              AND PESO2 IS NOT NULL
              AND PESO2 > 0
            ORDER BY PRODUTO
            """,
            (codigo,),
        )
        candidatos = [
            (txt(p), dec(w))
            for p, w in cur.fetchall()
            if txt(p) != codigo and dec(w) is not None and dec(w) > 0
        ]
        if len(candidatos) == 1:
            variantes_unicas[codigo] = candidatos[0][1]
            continue

        if len(codigo) >= 2:
            pref = codigo[:-1]
            cur.execute(
                """
                SELECT PESO2
                FROM PRODUTO
                WHERE PRODUTO STARTING WITH ?
                  AND PESO2 IS NOT NULL
                  AND PESO2 > 0
                """,
                (pref,),
            )
            ws = [dec(r[0]) for r in cur.fetchall() if dec(r[0]) is not None and dec(r[0]) > 0]
            distintos = sorted(set(ws))
            if ws and len(distintos) == 1:
                familia_consenso[codigo] = distintos[0]
                continue

        if len(codigo) > 3 and codigo.isdigit():
            suf = codigo[3:]
            cur.execute(
                """
                SELECT PESO2
                FROM PRODUTO
                WHERE CHAR_LENGTH(PRODUTO) = ?
                  AND SUBSTRING(PRODUTO FROM 4) = ?
                  AND PESO2 IS NOT NULL
                  AND PESO2 > 0
                """,
                (len(codigo), suf),
            )
            ws = [dec(r[0]) for r in cur.fetchall() if dec(r[0]) is not None and dec(r[0]) > 0]
            distintos = sorted(set(ws))
            if ws and len(distintos) == 1:
                processo_consenso[codigo] = distintos[0]

    registros = []
    sem_planejado = 0
    sem_peso = []

    for r in rows_r:
        (
            erp_id, data_apon, ordem, maquina, produto, descricao,
            cliente, turno, operador, qtd_refugo, motivo
        ) = r

        codigo = txt(produto)
        chave = (int(ordem), codigo) if ordem is not None else None
        qtd_plan = planejado.get(chave) if chave else None
        if qtd_plan is None:
            sem_planejado += 1

        peso_br = pesos.get(codigo)

        if peso_br is None or peso_br <= 0:
            base = remover_sufixo_alfabetico(codigo)
            if base and base != codigo:
                p = pesos.get(base)
                if p is not None and p > 0:
                    peso_br = p

        if peso_br is None or peso_br <= 0:
            p = variantes_unicas.get(codigo)
            if p is not None and p > 0:
                peso_br = p

        if peso_br is None or peso_br <= 0:
            p = familia_consenso.get(codigo)
            if p is not None and p > 0:
                peso_br = p

        if peso_br is None or peso_br <= 0:
            p = processo_consenso.get(codigo)
            if p is not None and p > 0:
                peso_br = p

        # No online, nunca inventa peso e nunca trava toda a sincronização.
        # O kg real é enviado mesmo se peso unitário ainda não tiver regra.
        sem_conversao = peso_br is None or peso_br <= 0
        if sem_conversao:
            sem_peso.append((int(erp_id), ordem, codigo, txt(descricao)))
            peso_br = None

        registros.append({
            "erp_id": int(erp_id),
            "data_apon": data_apon.isoformat(),
            "ordem": int(ordem) if ordem is not None else None,
            "qtd_planej": num_json(qtd_plan),
            "maquina": txt(maquina),
            "produto": codigo,
            "descricao": txt(descricao),
            "cliente": txt(cliente),
            "turno": txt(turno),
            "operador": txt(operador),
            "qtd_refugo": num_json(qtd_refugo),
            "motivo": txt(motivo),
            "peso_br": num_json(peso_br),
            "qtd_itens": qtd_itens(qtd_refugo, peso_br),
        })

    return registros, sem_planejado, sem_peso


def sincronizar_uma_vez():
    cfg = carregar_config()

    if not Path(FBCLIENT).exists():
        raise RuntimeError(f"fbclient.dll não encontrado: {FBCLIENT}")

    driver_config.fb_client_library.value = FBCLIENT
    driver_config.server_defaults.host.value = HOST
    driver_config.server_defaults.port.value = PORT

    fim = date.today()
    inicio = fim - timedelta(days=max(1, cfg["janela_dias"]) - 1)

    log(f"INÍCIO | janela {inicio:%d/%m/%Y} a {fim:%d/%m/%Y}")

    estado = carregar_estado()
    auditar_historico = estado.get("ultima_auditoria_completa") != fim.isoformat()
    snapshots_historicos = []

    with connect(DATABASE, user=cfg["usuario"], password=cfg["senha"]) as con:
        ro = con.transaction_manager(
            tpb(
                Isolation.READ_COMMITTED_RECORD_VERSION,
                access_mode=TraAccessMode.READ,
            )
        )
        cur = ro.cursor()
        try:
            rows = consultar_refugos(cur, inicio, fim)
            registros, sem_planejado, sem_peso = complementar(cur, rows)
            if auditar_historico:
                for periodo_inicio, periodo_fim in periodos_auditoria(
                    cfg["auditoria_inicio"], fim
                ):
                    snapshots_historicos.append(
                        (
                            periodo_inicio,
                            periodo_fim,
                            consultar_ids_refugo(cur, periodo_inicio, periodo_fim),
                        )
                    )
        finally:
            if ro.is_active():
                ro.rollback()

    recebidos = 0
    alterados = 0
    for lote in chunks(registros, TAMANHO_LOTE):
        resp = request_assinado(
            cfg["token"],
            "/refugo",
            {
                "connector_id": CONNECTOR_ID,
                "sent_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "records": lote,
            },
        )
        resultado = resp.get("resultado", {})
        recebidos += int(resultado.get("recebidos") or 0)
        alterados += int(resultado.get("alterados") or 0)

    ids_janela = sorted({int(registro["erp_id"]) for registro in registros})
    excluidos_janela = reconciliar_snapshot(cfg["token"], inicio, fim, ids_janela)

    excluidos_auditoria = 0
    if auditar_historico:
        for periodo_inicio, periodo_fim, erp_ids in snapshots_historicos:
            excluidos_auditoria += reconciliar_snapshot(
                cfg["token"], periodo_inicio, periodo_fim, erp_ids
            )
        estado["ultima_auditoria_completa"] = fim.isoformat()
        estado["auditoria_inicio"] = cfg["auditoria_inicio"].isoformat()
        estado["atualizado_em"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
        salvar_estado(estado)

    log(
        f"OK | ERP={len(registros)} | recebidos={recebidos} | alterados={alterados} "
        f"| excluidos_janela={excluidos_janela} "
        f"| auditoria_periodos={len(snapshots_historicos)} "
        f"| excluidos_auditoria={excluidos_auditoria} "
        f"| sem_qtd_planej={sem_planejado} | sem_peso={len(sem_peso)}"
    )

    for erp_id, ordem, codigo, descricao in sem_peso[:20]:
        log(
            f"ATENÇÃO SEM PESO | ERP_ID={erp_id} | OP={ordem} "
            f"| produto={codigo} | {descricao}"
        )
    if len(sem_peso) > 20:
        log(f"ATENÇÃO SEM PESO | mais {len(sem_peso) - 20} registros na janela")

    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--configurar", action="store_true")
    parser.add_argument("--executar", action="store_true")
    args = parser.parse_args()

    try:
        if args.configurar:
            configurar()
            return 0
        return sincronizar_uma_vez()
    except Exception as exc:
        try:
            log(f"ERRO | {type(exc).__name__}: {exc}")
        except Exception:
            print(type(exc).__name__ + ":", exc)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
