#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MAIN='src/main/java/br/com/globoplast/oee/view/MainView.java'
LAUNCH='src/main/java/br/com/globoplast/oee/service/LaunchService.java'
REFUGO='src/main/java/br/com/globoplast/oee/service/RefugoService.java'
CATALOG='src/main/java/br/com/globoplast/oee/service/CatalogService.java'
AUTH='src/main/java/br/com/globoplast/oee/service/AuthService.java'
PASSWORD='src/main/java/br/com/globoplast/oee/service/PasswordService.java'
OEE='src/main/java/br/com/globoplast/oee/service/OeeCalculator.java'
NORM='src/main/java/br/com/globoplast/oee/util/Norm.java'
DISPLAY='src/main/java/br/com/globoplast/oee/util/DisplayFormat.java'
RANGE='src/main/java/br/com/globoplast/oee/view/DateRangePicker.java'
CHART='src/main/java/br/com/globoplast/oee/view/InteractiveBarChart.java'
BAR_CHART='src/main/java/br/com/globoplast/oee/view/BarChart.java'
RANKING_CHART='src/main/java/br/com/globoplast/oee/view/OeeRankingChart.java'
GROUPED_CHART='src/main/java/br/com/globoplast/oee/view/GroupedIndicatorChart.java'
SYNC='src/main/java/br/com/globoplast/oee/web/SyncController.java'
SYNCSERVICE='src/main/java/br/com/globoplast/oee/service/SyncService.java'
CONFIG='src/main/java/br/com/globoplast/oee/config/AppConfig.java'
DATABASE='src/main/java/br/com/globoplast/oee/db/Database.java'
CSS='src/main/resources/META-INF/resources/globoplast.css'

must(){ grep -Fq -- "$2" "$1" || { echo "ERRO: $3"; exit 1; }; }
must_not(){ if grep -Fq -- "$2" "$1"; then echo "ERRO: $3"; exit 1; fi; }

echo '=== JAVA ==='
java -version

echo
echo '=== MAVEN ==='
mvn -version

echo
echo '=== V108 IDENTIDADE GLOBOPLAST CONSOLIDADA ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<artifactId>globoplast</artifactId>' 'artifactId ainda usa o nome antigo'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must src/main/resources/application.properties 'spring.application.name=globoplast' 'nome Spring não foi padronizado'
must "$SYNC" '"servico","globoplast"' 'health ainda anuncia o nome antigo'
must_not "$SYNC" '"servico","globoplast-java"' 'health ainda contém globoplast-java'
must deploy/deploy-vps.sh 'br.com.globoplast/globoplast/pom.properties' 'deploy procura coordenadas Maven antigas'
echo OK

echo
echo '=== V107 PADRONIZACAO VPS / DEPLOY AUTOMATIZADO ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must pom.xml '<finalName>globoplast</finalName>' 'nome final do JAR não foi padronizado'
must "$CONFIG" 'configured = "/var/lib/globoplast/database.db"' 'caminho padrão do banco não foi padronizado'
must deploy/globoplast.service 'WorkingDirectory=/opt/globoplast' 'serviço ainda usa diretório antigo'
must deploy/globoplast.service 'EnvironmentFile=-/etc/globoplast.env' 'serviço ainda usa arquivo de ambiente antigo'
must deploy/globoplast.service '/opt/globoplast/globoplast.jar' 'serviço não usa o JAR padronizado'
must deploy/deploy-vps.sh 'health check falhou; iniciando rollback' 'deploy sem rollback automático'
must deploy/deploy-vps.sh 'NR > 4' 'deploy não limita releases antigas'
must deploy/globoplast-backup 'remove_sqlite_sidecars' 'backup não limpa sidecars temporários'
test -f deploy/globoplast-backup-check || { echo 'ERRO: verificador de backup ausente'; exit 1; }
test -f deploy/globoplast-backup-drive || { echo 'ERRO: cópia para Drive ausente'; exit 1; }
bash -n deploy/deploy-vps.sh
bash -n deploy/padronizar-vps.sh
bash -n deploy/globoplast-backup-drive
echo OK

echo
echo '=== V106 CORTE DAS 06H / PRIMEIRA DETECCAO DO REFUGO ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$DATABASE" 'primeiro_sincronizado_em TEXT NOT NULL' 'primeira detecção não faz parte da tabela nova de Refugo'
must "$DATABASE" 'ADD COLUMN primeiro_sincronizado_em TEXT' 'migração da primeira detecção ausente'
must "$DATABASE" 'SET primeiro_sincronizado_em=sincronizado_em' 'histórico não recebe a primeira detecção conhecida'
must "$SYNCSERVICE" 'payload_hash,primeiro_sincronizado_em,sincronizado_em' 'sincronização não grava os dois instantes do Refugo'
must "$NORM" 'productiveScrapDate' 'regra produtiva específica do Refugo ausente'
must "$NORM" 'detected.getHour() < 6' 'corte das 06h não é aplicado à primeira detecção'
must "$REFUGO" 'Norm.productiveScrapDate(row.rawDate(), shift, row.firstDetectedAt())' 'página de Refugo não usa o corte produtivo preciso'
must "$LAUNCH" 'Norm.productiveScrapDate(r.date,r.shift,r.sync)' 'OEE não usa o mesmo corte produtivo do Refugo'
must "$MAIN" 'record.firstDetectedAt()' 'hora exibida não usa a primeira detecção'
echo OK

echo
echo '=== V105 ENVIO DIRETO PELO SUBMENU DO GRAFICO ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$MAIN" 'transfer.getSubMenu().addItem' 'submenu de setores ausente no menu do gráfico'
must "$MAIN" 'transferSelectedScrapToSector' 'envio direto pelo submenu ausente'
must_not "$MAIN" 'showScrapSectorTransfer' 'modal antigo de envio ainda está presente'
echo OK

echo
echo '=== V104 CURSOR MAO NO V. DO RODAPE ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$CSS" 'cursor:pointer!important' 'cursor de mão ausente no v. do rodapé'
echo OK

echo
echo '=== V103 RODAPE SOMENTE V / TOOLTIP DA VERSAO / ESPACO -25PX ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$MAIN" 'Span signature = new Span("v.");' 'assinatura v. ausente no rodapé'
must "$MAIN" 'String versionText = "globoplast.app " + AppConfig.VERSION;' 'texto dinâmico da versão ausente no tooltip do rodapé'
must "$MAIN" 'Tooltip.forComponent(signature).withText(versionText).withPosition(Tooltip.TooltipPosition.TOP);' 'tooltip da versão ausente no v. do rodapé'
must_not "$MAIN" 'Span version = new Span("globoplast.app " + AppConfig.VERSION);' 'versão completa ainda está visível no rodapé'
must "$CSS" 'pointer-events:auto!important' 'v. do rodapé não recebe hover'
must "$CSS" 'margin-bottom:calc(.25rem - 38px)!important' 'espaço entre Mostrar mais e v. não foi reduzido em mais 25 px'
echo OK

echo
echo '=== V102 LIXEIRA DE LANCAMENTOS / 30 DIAS ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$DATABASE" 'CREATE TABLE IF NOT EXISTS lancamentos_lixeira' 'tabela da lixeira de Lançamentos ausente'
must "$DATABASE" 'idx_lancamentos_lixeira_expira' 'índice de expiração da lixeira ausente'
must "$LAUNCH" 'putInTrash(c,"MANUAL"' 'exclusão manual não envia para a lixeira'
must "$LAUNCH" 'putInTrash(c,"ERP"' 'exclusão ERP não envia para a lixeira'
must "$LAUNCH" 'public void restoreTrash(long trashId,User user)' 'restauração da lixeira ausente'
must "$LAUNCH" '@Scheduled(fixedDelay=3600000,initialDelay=60000)' 'limpeza automática da lixeira ausente'
must "$LAUNCH" 'deleted.plusDays(30)' 'retenção de 30 dias ausente'
must "$MAIN" 'new Button(t("Lixeira"), VaadinIcon.TRASH.create())' 'botão Lixeira ausente no filtro de Lançamentos'
must "$MAIN" 'private void showLaunchTrash()' 'modal da lixeira ausente'
must "$MAIN" 'launches.restoreTrash(item.id(), user)' 'ação Restaurar ausente na lixeira'
must src/main/java/br/com/globoplast/oee/GloboplastApplication.java '@EnableScheduling' 'agendamento automático não habilitado'
echo OK

echo
echo '=== V101 MULTISSELECAO NO RESUMO DIA ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$MAIN" 'private final Set<String> summaryDaySectors = new LinkedHashSet<>();' 'estado multisseleção de Setor ausente no Resumo Dia'
must "$MAIN" 'private final Set<String> summaryDayMachines = new LinkedHashSet<>();' 'estado multisseleção de Máquina ausente no Resumo Dia'
must "$MAIN" 'private final Set<String> summaryDayShifts = new LinkedHashSet<>();' 'estado multisseleção de Turno ausente no Resumo Dia'
must "$MAIN" 't("Filtrar por Setor"), List.of(), summaryDaySectors' 'Setor do Resumo Dia não usa multisseleção'
must "$MAIN" 't("Filtrar por Máquina"), List.of(), summaryDayMachines' 'Máquina do Resumo Dia não usa multisseleção'
must "$MAIN" 't("Filtrar por Turno"), List.of("A", "B", "C"), summaryDayShifts' 'Turno do Resumo Dia não usa multisseleção'
must "$MAIN" 'summaryRowsForShifts(source, summaryDayShifts)' 'múltiplos turnos não são aplicados ao Resumo Dia'
must "$MAIN" 'summaryDaySectors.contains(r.getSector())' 'múltiplos setores não são aplicados ao Resumo Dia'
must "$MAIN" 'summaryDayMachines.contains(r.getMachine())' 'múltiplas máquinas não são aplicadas ao Resumo Dia'
echo OK

echo
echo '=== V100 TITULO VISUALIZAR SEM CODIGO DO PRODUTO ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$MAIN" 'Dialog dialog = launchDialog(t("Visualizar lançamento"));' 'título de Visualizar lançamento sem código ausente'
must_not "$MAIN" 't("Visualizar lançamento") + " · " + product' 'Código do Produto ainda aparece após Visualizar lançamento / View entry'
echo OK

echo
echo '=== V099 PREFLIGHT CORRIGIDO / V098 COLUNA CODIGO COMPACTA ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$MAIN" 'String compact = code.isBlank() ? full : code + (full.equals(code) ? "" : "...");' 'abreviação Código... ausente na coluna de Lançamentos'
must "$MAIN" '.withText(full)' 'tooltip completo Código · Cliente · Descrição ausente'
echo OK

