GLOBOPLAST JAVA v102

VERSÃO ATUAL: 0.0.104

V104
- Cursor de mão ao passar sobre “v.” no rodapé.

V103
- Rodapé: oculta “globoplast.app 0.0.104” e mantém visível somente “v.”.
- Hover sobre “v.” mostra “globoplast.app 0.0.104”, usando AppConfig.VERSION.
- Espaço entre “Mostrar mais” e “v.” reduzido em mais 25 px.
V102: Lixeira de Lançamentos no filtro, restauração por 30 dias e exclusão definitiva automática após o prazo.
V101: Resumo Dia permite multisseleção de Setor, Máquina e Turno, mantendo os demais comportamentos da v100.

V100 - REMOÇÃO DO CÓDIGO DO TÍTULO DE VISUALIZAÇÃO
- Remove somente o Código do Produto exibido após `Visualizar lançamento` / `View entry` no título do modal.
- O código continua disponível normalmente nos campos e demais locais já existentes.
- Nenhuma outra lógica, layout, filtro ou regra de negócio foi alterada.

V099 - CORREÇÃO EXCLUSIVA DO PREFLIGHT DA COLUNA COMPACTA
- Corrige somente a validação legada da v094 no `deploy/preflight.sh`, que ainda exigia `new Span(full)`.
- A validação passa a aceitar a implementação atual `new Span(compact)`, mantendo a célula visível como `Código...` e o tooltip completo.
- Nenhuma lógica, layout, filtro ou regra de negócio da v098 foi alterada.

V098 - CÓDIGO COMPACTO NA COLUNA DE LANÇAMENTOS
- A coluna Código Produto volta a mostrar somente `Código...` quando houver Cliente e/ou Descrição associados.
- O balão ao passar o mouse continua mostrando o conteúdo completo `Código · Cliente · Descrição`.
- Filtro Cliente, busca por Cliente e atualização automática de Cliente/Descrição pelo Código permanecem inalterados.
- Nenhuma regra de negócio foi alterada.

V097 - CORREÇÃO DE COMPILAÇÃO DO FILTRO CLIENTE
- Corrige a importação de `java.util.Collection` no `MainView.java`, necessária pelo novo filtro Cliente introduzido na v096.
- Mantém integralmente as funcionalidades da v096: Código · Cliente · Descrição, atualização por Código e filtro Cliente.
- Nenhuma outra regra de negócio ou interface foi alterada.

V096 - CLIENTE NOS LANÇAMENTOS E FILTRO DE CLIENTE
- Lançamentos exibem Código · Cliente · Descrição, mantendo o conteúdo completo no balão informativo.
- O nome do Cliente vem diretamente de `erp_apontamento_raw.cliente`, sem criar cadastro paralelo.
- Novo Lançamento e Editar Lançamento consultam Cliente e Descrição pelo Código do Produto; trocar o código atualiza os dois metadados.
- O salvamento consulta novamente Cliente e Descrição para garantir correspondência com o código definitivo.
- O menu de filtros de Lançamentos recebe o filtro Cliente, alimentado pelos clientes do período carregado.
- A busca de Lançamentos também passa a localizar pelo nome do Cliente.
- OEE, Refugo, produção, capacidade 24h, turnos, ordenação, calendários, rodapé, sincronização e backups permanecem inalterados.

V095 - DESCRIÇÃO AUTOMÁTICA NO LANÇAMENTO MANUAL
- Novo Lançamento e Editar Lançamento consultam a descrição já sincronizada do ERP pelo Código do Produto.
- Digitar, colar ou trocar o código atualiza imediatamente a descrição exibida abaixo do título.
- Ao salvar, a descrição é consultada novamente para garantir que corresponda ao código definitivo.
- Lançamentos manuais existentes também recebem a descrição na consulta, sem alterar seus dados gravados.
- OEE, Refugo, produção, capacidade 24h, turnos, ordenação, filtros, calendários, rodapé, sincronização e backups permanecem inalterados.

V094 - DESCRIÇÃO DO ITEM NOS LANÇAMENTOS
- Editar e Visualizar lançamento exibem a descrição completa do item imediatamente abaixo do título.
- A coluna Código Produto mostra `código...` quando há descrição cadastrada no apontamento ERP.
- Passar o mouse sobre `código...` exibe Código + Descrição completos em balão superior, mantendo a tabela compacta.
- A descrição não cria nova entrada, não altera o produto e não modifica os lançamentos manuais sem descrição ERP.
- Ordenação da v093, OEE, Refugo, capacidade 24h, turnos, filtros, calendários, rodapé, sincronização e backups permanecem inalterados.

V093 - LANÇAMENTO MAIS RECENTE OU ATUALIZADO NO TOPO
- Lançamentos são ordenados pela data e hora real da última movimentação, antes da data produtiva.
- Novo lançamento manual entra imediatamente no topo; editar um lançamento manual também o move para o topo.
- Novo apontamento ou alteração recebida do ERP atualiza a posição do lançamento correspondente.
- Edição de lançamento ERP usa a hora da própria edição e também move o registro para o topo.
- A mesma ordenação é preservada na lista de Lançamentos e em “Todos os Apontamentos do Mês”.
- Registros históricos existentes recebem uma migração compatível usando Data + Hora do lançamento, sem perda ou alteração dos dados produtivos.
- Nenhuma regra de OEE, Refugo, 24h, turno, filtro, calendário, login, tooltip, rodapé, sincronizador Windows ou backup foi alterada.

