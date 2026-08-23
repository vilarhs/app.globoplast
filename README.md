# Globoplast

Sistema web de acompanhamento de produção, OEE e Refugo da Globoplast.

## Estado atual

- Versão: `0.0.109`
- Java 21, Spring Boot, Vaadin e SQLite
- Aplicação publicada em `globoplast.app`
- Serviço da VPS: `globoplast.service`
- Banco de produção: `/var/lib/globoplast/database.db`
- Fuso e dia produtivo: `America/Sao_Paulo`, com virada às 06:00

O código atual é a fonte de verdade. Documentos e ferramentas da migração inicial foram removidos para não impor regras antigas às próximas versões. O histórico anterior permanece disponível no Git.

## Regras essenciais atuais

- O dia produtivo vira às 06:00.
- O turno C pertence ao dia produtivo anterior.
- Refugos A/B detectados antes das 06:00, quando o ERP informa a data civil corrente, pertencem ao dia produtivo anterior.
- A hora apresentada no Refugo usa a primeira detecção conhecida, preservada em `primeiro_sincronizado_em`.
- O OEE é consolidado por máquina e dia produtivo em uma única janela de 24 horas.
- Desempenho usa peças boas sobre a capacidade de 24 horas; Qualidade desconta o Refugo.
- O banco do DealerSystem/Firebird é acessado somente para leitura pelo sincronizador Windows.
- Edições de lançamentos ERP são overrides locais e não escrevem no ERP.
- Na tabela de Lançamentos, Programado, Lançado e Falta mostram o avanço acumulado por `OP + Código do Produto`; valores do ERP em milhares são convertidos para peças.

## Estrutura

- `src/main/java`: aplicação, serviços, regras e interface
- `src/main/resources`: configuração, estilos, imagens e favicon
- `deploy`: deploy, serviço systemd, Nginx e backups
- `deploy/windows/globoplast_sync_refugo_online.py`: sincronizador de Refugo do DealerSystem
- `DEPLOY.md`: procedimento operacional da VPS

## Validar e compilar

```bash
bash deploy/preflight.sh
```

O JAR final será criado em `target/globoplast.jar`.

## Deploy

```bash
bash deploy/deploy-vps.sh
```

O deploy valida a aplicação, troca a release de forma atômica, verifica `/health` e faz rollback automático em caso de falha. Consulte `DEPLOY.md` para diagnóstico e restauração.

## Segurança

Nunca versionar bancos SQLite, chaves SSH, tokens, senhas, arquivos `.env` ou configuração do `rclone`. Dados de produção são operacionais e permanecem fora do repositório.