echo
echo '=== V097 BASE V096 / COMPILACAO COLLECTION / VERSAO ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'versão interna incorreta'
must pom.xml '<version>0.0.108</version>' 'versão Maven incorreta'
must "$MAIN" 'import java.util.Collection;' 'import java.util.Collection ausente no MainView'
test -f PARIDADE_APPV723.md || { echo 'ERRO: matriz de paridade ausente'; exit 1; }
echo OK

echo
echo '=== V096 CLIENTE NOS LANCAMENTOS ==='
must "$LAUNCH" 'PRODUCT_METADATA_SQL' 'consulta de Cliente/Descrição por produto ausente'
must "$LAUNCH" 'cliente,maquina,turno,qtd_apon' 'Cliente não é lido do apontamento ERP'
must "$LAUNCH" 'Set<String> clients' 'filtro Cliente não chega ao serviço de Lançamentos'
must "$MAIN" 'launchClients' 'estado do filtro Cliente ausente'
must "$MAIN" 't("Cliente"), launchClientOptions' 'campo Cliente ausente no filtro de Lançamentos'
must "$MAIN" 'launchProductMetadataText' 'Código · Cliente · Descrição ausente'
must "$MAIN" 'record.setClientErp(productMetadata.client())' 'Cliente não é atualizado pelo código ao salvar'
echo OK


echo
echo '=== V037 NUMEROS / FONTE / REFUGO / FILTROS ==='
must "$DISPLAY" 'NumberFormat.getNumberInstance' 'formatação numérica localizada ausente'
must "$DISPLAY" 'setGroupingUsed(true)' 'separador de milhares ausente'
must "$MAIN" 't("{percentual}% do total").replace("{percentual}", format1(totalPct))' '100% do total do Refugo ausente'
must "$MAIN" 'periodValue = t("Hoje")' 'rótulo Hoje no período de Refugo ausente'
must "$MAIN" 'menuSyncItem.getElement().setAttribute("data-gp-sync-state", online ? "online" : "offline");' 'estado Online/Offline atual no menu ausente'
must "$MAIN" 'menuSyncItem.getElement().setAttribute("data-gp-sync-label", t(online ? "Online" : "Offline"));' 'rótulo Online/Offline atual no menu ausente'
must "$RANGE" 'setAttribute("aria-selected", "true")' 'dia selecionado não recebe estado visual explícito'
must "$CSS" '--lumo-font-family:"Source Sans Pro","Source Sans 3"' 'fonte do original não aplicada ao Lumo'
must "$CSS" '0.0.37 — TIPOGRAFIA / NUMEROS PT-BR / REFUGO / FILTROS' 'bloco CSS v037 ausente'
must "$CSS" '.gp-kpi-caption' 'legenda compacta dos KPIs ausente'
echo OK

echo

echo '=== V034 REFUGO / BARRAS ==='
must "$CHART" 'new BarSizing("92%", 320, 13)' 'barra mais larga para poucos itens ausente'
must "$CHART" 'return new BarSizing("68%", 110, 11)' 'barra mais larga para muitos itens ausente'
must "$CSS" 'border-radius:4px 4px 2px 2px!important' 'cantos arredondados das barras ausentes'
must "$CSS" 'var(--gp-refugo-bar-ratio,56%)' 'largura visual final das barras ausente'
echo OK


echo
echo '=== V036 MENU / MAIUSCULAS EM TEMPO REAL ==='
must "$MAIN" 'installContextMenuHoverOnly(trigger);' 'fix robusto de hover do menu ausente'
must "$MAIN" "input.addEventListener('input', () => uppercaseNow(true), true);" 'conversão imediata para maiúsculas ausente'
must "$MAIN" "toLocaleUpperCase('pt-BR')" 'maiúsculas client-side pt-BR ausentes'
must "$CSS" '0.0.36 — MENU HOVER REAL / MAIUSCULAS EM TEMPO REAL' 'bloco CSS v036 ausente'
must "$CSS" '@media (hover:hover) and (pointer:fine)' 'hover real do menu ausente'
echo OK

echo
echo '=== V035 LOGIN DARK / MENU DROPDOWN ==='
must "$MAIN" "MutationObserver(apply)" 'sincronização dinâmica da cor do login ausente'
must "$MAIN" "input.style.setProperty('color', color, 'important')" 'texto digitado do login não é forçado por tema'
must "$CSS" '0.0.35 — LOGIN DARK / MENU DROPDOWN' 'bloco CSS v035 ausente'
must "$CSS" 'vaadin-context-menu-item[focused]:not(:hover)' 'foco automático do primeiro item do menu não foi neutralizado'
must "$CSS" 'html[theme~="dark"] .gp-login-field::part(input-field)' 'input do login dark sem regra branca final'
echo OK

echo '=== V032 OEE / ALIASES / EDICAO ERP ==='
must "$DATABASE" 'maquinas_snapshot' 'snapshot de capacidade/setor de máquina ausente'
must "$LAUNCH" 'knownMachineMetadata' 'fallback de capacidade conhecida ausente'
must "$LAUNCH" 'ensureUserCanActOnRecord' 'permissão por setor preservado do ERP ausente'
must "$MAIN" 'machineOptions.add(originalMachine)' 'máquina ERP não cadastrada não é preservada no formulário'
must "$MAIN" 'else if (!record.isErp())' 'edição ERP ainda exige máquina cadastrada'
must "$CSS" 'max-width:1800px!important' 'margens laterais centralizadas da v030 ausentes'
echo OK

echo
echo '=== V032 CATALOGO/HISTORICO APPV723 ==='
must "$SYNC" '/java-sync/v1/catalogo' 'endpoint de catálogo v723 ausente'
must "$SYNC" '/java-sync/v1/diagnostico-oee' 'diagnóstico OEE ausente'
must "$SYNCSERVICE" 'importCatalog' 'importação de catálogo ausente'
must "$SYNCSERVICE" 'maquinas_catalogo_com_capacidade' 'diagnóstico de capacidade ausente'
must "$MAIN" 'capacityReadOnly' 'edição de capacidade ERP sem cadastro ausente'

must "$NORM" '{"INJEÇÂO ", "INJETORA "}' 'alias INJEÇÂO -> INJETORA ausente'
must "$NORM" '{"FECHA HOT AIR ", "HOT AIR "}' 'alias FECHA HOT AIR -> HOT AIR ausente'
must "$NORM" 'public static String machineKey' 'chave canônica de máquina ausente'
must "$LAUNCH" 'matchedMachine!=null?matchedMachine.name():machineNormalized' 'máquina ERP não é reconciliada ao catálogo antes do OEE'
must "$SYNCSERVICE" 'maquinas_erp_resolvidas_por_alias' 'diagnóstico real de aliases ausente'
must "$SYNCSERVICE" 'maquinas_historicas_recebidas' 'importação de capacidades históricas ausente'
must "$SYNC" 'maquinas_historicas' 'endpoint não aceita capacidades históricas'
test -f deploy/migrar_catalogo_v723.py || { echo 'ERRO: migrador de catálogo v723 ausente'; exit 1; }
echo OK

echo
echo '=== OEE / DIA PRODUTIVO ==='
must "$CONFIG" 'ZoneId.of("America/Sao_Paulo")' 'fuso Brasília ausente'
must "$NORM" 'if (now.getHour() < 6) now = now.minusDays(1)' 'virada 06:00 ausente'
must "$NORM" '"C".equalsIgnoreCase(text(shift)) ? rawDate.minusDays(1)' 'Turno C não pertence ao dia anterior'
must "$OEE" 'double scheduled=24.0;' 'janela única de 24h divergente'
must "$OEE" 'double proportional=capacity>0?capacity:0;' 'capacidade 24h da máquina divergente'
must "$OEE" 'good/proportional*100.0' 'desempenho não usa somente peças boas'
must_not "$OEE" 'processed/proportional*100.0' 'Refugo ainda eleva o Desempenho e compensa a perda de Qualidade'
must "$OEE" 'good*100.0/processed' 'qualidade divergente'
must "$OEE" 'availability/100.0*performance/100.0*quality/100.0*100.0' 'OEE divergente'
must_not "$LAUNCH" 'allocateUnifiedScheduledHours(out);' 'há redistribuição artificial de horas na visão consolidada'
echo OK

echo
echo '=== LANCAMENTOS / ERP / OVERRIDES ==='
must "$LAUNCH" 'int pcs=(int)Math.round(x.qty*1000.0)' 'Qtd.Apon não converte milhares para peças'
must "$LAUNCH" 'applyOverrides(items);' 'overrides ERP não aplicados'
must "$LAUNCH" 'saveErpOverride' 'edição ERP ausente'
must "$LAUNCH" 'hideErp' 'exclusão lógica ERP ausente'
must "$LAUNCH" 'ensureUserCanActOnMachine' 'permissão setorial de edição/exclusão ausente'
must "$LAUNCH" 'attachErpScrapToManualOnlyWhenNeeded' 'cruzamento posterior Refugo/produção manual ausente'
must "$LAUNCH" 'oee.recalculate(out);' 'OEE não recalcula ao materializar a visão atual'
must "$LAUNCH" 'recentFirst()' 'ordenação mais recente primeiro ausente'
must_not "$MAIN" 'launchSearch = Norm.text(record.getOrderNumber());' 'salvar lançamento ainda aplica busca automática pela OP'
must "$MAIN" 'buildProductionDetail' 'detalhamento multi-OP ausente'
must "$MAIN" 'parseScrapKg' 'parser de Refugo manual ausente'
must "$MAIN" 'parseHours' 'parser de horas ausente'
must "$MAIN" 'data-gp-launch-order' 'ordem Enter/Tab do formulário ausente'
echo OK

echo
echo '=== REFUGO / REGRAS NUMERICAS ==='
must "$REFUGO" 'plannedUnits = Math.max(0.0, r.getDouble("qtd_planej")) * 1000.0' 'Qtd.Planej não está em milhares'
must "$REFUGO" 'Math.rint((scrapKg * 1000.0) / unitWeightG)' 'fallback Qtd Itens divergente'
must "$REFUGO" 'VALID_SHIFTS = List.of("A", "B", "C")' 'diluição dos turnos inválidos ausente'
must "$NORM" 'case "777021" -> "Qualidade"' 'mapa 777021 divergente'
must "$NORM" 'case "777020" -> "Desenvolvimento"' 'mapa 777020 divergente'
must "$NORM" 'case "777025" -> "Devolução Cliente"' 'mapa 777025 divergente'
must "$NORM" 'case "994", "993", "120" -> "Injetados"' 'prefixos de Injetados divergentes'
must "$MAIN" 'scrapOrders' 'filtro Ordem do Refugo ausente'
must "$MAIN" 'scrapClients' 'filtro Cliente do Refugo ausente'
must "$MAIN" 'scrapOperators' 'filtro Operador do Refugo ausente'
must "$MAIN" 'renderRecentScrapLaunches' 'Lançamentos recentes do Refugo ausentes'
must "$MAIN" 'renderDescriptionDetails' 'detalhamento de descrição ausente'
echo OK