V092 - RÓTULO INGLÊS E ESPAÇAMENTO DO RODAPÉ
- Em inglês, “Search and Entries” passa a ser exibido somente como “Entries”.
- As informações do rodapé descem mais 3 px, passando de 10 px para 13 px de deslocamento total.
- O rodapé exibe somente “v.”; ao passar o mouse, mostra “globoplast.app 0.0.104”. O espaço entre “Mostrar mais” e “v.” foi reduzido em mais 25 px (38 px de redução total em relação à base original).
- Nenhuma regra de OEE, Refugo, produção, sincronização, filtro, calendário, login, tooltip ou backup foi alterada.

V091 - REFUGO REDUZ EFETIVAMENTE O OEE
- Desempenho passa a usar somente as peças boas produzidas em relação à capacidade 24h da máquina.
- Refugo continua reduzindo a Qualidade por `boas / (boas + refugo)` e não eleva mais o Desempenho.
- Consequentemente, todo Refugo convertido em peças reduz efetivamente a Qualidade e o OEE final.
- A janela única de 24h, a capacidade única por máquina/dia e o desconto de setup/paradas da v090 permanecem inalterados.
- Nenhuma tela, filtro, sincronização, calendário, login, tooltip, rodapé ou backup foi alterado.

V090 - OEE ÚNICO DE 24H POR MÁQUINA E DIA PRODUTIVO
- Todos os lançamentos da mesma máquina no mesmo dia produtivo compartilham uma única janela de 24 horas.
- A quantidade de lançamentos, OPs ou turnos não multiplica as horas programadas nem a capacidade da máquina.
- Todos os lançamentos do grupo usam a mesma capacidade 24h e recebem os mesmos indicadores consolidados de Disponibilidade, Desempenho, Qualidade e OEE.
- Setup e paradas continuam somados no grupo e descontados das mesmas 24 horas.
- Nenhuma tela, filtro, sincronização, regra de Refugo, calendário, login, tooltip, rodapé ou backup foi alterado.

V089 - BALÕES DOS GRÁFICOS NO PONTEIRO E RODAPÉ +5 PX
- Somente os quatro componentes de gráfico voltam a usar o balão nativo no ponteiro do mouse, exatamente como na v081.
- Balões de campos, calendários, ícones e tabelas permanecem no padrão da v088/v085.
- As informações do rodapé descem mais 5 px, passando de 5 px para 10 px de deslocamento total.
- Nenhuma outra alteração foi realizada.

V088 - BALÕES RESTAURADOS AO PADRÃO DA V085
- Remove exclusivamente todas as regras de tamanho de Tooltip introduzidas nas versões v086/v087.
- Os balões voltam exatamente ao tamanho e acabamento padrão da v085.
- Limpeza nativa dos filtros, títulos “Análise por ano/mês”, filtro correto por turno e Observações em maiúsculas permanecem inalterados.
- Nenhuma outra alteração foi realizada.

V087 - LIMPEZA NATIVA, TÍTULOS POR PERÍODO E BALÕES MAIORES
- Setor, Máquina e Turno do Resumo do Dia passam a usar o “X” nativo do mesmo ComboBox empregado nos demais filtros; o botão personalizado da v086 foi removido integralmente.
- Limpar Setor ou Turno preserva Máquina quando ela continua válida; cada “X” continua removendo somente o próprio filtro.
- A Data mantém o padrão dos demais calendários, com “Limpar” dentro do seletor.
- As abas permanecem “Anual” e “Mensal”; os títulos abaixo passam a ser “Análise por ano” e “Análise por mês”.
- Os balões informativos passam a usar 18 px diretamente nas variáveis e partes reais do Tooltip Vaadin.
- Nenhuma outra tela, cálculo, sincronização, calendário, login ou backup foi alterado.

V086 - ABAS, FILTROS POR CAMPO, TURNO, OBSERVAÇÕES E INFORMATIVOS
- As abas do gráfico de Refugo passam a se chamar “Anual” e “Mensal”, mantendo “Análise anual/mensal” somente no título abaixo.
- Cada campo do filtro do Resumo do Dia recebe um “X” próprio; ele limpa somente Data, Setor, Máquina ou Turno e preserva os demais filtros válidos.
- O filtro de Turno do Resumo do Dia limita produção, refugo e percentual de refugo ao turno selecionado sem alterar os dados salvos.
- Observações de Novo Lançamento e Editar Lançamento são convertidas para maiúsculas durante a digitação.
- Os textos de todos os balões informativos aumentam para 16 px, preservando posição e conteúdo.
- Nenhuma outra tela, cálculo persistido, sincronização, calendário, login ou backup foi alterado.

V085 - ABAS DE REFUGO E HORA REAL DE CARGA
- As abas do gráfico de Refugo voltam ao alinhamento original à esquerda, sem divisão forçada da largura.
- Os KPIs de Refugo continuam ajustados para manter período, valor e unidade na mesma linha.
- “Ver lançamentos” passa a mostrar a hora da carga de `sincronizado_em`, exatamente pela mesma regra de “Lançamentos recentes”, em vez da estimativa fixa por turno.
- A auditoria contra a v082 confirmou que nenhuma outra redistribuição estrutural foi introduzida.
- Todas as demais funcionalidades da v084 e das versões anteriores permanecem inalteradas.

