# GLOBOPLAST Java v030 — Matriz de paridade com appv723

## v095 — descrição automática no lançamento manual

- Novo Lançamento e Editar Lançamento resolvem a descrição pela versão normalizada do Código do Produto em `erp_apontamento_raw`.
- A descrição abaixo do título acompanha a digitação do código e é confirmada novamente no salvamento.
- Lançamentos manuais já existentes são enriquecidos apenas em memória para exibição; nenhum dado produtivo salvo é reescrito.
- Um índice idempotente acelera a consulta por produto sem alterar dados ou regras de sincronização.

## v094 — descrição do item nos lançamentos

- A descrição já recebida em `erp_apontamento_raw.descricao` é preservada no agrupamento e exibida abaixo do título dos modais Editar/Visualizar.
- A coluna Código Produto usa `código...` somente quando existe descrição; o balão superior mostra Código + Descrição completos.
- Lançamentos sem descrição continuam mostrando apenas o código, sem marcador ou balão vazio.
- Nenhum campo produtivo, fórmula, filtro ou regra de sincronização foi alterado.

## v093 — lançamento mais recente ou atualizado no topo

- A ordenação usa primeiro o instante completo da última movimentação do lançamento; Data produtiva, Hora e ID permanecem apenas como desempate compatível.
- Lançamentos manuais registram `movimentado_em` na criação e em cada edição; lançamentos históricos são migrados de forma idempotente usando `Data + Hora do lançamento`.
- Grupos ERP usam o maior `sincronizado_em` dos apontamentos e overrides usam `atualizado_em`, mantendo a hora visível coerente com a última carga/edição.
- A regra é aplicada na tela Lançamentos e na lista detalhada mensal, sem alterar consolidados, indicadores ou fórmulas.

## v092 — rótulo inglês e espaçamento do rodapé

- A tradução de `Lançamentos` passa de `Search and Entries` para `Entries`.
- Informações do rodapé recebem mais 3 px, totalizando `translateY(13px)`.
- A margem inferior efetiva de `Mostrar mais` é reduzida exatamente 13 px, sem alterar outros blocos da página.
- Regras de OEE/refugo da v091 e a base de 24h da v090 permanecem inalteradas.

## v091 — Refugo reduz efetivamente o OEE

- Desempenho usa `Peças boas / Capacidade 24h`; Refugo não integra mais o numerador deste indicador.
- Qualidade permanece `Peças boas / (Peças boas + Refugo em peças)`.
- OEE permanece `Disponibilidade × Desempenho × Qualidade`, agora sem a compensação matemática que anulava a perda do Refugo.
- A regra de 24h e capacidade única por `Máquina + Dia produtivo` da v090 permanece integralmente preservada.

## v090 — OEE único de 24h por máquina e dia produtivo

- O grupo `Máquina + Dia produtivo` usa exatamente uma janela de 24 horas, independentemente da quantidade de lançamentos, OPs ou turnos.
- A capacidade 24h é única no grupo e propagada para todos os lançamentos da mesma máquina/dia.
- Setup e paradas são somados no grupo e descontados das 24 horas; produção e Refugo continuam consolidados antes do cálculo dos indicadores.
- Esta regra substitui exclusivamente a soma histórica de horas programadas descrita na seção de paridade do OEE, sem alterar as demais fórmulas.

## v089 — balões dos gráficos no ponteiro e rodapé

- `InteractiveBarChart`, `BarChart`, `OeeRankingChart` e `GroupedIndicatorChart` restauram `setTitle`, recuperando o balão nativo junto ao ponteiro.
- Demais Tooltips permanecem inalterados.
- Informações do rodapé recebem mais 5 px, totalizando `translateY(10px)`.

## v088 — balões restaurados à v085

- Remove somente as regras globais de fonte de `vaadin-tooltip`/`vaadin-tooltip-overlay`, restaurando o padrão visual da v085.
- Todas as demais alterações funcionais da v087 permanecem preservadas.

## v087 — limpeza nativa e títulos por período

- Resumo do Dia usa limpeza nativa de `ComboBox` em Setor, Máquina e Turno; o calendário conserva seu próprio comando `Limpar`.
- Refugo mantém abas `Anual/Mensal` e usa `Análise por ano/mês` somente no título inferior.
- Tooltips Vaadin recebem 18 px por variáveis Lumo e pelas partes `overlay/content`.

## v086 — abas, filtros por campo, turno e informativos

- Refugo usa abas `Anual` e `Mensal`, com os títulos `Análise anual/mensal` apenas abaixo.
- Filtros do Resumo do Dia possuem limpeza individual; a seleção de turno projeta somente produção/refugo daquele turno em cópias de exibição.
- Observações são maiúsculas em tempo real nos formulários editáveis.
- Balões informativos usam texto de 16 px.

## v085 — abas de Refugo e hora real de carga

- Abas do gráfico de Refugo restauradas ao fluxo original alinhado à esquerda, sem `width:100%` ou `flex:1`.
- A tabela `Ver lançamentos` usa `sincronizado_em` e `Norm.syncTime`, a mesma origem e conversão da hora exibida em `Lançamentos recentes`.
- KPIs de Refugo, múltiplas OPs, rodapé e demais alterações da v083/v084 permanecem preservados.

## v084 — compatibilidade Vaadin dos informativos

- Remove apenas a chamada incompatível a `Tooltip.getElement()`; os informativos continuam usando a API pública `Tooltip.forComponent` do Vaadin 25.2.4.
- Mantém integralmente os ajustes funcionais da v083.

## v083 — consolidado, Refugo, rodapé e múltiplas OPs

- Consolidado diário e seus informativos usam 16 px, com cursor de mão para valores múltiplos.
- Refugo mantém indicadores completos na mesma linha e apresenta `Análise por ano` antes de `Análise por mês`.
- Lançamentos compacta múltiplas OPs e usa `op_producao_detalhe` para informar a produção correspondente a cada OP.
- Salvar novo lançamento preserva todos os filtros e a busca atuais; nenhuma busca automática é aplicada.
- Informações do rodapé descem 5 px.