echo
echo '=== RESUMO DIA / MES ==='
must "$MAIN" 'Resumo Diário da Produção' 'Resumo Diário ausente'
must "$MAIN" 'gp-summary-filter-trigger-v047' 'botão de filtro do Resumo Dia ausente'
must "$MAIN" '🎯 OEE Geral' 'KPI OEE do Resumo Dia ausente'
must_not "$MAIN" 'new GroupedIndicatorChart(' 'gráfico removido do Resumo Dia voltou a ser criado'
must "$MAIN" 'Tabela Consolidada por Máquina' 'tabela consolidada Dia ausente'
must "$MAIN" 'Resumo Mensal de Eficiência' 'Resumo Mensal ausente'
must "$MAIN" 'summaryFilterDropdown(filter, filterFields' 'popover de filtros do Resumo Mês ausente'
must "$MAIN" 'Ranking de OEE no Mês' 'ranking mensal ausente'
must "$MAIN" 'Tabela Consolidada por Equipamento' 'consolidado mensal ausente'
must "$MAIN" 'Todos os Apontamentos do Mês' 'lista mensal ausente'
echo OK

echo
echo '=== AUTENTICACAO / PERFIS / CADASTROS ==='
must "$PASSWORD" 'PBKDF2WithHmacSHA256' 'hash de senha não usa PBKDF2-HMAC-SHA256'
must "$AUTH" 'Não é possível excluir o usuário atualmente logado.' 'proteção de autoexclusão ausente'
must "$AUTH" 'O sistema deve manter pelo menos um usuário Administrador.' 'proteção último administrador ausente'
must "$AUTH" 'UPDATE usuarios SET is_admin=1,perfil=? WHERE id=?' 'promoção do ADMIN existente ausente'
must "$DATABASE" 'ensureColumn(c, "usuarios", "idioma"' 'migração de idioma do usuário ausente'
must "$DATABASE" 'UPDATE usuarios SET usuario=UPPER(TRIM(usuario))' 'normalização histórica de usuários ausente'
must "$CATALOG" "perfil='padrao'" 'bloqueio de exclusão de setor vinculado ausente'
must "$MAIN" 'Conferente' 'perfil Conferente ausente'
must "$MAIN" 'showReports()' 'dialog Relatórios ausente'
echo OK

echo
echo '=== UI / NAVEGACAO / TEMA ==='
must "$MAIN" 'setAttribute("gp_tab", key)' 'persistência da aba na sessão ausente'
must "$MAIN" 'history.replaceState' 'limpeza controlada da URL ausente'
must "$MAIN" '📋 ' 'rótulo original Lançamentos ausente'
must "$MAIN" '📅 ' 'rótulo original Resumo Dia ausente'
must "$MAIN" '📊 ' 'rótulo original Resumo Mês ausente'
must "$MAIN" '♻️ ' 'rótulo original Refugo ausente'
must "$MAIN" 'gp-filter-funnel-icon' 'funil original ausente'
must "$CSS" 'clip-path:polygon(0 0,100% 0,64% 45%,64% 100%,36% 100%,36% 45%)' 'geometria do funil original divergente'
must "$MAIN" 'setCloseOnOutsideClick(true)' 'fechamento externo de popover ausente'
must "$RANGE" 'DateTimeFormatter.ofPattern("dd/MM/yyyy", locale())' 'data dd/mm/aaaa ausente'
must "$MAIN" "root.setAttribute('theme', resolved)" 'tema claro/escuro não aplicado ao HTML'
echo OK

echo
echo '=== SYNC ERP ==='
must "$SYNC" '/java-sync/v1/status' 'status Java sync ausente'
must "$SYNC" '/java-sync/v1/apontamento' 'endpoint apontamento ausente'
must "$SYNC" '/java-sync/v1/refugo' 'endpoint refugo ausente'
must "$SYNC" 'MAX_RECORDS=5000' 'limite por lote divergente'
must "$SYNC" 'MAX_SKEW=300' 'janela HMAC divergente'
echo OK

echo
echo "=== V039 FONTE / CALENDARIO ==="
must "$CSS" 'fonts.googleapis.com/css2?family=Source+Sans+3' 'fonte Source Sans 3 ausente'
must "$RANGE" 'gp-period-selected' 'classe de seleção de período ausente'
must "$RANGE" 'public void setReadOnly(boolean readOnly)' 'modo somente leitura do calendário unificado ausente'
echo "OK"

echo
echo "=== V040 CARREGAMENTO / FILTRO / BUSCA ==="
must "$MAIN" 'search.setValueChangeMode(ValueChangeMode.EAGER);' 'busca incremental de Lançamentos ausente'
must "$MAIN" 'cachedLaunchData(launchStart, launchEnd)' 'cache da página Lançamentos ausente'
must "$MAIN" 'cachedScrapData(scrapStart, scrapEnd)' 'cache da página Refugo ausente'
must "$MAIN" 'invalidateDataCaches();' 'invalidação de cache após sincronização ausente'
must "$MAIN" 'refreshScrap(scrapActiveDimension);' 'refresh leve do Refugo após sync ausente'
must "$MAIN" 'configureAdaptiveGridHeight(grid, selected.size(), 18, 560);' 'virtualização de grids grandes do Refugo ausente'
must "$CSS" '0.0.40 — CARREGAMENTO / HOVER FILTRO / BUSCA INCREMENTAL' 'bloco CSS v040 ausente'
must "$CSS" 'background:color-mix(in srgb,var(--gp-text) 6%,transparent)!important' 'hover discreto do filtro ausente'
must "$CSS" 'border-color:color-mix(in srgb,var(--gp-text) 42%,transparent)!important' 'foco neutro do filtro ausente'
echo "OK"

echo
echo '=== V041 CORRECOES EFETIVAS ==='
must "$MAIN" 'search.setValueChangeMode(ValueChangeMode.EAGER);' 'busca incremental não está em EAGER'
must "$MAIN" 'activeDayRefresh = refreshRef[0];' 'refresh leve do resumo diário ausente'
must "$MAIN" 'activeMonthRefresh = refreshRef[0];' 'refresh leve do resumo mensal ausente'
must "$LAUNCH" 'out.removeIf(r -> !Norm.fold(' 'busca ainda usa bloqueio por correspondência exata'
must "$RANGE" 'day.addThemeVariants(ButtonVariant.LUMO_PRIMARY);' 'data selecionada não usa variante visual forte'
must "$CSS" '0.0.41 — CAMADA CANONICA FINAL' 'camada CSS canônica v041 ausente'
must "$CSS" 'background:color-mix(in srgb,var(--gp-text) 9%,transparent)!important' 'hover discreto do filtro ausente'
must_not "$CSS" '0.0.41 — CAMADA CANONICA FINAL RED' 'marcador inválido'
echo OK

echo
echo '=== V043 FUNIL AO LADO DA BUSCA ==='
must "$MAIN" 'VaadinIcon.FILTER.create()' 'ícone padrão de funil Vaadin ausente'
must "$CSS" '0.0.43 — FUNIL PADRÃO AO LADO DA BUSCA' 'bloco CSS v043 ausente'
must "$CSS" 'vaadin-icon.gp-filter-funnel-icon' 'renderização final do ícone de funil ausente'
echo OK


echo
echo '=== V044 FUNIL REAL AO LADO DA BUSCA ==='
must "$MAIN" 'searchFilterButton()' 'botão de funil dedicado à busca ausente'
must "$MAIN" 'gp-search-filter-toolbar-v044' 'toolbar busca+funil v044 ausente'
must "$MAIN" 'gp-search-filter-funnel-v044' 'SVG de funil v044 ausente'
must "$CSS" '0.0.44 — FUNIL REAL IMEDIATAMENTE AO LADO DA BUSCA' 'bloco CSS v044 ausente'
must "$CSS" 'display:flex!important' 'toolbar v044 não usa layout flexível'
must "$CSS" 'flex:0 1 560px!important' 'campo de busca não limita largura para manter funil ao lado'
echo OK

echo
echo '=== V045 TOOLBAR / STATUS MENU / SCROLL REFUGO ==='
must "$MAIN" 'gp-launch-toolbar-v045' 'Novo Lançamento não foi movido para a linha de busca/filtro'
must "$MAIN" 'gp-launch-new-inline-v045' 'botão Novo Lançamento inline ausente'
must_not "$MAIN" 'status.setId("launch-status")' 'status Online/Offline ainda está na página de Lançamentos'
must "$MAIN" 'gp-menu-sync-info-v045' 'status de sincronização no menu ausente'
must "$MAIN" 't("Última sincronização") + " " + time' 'horário da última sincronização ausente no menu'
must "$MAIN" 'data-gp-sync-label' 'estado Online/Offline no menu ausente'
must "$CSS" '0.0.45 — TOOLBAR LANÇAMENTOS / STATUS NO MENU / SCROLL REFUGO' 'bloco CSS v045 ausente'
must "$CSS" '.gp-filter-dropdown-refugo>.gp-filter-dropdown-grid' 'área rolável exclusiva dos campos de Refugo ausente'
must "$CSS" 'overflow-y:auto!important' 'rolagem dos campos do filtro Refugo ausente'
must "$CSS" '.gp-filter-dropdown-refugo>.gp-filter-dropdown-actions' 'rodapé fixo do filtro Refugo ausente'
echo OK

echo
echo '=== V046 PREFLIGHT STATUS ATUAL ==='
must_not "$MAIN" 'status.setId("launch-status")' 'status antigo ainda existe na página de Lançamentos'
must "$MAIN" 't("Última sincronização") + " " + time' 'horário da última sincronização não está no menu'
must "$MAIN" 'data-gp-sync-state' 'estado online/offline atual não está no menu'
must "$MAIN" 'data-gp-sync-label' 'rótulo online/offline atual não está no menu'
echo OK

echo
echo '=== V047 FILTROS / TOOLBAR / BARRAS ==='
must "$MAIN" 'clearMultiSelectSearchText' 'limpeza do texto duplicado do multiselect ausente'
must "$MAIN" 'gp-summary-title-row-v047' 'cabeçalho com filtro à direita nos resumos ausente'
must "$MAIN" 'summaryFilterDropdown(filter, filterFields' 'filtros dos resumos não foram movidos para popover'
must "$CSS" 'margin-left:auto!important' 'Novo Lançamento não está ancorado à direita'
must "$CSS" 'height:44px!important' 'Novo Lançamento/filtro não acompanha a altura da busca'
must "$CHART" 'new BarSizing("92%", 320, 13)' 'barras de Refugo não foram alargadas'
echo OK

echo
echo '=== V042 PREFLIGHT CONSOLIDADO ==='
echo OK