V084 - COMPATIBILIDADE VAADIN DO INFORMATIVO
- Corrige exclusivamente a compilação dos informativos de Código/OP da v083 para a API do Vaadin 25.2.4.
- Os informativos continuam acima, os textos do consolidado permanecem com o tamanho de Lançamentos e o cursor continua sendo a mãozinha.
- Todas as demais alterações funcionais da v083 e das versões anteriores permanecem inalteradas.

V083 - CONSOLIDADO, REFUGO, RODAPÉ E MÚLTIPLAS OPS
- A tabela consolidada do Resumo do Dia usa os mesmos tamanhos de texto da tabela de Lançamentos.
- Códigos e OPs múltiplos usam somente cursor de mão; o cursor de ajuda com “?” foi removido.
- Os informativos de códigos e OPs usam texto de 16 px, igual às linhas de Lançamentos.
- Em Refugo, os indicadores do comparativo mantêm período, valor e unidade na mesma linha e as abas dividem a largura disponível.
- A aba “Análise por ano” aparece antes de “Análise por mês”; as regras internas dos comparativos permanecem inalteradas.
- As informações do rodapé foram deslocadas exatamente 5 px para baixo.
- Em Lançamentos, múltiplas OPs aparecem como primeira OP seguida de “...” e o informativo relaciona cada OP à sua quantidade produzida.
- Salvar um novo lançamento não altera mais período, Setor, Máquina nem a busca de Lançamentos.
- Todas as demais telas, cálculos, sincronização, calendários, login e backups permanecem inalterados.

V082 - RESUMO DO DIA CONSOLIDADO E INFORMATIVOS ACIMA
- O Resumo do Dia mantém somente os KPIs no topo e a tabela consolidada abaixo; o gráfico foi removido apenas dessa tela.
- Código Produto e Nº da OP mostram o primeiro valor seguido de “...” quando o consolidado contém mais de um valor.
- Ao passar o mouse ou focar Código Produto/Nº da OP com múltiplos valores, um informativo acima apresenta a lista completa.
- O filtro do Resumo do Dia passa a incluir Turno A, B e C, preservando Data, Setor e Máquina.
- Todos os informativos do sistema passam a abrir acima de seus respectivos alvos.
- Comparativos, calendários, lançamentos, Refugo, sincronização, login e backups permanecem inalterados.

V081 - NOMES DOS COMPARATIVOS E CALENDÁRIOS UNIFICADOS
- Na aba Refugo, “Comparativo Mensal” passa a aparecer como “Mensal”, com o título “Análise mensal” abaixo.
- “Comparativo Anual” passa a aparecer como “Anual”, com o título “Análise anual” abaixo.
- Novo Lançamento, Editar Lançamento e Visualizar Lançamento passam a usar o mesmo componente de calendário dos filtros.
- Todos os calendários compartilham mês/ano, ordem domingo a sábado, destaque da data e acabamento visual; formulários mantêm seleção única e filtros mantêm seleção de intervalo.
- Comparativos dinâmicos, coluna “Lançado por”, login por aba, sincronização, backups e todas as demais regras anteriores permanecem inalterados.

V080 - COMPARATIVOS DINÂMICOS DE REFUGO E LANÇADO POR
- Quando o recorte filtrado contém dados de mais de um mês, a aba “Comparativo Mensal” passa a aparecer imediatamente, sem precisar sair e voltar à tela.
- Quando o recorte filtrado contém dados de mais de um ano, a aba “Comparativo Anual” passa a aparecer imediatamente.
- Ao retornar para um único mês ou um único ano, somente o comparativo que deixou de se aplicar é ocultado; as demais dimensões e filtros permanecem intactos.
- A tabela “Ver lançamentos”, aberta pelo menu do gráfico de Refugo, recebe a coluna “Lançado por”, usando o nome do operador vindo do ERP.
- Todas as funções, posições, sincronizações e regras das versões anteriores permanecem inalteradas.

V079 - NOVO LANÇAMENTO MAIS 1 PX ACIMA
- O botão “Novo Lançamento” sobe mais 1 px em relação à v078, passando de -2,5 px para -3,5 px.
- A toolbar, o campo de pesquisa, o filtro e todos os demais componentes permanecem inalterados.
- O comportamento de login da v078 é preservado integralmente.

V078 - LOGIN PRESERVADO NA MESMA ABA
- O login continua ativo ao atualizar com F5 ou recarregar a página.
- O login também continua ativo ao digitar ou confirmar novamente `globoplast.app` na barra de endereço da mesma aba.
- A autorização permanece em `sessionStorage`, que pertence à aba e é descartado quando ela é fechada.
- Foi removido o tratamento `pagehide` da v077, pois ele interpretava uma nova entrada pelo endereço como encerramento.
- A posição do botão “Novo Lançamento” da v077 permanece inalterada em -2,5 px.