## v082 — Resumo do Dia consolidado e informativos acima

- Resumo Diário exibe KPIs e tabela consolidada, sem o gráfico intermediário.
- Códigos e OPs múltiplos são compactados com `...` e exibidos integralmente em informativo superior.
- O filtro diário inclui Turno A/B/C e os informativos do sistema usam posição superior.

## v081 — nomes dos comparativos e calendários unificados

- Refugo apresenta as abas `Mensal` e `Anual`, mantendo internamente as mesmas regras dinâmicas de comparação da v080.
- Os títulos dos gráficos seguem `Análise mensal` e `Análise anual`.
- Formulários e filtros usam `DateRangePicker`; o modo de formulário seleciona uma data e o modo de filtro preserva intervalos.

Esta versão foi revisada usando **todo o conteúdo do `appv723.zip`** como referência funcional e de backend, não somente a tela principal. Foram auditados `appv723.py`, `globoplast_core/assets.py`, `auth_ui.py`, `config.py`, `data.py`, `date_picker.py`, `erp_sync.py`, `i18n.py`, `parsing.py`, `refugo_ui.py`, `styles.py`, `sync_server.py` e `ui.py`, além do schema SQLite distribuído no ZIP.

## Regra de precedência

A referência é o appv723. Quatro requisitos pedidos depois da v723 foram preservados como extensões deliberadas, sem mudar as fórmulas originais: (1) Lançamento manual e automático podem coexistir e ambos participam dos Resumos/OEE; (2) override de lançamento ERP tem precedência visual/calculada sobre a linha automática; (3) quando Refugo chega depois da Produção, a visão é recalculada e o OEE muda imediatamente; (4) usuário, setor e máquina são normalizados em maiúsculas conforme pedido posterior. O menu de tema Claro/Escuro/Sistema também é mantido como extensão Java pedida posteriormente.

## 1. Navegação e estrutura principal — `appv723.py`

- Abas: `📋 Lançamentos`, `📅 Resumo do Dia`, `📊 Resumo do Mês`, `♻️ Refugo`.
- Perfil Conferente: somente Lançamentos + Refugo.
- Aba atual persistida em `?aba=` para sobreviver ao F5.
- Conteúdo carregado sob demanda, sem duplicação visual de abas.
- Rodapé flexível no fluxo da página.
- Cabeçalho, logo por tema e menu `•••` preservados.

Java: `MainView.navigation`, `selectTab`, `render`, `footer`, `menu`.

### Ajuste v076 — exclusões do Refugo no ERP

- O agente Windows envia um snapshot autoritativo após concluir todos os lotes; ausências em lotes parciais nunca provocam exclusão.
- O servidor reconcilia no máximo 31 dias por chamada, arquiva o conteúdo completo em JSON e remove o Refugo bruto e suas reclassificações em uma única transação.
- A janela recente é reconciliada em toda execução e o histórico desde 01/01/2025 é auditado diariamente, permitindo retirar também exclusões antigas do ERP.
- Como o vínculo Refugo/Produção é calculado a partir de `erp_refugo_raw`, a retirada atualiza automaticamente Refugo, Qualidade e OEE.

### Ajuste v075 — favicon após migração

- Referências de favicon preservadas pelo navegador são removidas quando a interface é montada.
- O favicon Java usa uma URL versionada para invalidar o ícone armazenado durante o período em que o domínio apontava para o Streamlit.

### Ajuste v074 — busca de Refugo

- “Pesquisar refugo”, o campo e o botão de filtro usam a mesma margem, altura e linha-base da barra de busca em Lançamentos.
- Os deslocamentos fracionários anteriores são neutralizados por uma regra final exclusiva, sem alterar os demais componentes.

## 2. Autenticação e sessão — `auth_ui.py` + `data.py`

- Usuário normalizado em maiúsculas.
- PBKDF2-HMAC-SHA256, 260.000 iterações.
- ADMIN inicial somente quando não existe administrador.
- Sessão persistente no navegador e invalidável no logout.
- Perfis: Padrão, Acompanhamento, Conferente, Administrador.
- Acompanhamento/Conferente somente leitura.
- Padrão pode modificar somente máquinas do setor atribuído.
- Administrador sem restrição setorial.
- Idioma persistido por usuário.

Java: `AuthService`, `PasswordService`, `User`, cookie de sessão HttpOnly e `sessoes_web`.

## 3. Usuários — `ui.py`

- Novo usuário exige nome >= 3 caracteres e senha.
- Perfil Padrão exige setor.
- Demais perfis ignoram setor.
- Edição permite senha opcional com confirmação na interface.
- Não permite excluir o usuário logado.
- Não permite remover/rebaixar o último Administrador.
- Exclusão invalida sessões web do usuário.

Java: `AuthService.saveUser`, `deleteUser`, `MainView.showUsers/showUserEdit`.

## 4. Cadastro de setores e máquinas — `ui.py` + `data.py`

- Setor obrigatório e único.
- Renomear setor atualiza máquinas e usuários Padrão vinculados.
- Setor não pode ser excluído enquanto houver usuário Padrão vinculado.
- Máquina exige nome, setor válido e capacidade > 0.
- Máquina única por nome.
- Ações Editar/Excluir com confirmação.

Java: `CatalogService`, `MainView.showRegistry`.

## 5. Parsing do lançamento manual — `parsing.py` + `ui.py`

- `parse_float`: vírgula decimal aceita.
- Produção por turno aceita soma com `+`.
- Múltiplas OPs aceitam `/`, `;` ou `|`.
- Parcelas por posição pertencem à OP da mesma posição.
- OPs sem parcela recebem zero; parcelas excedentes ficam agregadas no turno.
- Refugo: inteiro sem separador é gramas (`2500 = 2,500 kg`); decimal explícito é kg; soma `+` aceita.
- Horas: aceita decimal e `H:M`; padrão de Horas Programadas = 24 quando vazio/zero.
- Peso em gramas.
- Refugo em peças usa truncamento `int(kg*1000/peso)` no lançamento manual.
- Produto e Observações normalizados conforme original/requisitos posteriores.