echo '=== V048 MULTISELECT / CALENDARIO / MODAIS ==='
must "$MAIN" 'new DateRangePicker(' 'Resumo Dia sem calendário padrão'
must "$MAIN" 'this::t, null, true' 'Resumo Dia sem modo de data única'
must "$MAIN" '__gpMultiSelectSingleRenderV052' 'limpeza estrutural atual do MultiSelect ausente'
must "$MAIN" 'addThemeName("gp-modal-v053")' 'tema uniforme atual dos modais ausente'
must "$CSS" '0.0.48 — MULTISELECT / CALENDARIO RESUMO DIA / MODAIS' 'bloco CSS v048 reemitido ausente'
must "$CSS" '0.0.49 — MULTISELECT SEM DUPLICACAO / MODAL UNIFORME' 'bloco CSS v049 ausente'
echo OK
echo


echo '=== V049 MULTISELECT / MODAL UNIFORME ==='
must "$MAIN" 'setProperty("keepFilter", false)' 'keepFilter=false ausente no MultiSelect'
must "$MAIN" 'clearTree(host);' 'limpeza do filtro duplicado ausente'
must "$CSS" 'background:#3a3c44!important' 'cinza claro uniforme do modal ausente'
must "$CSS" 'background:transparent!important' 'header/content/footer não estão transparentes sobre o mesmo modal'
must_not "$CSS" '\\n\\n/* =================================================================' 'CSS contém escapes literais inválidos'
echo OK

echo
echo '=== V050 BUSCA / FUNIL / MODAL ==='
must "$MAIN" 'addThemeName("gp-modal-v053")' 'tema atual não foi aplicado aos modais'
must "$CSS" '0.0.50 — BUSCA/FUNIL PADRONIZADOS / MODAL CINZA CLARO UNIFORME' 'bloco CSS v050 ausente'
must "$CSS" 'height:44px!important' 'altura padronizada de busca/funil ausente'
must "$CSS" 'gap:8px!important' 'distância padronizada entre busca e funil ausente'
must "$CSS" 'background:#4a4d55!important' 'cinza claro uniforme v050 ausente'
must "$CSS" 'vaadin-dialog-overlay[theme~="gp-modal-v050"]::part(footer)' 'rodapé uniforme do modal v050 ausente'
echo OK

echo
echo '=== V051 MULTISELECT / DIALOG / BOTAO ==='
must "$MAIN" 'box.setKeepFilter(false);' 'API Java keepFilter=false ausente'
must "$MAIN" '__gpMultiSelectSingleRenderV052' 'limpeza client-side pós-seleção ausente'
must_not "$MAIN" "host.addEventListener('filter-changed'" 'listener antigo pode apagar texto durante pesquisa'
must "$MAIN" 'addThemeName("gp-modal-v053")' 'tema atual não aplicado ao Dialog'
must "$CSS" 'vaadin-dialog.gp-modal-v051::part(overlay)' 'seletor correto do host Dialog ausente'
must "$CSS" 'vaadin-dialog.gp-modal-v051::part(footer)' 'footer uniforme do Dialog ausente'
must "$CSS" 'top:-3.5px!important' 'Novo Lançamento não foi elevado mais 1 px na v079'
echo OK

echo
echo '=== V052 FILTRO VISUAL / MODAL POR TEMA ==='
must "$MAIN" 'host.opened=false' 'seletor não fecha após a escolha'
must "$CSS" 'gp-filter-multiselect[has-value]:not([opened])>input' 'input duplicado não é ocultado no estado fechado'
must "$MAIN" 'addThemeName("gp-modal-v053")' 'tema atual não aplicado ao Dialog'
must "$CSS" '--gp-modal-bg-v052:#30323a' 'cinza discreto do modal escuro ausente'
must "$CSS" '--gp-modal-bg-v052:#f1f2f4' 'cinza discreto do modal claro ausente'
must "$CSS" 'background:var(--gp-modal-bg-v052)!important' 'plano uniforme do modal ausente'
echo OK

echo
echo '=== V053 MODAIS TOTALMENTE UNIFORMES ==='
must "$MAIN" 'addThemeName("gp-modal-v053")' 'tema v053 não aplicado pela fábrica de Dialog'
must "$MAIN" 'addClassName("gp-modal-v053")' 'classe v053 não aplicada pela fábrica de Dialog'
must "$CSS" '0.0.53 — MODAIS TOTALMENTE UNIFORMES' 'bloco CSS v053 ausente'
must "$CSS" '--vaadin-grid-cell-background:var(--gp-modal-bg-v053)!important' 'Grid do modal ainda pode herdar fundo global'
must "$CSS" 'vaadin-grid::part(header-cell)' 'cabeçalho do Grid do modal não foi uniformizado'
must "$CSS" 'background:var(--gp-modal-bg-v053)!important' 'plano único v053 ausente'
must "$CSS" 'border:1px solid var(--gp-control-border)!important' 'separação discreta dos campos ausente'
echo OK

echo
echo '=== V054 CAMPO MAQUINA / FLUIDEZ GLOBAL ==='
must "$MAIN" 'gp-launch-standard-field-v054' 'campos de lançamento não preservam a padronização v054'
must "$CSS" '0.0.54 — CAMPO MAQUINA PADRONIZADO / INTERFACE MAIS FLUIDA' 'bloco CSS v054 ausente'
must "$CSS" '.gp-launch-standard-field-v054::part(input-field)' 'caixa visual única dos campos de lançamento ausente'
must "$MAIN" 'launchRangeCache' 'cache de múltiplas faixas de Lançamentos ausente'
must "$MAIN" 'scrapRangeCache' 'cache de múltiplas faixas de Refugo ausente'
must "$MAIN" 'refreshEntriesRef[0].run()' 'paginação incremental do Resumo do Mês ausente'
must "$LAUNCH" 'rememberMachineMetadata(snapshots.values())' 'persistência em lote dos metadados ausente'
must "$LAUNCH" 'p.executeBatch();' 'batch SQLite dos metadados ausente'
must_not "$LAUNCH" 'rememberMachineMetadata(r);' 'persistência por lançamento ainda está ativa'
must "$DATABASE" 'idx_historico_oee_maquina_data' 'índice de histórico por máquina/data ausente'
echo OK

echo
echo '=== V055 CAMPO MAQUINA ESTRUTURALMENTE IGUAL ==='
must "$MAIN" 'f.machine = new ComboBox<>()' 'Máquina ainda não usa a mesma base de input dos demais campos'
must "$MAIN" 'f.machine.setAllowCustomValue(false)' 'ComboBox permite máquina fora do cadastro'
must "$MAIN" 'gp-launch-machine-field-v055' 'classe estrutural v055 do campo Máquina ausente'
must_not "$MAIN" 'f.machine = new Select<>()' 'campo Máquina ainda usa Select'
must "$CSS" '0.0.55 — MAQUINA COM A MESMA ESTRUTURA DOS TEXTFIELDS' 'bloco CSS v055 ausente'
must "$CSS" 'vaadin-combo-box.gp-launch-machine-field-v055::part(input-field)' 'caixa visual do ComboBox Máquina ausente'
echo OK

echo
echo '=== V056 KPIS DE REFUGO MAIORES E DISTRIBUIDOS ==='
must "$MAIN" 'gp-refugo-kpis-v056' 'classe exclusiva dos KPIs principais de Refugo ausente'
must "$CSS" '0.0.56 — KPIS DE REFUGO MAIORES E DISTRIBUIDOS' 'bloco CSS v056 ausente'
must "$CSS" 'grid-template-columns:repeat(5,minmax(0,1fr))!important' 'KPIs não usam cinco colunas iguais'
must "$CSS" 'min-height:96px!important' 'KPIs de Refugo não foram ampliados'
must "$CSS" 'font-size:clamp(1.65rem,2.15vw,2.15rem)!important' 'valores dos KPIs não foram ampliados'
must "$CSS" 'grid-column:1/-1!important' 'distribuição responsiva do último KPI ausente'
echo OK

echo
echo '=== V057 ALINHAMENTO / USUARIOS / GRAFICO / FILTROS / CORES ==='
must "$CSS" '0.0.57 — CAMPOS / GRAFICO / FILTROS CONTEXTUAIS / COR UNICA' 'bloco CSS v057 ausente'
must "$CSS" '.gp-launch-row-2{' 'regra de alinhamento Máquina/Capacidade ausente'
must "$CSS" 'align-items:end!important' 'Capacidade não alinha pela base de Máquina'
must "$MAIN" 'ComboBox<String> profile = new ComboBox<>()' 'Perfil de Usuário ainda não usa ComboBox'
must "$MAIN" 'ComboBox<String> sector = new ComboBox<>()' 'Setor de Usuário ainda não usa ComboBox'
must "$MAIN" 'gp-admin-standard-field-v057' 'campos de Usuário não foram padronizados'
must "$CSS" 'justify-content:flex-end!important' 'título Análise por dimensão não está à direita'
must "$CSS" 'margin-top:14px!important' 'menu do gráfico não acompanha o título'
must "$MAIN" 'updateOptionsRef' 'filtros contextuais de Refugo ausentes'
must "$MAIN" 'selectedSectors.stream().anyMatch(s -> s.equalsIgnoreCase(r.sector()))' 'Setor não limita as Máquinas'
must "$MAIN" 'selectedMachines.contains(r.machine())' 'Máquina não limita os demais filtros'
must "$CSS" '--gp-modal-bg-v053:var(--gp-surface)!important' 'modal não usa a cor do dropdown do menu'
must "$CSS" '--vaadin-input-field-background:var(--gp-surface)!important' 'campos não usam a cor do menu'
echo OK

echo
echo '=== V058 MENU CONTEXTUAL / TITULO / MODAIS / DROPDOWN ==='
must "$MAIN" 'attachScrapContextMenu(barChart, rows, dimension)' 'menu contextual não foi ligado ao gráfico'
must "$MAIN" 'menu.setTarget(target)' 'menu do gráfico não usa o ponto clicado como alvo'
must "$MAIN" 'setProperty("selector", ".gp-refugo-bar-column")' 'menu não está restrito às barras'
must_not "$MAIN" 'chartLine.add(barChart, scrapContextMenu(rows, dimension))' 'botão fixo do gráfico ainda está ativo'
must "$CHART" '__gpSelectV061' 'seleção visual imediata da barra ausente'
must "$CSS" '0.0.58 — MENU NO CLIQUE / TITULO A ESQUERDA / SUPERFICIES CORRIGIDAS' 'bloco CSS v058 ausente'
must "$CSS" 'justify-content:flex-start!important' 'título Análise por dimensão não está à esquerda'
must "$CSS" '--gp-modal-uniform-v058:#30323a' 'cor uniforme dos modais no tema escuro ausente'
must "$CSS" '--vaadin-dialog-background:var(--gp-modal-uniform-v058)!important' 'superfície oficial do Dialog não foi definida'
must "$CSS" 'vaadin-context-menu-overlay:not(.gp-v058-specificity)::part(content)' 'dropdown principal não foi restaurado'
echo OK