V077 - LOGIN POR ABA E AJUSTE DO NOVO LANÇAMENTO
- O login passa a ser autorizado por aba usando `sessionStorage`; o evento `pagehide` encerra a autorização daquela página.
- Uma atualização comprovada por `navigation.type === 'reload'` restaura a autorização transitória e mantém o usuário conectado.
- Ao fechar a aba, a autorização não é restaurada; ao abrir o sistema novamente, a tela de login é obrigatória, inclusive ao reabrir uma aba fechada.
- Cookies antigos não restauram acesso em uma aba sem a marca de autenticação correspondente ao usuário.
- A saída manual também remove imediatamente a autorização da aba.
- O botão “Novo Lançamento” sobe exatamente 1 px em relação à v076, de -1,5 px para -2,5 px, sem mover a toolbar ou os demais controles.

V076 - EXCLUSÕES DO REFUGO SINCRONIZADAS COM O ERP
- O servidor aceita snapshots completos e remove registros de Refugo que deixaram de existir no ERP.
- A exclusão remove `erp_refugo_raw` e eventuais reclassificações ligadas ao mesmo ERP_ID na mesma transação.
- O vínculo com a produção é dinâmico; ao remover o registro bruto, Refugo, Qualidade e OEE são recalculados sem manter valor residual.
- Lotes normais continuam somente com insert/update: exclusões só são permitidas em snapshots completos de até 31 dias, evitando apagamentos por lote parcial.
- Toda exclusão fica registrada em `erp_sync_exclusoes`, incluindo uma cópia JSON completa do registro removido para auditoria/recuperação; `erp_sync_lotes` registra a quantidade excluída.
- O sincronizador Windows reconcilia os últimos 7 dias em toda execução e realiza uma auditoria diária desde 01/01/2025, em blocos seguros de até 31 dias.
- A atualização do sincronizador preserva credenciais, configuração e tarefa agendada existentes; o Firebird permanece somente leitura.

V075 - FAVICON DO JAVA SEM CACHE DO STREAMLIT
- O sistema remove referências antigas de favicon que possam ter sido preservadas no navegador durante a migração do domínio.
- O favicon do Java passa a usar uma URL versionada (`/favicon.png?v=075-20260821`), forçando uma nova transferência sem depender da limpeza manual do cache.
- Nenhuma regra de DNS, Nginx, telas ou dados foi alterada.

V074 - REFUGO ALINHADO EXATAMENTE A LANÇAMENTOS
- O nome “Pesquisar refugo”, o campo de pesquisa e o botão de filtro passam a usar exatamente a mesma altura e linha-base da busca em Lançamentos.
- A margem exclusiva antiga do Refugo foi substituída pela geometria de Lançamentos.
- Os deslocamentos fracionários acumulados das versões anteriores foram neutralizados; nenhuma outra tela foi alterada.

V073 - BUSCA E FILTRO DE REFUGO MAIS 0,2 PX ABAIXO
- O conjunto “Pesquisar refugo” e seu botão de filtro desce mais 0,2 px em relação à v072.
- A posição final permanece 0,05 px acima da posição original; nenhum outro elemento é alterado.

V072 - BUSCA E FILTRO DE REFUGO 0,25 PX ABAIXO
- O conjunto “Pesquisar refugo” e seu botão de filtro desce 0,25 px em relação à v071.
- A posição final permanece 0,25 px acima da posição original; nenhum outro elemento é alterado.

V071 - BUSCA E FILTRO DE REFUGO 0,5 PX ACIMA
- O conjunto “Pesquisar refugo” e seu botão de filtro sobe exatamente 0,5 px.
- Tamanho, alinhamento interno, distância entre campo e botão e a toolbar de Lançamentos permanecem inalterados.

V070 - SETOR ÚNICO, LIMPEZA ORIGINAL E TÍTULOS ALINHADOS
- O gráfico de Setor passa a agrupar nomes sem diferenciar maiúsculas e minúsculas, evitando duas barras para o mesmo setor após uma transferência.
- “Limpar filtros” remove filtros, busca, exclusões temporárias e reclassificações, restaurando os setores calculados originalmente a partir do ERP.
- A restauração limpa overrides analíticos e legados sem alterar os dados brutos sincronizados.
- “Análise por Setor” volta mais 5 px, de 53 px para 48 px, e desce 3 px, de -3 px para 0 px.
- Setor, Máquina, Turno, Descrição, Motivo e Comparativos passam a usar exatamente a mesma posição de título.

V069 - POSIÇÃO SETOR, RECUO DE 65 PX E TRANSFERÊNCIA FILTRADA
- “Análise por Setor” volta 15 px, passando de 68 px para 53 px, e sobe 7 px, passando de `top:4px` para `top:-3px`.
- O recuo direito aumenta mais 3 px, passando de 62 px para 65 px.
- A transferência de Refugo passa a salvar cada `analysisId` efetivamente presente no filtro aplicado.
- O valor transferido é retirado do setor de origem e reaparece no setor de destino sem mover turnos ou linhas fora do filtro.
- Overrides antigos por `erp_id` continuam compatíveis; os novos overrides analíticos têm precedência.
- Os dados brutos de `erp_refugo_raw` permanecem imutáveis.

V068 - RECUO DIREITO DE 62 PX E TÍTULO SETOR FORÇADO
- O recuo direito do gráfico aumenta mais 17 px, passando de 45 px para 62 px.
- Mantendo `margin-left:-25px`, a largura passa para `calc(100% - 37px)`.
- “Análise por Setor” permanece em 68 px, correspondente ao avanço de 30 px solicitado na v067.
- Além do CSS, o valor de 68 px é aplicado diretamente no elemento do título com prioridade `important` após sua montagem.
- Nenhuma outra posição ou funcionalidade foi alterada.

