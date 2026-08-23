# Sincronizador Windows de Refugo

`globoplast_sync_refugo_online.py` é o conector atual entre o DealerSystem/Firebird e o Globoplast.

- O Firebird é acessado somente para leitura.
- Credenciais e token ficam protegidos pelo Windows DPAPI.
- A sincronização recente cobre os últimos 7 dias.
- A auditoria histórica começa em 01/01/2025 e reconcilia exclusões do ERP.
- Arquivos operacionais ficam em `C:\ProgramData\GloboplastSync`.
- A tarefa agendada esperada é `Globoplast Refugo Online`.
- O log fica em `C:\ProgramData\GloboplastSync\refugo_online.log`.

Para configurar uma instalação nova, copie o script para a pasta operacional e execute em um Prompt de Comando como Administrador:

```text
py -u globoplast_sync_refugo_online.py --configurar
```

Para validar manualmente a sincronização:

```text
py -u globoplast_sync_refugo_online.py --executar
```

Antes de substituir o script instalado, faça uma cópia do arquivo atual. A configuração existente não deve ser incluída no Git.