echo
echo '=== V059 TABELA LANCAMENTOS / OBSERVACOES OEE ==='
must "$MAIN" 'grid.addClassName("gp-launch-grid-v059")' 'classe exclusiva da tabela de Lançamentos ausente'
must "$MAIN" '.withText(obs)' 'texto das Observações não foi ligado ao valor do OEE'
must "$MAIN" 'gp-oee-observation-icon-v063' 'ícone do OEE não recebeu o alvo do tooltip'
must_not "$MAIN" 'Span capacityInfo = new Span("ⓘ")' 'ícone de capacidade ausente ainda aparece na coluna OEE'
must_not "$MAIN" 'return value.toUpperCase(locale())' 'tooltip não preserva exatamente o texto de Observações'
must "$CSS" '0.0.59 — TABELA DE LANCAMENTOS / INFORMATIVO DE OBSERVACOES' 'bloco CSS v059 ausente'
must "$CSS" 'font-size:16px!important' 'texto das linhas de Lançamentos não foi ampliado'
must "$CSS" 'font-size:15px!important' 'texto dos cabeçalhos de Lançamentos não foi ampliado'
must "$CSS" 'content:none!important' 'balão CSS antigo não foi desativado para o novo ícone'
echo OK

echo
echo '=== V060 OEE / DUPLO CLIQUE / CURSOR / MAQUINA / INTEGRIDADE ==='
must "$MAIN" '.withText(obs)' 'tooltip não está diretamente no informativo do OEE'
must_not "$MAIN" 'new Span("ⓘ")' 'ícone adicional ainda aparece ao lado do OEE'
must "$MAIN" 'toggleScrapSelectionAndRefreshKpis' 'clique simples não alterna a seleção do gráfico'
must "$MAIN" 'menu.setOpenOnClick(false)' 'menu do gráfico ainda abre com clique simples'
must "$MAIN" 'menu.setOpenOnClick(false)' 'menu contextual não está restrito ao botão direito'
must "$MAIN" 'ComboBox<String> sector = new ComboBox<>()' 'Atribuir ao Setor ainda não usa ComboBox'
must "$MAIN" 'gp-machine-edit-form-v060' 'layout compacto de Editar Máquina ausente'
must "$CATALOG" 'SELECT COUNT(*) FROM maquinas WHERE setor=? COLLATE NOCASE' 'Setor ainda pode ser excluído com Máquinas vinculadas'
must "$AUTH" 'O sistema deve manter pelo menos um usuário Administrador.' 'proteção do último Administrador ausente'
must "$CSS" '0.0.60 — DUPLO CLIQUE / CURSOR / MAQUINA / PROTECOES' 'bloco CSS v060 ausente'
must "$CSS" 'cursor:pointer!important' 'cursor de mão nos itens acionáveis ausente'
must "$CSS" 'grid-template-columns:minmax(0,1.35fr) minmax(118px,.65fr)!important' 'modal de Máquina não usa layout compacto'
echo OK

echo
echo '=== V061 OEE / ABAS / SCROLL / FILTROS ==='
must "$MAIN" '.withText(obs)' 'Observações não alimentam o balão do OEE'
must "$MAIN" '.withPosition(Tooltip.TooltipPosition.TOP)' 'posição superior dos informativos não foi preservada'
must "$MAIN" '__gpInstantTabsV061' 'resposta imediata das abas ausente'
must "$MAIN" 'Objects.equals(renderedTabKey, key)' 'proteção contra renderização duplicada de aba ausente'
must_not "$CHART" 'setTimeout(()=>{' 'gráfico ainda atrasa a seleção com temporizador'
must "$MAIN" 'grid.setAllRowsVisible(true)' 'Lançamentos recentes ainda podem criar scroll interno'
must "$MAIN" 'new Div(period, sector, machine, order' 'Máquina não aparece antes de Ordem no filtro de Refugo'
must "$MAIN" 'gp-uppercase-sector-filter-v061' 'maiúsculas dos filtros de Setor ausentes'
must "$MAIN" 'gp-modal-open-v061' 'bloqueio do scroll da página atrás do modal ausente'
must "$CSS" '0.0.61 — OEE / ABAS IMEDIATAS / SCROLL MODAL / FILTROS REFUGO' 'bloco CSS v061 ausente'
must "$CSS" 'overscroll-behavior:none!important' 'isolamento de rolagem da página ausente'
echo OK

echo
echo '=== V062 DETALHES / GRAFICO / OEE / SETOR ==='
must "$MAIN" 'refreshScrapSelectionPanels(currentScrapRows(), dimension)' 'clique simples não atualiza Detalhes do item'
must "$MAIN" 'if (selected && "Descrição".equals(dimension))' 'Detalhes do item não respeitam a seleção de Descrição'
must "$MAIN" 'if (selected && scrapShowLaunches)' 'Lançamentos não permanecem exclusivos da ação do menu'
must "$MAIN" 'scrapShowLaunches = true;' 'ação Ver lançamentos não ativa a tabela'
must "$MAIN" '.withText(obs)' 'balão do OEE ausente'
must_not "$MAIN" 'data-gp-oee-tooltip' 'tooltip flutuante anterior ainda está ativo'
must "$CSS" '0.0.62 — DETALHES NO CLIQUE / GRAFICO À ESQUERDA / OEE CONFIAVEL' 'bloco CSS v062 ausente'
must "$CSS" 'margin-left:-15px!important' 'deslocamento atual dos gráficos não foi preservado'
must "$CSS" 'width:100%!important' 'gráfico não preserva o recuo da lateral direita'
must_not "$CSS" '.gp-uppercase-sector-filter-v061::part(label)' 'título do campo Setor ainda é forçado para maiúsculas'
must "$CSS" '.gp-uppercase-sector-filter-v061[has-value]::part(value-button)' 'valor selecionado de Setor não é forçado para maiúsculas'
echo OK

echo
echo '=== V063 POSICAO / TODOS / ICONE OEE ==='
must "$MAIN" 'Icon info = VaadinIcon.INFO_CIRCLE.create()' 'ícone real de informação do OEE ausente'
must "$MAIN" 'if (!obs.isBlank())' 'ícone do OEE não está condicionado a Observações'
must "$MAIN" 'gp-oee-observation-icon-v063' 'classe exclusiva do ícone OEE ausente'
must "$MAIN" 'Tooltip.forComponent(info)' 'balão do ícone OEE ausente'
must "$MAIN" '.withPosition(Tooltip.TooltipPosition.TOP)' 'informativo OEE não abre acima'
must_not "$MAIN" 'new Span("?")' 'interrogação ainda é usada como ícone do OEE'
must "$MAIN" 'String(input.value||' 'uppercase de Setor não depende do conteúdo digitado'
must "$CSS" '0.0.63 — POSICAO REFUGO / PLACEHOLDER SETOR / ICONE OEE' 'bloco CSS v063 ausente'
must "$CSS" 'margin-left:-15px!important' 'gráfico não está exatamente 15 px à esquerda'
must "$CSS" 'width:100%!important' 'gráfico não preserva o recuo da lateral direita'
must "$CSS" 'left:18px!important' 'Quantidade Refugada não avançou 10 px à direita'
must "$CSS" '.gp-uppercase-sector-filter-v061[has-value]::part(value-button)' 'uppercase ainda não depende de valor selecionado'
must "$CSS" '.gp-uppercase-sector-filter-v061:not([has-value])::part(value-button)' 'placeholder Todos não foi protegido'
must "$CSS" 'html body .gp-oee-cell>.gp-oee-observation-icon-v063' 'visibilidade do ícone OEE não foi garantida'
echo OK

echo
echo '=== V064 REFUGO / BOTAO DIREITO / DROPDOWNS / TROCA DE SETOR ==='
must "$MAIN" '__gpScrapScrollV065' 'preservação atual da posição da página ao trocar abas ausente'
must "$MAIN" 'gp-refugo-sector-chart-v064' 'classe exclusiva do título Análise por Setor ausente'
must "$MAIN" 'Enviar para outro setor' 'ação de troca de setor ausente no menu do gráfico'
must "$MAIN" 'transfer.getSubMenu().addItem' 'submenu de seleção do setor de destino ausente'
must "$MAIN" 'transferSelectedScrapToSector' 'envio direto para o setor selecionado ausente'
must_not "$MAIN" 'showScrapSectorTransfer' 'modal antigo de seleção do setor de destino ainda está presente'
must_not "$MAIN" '__gpDoubleContextV060' 'evento sintético de duplo clique ainda está ativo'
must "$CHART" 'addEventListener("contextmenu"' 'barra não comunica o botão direito ao servidor'
must_not "$CHART" 'addEventListener("dblclick"' 'duplo clique ainda abre o menu do gráfico'
must "$REFUGO" 'reassignSector' 'persistência da troca de setor ausente'
must "$REFUGO" 'erp_refugo_setor_overrides' 'serviço não usa a tabela de override de setor'
must "$DATABASE" 'CREATE TABLE IF NOT EXISTS erp_refugo_setor_overrides' 'tabela de override de setor ausente'
must "$REFUGO" 'equalsIgnoreCase(r.sector())' 'filtro de Setor não compara os nomes em maiúsculas com segurança'
must "$CSS" '0.0.64 — REFUGO / MENU DIREITO / DROPDOWNS GLOBAIS / TROCA DE SETOR' 'bloco CSS v064 ausente'
must "$CSS" '.gp-refugo-sector-chart-v064 .gp-refugo-chart-title' 'posição exclusiva de Análise por Setor ausente'
must "$CSS" 'left:15px!important' 'Análise por Setor não avançou 15 px'
must "$CSS" 'top:4px!important' 'Análise por Setor não subiu 10 px'
must "$CSS" 'vaadin-combo-box-overlay:not(.gp-v064-specificity)::part(overlay)' 'dropdown global padronizado ausente'
echo OK