V067 - RECUO DIREITO AMPLIADO E TÍTULO SETOR
- O recuo direito do gráfico de Refugo aumenta exatamente 30 px, passando de 15 px para 45 px.
- O conjunto permanece 25 px à esquerda; a largura passa para `calc(100% - 20px)` para produzir o novo recuo.
- “Análise por Setor” avança exatamente 30 px à direita, passando de 38 px para 68 px.
- “Quantidade Refugada (Kg)” permanece na posição atual de 38 px.
- Nenhum outro comportamento ou estilo do sistema foi alterado.

V066 - RECUO DIREITO, TÍTULOS ALINHADOS E MENU SEM CAIXA
- O conjunto do gráfico permanece 25 px à esquerda, mas agora termina com 15 px de recuo real na lateral direita.
- “Análise por Setor” e “Quantidade Refugada (Kg)” usam a mesma referência horizontal de 38 px.
- O botão de três pontos passa a usar a variante inline e neutraliza fundo, borda, sombra, foco e pseudo-elementos internos do Vaadin.
- No hover, somente os três pontos aumentam de opacidade; nenhuma caixa quadrada é desenhada.
- Nenhuma regra de filtros, Refugo, OEE, lançamentos, usuários ou banco foi alterada.

V065 - GEOMETRIA DO REFUGO, ABAS ESTÁVEIS, MENU E URL LIMPOS
- O conjunto completo dos gráficos de Refugo avança mais 10 px à esquerda, chegando a -25 px.
- A largura do gráfico compensa o deslocamento e sua extremidade direita termina no mesmo alinhamento do logo.
- Quantidade Refugada (Kg) avança exatamente 20 px à direita, passando de 18 px para 38 px.
- A troca entre as abas internas do Refugo preserva a posição vertical da janela e desativa a ancoragem automática do navegador.
- A largura reservada para a barra vertical permanece estável, evitando deslocamento horizontal da página.
- O botão do menu do sistema não cria contorno, fundo, halo ou sombra no hover; apenas fica totalmente aceso.
- A aba ativa continua salva na sessão, mas a URL permanece limpa, sem `?aba=...` após o endereço.

V064 - REFUGO, MENU DIREITO, DROPDOWNS E TROCA DE SETOR
- “Análise por Setor” avança 15 px para a direita e sobe 10 px, sem alterar os demais títulos.
- O gráfico mantém o deslocamento de 15 px à esquerda e recupera o recuo padrão no lado direito.
- O menu de uma barra abre somente pelo botão direito do mouse; clique simples continua selecionando e removendo a seleção.
- A troca de abas do gráfico preserva a posição vertical da página.
- Os nomes dos setores no filtro de Refugo aparecem em maiúsculas, mantendo “Todos” com capitalização normal.
- Dropdowns de filtros, lançamentos, usuários e cadastros usam a mesma superfície, borda, raio, sombra e hover.
- Administradores podem enviar todos os lançamentos da barra selecionada a outro setor cadastrado.
- A nova classificação é persistida em uma tabela de override; os registros brutos sincronizados do ERP não são alterados.

V063 - POSIÇÃO DO REFUGO E ÍCONE DE OBSERVAÇÕES
- O gráfico completo de Refugo fica exatamente 15 px à esquerda.
- O texto vertical Quantidade Refugada (Kg) fica exatamente 10 px à direita da posição anterior.
- O campo Setor mostra “Todos” normalmente quando vazio; somente busca e valores selecionados ficam em maiúsculas.
- Um ícone real de informação em círculo aparece ao lado do OEE somente quando Observações possui texto válido.
- Ao passar o mouse ou focar o ícone, o balão mostra somente o conteúdo de Observações; o atributo title funciona como segurança adicional.

V062 - DETALHES NO CLIQUE E OEE CONFIÁVEL
- Um clique no item do gráfico de Descrição atualiza imediatamente a área Detalhes do item.
- A tabela de Lançamentos continua aparecendo somente após escolher Ver lançamentos no menu do gráfico.
- As linhas dos gráficos de Refugo foram deslocadas exatamente 20 px para a esquerda.
- Observações usa o balão nativo diretamente no valor do OEE, sem ícone e sem depender de overlay dentro do Grid.
- Nos filtros de Setor, somente o conteúdo digitado/selecionado fica em maiúsculas; o título mantém a capitalização normal.

V061 - INFORMATIVO OEE, ABAS E ROLAGEM CONTROLADA
- O texto de Observações aparece em um balão flutuante diretamente sobre o valor do OEE, sem “?” ou ícone.
- As abas recebem resposta visual no pressionamento e a renderização duplicada da aba inicial foi eliminada.
- A seleção das barras do Refugo deixa de esperar 240 ms; clique simples alterna imediatamente e clique duplo mantém o menu.
- Modais bloqueiam a rolagem da página de fundo e contêm a rolagem ao chegar ao início ou fim do conteúdo.
- Lançamentos recentes do Refugo expandem com todas as linhas, sem scroll interno.
- Setor permanece em maiúsculas nos filtros e Máquina passa a aparecer antes de Ordem no filtro de Refugo.