Java: `MainView.parseProductionComponents`, `buildProductionDetail`, `parseScrapKg`, `parseHours`, `LaunchService.finalizeManual`.

## 6. Formulário Novo / Editar / Visualizar — `ui.py`

Mesma sequência funcional:

1. Data
2. Máquina
3. Capacidade automática e somente leitura
4. Código Produto
5. Nº OP
6. Horas Programadas
7. Peso
8. Turno A + Refugo A
9. Turno B + Refugo B
10. Turno C + Refugo C
11. Qtd. Trocas
12. Setup
13. Paradas
14. Observações

Enter segue a mesma ordem lógica de Tab. Novo, Editar e Visualizar usam a mesma estrutura; Visualizar é somente leitura. Edição ERP grava override local e nunca escreve no DealerSystem.

Java: `MainView.showLaunchDialog`, `showLaunchView`, `createLaunchFields`, navegação `data-gp-launch-order`.

## 7. OEE — `data.py`

Paridade matemática:

- Grupo = Máquina + Dia produtivo.
- Horas Programadas = soma de todas as linhas do grupo, **sem cap de 24 h**.
- Capacidade proporcional = Capacidade 24h × Horas Programadas / 24.
- Produzido = peças boas.
- Processado = Produzido + Refugo em peças.
- Tempo Produzindo = max(0, Horas Programadas − Setup − Paradas).
- Disponibilidade = Tempo Produzindo / Horas Programadas; quando horas = 0, 100%.
- Desempenho = Processado / Capacidade proporcional.
- Qualidade = Produzido / Processado.
- OEE = Disponibilidade × Desempenho × Qualidade.
- OEE > 100% permitido.
- Refugo % por linha = Refugo pçs / (Produzido + Refugo pçs).
- OEE do grupo é aplicado a todas as linhas da máquina/dia.

Java: `OeeCalculator`.

**v030 remove uma redistribuição artificial de horas introduzida na migração.** Apontamentos ERP continuam recebendo a distribuição de 24h definida pelo algoritmo original da carga automática; linhas manuais preservam as horas digitadas. Ao consolidar ambas, o OEE soma as horas efetivamente presentes, exatamente como a fórmula original.

## 8. Lançamentos automáticos ERP — `appv723.py`

- `Qtd.Apon × 1000`, arredondado para peças.
- Chave: Data produtiva + OP normalizada + Máquina normalizada + Produto.
- Aliases de máquina iguais ao Python (COL TPA, EXTRUSÃO, FOR DE OMBRO, DECORAÇÃO etc.).
- Capacidade/setor reconciliados com o cadastro.
- Turno C atribuído ao dia anterior.
- Refugo cruzado por OP + Produto, depois Setor, Dia, Máquina e desempate por produção do turno/produção total/data/ordem estável.
- `Qtd Itens = 0` é valor válido; fallback por peso só ocorre quando NULL.
- Refugo sem turno permanece no total de kg, sem inventar A/B/C.
- Peso unitário ponderado por quantidade de peças.
- Horas ERP distribuídas em 24h por Máquina + Dia proporcionalmente ao processado, com ajuste da última fração.
- Override aplicado depois da montagem da linha automática.
- DealerSystem permanece somente leitura.

Java: `LaunchService.automatic`, `Norm`, `SyncService`.

## 9. Edição e exclusão — `data.py` / `ui.py`

- Manual: UPDATE/DELETE local e recálculo do grupo Máquina+Dia afetado.
- Se Data/Máquina mudarem, origem e destino são recalculados.
- ERP: edição vira override local; exclusão vira `oculto=1`; staging/ERP não é apagado.
- Usuário Padrão só pode editar/excluir a própria área.

Java: `LaunchService.updateManual/deleteManual/saveErpOverride/hideErp`.

## 10. Lista de Lançamentos — `appv723.py` / `ui.py`

- Busca prioriza igualdade exata por OP/Produto e depois busca textual.
- Filtros: Período, Setor, Máquina; Máquina depende do Setor.
- `Limpar filtros` volta ao dia produtivo atual.
- Mais recentes no topo por Data/Hora/ID.
- Paginação incremental de 20 registros com `Mostrar mais` textual.
- Ações Visualizar / Editar / Excluir.
- OEE recalculado da visão atual; OEE < 85% destacado conforme requisito posterior.

Java: `LaunchService.filter`, `MainView.renderLaunches`.

## 11. Resumo Diário — `appv723.py` / `ui.py`

- Três filtros inline: Data, Setor, Máquina.
- Máquina dependente do Setor.
- 5 KPIs: OEE Geral, Disponibilidade, Desempenho, Qualidade, Peças Boas Produzidas.
- Gráfico agrupado por máquina com Disponibilidade, Desempenho, Qualidade e OEE.
- Tabela consolidada por Máquina.
- Produtos únicos combinados por ` / `; OPs únicas combinadas por `/`.
- Soma produção/refugo/turnos/setup/paradas/trocas.
- Capacidade = maior capacidade do grupo.
- Quantidade de lançamentos exibida.

Java: `MainView.renderDay`, `summarizeDaily`, `GroupedIndicatorChart`.

Extensão solicitada posteriormente: a fonte do resumo é a visão consolidada ERP + manual + override, em vez de apenas ERP.

## 12. Resumo Mensal — `appv723.py`

- Dois filtros inline: Mês, Setor.
- Ranking de OEE por máquina.
- Tabela consolidada por equipamento.
- Produção/refugo/setup/paradas/trocas somados.
- Indicadores mensais = média dos indicadores diários únicos por Máquina + Dia.
- Todos os apontamentos do mês, 20 por vez, `Mostrar mais`.