echo
echo '=== V065 GEOMETRIA / ABAS ESTAVEIS / MENU E URL LIMPOS ==='
must "$MAIN" 'gp-refugo-page-v065' 'classe de geometria final do Refugo ausente'
must "$MAIN" 'window.__gpScrapScrollV065=window.scrollY' 'captura da posição vertical ausente'
must "$MAIN" "this.addEventListener('selected-changed',capture" 'troca por teclado não preserva a posição'
must "$MAIN" 'setTimeout(restore,60)' 'restauração após a renderização final ausente'
must "$MAIN" 'gp-system-menu-trigger-v065' 'classe final do botão de menu ausente'
must "$MAIN" "history.replaceState(null,'',location.pathname)" 'URL não foi limpa após trocar de aba'
must_not "$MAIN" "location.pathname+'?aba='" 'nome da aba ainda aparece na URL'
must "$CSS" '0.0.65 — GEOMETRIA REFUGO / ABAS ESTAVEIS / MENU E URL LIMPOS' 'bloco CSS v065 ausente'
must "$CSS" 'margin-left:-25px!important' 'conjunto do gráfico não avançou mais 10 px à esquerda'
must "$CSS" 'width:calc(100% + 25px)!important' 'lado direito não termina no alinhamento do logo'
must "$CSS" 'left:38px!important' 'Quantidade Refugada não avançou 20 px'
must "$CSS" 'overflow-anchor:none!important' 'ancoragem automática do Refugo ainda está ativa'
must "$CSS" 'scrollbar-gutter:stable' 'largura da página ainda pode oscilar entre abas'
must "$CSS" '.gp-system-menu-trigger-v065:hover' 'hover limpo do menu ausente'
echo OK

echo
echo '=== V066 RECUO / ALINHAMENTO / MENU SEM CAIXA ==='
must "$MAIN" 'gp-refugo-page-v066' 'classe final de geometria do Refugo ausente'
must "$MAIN" 'gp-system-menu-trigger-v066' 'classe final do menu ausente'
must "$MAIN" 'ButtonVariant.LUMO_TERTIARY_INLINE' 'menu não usa a variante inline sem caixa'
must "$CSS" '0.0.66 — RECUO DIREITO / TITULOS ALINHADOS / MENU SEM CAIXA' 'bloco CSS v066 ausente'
must "$CSS" 'width:calc(100% + 10px)!important' 'recuo real de 15 px no lado direito ausente'
must "$CSS" '.gp-refugo-sector-chart-v064 .gp-refugo-chart-title' 'Análise por Setor não recebeu alinhamento exclusivo'
must "$CSS" 'left:38px!important' 'títulos do Refugo não compartilham a referência de 38 px'
must "$CSS" 'vaadin-button.gp-system-menu-trigger-v066::before' 'pseudo-elemento interno do menu não foi neutralizado'
must "$CSS" 'vaadin-button.gp-system-menu-trigger-v066::after' 'foco interno do menu não foi neutralizado'
must "$CSS" 'vaadin-button.gp-system-menu-trigger-v066::part(label)' 'label interno do menu não foi neutralizado'
must "$CSS" '--lumo-primary-color-10pct:transparent!important' 'fundo de hover do tema ainda pode aparecer'
echo OK

echo
echo '=== V067 RECUO DIREITO / TITULO SETOR ==='
must "$MAIN" 'gp-refugo-page-v067' 'classe de geometria v067 ausente'
must "$CSS" '0.0.67 — RECUO DIREITO AMPLIADO / TITULO SETOR À DIREITA' 'bloco CSS v067 ausente'
must "$CSS" 'width:calc(100% - 20px)!important' 'recuo total de 45 px à direita ausente'
must "$CSS" 'left:68px!important' 'Análise por Setor não avançou 30 px à direita'
echo OK

echo
echo '=== V068 RECUO 62PX / TITULO SETOR FORCADO ==='
must "$MAIN" 'gp-refugo-page-v068' 'classe de geometria v068 ausente'
must "$MAIN" 'gp-refugo-sector-chart-v068' 'classe específica do título Setor ausente'
must "$MAIN" 'requestAnimationFrame(apply)' 'posição do título não é reafirmada após a montagem'
must "$CSS" '0.0.68 — RECUO DIREITO 62 PX / TITULO SETOR FORCADO' 'bloco CSS v068 ausente'
must "$CSS" 'width:calc(100% - 37px)!important' 'recuo direito total de 62 px ausente'
echo OK

echo
echo '=== V069 POSICAO / RECUO 65PX / TRANSFERENCIA FILTRADA ==='
must "$MAIN" 'gp-refugo-page-v069' 'classe de geometria v069 ausente'
must "$MAIN" 'gp-refugo-sector-chart-v069' 'classe específica v069 do título Setor ausente'
must "$MAIN" 'List<RefugoRecord> selectedRows = currentScrapRows().stream()' 'transferência não recalcula as linhas do filtro ativo'
must "$MAIN" 'scraps.reassignSector(selectedRows, destinationSector, user)' 'submenu não envia diretamente as linhas analíticas selecionadas'
must "$REFUGO" 'Map<String, Long> ids = new LinkedHashMap<>()' 'transferência ainda agrupa somente pelo ERP bruto'
must "$REFUGO" 'loadAnalysisSectorOverrides()' 'override analítico não é carregado'
must "$REFUGO" 'erp_refugo_analysis_setor_overrides' 'persistência por analysis_id ausente'
must "$DATABASE" 'CREATE TABLE IF NOT EXISTS erp_refugo_analysis_setor_overrides' 'tabela de transferência analítica ausente'
must "$CSS" '0.0.69 — POSICAO SETOR / RECUO 65 PX / TRANSFERENCIA FILTRADA' 'bloco CSS v069 ausente'
must "$CSS" 'width:calc(100% - 40px)!important' 'recuo direito total de 65 px ausente'
must "$CSS" 'left:53px!important' 'posição horizontal final do título ausente'
must "$CSS" 'top:-3px!important' 'posição vertical final do título ausente'
echo OK

echo
echo '=== V070 SETOR UNICO / LIMPEZA ORIGINAL / TITULOS ALINHADOS ==='
must "$MAIN" 'gp-refugo-page-v070' 'classe da página v070 ausente'
must "$MAIN" 'gp-refugo-chart-title-aligned-v070' 'classe comum dos títulos de Refugo ausente'
must "$MAIN" "title.style.setProperty('left','48px','important')" 'títulos não voltaram 5 px'
must "$MAIN" "title.style.setProperty('top','0px','important')" 'títulos não desceram 3 px'
must "$MAIN" 'default -> uppercaseSector(nonBlank(r.sector()))' 'setores com diferença apenas de caixa ainda podem duplicar no gráfico'
must "$MAIN" 'scraps.clearSectorReassignments(user)' 'Limpar filtros não restaura os setores originais'
must "$MAIN" 'invalidateDataCaches();' 'limpeza não invalida o cache após restaurar setores'
must "$REFUGO" 'public int clearSectorReassignments(User actor)' 'serviço de restauração original ausente'
must "$REFUGO" 'DELETE FROM erp_refugo_analysis_setor_overrides' 'override analítico não é limpo'
must "$REFUGO" 'DELETE FROM erp_refugo_setor_overrides' 'override legado não é limpo'
must_not "$REFUGO" 'INSERT INTO setores' 'transferência de Refugo não pode criar setor no cadastro'
must "$CSS" '0.0.70 — SETORES UNIFICADOS / LIMPEZA ORIGINAL / TITULOS ALINHADOS' 'bloco CSS v070 ausente'
must "$CSS" 'left:48px!important' 'posição horizontal v070 ausente'
must "$CSS" 'top:0!important' 'posição vertical v070 ausente'
echo OK

echo
echo '=== V071 BUSCA E FILTRO REFUGO 0,5PX ACIMA ==='
must "$MAIN" 'gp-refugo-search-toolbar-v071' 'classe exclusiva da toolbar de Refugo ausente'
must "$CSS" '0.0.71 — BUSCA E FILTRO DE REFUGO 0,5 PX ACIMA' 'bloco CSS v071 ausente'
must "$CSS" 'transform:translateY(-.5px)!important' 'campo e filtro de Refugo não subiram exatamente 0,5 px'
echo OK

echo
echo '=== V072 BUSCA E FILTRO REFUGO 0,25PX ABAIXO ==='
must "$MAIN" 'gp-refugo-search-toolbar-v072' 'classe exclusiva v072 da toolbar de Refugo ausente'
must "$CSS" '0.0.72 — BUSCA E FILTRO DE REFUGO 0,25 PX ABAIXO' 'bloco CSS v072 ausente'
must "$CSS" 'transform:translateY(-.25px)!important' 'campo e filtro não desceram exatamente 0,25 px'
echo OK

echo
echo '=== V073 BUSCA E FILTRO REFUGO MAIS 0,2PX ABAIXO ==='
must "$MAIN" 'gp-refugo-search-toolbar-v073' 'classe exclusiva v073 da toolbar de Refugo ausente'
must "$CSS" '0.0.73 — BUSCA E FILTRO DE REFUGO MAIS 0,2 PX ABAIXO' 'bloco CSS v073 ausente'
must "$CSS" 'transform:translateY(-.05px)!important' 'campo e filtro não desceram mais 0,2 px'
echo OK

echo
echo '=== V074 REFUGO ALINHADO EXATAMENTE A LANCAMENTOS ==='
must "$MAIN" 'gp-refugo-search-toolbar-v074' 'classe final v074 da toolbar de Refugo ausente'
must "$CSS" '0.0.74 — REFUGO ALINHADO EXATAMENTE A LANCAMENTOS' 'bloco CSS v074 ausente'
must "$CSS" 'margin:.28rem 0 .65rem!important' 'margem do Refugo não coincide com Lançamentos'
must "$CSS" 'transform:none!important' 'deslocamentos fracionários anteriores não foram neutralizados'
must "$CSS" 'align-items:flex-end!important' 'rótulo, campo e filtro não compartilham a linha-base de Lançamentos'
echo OK

echo
echo '=== V075 FAVICON JAVA SEM CACHE DO STREAMLIT ==='
must "$MAIN" 'link[rel~=icon],link[rel=\"shortcut icon\"]' 'referências antigas de favicon não são removidas'
must "$MAIN" "l.href='/favicon.png?v=075-20260821'" 'favicon Java não usa URL versionada'
test -f src/main/resources/META-INF/resources/favicon.png || { echo 'ERRO: favicon PNG ausente'; exit 1; }
test -f src/main/resources/META-INF/resources/favicon.ico || { echo 'ERRO: favicon ICO ausente'; exit 1; }
echo OK

