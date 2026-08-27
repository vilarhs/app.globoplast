# Deploy do Globoplast

## Estrutura da VPS

- Serviço: `globoplast.service`
- Aplicação: `/opt/globoplast/globoplast.jar`
- Releases: `/opt/globoplast/releases/globoplast-<versão>.jar`
- Banco: `/var/lib/globoplast/database.db`
- Ambiente: `/etc/globoplast.env`
- Backups: `/var/backups/globoplast`
- Backup diário: `globoplast-backup.timer`, às 03:15 (America/Sao_Paulo)
- Segunda cópia: `globoplast-backup-drive.timer`, ativada automaticamente quando o rclone estiver configurado

## Deploy normal

Na raiz do projeto:

```bash
bash deploy/deploy-vps.sh
```

O script executa:

1. preflight e build de produção;
2. upload do JAR e dos arquivos operacionais;
3. validação da versão contida no JAR;
4. instalação em `releases/` e troca atômica do link atual;
5. restart e health check por até 30 segundos;
6. rollback automático se a nova versão não ficar saudável;
7. retenção das quatro releases mais recentes.

Para usar outro servidor ou chave:

```bash
bash deploy/deploy-vps.sh --host ubuntu@SERVIDOR --key /caminho/chave.key
```

`--skip-build` deve ser usado somente quando o preflight do mesmo JAR já terminou com sucesso.

## Verificações na VPS

```bash
systemctl status globoplast --no-pager
curl -fsS http://127.0.0.1:8080/health
sudo journalctl -u globoplast -n 100 --no-pager
sudo /usr/local/sbin/globoplast-backup-check --directory /var/backups/globoplast
```

## Git e versões

Antes do deploy definitivo:

```bash
git status
git add -A
git commit -m "vX.Y.Z - resumo"
git tag -a vX.Y.Z -m "vX.Y.Z"
git push origin main
git push origin vX.Y.Z
```

Nunca versionar banco, arquivos `.env`, tokens, chaves SSH ou configuração do `rclone`.

## Recomendações

- Manter o repositório GitHub privado.
- Proteger a branch `main` e exigir revisão/preflight antes do merge.
- O deploy ativa o timer do Google Drive somente quando a configuração do rclone existir; faça um teste manual antes do primeiro deploy com essa configuração.
- Fazer um teste de restauração do banco periodicamente; backup sem teste de restauração não é garantia de recuperação.
- Para automação futura via GitHub Actions, guardar a chave SSH e o host em GitHub Environments/Secrets, nunca no repositório.