Java: `MainView.renderMonth`, `summarizeMonthly`, `OeeRankingChart`.

Extensão solicitada posteriormente: ERP + manual + override participam do mês.

## 13. Refugo — `refugo_ui.py`

- Fonte ERP online é prioritária. O uploader `.xlsx/.xls/.csv` do Python só aparece quando a fonte ERP não existe; portanto não é exposto na operação Java conectada ao ERP.
- Filtros completos: Período, Setor, Ordem, Máquina, Produto, Descrição, Cliente, Turno, Operador e Motivo.
- Busca: Ordem, Produto ou Descrição.
- `Qtd.Planej` em milhares → ×1000 unidades.
- `Qtd Itens` usa exatamente o ERP, inclusive zero; fallback somente se NULL.
- Diluição de turno inválido entre turnos válidos de Setor + OP; sem referência, A/B/C.
- Dia produtivo aplicado depois da normalização do turno.
- Mapeamentos 770/771/772/773/775/776/994/993/120 e 777 preservados.
- 777028/777029 = BORRA.
- Gráficos: Comparativo Mensal/Anual quando houver dados, Setor, Máquina, Turno, Descrição, Motivo.
- 15 itens por página.
- Seleção, Excluir item, Ver lançamentos, Restaurar excluídos.
- Top 5 motivos.
- Detalhes de Descrição com Planejado, Refugo kg/un e Perda %.
- Lançamentos recentes: somente Hoje, fechado por padrão, últimos 20.

Java: `RefugoService`, `MainView.renderScrap` e helpers de Refugo.

## 14. Regras de produto/setor do Refugo — `refugo_ui.py`

- 770 Extrusão
- 771 Impressão
- 772 Silk Screen
- 773 Hot Stamping
- 775 Fechamento de Fundo
- 776 Colocação de Tampa
- 994/993/120 Injetados
- 777021 Qualidade
- 777020 Desenvolvimento
- 777028 Extrusão
- 777024 Preparação MP
- 777023 Varredura Armazém
- 777025 Devolução Cliente
- 777027 Material Obsoleto
- 777029 Injetados
- 777022 Varredura Fábrica

Java: `Norm.scrapSector`.

## 15. Dia produtivo / fuso — `appv723.py`, `refugo_ui.py`

- Fuso: `America/Sao_Paulo`.
- Dia produtivo: 06:00 até 05:59:59 do dia seguinte.
- 00:00–05:59 pertence ao dia anterior.
- Turno C pertence ao dia produtivo anterior na data bruta do ERP.
- Sem exceção de domingo.

Java: `AppConfig.ZONE`, `Norm.productiveToday`, `Norm.productiveDate`.

## 16. Calendário — `date_picker.py`

- pt-BR/en-US.
- `dd/MM/yyyy` na interface.
- Range com primeiro clique início / segundo clique fim.
- Limpar e seleção do período completo suportados.
- Domingo como primeiro dia em pt-BR.

Java: `DateRangePicker` + `MainView.datePicker`.

## 17. Idiomas — `i18n.py`

- pt-BR padrão.
- en-US secundário.
- idioma persistido por usuário.
- labels, menus, tabelas, estados, filtros e mensagens passam por `I18n`.

Java: `I18n`, `AuthService.saveLanguage`, `MainView.t`.

## 18. Tema / assets — `assets.py` + `styles.py`

- Logo claro/escuro.
- Favicon original.
- contraste de inputs, tabelas, menus, dialogs e login.
- layout responsivo sem criar coluna artificial no mobile.

Java: `globoplast.css`, `MainView.applyThemeMode`, assets em `META-INF/resources`.

## 19. Sincronização ERP — `erp_sync.py` + `sync_server.py`

- Firebird é somente leitura no conector Windows.
- Java recebe somente staging `erp_apontamento_raw` / `erp_refugo_raw`.
- Upsert por `ERP_ID`, idempotente.
- Hash de payload evita UPDATE quando nada mudou.
- Lotes até 5000 registros.
- HMAC-SHA256: Bearer token + timestamp + assinatura de `timestamp.body`.
- tolerância do relógio = 300 s.
- status de último recebimento/ERP ID/total.

Java: `SyncController`, `SyncService`.

## 20. Banco e separação de fontes

- `historico_oee`: lançamentos manuais.
- `erp_apontamento_raw`: staging produção ERP.
- `erp_refugo_raw`: staging refugo ERP.
- `erp_lancamento_overrides`: edição/ocultação local de linha ERP.
- `usuarios`, `sessoes_web`, `setores`, `maquinas` separados.
- Nenhuma operação Java escreve no Firebird/DealerSystem.

Java: `Database` e serviços correspondentes.

---

A v030 usa esta matriz também no `deploy/preflight.sh`. O preflight falha antes do deploy quando uma regra crítica de paridade é removida acidentalmente.

## 21. Ajustes finais da auditoria v030

- `Database`: repete as migrações defensivas da v723 para `numero_op`, `op_producao_detalhe`, `hora_lancamento`, `setor`, `perfil` e `idioma`, incluindo normalização histórica de usuário/perfil/idioma.
- `AuthService`: reproduz a garantia do administrador inicial da v723, inclusive promovendo um `ADMIN` já existente e impedindo exclusão/rebaixamento do último administrador.
- `LaunchService`: mantém a distribuição automática ERP de 24 h por máquina/dia antes dos overrides, mas não cria um teto artificial quando lançamentos manuais coexistem. O OEE usa a soma real das horas dos registros, como o cálculo original.
- `MainView`: ao salvar lançamento manual novo, o período é reposicionado para a data do lançamento e a busca para a OP, reproduzindo o foco visual da v723.
- Edições locais de lançamentos ERP continuam prevalecendo sobre o payload recebido em novas sincronizações; ocultações locais também são preservadas.
- A integração online ERP é a fonte ativa de Refugo. O uploader/reabertura de arquivos de Refugo da v723 era um fallback exibido apenas quando a fonte ERP não estava disponível; ele permanece fora da interface online Java para não criar duas fontes concorrentes no ambiente conectado.