echo
echo '=== V076 EXCLUSOES DO REFUGO SINCRONIZADAS ==='
must "$CONFIG" 'VERSION = "0.0.108"' 'base funcional v076 não está integrada à versão atual'
must "$SYNCSERVICE" 'reconcileRefugoSnapshot' 'reconciliação autoritativa do Refugo ausente'
must "$SYNCSERVICE" 'ChronoUnit.DAYS.between(start,end)>30' 'limite seguro de 31 dias ausente'
must "$SYNCSERVICE" 'DELETE FROM erp_refugo_raw WHERE erp_id=?' 'registro bruto excluído no ERP não é removido'
must "$SYNCSERVICE" 'DELETE FROM erp_refugo_analysis_setor_overrides WHERE erp_id=?' 'reclassificação analítica órfã não é removida'
must "$SYNCSERVICE" 'DELETE FROM erp_refugo_setor_overrides WHERE erp_id=?' 'reclassificação legada órfã não é removida'
must "$SYNCSERVICE" 'snapshot_reconciliado' 'confirmação explícita de reconciliação ausente'
must "$DATABASE" 'CREATE TABLE IF NOT EXISTS erp_sync_exclusoes' 'auditoria de exclusões ausente'
must "$DATABASE" 'payload_json TEXT' 'auditoria não preserva o conteúdo completo do registro excluído'
must "$DATABASE" 'excluidos INTEGER NOT NULL DEFAULT 0' 'contador de exclusões nos lotes ausente'
must "$SYNCSERVICE" 'INSERT INTO erp_sync_exclusoes' 'exclusões não são auditadas'
must "$SYNCSERVICE" 'json.writeValueAsString(payload)' 'conteúdo removido não é arquivado para recuperação'
must "$SYNCSERVICE" 'INSERT INTO erp_sync_lotes(fonte,connector_id,sent_at,recebidos,alterados,excluidos,recebido_em)' 'lote não registra exclusões'
must "$MAIN" 'invalidateDataCaches();' 'cache não pode ser invalidado após mudança da assinatura de sync'
must "$SYNCSERVICE" 'ultimo_recebimento=excluded.ultimo_recebimento' 'reconciliação não atualiza assinatura da sincronização'
must "$LAUNCH" 'attachErpScrapToManualOnlyWhenNeeded' 'vínculo dinâmico Refugo/produção ausente'
must src/main/java/br/com/globoplast/oee/web/SyncController.java 'snapshot_complete' 'endpoint não reconhece snapshot completo'
must src/main/java/br/com/globoplast/oee/web/SyncController.java 'snapshot_erp_ids' 'endpoint não recebe IDs presentes no ERP'
test -f deploy/windows/globoplast_sync_refugo_online.py || { echo 'ERRO: sincronizador Windows v076 ausente'; exit 1; }
test -f deploy/windows/atualizar_globoplast_refugo_v076.py || { echo 'ERRO: atualizador Windows v076 ausente'; exit 1; }
must deploy/windows/globoplast_sync_refugo_online.py 'snapshot_complete' 'agente Windows não envia snapshot completo'
must deploy/windows/globoplast_sync_refugo_online.py 'ultima_auditoria_completa' 'auditoria histórica diária ausente'
must deploy/windows/globoplast_sync_refugo_online.py 'AUDITORIA_INTERVALO_DIAS = 31' 'auditoria histórica não usa blocos seguros'
must deploy/windows/globoplast_sync_refugo_online.py 'AUDITORIA_INICIO = date(2025, 1, 1)' 'início da auditoria histórica incorreto'
echo OK

echo
echo '=== V078 LOGIN NA MESMA ABA / NOVO LANCAMENTO +1PX ==='
must "$MAIN" 'TAB_AUTH_STORAGE_KEY = "globoplast_tab_auth_v078"' 'chave de autenticação por aba ausente'
must "$MAIN" 'sessionStorage.getItem($0)' 'autorização da aba não é consultada'
must "$MAIN" 'String.valueOf(candidate.id()).equals(tabUserId)' 'marca da aba não está vinculada ao usuário autenticado'
must "$MAIN" 'sessionStorage.setItem($0,$1)' 'login não grava autorização na aba'
must "$MAIN" 'sessionStorage.removeItem($0)' 'logout não remove autorização da aba'
must "$MAIN" 'if (candidate != null) auth.logout();' 'cookie antigo não é invalidado ao abrir nova aba'
must_not "$MAIN" 'TAB_AUTH_RELOAD_KEY' 'chave transitória da v077 ainda está presente'
must_not "$MAIN" "'pagehide'" 'entrada pelo endereço ainda seria interpretada como fechamento'
must_not "$MAIN" "navigation.type === 'reload'" 'persistência ainda está limitada somente ao F5'
must "$CSS" 'top:-3.5px!important' 'Novo Lançamento não preserva o deslocamento final da v079'
echo OK

echo
echo '=== V079 NOVO LANCAMENTO MAIS 1PX ACIMA ==='
must "$CSS" 'top:-3.5px!important' 'Novo Lançamento não subiu mais 1 px sobre a v078'
must_not "$CSS" 'top:-2.5px!important' 'posição antiga do botão Novo Lançamento ainda está ativa'
must "$MAIN" 'TAB_AUTH_STORAGE_KEY = "globoplast_tab_auth_v078"' 'comportamento de login da v078 foi alterado'
echo OK

echo
echo '=== V080 COMPARATIVOS DINAMICOS / LANCADO POR ==='
must "$MAIN" 'monthlyComparisonTab.setVisible(scrapHasMonthlyComparison());' 'comparativo mensal não atualiza com o período'
must "$MAIN" 'yearlyComparisonTab.setVisible(scrapHasYearlyComparison());' 'comparativo anual não atualiza com o período'
must "$MAIN" '.map(r -> YearMonth.from(r.productiveDate()))' 'comparativo mensal não verifica os meses presentes no recorte'
must "$MAIN" '.map(r -> r.productiveDate().getYear())' 'comparativo anual não verifica os anos presentes no recorte'
must "$MAIN" '.limit(2)' 'detecção eficiente de múltiplos períodos ausente'
must "$MAIN" 'grid.addColumn(RefugoRecord::operator).setHeader(t("Lançado por"));' 'coluna Lançado por ausente em Ver lançamentos'
echo OK

echo
echo '=== V081 NOMES DOS COMPARATIVOS / CALENDARIOS UNIFICADOS ==='
must "$MAIN" 'dimensions.put(monthlyComparisonTab, "Comparativo Mensal")' 'regra interna do comparativo mensal ausente'
must "$MAIN" 'dimensions.put(yearlyComparisonTab, "Comparativo Anual")' 'regra interna do comparativo anual ausente'
must "$MAIN" 't(monthly ? "Análise por mês" : "Análise por ano")' 'títulos Análise por mês/ano ausentes'
must "$MAIN" 'private DateRangePicker datePicker(String label, LocalDate value)' 'formulários não usam o calendário padrão dos filtros'
must "$MAIN" 'gp-unified-date-picker-v081' 'marcador do calendário unificado ausente'
must_not "$MAIN" 'new DatePicker(' 'calendário nativo antigo ainda está em uso'
must_not "$MAIN" 'com.vaadin.flow.component.datepicker.DatePicker' 'import do calendário nativo antigo ainda está presente'
must "$RANGE" 'public void focus()' 'foco do calendário unificado ausente'
must "$CSS" '.gp-unified-date-picker-v081 .gp-period-field' 'acabamento do calendário de formulários ausente'
echo OK

echo
echo '=== V082 RESUMO DIA / TURNO / INFORMATIVOS ACIMA ==='
must "$MAIN" 'private final Set<String> summaryDayShifts = new LinkedHashSet<>();' 'estado do filtro de turno ausente'
must "$MAIN" 't("Filtrar por Turno"), List.of("A", "B", "C"), summaryDayShifts' 'filtro A/B/C ausente no Resumo do Dia'
must "$MAIN" 'summaryRowsForShifts(source, summaryDayShifts)' 'filtro de turno não está aplicado aos lançamentos'
must "$MAIN" 'result.add(metrics,tableTitle,grid);' 'KPIs e consolidado não são os únicos blocos do Resumo do Dia'
must_not "$MAIN" 'new GroupedIndicatorChart(' 'gráfico ainda está presente no Resumo do Dia'
must "$MAIN" 'summaryCompactCell(r.getProduct())' 'compactação de múltiplos códigos ausente'
must "$MAIN" 'summaryCompactCell(r.getOrderNumber())' 'compactação de múltiplas OPs ausente'
must "$MAIN" 'items.get(0) + (items.size() > 1 ? "..." : "")' 'indicador de múltiplos códigos/OPs ausente'
must "$MAIN" '.withPosition(Tooltip.TooltipPosition.TOP)' 'informativos superiores ausentes na tela principal'
must_not "$MAIN" 'setAttribute("title"' 'balão nativo sem posição controlada permanece na tela principal'
must "$RANGE" 'Tooltip.TooltipPosition.TOP' 'informativos do calendário não abrem acima'
must "$CHART" 'setTitle(' 'balão do gráfico de Refugo não acompanha o ponteiro'
must "$BAR_CHART" 'setTitle(' 'balão das barras não acompanha o ponteiro'
must "$RANKING_CHART" 'setTitle(' 'balão do ranking não acompanha o ponteiro'
must "$GROUPED_CHART" 'setTitle(' 'balão do gráfico agrupado não acompanha o ponteiro'
must "$CSS" '.gp-summary-compact-value-v082' 'acabamento dos códigos e OPs compactos ausente'
echo OK

echo
echo '=== V083 CONSOLIDADO / REFUGO / RODAPE / MULTIPLAS OPS ==='
must "$MAIN" 'grid.addClassNames("gp-summary-grid", "gp-summary-day-grid-v083")' 'tabela diária não recebeu tamanho de Lançamentos'
must "$CSS" 'vaadin-grid.gp-summary-day-grid-v083::part(body-cell)' 'texto do consolidado não acompanha Lançamentos'
must "$CSS" '.gp-launch-order-compact-v083[tabindex]' 'cursor da OP múltipla ausente'
must "$CSS" 'cursor:pointer!important' 'cursor de mão não foi aplicado'
must "$MAIN" 'new Tab(t("Anual"))' 'nome Anual ausente'
must "$MAIN" 'new Tab(t("Mensal"))' 'nome Mensal ausente'
year_line=$(grep -nF 'tabsList.add(yearlyComparisonTab);' "$MAIN" | head -1 | cut -d: -f1)
month_line=$(grep -nF 'tabsList.add(monthlyComparisonTab);' "$MAIN" | head -1 | cut -d: -f1)
test -n "$year_line" && test -n "$month_line" && test "$year_line" -lt "$month_line" || { echo 'ERRO: Análise por ano não aparece antes de Análise por mês'; exit 1; }
must "$MAIN" 'dims.addClassName("gp-inner-tabs")' 'alinhamento original das abas de Refugo ausente'
must_not "$MAIN" 'dims.setWidthFull();' 'abas de Refugo continuam ocupando toda a largura'
must_not "$CSS" 'flex:1 1 0!important' 'abas de Refugo continuam divididas pela tela'
must "$CSS" '.gp-refugo-page .gp-comparison-kpis' 'indicadores do comparativo não ocupam toda a largura'
must "$CSS" 'white-space:nowrap!important' 'valor/unidade do comparativo ainda pode quebrar linha'
must "$CSS" 'transform:translateY(13px)!important' 'informações do rodapé não preservaram o deslocamento atual'
must "$MAIN" 'new ComponentRenderer<>(this::launchOrderCell)' 'coluna OP não compacta múltiplas OPs'
must "$MAIN" 'private Component launchOrderCell(LaunchRecord record)' 'informativo de múltiplas OPs ausente'
must "$MAIN" 'totals = extractJsonIntArray(detail, "totais")' 'quantidades por OP não usam o detalhamento salvo'
must "$MAIN" 'lines.add(ops.get(i) + " · " + quantity)' 'informativo não relaciona OP e produção'
must_not "$MAIN" 'launchSearch = Norm.text(record.getOrderNumber());' 'salvar novo lançamento ainda aplica busca automática'
echo OK

