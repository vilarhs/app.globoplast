from __future__ import annotations

import ctypes
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path


TASK_NAME = "Globoplast Refugo Online"
SCRIPT_NAME = "globoplast_sync_refugo_online.py"


def is_admin() -> bool:
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def run(*args: str):
    return subprocess.run(
        list(args),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def main() -> int:
    print("=" * 76)
    print("GLOBOPLAST - ATUALIZAÇÃO DO REFUGO ONLINE V076")
    print("=" * 76)

    if not is_admin():
        print("ERRO: execute este atualizador em um Prompt de Comando como Administrador.")
        return 2

    source = Path(__file__).resolve().parent / SCRIPT_NAME
    app_dir = Path(os.environ.get("PROGRAMDATA", r"C:\ProgramData")) / "GloboplastSync"
    target = app_dir / SCRIPT_NAME
    config = app_dir / "refugo_online_config.json"
    backup = app_dir / "globoplast_sync_refugo_online.pre-v076.py"

    if not source.exists():
        print(f"ERRO: arquivo ausente ao lado do atualizador: {source}")
        return 3
    if not target.exists() or not config.exists():
        print("ERRO: sincronizador atual não foi encontrado em C:\\ProgramData\\GloboplastSync.")
        return 4

    print("[1/4] Interrompendo uma eventual execução da tarefa...")
    run("schtasks", "/End", "/TN", TASK_NAME)
    time.sleep(2)

    print("[2/4] Criando backup e instalando o sincronizador v076...")
    shutil.copy2(target, backup)
    shutil.copy2(source, target)

    print("[3/4] Executando sincronização e reconciliação de teste...")
    test = subprocess.run([sys.executable, "-u", str(target), "--executar"])
    if test.returncode != 0:
        print("ERRO no teste. Restaurando automaticamente a versão anterior.")
        shutil.copy2(backup, target)
        return test.returncode

    print("[4/4] Confirmando a tarefa agendada...")
    query = run("schtasks", "/Query", "/TN", TASK_NAME)
    if query.returncode != 0:
        print("ERRO: a tarefa agendada não foi encontrada.")
        print(query.stderr.strip())
        return query.returncode

    print()
    print("ATUALIZAÇÃO V076 CONCLUÍDA")
    print("Script :", target)
    print("Backup :", backup)
    print("A tarefa existente permanece com a mesma configuração e credenciais.")
    print("O Firebird continua sendo acessado somente para leitura.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