### Extensões posteriores à v723 preservadas por decisão do projeto

Estas diferenças são intencionais porque foram pedidas depois da v723 e não reduzem as regras originais:

- lançamentos manuais e automáticos coexistem na visão consolidada e participam dos Resumos/OEE;
- um lançamento ERP editado localmente usa o override nos cálculos;
- Produção pode chegar antes do Refugo: o OEE é calculado com o dado disponível e recalculado automaticamente quando o Refugo chega depois;
- tema Sistema/Claro/Escuro permanece disponível no menu Java;
- login, usuários, setores e máquinas são normalizados em maiúsculas;
- dialogs Java mantêm botão `X` de fechamento e cabeçalhos sem emojis, conforme refinamentos posteriores.

## v031 — catálogo de backend do appv723

O cálculo OEE automático do appv723 depende de `maquinas.capacidade` (Capacidade 24h). Produção e Refugo do ERP não carregam essa capacidade. Portanto, migrar somente os apontamentos/refugos sem migrar `setores`/`maquinas` deixa a capacidade automática em zero e, corretamente pela fórmula original, o Desempenho/OEE ficam em zero.

A v031 adiciona importação autenticada e idempotente do catálogo original (`/java-sync/v1/catalogo`), sem excluir cadastros Java adicionais. O utilitário `deploy/migrar_catalogo_v723.py` abre o `database.db` do appv723 em `mode=ro&immutable=1`, envia setores e máquinas/capacidades ao Java e depois chama o diagnóstico autenticado `/java-sync/v1/diagnostico-oee`.

Para ERP cuja máquina não esteja no catálogo, a edição continua preservando todos os dados originais. Se a capacidade já estiver presente no item/override, ela é preservada; se estiver ausente, a capacidade pode ser informada no próprio override ERP e passa a ser lembrada no `maquinas_snapshot` para os demais apontamentos equivalentes.

## v031 — catálogo de backend do appv723

O cálculo OEE automático do appv723 depende de `maquinas.capacidade` (Capacidade 24h). Produção e Refugo do ERP não carregam essa capacidade. Portanto, migrar somente os apontamentos/refugos sem migrar `setores`/`maquinas` deixa a capacidade automática em zero e, pela fórmula original, o Desempenho/OEE ficam em zero.

A v031 adiciona importação autenticada e idempotente do catálogo original (`/java-sync/v1/catalogo`), sem excluir cadastros Java adicionais. O utilitário `deploy/migrar_catalogo_v723.py` abre o `database.db` do appv723 em `mode=ro&immutable=1`, envia setores e máquinas/capacidades ao Java e depois chama o diagnóstico autenticado `/java-sync/v1/diagnostico-oee`.

Para ERP cuja máquina não esteja no catálogo, a edição continua preservando todos os dados originais. Se a capacidade já estiver presente no item/override, ela é preservada; se estiver ausente, a capacidade pode ser informada no próprio override ERP e passa a ser lembrada no `maquinas_snapshot` para os demais apontamentos equivalentes.


## v032 — equivalência ERP × catálogo e capacidade histórica

A base real mostrou que os nomes operacionais do ERP não são os mesmos nomes do cadastro (`EXTRUSÃO 03` × `EXTRUSORA 03`, `COL TPA 05` × `COL DE TAMPA 05`, `INJEÇÂO 05` × `INJETORA 05`, etc.). A v032 aplica uma chave canônica de família+número antes do cálculo do OEE e usa a máquina/capacidade catalogada quando existe correspondência. Máquinas realmente ausentes do cadastro podem recuperar uma capacidade já conhecida no `historico_oee` original através do snapshot, sem serem criadas no cadastro Java. Nenhuma capacidade é inventada.


## v034 — ajuste exclusivamente visual dos gráficos de Refugo

Partindo da v032, somente a geometria visual das barras foi alterada: barras mais largas e cantos levemente arredondados. Não houve alteração de regras de backend, cálculo de OEE, carga ERP, Refugo ou persistência.

## v035 — contraste do login e foco visual do menu

Alteração exclusivamente de interface sobre a v034:
- login no tema escuro força label, valor digitado, caret e reveal de senha para branco;
- observador de tema mantém a cor correta no input encapsulado do Vaadin;
- foco automático do ContextMenu não simula hover no primeiro item ao abrir;
- hover real continua visível normalmente.

Backend e regras funcionais permanecem iguais à v034/v032.


## v036 — menu sem seleção falsa e maiúsculas durante a digitação
- O ContextMenu é neutralizado ao abrir e em overlays/submenus criados dinamicamente; atributos de foco/seleção do Vaadin não simulam hover.
- O destaque visual é aplicado apenas por mouseenter real.
- `forceUppercase` mantém fallback no servidor e adiciona transformação no input a cada digitação, preservando a posição do cursor.

## v037 — apresentação numérica, tipografia e microinterações do Refugo
- Formatação visual volta ao padrão localizado da v723: pt-BR usa ponto para milhar e vírgula decimal.
- A família Source Sans passa a ser aplicada ao Lumo/Vaadin, não somente ao body.
- KPI Período do Refugo: Hoje + data abaixo, como `_rotulo_periodo_kpi_refugo` da v723.
- KPI Total Refugo: legenda `{percentual}% do total` restaurada.
- Status ERP reduzido ao estado Online/Offline com semântica verde/vermelho.
- Dia selecionado no calendário recebe estado visual explícito e persistente.
- Hover dos filtros reduzido para contorno/fundo discretos, sem halo.