V060 - OEE SEM ÍCONE, DUPLO CLIQUE E PROTEÇÕES DE CADASTRO
- O tooltip de Observações fica diretamente no valor do OEE; nenhum “?” ou ícone adicional aparece.
- Clique simples em uma barra seleciona ou remove a seleção; somente o clique duplo abre o menu contextual.
- Controles acionáveis usam cursor de mão, preservando o cursor de texto nos campos de digitação.
- Editar Máquina usa layout compacto em duas linhas e Atribuir ao Setor passa a ComboBox padronizado.
- A exclusão de Setor é impedida quando houver Máquinas ou usuários Padrão vinculados.
- A proteção já existente do último Administrador permanece ativa ao excluir ou alterar perfis.

V059 - TEXTOS DA TABELA E INFORMATIVO DE OBSERVAÇÕES
- Textos das linhas da tabela principal de Lançamentos aumentados para 16 px e cabeçalhos para 15 px.
- O ícone informativo ao lado do OEE aparece somente quando o campo Observações possui conteúdo válido.
- O aviso de capacidade ausente deixa de criar um ícone na coluna OEE.
- Ao passar o mouse ou focar o ícone, um tooltip mostra exatamente o texto preenchido em Observações.

V058 - MENU CONTEXTUAL DO GRÁFICO E SUPERFÍCIES CORRIGIDAS
- Ao clicar em uma barra, o menu do gráfico abre à direita do ponto clicado; o botão fixo de três pontos foi removido.
- “Análise por Setor/Máquina/Turno...” volta a alinhar à esquerda com a aba selecionada.
- O dropdown principal recupera a superfície e o hover discretos que possuía antes da v057.
- Cabeçalho, conteúdo, rodapé, campos e tabelas dos modais usam uma única cor própria e uniforme.

V057 - PADRONIZAÇÃO COMPLETA E FILTROS CONTEXTUAIS
- Capacidade passa a alinhar pela base do campo Máquina em Novo/Editar Lançamento.
- Perfil e Setor de Novo/Editar Usuário passam de Select para ComboBox, com a mesma estrutura dos demais campos.
- Título do gráfico de Refugo fica alinhado à direita e o menu permanece imediatamente à direita do gráfico.
- No Refugo, Setor limita as Máquinas e Setor + Máquina limitam Ordem, Produto, Descrição, Cliente, Turno, Operador e Motivo.
- Modais, dropdowns, campos e tabelas internas usam `--gp-surface`, exatamente a cor do dropdown do menu do sistema.

V056 - KPIS DE REFUGO MAIORES E DISTRIBUÍDOS
- Os cinco KPIs principais de Refugo passam a ocupar cinco colunas iguais em toda a largura da tela.
- Rótulos, valores e legendas foram ampliados, com maior altura e espaçamento uniforme.
- No mobile, os KPIs reorganizam-se em duas colunas e o último ocupa a largura completa, sem rolagem horizontal.

V055 - CAMPO MÁQUINA ESTRUTURALMENTE IGUAL
- O campo Máquina de Novo/Editar Lançamento deixa de usar Select e passa a usar ComboBox com seleção restrita às máquinas cadastradas.
- O componente agora possui o mesmo input real, altura, espaçamento, fundo, borda, raio, fonte e foco dos demais campos.
- A seta de seleção permanece funcional e integrada ao campo, sem criar uma caixa visual diferente.

V054 - CAMPO MÁQUINA PADRONIZADO E DESEMPENHO
- O campo Máquina de Novo/Editar Lançamento usa a mesma altura, fundo, borda, raio, fonte e foco dos demais campos.
- Resumo do Mês mantém os componentes e atualiza somente seus dados; Mostrar mais não reconstrói gráfico e consolidado.
- Cache por sessão passa a guardar várias faixas de data para deixar a troca entre abas imediata.
- Metadados de máquinas são persistidos em um único lote por carga, em vez de uma conexão SQLite para cada lançamento.
- Índices adicionais aceleram as consultas do histórico por data e máquina.

V053 - MODAIS TOTALMENTE UNIFORMES
- Cabeçalho, conteúdo, rodapé, campos e tabelas internas usam exatamente o mesmo fundo do modal.
- O bloco preto no centro dos modais foi removido; linhas e controles são separados apenas por bordas discretas.
- A regra é aplicada pela fábrica central de Dialog e alcança Cadastro, Usuários, Relatórios e demais modais.

V052 - FILTRO SEM DUPLICAÇÃO E MODAL NEUTRO
- Após cada seleção, o filtro fecha e mostra somente o chip selecionado; o input interno duplicado fica oculto até a próxima abertura.
- Ao reabrir, o campo de pesquisa volta normalmente para permitir novas seleções.
- Modais deixam de usar o cinza fixo e o texto branco forçado da v051; passam a usar cinza discreto adaptado ao tema.
- Cabeçalho, conteúdo e rodapé permanecem no mesmo plano de cor.

V051 - CORREÇÕES EFETIVAS DE INTERFACE
- Novo Lançamento elevado exatamente 1,5 px.
- MultiSelect usa setKeepFilter(false) da API Java do Vaadin e limpa o input interno após cada seleção.
- Modais estilizados no host vaadin-dialog correto, com cabeçalho, conteúdo e rodapé no mesmo cinza.