echo
echo '=== V084 COMPATIBILIDADE VAADIN DOS INFORMATIVOS ==='
must_not "$MAIN" 'tooltip.getElement()' 'Tooltip usa método inexistente no Vaadin 25.2.4'
must "$MAIN" 'Tooltip.forComponent(value)' 'informativos de Código/OP ausentes'
must "$MAIN" '.withHoverDelay(150)' 'atraso dos informativos foi alterado'
echo OK

echo
echo '=== V085 ABAS REFUGO / HORA REAL DA CARGA ==='
must "$MAIN" 'grid.addColumn(MainView::scrapLoadTime).setHeader(t("Hora")).setAutoWidth(true);' 'Lançamentos recentes não usam a hora comum de carga'
must "$MAIN" 'grid.addColumn(MainView::scrapLoadTime).setHeader(t("Hora"));' 'Ver lançamentos não usa a hora comum de carga'
must "$MAIN" 'private static String scrapLoadTime(RefugoRecord record)' 'conversão comum da hora de carga ausente'
must "$MAIN" 'Norm.syncTime(record == null ? null : record.firstDetectedAt())' 'hora não vem da primeira detecção'
must_not "$MAIN" 'setHeader(t("Hora aprox."))' 'Ver lançamentos ainda mostra hora aproximada'
echo OK

echo
echo '=== V086 ABAS / LIMPEZA INDIVIDUAL / TURNO / MAIUSCULAS / INFORMativos ==='
must_not "$MAIN" 'new Tab(t("Análise por ano"))' 'Análise ainda aparece no nome da aba anual'
must_not "$MAIN" 'new Tab(t("Análise por mês"))' 'Análise ainda aparece no nome da aba mensal'
must "$MAIN" 't(monthly ? "Análise por mês" : "Análise por ano")' 'Análise por ano/mês não permanece abaixo das abas'
must "$MAIN" 't("Filtrar por Setor"), List.of(), summaryDaySectors' 'multisseleção/limpeza de Setor ausente'
must "$MAIN" 't("Filtrar por Máquina"), List.of(), summaryDayMachines' 'multisseleção/limpeza de Máquina ausente'
must "$MAIN" 't("Filtrar por Turno"), List.of("A", "B", "C"), summaryDayShifts' 'multisseleção/limpeza de Turno ausente'
must "$MAIN" 'summaryRowsForShifts(source, summaryDayShifts)' 'filtro de turno não limita os valores exibidos'
must "$MAIN" 'summaryRecordForShift(LaunchRecord original, String shift)' 'projeção isolada do turno ausente'
must "$MAIN" 'if (!readOnly) forceUppercase(f.observations);' 'Observações não recebem maiúsculas em tempo real'
echo OK

echo
echo '=== V087 LIMPEZA NATIVA / TITULOS ==='
must "$MAIN" 'private ComboBox<String> summaryFilterCombo' 'ComboBox padrão dos filtros ausente'
must "$MAIN" 'field.setClearButtonVisible(true);' 'X nativo dos filtros ausente'
must_not "$MAIN" 'summaryFieldClearButton' 'botão X personalizado ainda permanece'
must_not "$MAIN" 'summaryClearableFilter' 'estrutura personalizada de limpeza ainda permanece'
must_not "$CSS" 'gp-summary-field-clear-v086' 'CSS do X personalizado ainda permanece'
echo OK

echo
echo '=== V088 BALOES RESTAURADOS A V085 ==='
must_not "$CSS" 'html body vaadin-tooltip-overlay' 'override global de Tooltip ainda permanece'
must_not "$CSS" '--lumo-font-size-xxs:18px!important' 'fonte ampliada do Tooltip ainda permanece'
must_not "$CSS" '--lumo-font-size-xs:18px!important' 'fonte ampliada do Tooltip ainda permanece'
must_not "$CSS" '--lumo-font-size-s:18px!important' 'fonte ampliada do Tooltip ainda permanece'
echo OK

echo
echo '=== V089 GRAFICOS NO PONTEIRO / RODAPE +5PX ==='
must_not "$CHART" 'com.vaadin.flow.component.shared.Tooltip' 'Tooltip superior ainda permanece no gráfico de Refugo'
must_not "$BAR_CHART" 'com.vaadin.flow.component.shared.Tooltip' 'Tooltip superior ainda permanece nas barras'
must_not "$RANKING_CHART" 'com.vaadin.flow.component.shared.Tooltip' 'Tooltip superior ainda permanece no ranking'
must_not "$GROUPED_CHART" 'com.vaadin.flow.component.shared.Tooltip' 'Tooltip superior ainda permanece no gráfico agrupado'
must "$CSS" 'transform:translateY(13px)!important' 'rodapé não preservou o deslocamento atual'
echo OK

echo
echo '=== V090 OEE 24H / CAPACIDADE UNICA POR MAQUINA-DIA ==='
must "$OEE" 'double scheduled=24.0;' 'OEE não usa uma única janela de 24h por máquina/dia'
must "$OEE" 'double proportional=capacity>0?capacity:0;' 'desempenho não usa diretamente a capacidade 24h da máquina'
must "$OEE" 'r.setScheduledHours(24.0);r.setCapacity24h(capacity);' 'horas/capacidade não são unificadas em todos os lançamentos do grupo'
must_not "$OEE" 'mapToDouble(r->Math.max(0,r.getScheduledHours())).sum()' 'horas dos lançamentos ainda estão multiplicando a janela do dia'
must "$LAUNCH" 'Máquina + Dia produtivo representa uma única operação de' 'regra de consolidação 24h não está documentada no fluxo'
echo OK

echo
echo '=== V091 REFUGO REDUZ QUALIDADE E OEE ==='
must "$OEE" 'double performance=proportional>0?good/proportional*100.0:0.0;' 'Desempenho ainda não usa exclusivamente peças boas'
must "$OEE" 'double quality=processed>0?good*100.0/processed:0.0;' 'Qualidade não desconta Refugo em peças'
must_not "$OEE" 'double performance=proportional>0?processed/proportional*100.0:0.0;' 'Refugo ainda compensa sua perda elevando o Desempenho'
echo OK

echo
echo '=== V092 INGLES / RODAPE / MOSTRAR MAIS ==='
must src/main/java/br/com/globoplast/oee/service/I18n.java 'Map.entry("Lançamentos", "Entries")' 'rótulo inglês de Lançamentos incorreto'
must_not src/main/java/br/com/globoplast/oee/service/I18n.java 'Search and Entries' 'rótulo inglês antigo ainda permanece'
must "$CSS" 'transform:translateY(13px)!important' 'informações do rodapé não desceram mais 3 px'
must "$CSS" 'margin-bottom:calc(.25rem - 38px)!important' 'redução atual do espaço após Mostrar mais não corresponde à v103'
echo OK

echo
echo '=== V093 ORDENACAO POR ULTIMA MOVIMENTACAO ==='
must "$DATABASE" 'movimentado_em TEXT' 'coluna da última movimentação manual ausente'
must "$DATABASE" 'ensureColumn(c, "historico_oee", "movimentado_em"' 'migração da última movimentação ausente'
must "$DATABASE" "UPDATE historico_oee SET movimentado_em=data||'T'" 'compatibilidade dos lançamentos históricos ausente'
must src/main/java/br/com/globoplast/oee/model/LaunchRecord.java 'private String movementAt = "";' 'instante da última movimentação ausente no modelo'
must "$LAUNCH" 'Comparator.comparingLong(LaunchService::movementEpoch).reversed()' 'ordenação não prioriza a última movimentação'
must "$LAUNCH" 'r.setMovementAt(now.toString());' 'lançamento manual não grava a movimentação atual'
must "$LAUNCH" 'applyMovementIfLater(item,x.sync,true);' 'lançamento ERP não usa a carga mais recente'
must "$LAUNCH" 'applyMovementIfLater(x,ov.updatedAt,true);' 'edição ERP não sobe o lançamento ao topo'
must "$MAIN" 'filtered=launches.newestFirst(filtered);' 'lista mensal não preserva a ordenação mais recente'
echo OK

echo
echo '=== V094 DESCRICAO DO ITEM NOS LANCAMENTOS ==='
must "$LAUNCH" 'if(!description.isBlank())item.setDescriptionErp(description);' 'descrição ERP não é preservada no agrupamento'
must "$MAIN" 'new ComponentRenderer<>(this::launchProductCell)' 'coluna Código Produto não usa célula informativa'
must "$MAIN" 'addLaunchItemDescription(dialog, record);' 'descrição não aparece nos modais de lançamento'
must "$MAIN" 'Span value = new Span(compact);' 'célula Código... com tooltip completo ausente'
must "$MAIN" '.withPosition(Tooltip.TooltipPosition.TOP)' 'balão da descrição não permanece no topo'
must "$CSS" '0.0.94 — DESCRIÇÃO DO ITEM EM LANÇAMENTOS' 'estilo exclusivo da descrição ausente'
must "$CSS" '.gp-launch-product-description-v094' 'estilo da célula Código + descrição ausente'
echo OK

echo
echo '=== V095 DESCRICAO AUTOMATICA NO LANCAMENTO MANUAL ==='
must "$LAUNCH" 'public String productDescription(String productCode)' 'consulta de descrição por código ausente'
must "$LAUNCH" 'fillMissingProductMetadata(manual);' 'Cliente/Descrição não são aplicados aos lançamentos manuais consultados'
must "$DATABASE" 'idx_erp_apontamento_produto_sync' 'índice de consulta por produto ausente'
must "$MAIN" 'configureProductMetadataLookup(fields.product, record, itemDescription);' 'campo Código Produto não atualiza Cliente/Descrição'
must "$MAIN" 'product.setValueChangeMode(ValueChangeMode.LAZY);' 'consulta durante a edição do código ausente'
must "$MAIN" 'record.setDescriptionErp(productMetadata.description());' 'descrição não é confirmada ao salvar'
must "$MAIN" 'itemDescription.setVisible(!value.isBlank());' 'descrição vazia não é tratada corretamente'
echo OK

echo '=== BUILD ==='
mvn clean package -DskipTests

echo
echo '=== JAR ==='
test -f target/globoplast.jar
ls -lh target/globoplast.jar

echo
echo 'PRE-FLIGHT OK'