## v038 — tipografia, calendário e filtro
- Tipografia global força Source Sans 3/Source Sans Pro/Source Sans e a carrega via CSS web, aproximando a fonte efetiva do Streamlit original também dentro dos componentes Vaadin.
- Tabelas mantêm 13 px no cabeçalho e 14 px nos dados, conforme variáveis da v723 desktop.
- Dias selecionados do calendário próprio não usam mais a variante tertiary-inline do Vaadin e recebem estado selecionado explícito.
- DatePicker nativo recebe reforço visual para a data selecionada dentro do Shadow DOM.
- Hover do filtro não preenche o botão: altera apenas o contorno e a opacidade do funil; foco por teclado permanece acessível.

## v047 — refinamentos solicitados
- Filtros MultiSelect exibem cada seleção uma única vez no campo.
- Resumo Dia/Mês migram os controles visíveis para popover acionado por funil à direita.
- Toolbar de Lançamentos mantém busca/funil à esquerda e Novo Lançamento à direita.
- Barras do Refugo ampliadas sem alteração das regras numéricas.

## v049 — correções visuais de filtros e modais
- MultiSelectComboBox: `keepFilter=false` e limpeza do filtro residual igual ao chip selecionado, evitando rótulo duplicado dentro do mesmo campo.
- Modais: cinza uniforme mais claro; header, conteúdo e footer usam o mesmo plano visual, sem faixa diferente na área dos botões.
- O bloco CSS v048 que havia sido anexado com escapes `\\n` literais foi reemitido como CSS válido.

## v050 — padronização de busca/funil e modal uniforme

- Lançamentos e Refugo passam a compartilhar altura de 44 px no campo de busca e no botão de filtro.
- A distância entre a busca e o funil fica padronizada em 8 px nas duas telas.
- Cabeçalho, conteúdo e rodapé dos modais usam o mesmo cinza mais claro, sem faixa diferente na área de ações.

## v051 — correções efetivas de componentes Vaadin

- Novo Lançamento sobe exatamente 1,5 px sem deslocar a toolbar.
- MultiSelect passa a usar `setKeepFilter(false)` da API Java e uma limpeza pós-seleção restrita ao evento de seleção.
- Modais passam a estilizar os `parts` do host `vaadin-dialog`, que é o componente correto no Vaadin 25.

## v052 — filtro sem duplicação visual e modal neutro

- Após selecionar, o MultiSelect fecha e oculta o input nativo no estado fechado, deixando visível somente o chip uma vez.
- Ao reabrir o seletor, o input volta para permitir nova pesquisa e seleção.
- Modais usam cinza discreto por tema, sem texto branco forçado e sem faixas distintas entre cabeçalho, conteúdo e rodapé.

## v053 — modais uniformes em toda a área interna

- Campos, tabelas, cabeçalhos de tabela, linhas e rodapés passam a usar exatamente o mesmo fundo do modal.
- O fundo preto herdado pelo `vaadin-grid` global deixa de aparecer dentro dos diálogos.
- Bordas discretas mantêm a separação visual sem criar faixas ou blocos de outra cor.

## v054 — padronização do campo Máquina e fluidez

- O seletor de Máquina em Novo/Editar Lançamento compartilha a mesma caixa visual dos demais campos.
- Resumo do Mês reutiliza os componentes já criados e pagina os apontamentos sem reconstruir o ranking e o consolidado.
- Cache limitado de múltiplas faixas evita recargas ao alternar entre Lançamentos, Resumo do Dia, Resumo do Mês e Refugo.
- A atualização de `maquinas_snapshot` passa de uma conexão por lançamento para um lote por carga, mantendo os mesmos dados e cálculos.
- Índices idempotentes no histórico aceleram filtros por data e máquina em bancos existentes.

## v055 — campo Máquina com a mesma estrutura dos demais

- Novo/Editar Lançamento passam a usar `ComboBox<String>` para Máquina no lugar de `Select<String>`.
- A seleção continua limitada ao cadastro permitido para o usuário, mas o componente passa a compartilhar a mesma estrutura de input dos `TextField`.
- Enter continua selecionando uma opção quando o dropdown estiver aberto e mantém a navegação sequencial quando estiver fechado.

## v056 — KPIs principais de Refugo ampliados

- Total Refugo, Item Selecionado, Ordens Afetadas, Total de Lançamentos e Período usam colunas iguais distribuídas por toda a tela.
- Valores, rótulos, legendas e área vertical foram ampliados sem alterar nenhum cálculo.
- Em telas estreitas, a grade responsiva preserva a leitura em duas colunas e evita rolagem horizontal.

## v057 — padronização completa, gráfico e filtros contextuais

- Capacidade alinha pela base de Máquina no formulário de lançamento.
- Perfil e Setor do formulário de usuário passam a usar `ComboBox<String>` restrito às opções permitidas.
- O cabeçalho do gráfico de Refugo fica à direita, seguido pelo menu do gráfico em coluna própria.
- Os filtros de Refugo tornam-se contextuais: Setor limita Máquina; ambos limitam as listas dos demais filtros e descartam seleções incompatíveis.
- A cor `--gp-surface` do dropdown do menu passa a ser a referência única de modais, dropdowns, campos e grids internos.

## v058 — menu contextual e separação correta das superfícies

- O menu de ações do gráfico deixa de ocupar uma coluna fixa e abre à direita do clique na barra selecionada.
- O título “Análise por...” alinha à esquerda com as abas de dimensão.
- O dropdown principal recupera os itens transparentes em repouso e o hover discreto.
- Os modais usam uma cor uniforme própria em cabeçalho, conteúdo, rodapé, campos e grids, sem alterar o menu principal.

## v059 — legibilidade da tabela e observações no OEE

- A tabela principal de Lançamentos usa textos maiores sem alterar os grids dos Resumos ou dos modais.
- O ícone informativo da coluna OEE existe somente quando Observações possui conteúdo real.
- O tooltip é um overlay do Vaadin e apresenta o texto preenchido sem ser cortado pela célula.

## v060 — interação do gráfico e integridade dos cadastros