V050 - BUSCA/FUNIL E MODAIS
- Lançamentos e Refugo usam a mesma altura de 44 px para campo de busca e botão de filtro.
- O funil fica alinhado à base do campo e separado por 8 px nas duas telas.
- Todos os modais usam um único cinza mais claro no cabeçalho, conteúdo e rodapé, sem faixa diferente na área dos botões.

OBJETIVO
- Auditoria completa de paridade com todo o appv723.zip, não somente a interface principal.
- Referência auditada: appv723.py + todos os módulos globoplast_core + schema SQLite.
- Matriz detalhada: PARIDADE_APPV723.md.
- Ambiente Java de migração: http://168.138.142.85/
- Produção Streamlit permanece separada e não é alterada.

V030 — PARIDADE APPV723
- OEE reproduz a fórmula original por Máquina + Dia produtivo, sem cap ou redistribuição artificial de horas na visão consolidada.
- Produção ERP: Qtd.Apon x1000, agrupamento, Turno C, capacidade/setor e distribuição original das 24h preservados.
- Cruzamento Refugo x Produção reproduz prioridade OP+Produto -> Setor -> Dia -> Máquina -> produção/ordem estável.
- Qtd Itens=0 é valor válido; fallback por peso somente quando NULL.
- Refugo sem turno permanece no total de kg do lançamento automático.
- Peso unitário ERP é ponderado pelas unidades de refugo.
- Lançamentos manuais preservam parsing original de +, múltiplas OPs, refugo implícito em gramas e horas H:M/H.MM.
- Novo/Edit/View usam a mesma estrutura e a mesma sequência lógica de teclado.
- Manual recém-salvo é focado/visível imediatamente como na v723.
- Overrides ERP permanecem locais; DealerSystem/Firebird não é escrito.
- Permissões de edição/exclusão aplicadas também no backend dos serviços.
- Perfis Padrão/Acompanhamento/Conferente/Administrador e restrições de abas preservados.
- Usuários: senha/confirmacao, último administrador, autoexclusão, setor obrigatório para Padrão e sessões invalidadas na exclusão.
- Cadastro: regras de setor/máquina, capacidade >0, renomeação de setor e bloqueio por usuários Padrão.
- Banco: migrações defensivas de numero_op, op_producao_detalhe, hora_lancamento, setor/perfil/idioma e normalização de usuários, como no appv723.
- ADMIN inicial: corrige/promove ADMIN existente se necessário antes de criar um novo, como no original.
- Lançamentos: busca, filtros, paginação de 20, ações e ordenação mais recente primeiro.
- Resumo do Dia volta à estrutura original: 3 filtros inline, 5 KPIs, gráfico agrupado e tabela consolidada.
- Resumo do Mês volta à estrutura original: 2 filtros inline, ranking OEE, consolidado e todos os apontamentos com Mostrar mais.
- Refugo: filtros completos, busca, Qtd.Planej x1000, Qtd Itens, diluição de turnos, códigos/setores, gráficos, comparativos, Top 5, detalhes, exclusões contextuais e lançamentos recentes.
- Calendário: dd/MM/yyyy, domingo primeiro, range por dois cliques, mês inteiro ao trocar mês nos filtros de período e Limpar/Todas.
- pt-BR/en-US revisados; tema Claro/Escuro/Sistema continua como extensão solicitada após a v723.
- Abas persistidas em ?aba=.
- Relatórios abre dialog real.
- Funil de filtros usa a geometria CSS exata do appv723.
- HMAC/API de sincronização permanece em /java-sync/v1, lote máximo 5000 e tolerância de 300s.

EXTENSÕES DELIBERADAS PEDIDAS APÓS A V723
- Manual e ERP podem coexistir e ambos participam de Resumo Dia/Mês/OEE.
- Override de ERP tem precedência sobre o automático nos cálculos.
- OEE é recalculado quando Produção ou Refugo chega/é atualizado posteriormente.
- Usuário, setor e máquina são normalizados em maiúsculas.
- Tema Claro/Escuro/Sistema é mantido no Java.
- Dialogs têm X e cabeçalhos minimalistas, conforme pedido posterior.

SEGURANÇA
- Firebird/DealerSystem: SOMENTE LEITURA pelo sincronizador externo.
- Banco Java: /var/lib/globoplast-java/database.db.
- Produção Streamlit/globoplast.app não é alterada durante a migração.

BUILD
bash deploy/preflight.sh

Após BUILD SUCCESS / PRE-FLIGHT OK:
target/globoplast-java.jar

V030 — OEE / EDIÇÃO ERP / LAYOUT
- Edição de linha ERP preserva máquina, capacidade e setor mesmo sem cadastro local atual.
- Capacidade conhecida pode ser recuperada de snapshot/histórico sem inventar valor novo.
- OEE com produção e capacidade desconhecida recebe informativo explícito.
- Botões de filtro recebem contorno e destaque no hover.
- Margens laterais ampliadas e alinhadas pelo mesmo shell central.

