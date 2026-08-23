# Sincronizador Windows de Refugo

`globoplast_sync_refugo_online.py` é o conector atual entre o DealerSystem/Firebird e o Globoplast.

- O Firebird é acessado somente para leitura.
- Credenciais e token ficam protegidos pelo Windows DPAPI.
- A sincronização recente cobre os últimos 7 dias.
- A auditoria histórica começa em 01/01/2025 e reconcilia exclusões do ERP.
- Arquivos operacionais ficam em `C:\ProgramData\GloboplastSync`.
- A tarefa agendada esperada é `Globoplast Refugo Online`.
- Produção e Refugo devem sincronizar a cada 2 minutos.
- O Planejamento/Estoque por OP também sincroniza a cada 2 minutos pela tarefa `Globoplast Planejamento Online`.
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

## Identificar o estoque do ERP

`diagnosticar_estoque_erp.py` consulta somente os metadados do Firebird e lista
tabelas/colunas candidatas a estoque. Ele não exibe credenciais nem altera a
base. Copie-o para o Windows e execute:

```text
py diagnosticar_estoque_erp.py > estrutura-estoque-erp.txt
```

## Instalar Planejamento/Estoque por OP

Depois que a versão compatível estiver publicada na VPS, copie
`globoplast_sync_planejamento_online.py` e `instalar-planejamento-erp.ps1` para
a mesma pasta no Windows. Abra o PowerShell como Administrador e execute:

```powershell
powershell -ExecutionPolicy Bypass -File .\instalar-planejamento-erp.ps1
```

O instalador reutiliza a configuração Firebird/token já protegida pelo DPAPI,
faz backup do conector anterior e cria a tarefa com intervalo de 2 minutos.

## Restaurar o intervalo de 2 minutos

No servidor do DealerSystem, abra o PowerShell como Administrador e execute:

```powershell
powershell -ExecutionPolicy Bypass -File .\ajustar-intervalo-erp-2min.ps1
```

O utilitário identifica somente as tarefas Globoplast atuais, ativas e executadas com sucesso recentemente. Antes da alteração, salva o XML original em `C:\ProgramData\GloboplastSync\task-backups` e valida que o intervalo final ficou em `PT2M`.