- O valor do OEE é o próprio alvo do tooltip de Observações, sem ícone adicional.
- Clique simples alterna a seleção da barra e clique duplo abre o menu contextual no ponto clicado.
- Itens acionáveis usam cursor de mão.
- Editar Máquina recebe layout compacto e Setor usa ComboBox padronizado.
- Setores com Máquinas ou usuários Padrão vinculados não podem ser excluídos.
- A regra de manutenção de pelo menos um Administrador continua protegida no serviço.

## v061 — tooltip confiável, resposta imediata e rolagem isolada

- O valor do OEE abre um balão flutuante no `body`, sem ícone e sem sofrer recorte da célula virtualizada do Grid.
- A seleção visual das abas ocorre no `pointerdown` e a aba inicial deixa de ser renderizada duas vezes.
- O clique da barra não usa temporizador: alterna a seleção imediatamente, preservando o menu apenas no clique duplo.
- Todo Dialog bloqueia a rolagem da página de fundo e contém o encadeamento ao fim da sua própria rolagem.
- Lançamentos recentes do Refugo usa todas as linhas visíveis, sem área rolável interna.
- Setor é exibido em maiúsculas nos filtros; no Refugo, Máquina antecede Ordem.

## v062 — detalhes no clique e correções de apresentação

- O clique simples em uma barra de Descrição atualiza KPIs e Detalhes do item no mesmo evento.
- Lançamentos não são abertos pelo clique simples; continuam exclusivos da ação Ver lançamentos.
- As linhas dos gráficos de Refugo avançam 20 px à esquerda sem deslocar abas, KPIs, paginação ou detalhes.
- O valor do OEE recebe `title` nativo apenas quando Observações possui conteúdo real.
- A transformação para maiúsculas alcança somente busca, valor e chips de Setor; o label não é transformado.

## v063 — geometria final e ícone explícito de Observações

- A linha completa de cada gráfico de Refugo usa deslocamento final de 15 px à esquerda.
- O título vertical Quantidade Refugada (Kg) avança 10 px à direita, sem mover o restante do eixo.
- “Todos” permanece com capitalização normal quando Setor está vazio; o uppercase depende de conteúdo/seleção real.
- A célula OEE cria `VaadinIcon.INFO_CIRCLE` somente dentro da condição de Observações válidas.
- O ícone recebe tooltip do Vaadin e `title` nativo com o texto exato de Observações, sem usar “?”.

## v064 — menu direito, posição estável e reclassificação de Refugo

- “Análise por Setor” desloca 15 px à direita e 10 px para cima sem mover os títulos das demais dimensões.
- A linha do gráfico mantém o deslocamento à esquerda, mas volta a respeitar o recuo padrão na lateral direita.
- O menu contextual abre pelo botão direito sobre a barra; clique simples permanece dedicado a selecionar ou remover a seleção.
- A posição vertical da página é preservada ao alternar dimensões do gráfico de Refugo.
- Os nomes dos setores no filtro de Refugo são apresentados em maiúsculas e comparados sem diferença entre maiúsculas/minúsculas.
- Overlays de ComboBox, MultiSelect e Select adotam globalmente o mesmo padrão visual dos dropdowns de filtro.
- Administradores podem reclassificar os lançamentos da barra selecionada para qualquer setor cadastrado.
- A reclassificação é persistida em `erp_refugo_setor_overrides`, mantendo `erp_refugo_raw` imutável.

## v065 — alinhamento final do Refugo, abas estáveis e URL limpa

- O conjunto dos gráficos passa de -15 px para -25 px à esquerda e compensa a largura até o eixo direito do logo.
- O título vertical Quantidade Refugada (Kg) passa de `left:18px` para `left:38px`.
- A troca de dimensão registra e restaura a posição da janela em eventos de mouse ou teclado.
- `overflow-anchor:none` impede o navegador de reposicionar a página durante a substituição do gráfico.
- `scrollbar-gutter:stable` evita variação lateral quando a altura do conteúdo muda entre dimensões.
- O botão de três pontos do sistema perde qualquer caixa visual no hover e apenas aumenta sua presença.
- A aba principal permanece na sessão Vaadin, enquanto `history.replaceState` mantém somente `location.pathname`.

## v066 — recuo direito e remoção definitiva da caixa do menu

- O gráfico conserva `margin-left:-25px`, mas usa `width:calc(100% + 10px)`, resultando em 15 px de recuo à direita.
- “Análise por Setor” e o título vertical Quantidade Refugada usam `left:38px` como referência comum.
- O botão do menu recebe `LUMO_TERTIARY_INLINE` e uma classe exclusiva da v066.
- Host, label, `::before` e `::after` do botão permanecem transparentes e sem borda, outline ou sombra em repouso, hover e foco.
- Somente a opacidade do glyph muda de `.72` para `1` durante a interação.

## v067 — recuo direito ampliado e título de Setor

- O recuo direito do gráfico aumenta 30 px, totalizando 45 px.
- Com `margin-left:-25px`, a largura `calc(100% - 20px)` encerra o gráfico 45 px antes do limite direito.
- “Análise por Setor” passa de `left:38px` para `left:68px`.
- Quantidade Refugada permanece em `left:38px`; as demais geometrias e funções são preservadas.

## v068 — recuo de 62 px e deslocamento garantido do título

- O recuo direito recebe mais 17 px, totalizando 62 px.
- A geometria final usa `margin-left:-25px` e `width:calc(100% - 37px)`.
- “Análise por Setor” continua em `left:68px`.
- Para impedir que regras históricas neutralizem o deslocamento, o elemento `.gp-refugo-chart-title` recebe `left:68px!important` diretamente após o attach e no próximo frame.

## v069 — transferência baseada no filtro e geometria final