V031 - CORREÇÃO OEE / CATÁLOGO APPV723
- OEE automático depende da Capacidade 24h da tabela maquinas, exatamente como no appv723.
- Endpoint autenticado /java-sync/v1/catalogo para migrar setores/máquinas/capacidades do backend original.
- Endpoint autenticado /java-sync/v1/diagnostico-oee para validar cobertura de capacidades.
- deploy/migrar_catalogo_v723.py lê o database.db original em SOMENTE LEITURA e não altera o globoplast.app.
- Em edição ERP sem máquina cadastrada, todos os dados originais permanecem; capacidade pode ser informada no override quando ausente e passa ao snapshot da máquina.


V032 - OEE / EQUIVALENCIA REAL DE MAQUINAS
-------------------------------------------
- Corrige equivalência ERP x catálogo usando família + número, com acentos e zeros à esquerda normalizados.
- Inclui INJEÇÂO/INJEÇÃO/INJECAO -> INJETORA e FECHA HOT AIR -> HOT AIR.
- Apontamento automático passa a usar o nome/capacidade da máquina catalogada quando houver correspondência.
- O diagnóstico OEE informa máquinas ERP resolvidas por alias e as que realmente seguem sem capacidade.
- O migrador v002 também lê, em modo somente leitura, capacidades históricas de historico_oee e grava apenas no snapshot Java; isso não cria máquinas no cadastro atual.


V034 - AJUSTE VISUAL DOS GRAFICOS DE REFUGO
- Base funcional: v032. A v033 anterior não faz parte desta versão.
- Barras dos gráficos de Refugo mais largas em todas as dimensões e comparativos.
- Largura continua adaptativa conforme a quantidade de categorias.
- Cantos superiores suavemente arredondados, com arredondamento discreto na base.
- Nenhuma alteração em regras de Refugo, OEE, ERP, filtros ou banco.

V035 - LOGIN DARK / MENU DROPDOWN
- Tema escuro: texto digitado, labels, caret e ícone de revelar senha do login ficam brancos.
- A cor do input acompanha mudanças de tema mesmo dentro do Shadow DOM do Vaadin.
- Menu dropdown: ao abrir, o primeiro item não aparece mais como se estivesse em hover.
- O destaque do item do menu passa a ocorrer somente quando o ponteiro está realmente sobre ele.
- Mantém integralmente backend/OEE/Refugo e barras da v034.


V036 - MENU HOVER REAL / MAIUSCULAS EM TEMPO REAL
- Dropdown do sistema: nenhum item fica visualmente selecionado ao abrir.
- Destaque somente quando o ponteiro entra realmente no item.
- Submenus de idioma/tema recebem a mesma regra.
- Campos com regra de maiúsculas convertem o valor no próprio evento de digitação.
- Caps Lock pode permanecer desligado; o texto já aparece e é enviado em maiúsculas.
- Backend e regras funcionais permanecem os da v035/v032.

V037 - NUMEROS PT-BR / FONTE ORIGINAL / REFUGO / FILTROS
- Formatação numérica com agrupamento e decimais localizados (pt-BR: 1.234,56).
- Fonte Source Sans aplicada também aos componentes Vaadin/Lumo, aproximando o appv723.
- Refugo: Total Refugo volta a exibir "100,0% do total".
- Refugo: período de hoje mostra "Hoje" com a data logo abaixo.
- Status de sincronização mostra somente Online (verde) ou Offline (vermelho).
- Calendário mantém o dia selecionado destacado.
- Hover do botão/opções de filtro mais discreto, sem halo ou deslocamento.

V040 - CARREGAMENTO / FILTRO / BUSCA INCREMENTAL
- Cache por sessão para a faixa atual de Lançamentos/Resumo Dia/Resumo Mês, evitando reler e recalcular o mesmo período a cada filtro, tecla ou Mostrar mais.
- Cache equivalente no Refugo, reutilizado pela página, KPIs, gráficos e dropdown de filtros.
- Invalidação automática dos caches quando chega nova sincronização ERP, quando um lançamento é salvo/excluído ou quando cadastro de máquina/setor muda.
- Poll de sincronização deixa de reconstruir Lançamentos e Refugo inteiros; atualiza somente os componentes de dados visíveis.
- Grids grandes de detalhamento de Refugo mantêm virtualização em vez de renderizar centenas/milhares de linhas DOM de uma vez.
- Pesquisa de Lançamentos usa ValueChangeMode.EAGER e filtra a lista já carregada em memória, mostrando resultados conforme o usuário digita.
- Hover do filtro recebe destaque neutro e discreto; foco não usa mais contorno vermelho.
- Nenhuma regra de OEE, Refugo, dia produtivo, ERP ou permissões foi alterada nesta versão.


V041: camada CSS canônica para eliminar conflitos históricos; filtro sem contorno vermelho; datas selecionadas com variant primary; busca incremental real por substring; refresh leve Dia/Mês.

V042: corrige exclusivamente o preflight/versionamento herdado da v041; nenhuma regra funcional ou visual foi alterada.

0.0.47
- MultiSelect dos filtros limpa o texto de pesquisa após selecionar um item, evitando valor duplicado dentro do campo.
- Novo Lançamento fica na lateral direita da toolbar, alinhado verticalmente e com a mesma altura visual do campo de busca.
- Resumo Dia e Resumo Mês usam botão de filtro no cabeçalho à direita; os campos ficam dentro do popover.
- Barras dos gráficos de Refugo ficaram mais largas, mantendo largura adaptativa e cantos suaves.