- “Análise por Setor” passa de 68 px para 53 px e de `top:4px` para `top:-3px`.
- O recuo direito passa de 62 px para 65 px com `width:calc(100% - 40px)` e `margin-left:-25px`.
- A ação Enviar para outro setor recalcula as linhas selecionadas usando `currentScrapRows()` no momento do clique.
- A persistência nova usa `analysis_id` em `erp_refugo_analysis_setor_overrides`, preservando exatamente turno, período, máquina e demais filtros ativos.
- O override analítico é aplicado ao `RefugoRecord` depois da transformação do ERP e tem precedência sobre o override legado por `erp_id`.
- A origem perde e o destino recebe exatamente o mesmo lançamento analítico; `erp_refugo_raw` não é alterado.

## v070 — setor único, limpeza original e títulos alinhados

- A dimensão Setor usa uma chave em maiúsculas para que valores como `Extrusão` e `EXTRUSÃO` sejam somados na mesma barra.
- A transferência continua limitada às linhas do filtro ativo e não cria registros na tabela de cadastro `setores`.
- “Limpar filtros” apaga `erp_refugo_analysis_setor_overrides` e o override legado `erp_refugo_setor_overrides`, restaurando a classificação original sem alterar `erp_refugo_raw`.
- Todos os filtros, a busca, exclusões temporárias, seleção e paginação também voltam ao estado inicial.
- Todos os títulos dos gráficos de Refugo passam a usar `left:48px` e `top:0px`; em Setor isso representa 5 px à esquerda e 3 px abaixo da v069.

## v071 — busca e filtro de Refugo 0,5 px acima

- A toolbar exclusiva de Refugo recebe `gp-refugo-search-toolbar-v071`.
- `transform:translateY(-.5px)` desloca conjuntamente o campo “Pesquisar refugo” e o funil, sem alterar o fluxo, dimensões ou outras telas.

## v072 — busca e filtro de Refugo 0,25 px abaixo

- A toolbar recebe `gp-refugo-search-toolbar-v072` e passa a usar `translateY(-.25px)`.
- O conjunto desce 0,25 px em relação à v071 e permanece 0,25 px acima da posição original.

## v073 — busca e filtro de Refugo mais 0,2 px abaixo

- A toolbar recebe `gp-refugo-search-toolbar-v073` e passa a usar `translateY(-.05px)`.
- O conjunto desce mais 0,2 px em relação à v072 e permanece 0,05 px acima da posição original.
## v077 — login por aba e posição do Novo Lançamento

- A autorização visual da sessão passa a exigir uma marca vinculada ao usuário no `sessionStorage` da aba.
- `pagehide` remove a autorização ativa e guarda somente uma marca transitória; ela é restaurada exclusivamente quando a nova navegação informa `type === 'reload'`.
- Fechar ou reabrir uma aba não satisfaz a condição de atualização, portanto invalida eventual cookie antigo e exige novo login.
- A saída manual remove a marca da aba antes de invalidar a sessão no servidor.
- O botão Novo Lançamento passa de `top:-1.5px` para `top:-2.5px`, subindo exatamente 1 px sem deslocar outros componentes.
## v078 — login preservado no F5 e na entrada pelo endereço

- A marca de autenticação vinculada ao usuário permanece no `sessionStorage` durante toda a vida da mesma aba.
- F5, atualização normal e nova entrada de `globoplast.app` na barra de endereço preservam essa marca e mantêm o login.
- Fechar a aba destrói seu `sessionStorage`; uma nova aba sem a marca exige autenticação mesmo que exista cookie antigo.
- O guardião `pagehide` da v077 foi removido porque uma navegação pelo endereço também dispara esse evento.
- O botão Novo Lançamento permanece 1 px acima da v076, sem novas alterações visuais.
## v079 — Novo Lançamento mais 1 px acima

- O botão Novo Lançamento passa de `top:-2.5px` para `top:-3.5px`, subindo exatamente mais 1 px.
- Nenhum outro controle da toolbar é deslocado.
- O login permanece ativo no F5 e na entrada pelo endereço dentro da mesma aba, sendo descartado somente ao fechar a aba.

## v080 — comparativos dinâmicos de Refugo e operador nos lançamentos

- As abas Comparativo Mensal e Comparativo Anual permanecem montadas e têm a visibilidade atualizada imediatamente conforme o período selecionado.
- Um recorte com dados de mais de um mês habilita o comparativo mensal; com dados de mais de um ano habilita também o comparativo anual, preservando a regra do original.
- Se o usuário reduzir o período enquanto estiver em um comparativo que deixou de ser aplicável, a tela retorna com segurança para Setor.
- A tabela Ver lançamentos do menu do gráfico passa a mostrar o operador na coluna Lançado por.

## v105 — envio direto pelo menu do gráfico

- “Enviar para outro setor” passa a abrir um submenu com os setores cadastrados, no mesmo padrão dos submenus de idioma e tema.
- A escolha do setor executa a transferência imediatamente, sem abrir modal, preservando os filtros ativos e a seleção do gráfico.

## v106 — corte produtivo e primeira detecção do Refugo

- Registros A/B cuja primeira detecção ocorre antes das 06h na própria `DATA_APON` são classificados no dia produtivo anterior.
- O Turno C continua sempre associado ao dia anterior, preservando a regra operacional existente.
- `primeiro_sincronizado_em` registra a primeira detecção de cada ID e permanece imutável em atualizações posteriores.
- Página de Refugo, associação aos lançamentos e OEE passam a compartilhar exatamente a mesma data produtiva.

## v107 — padronização operacional e deploy automatizado

- Serviço, diretórios de aplicação, banco, backups e arquivo de ambiente passam a usar somente o nome `globoplast`.
- A VPS conserva apenas releases executáveis; código-fonte, classes intermediárias e histórico ficam no repositório Git.
- O deploy automatizado valida a versão do JAR, troca o release atomicamente, executa health check e restaura o anterior em caso de falha.

## v108 — identidade Globoplast consolidada

- Coordenadas Maven, nome Spring e identificação dos endpoints de saúde passam a usar `globoplast`.
- O script de deploy é exercitado na estrutura final da VPS e mantém as quatro releases mais recentes para rollback.
