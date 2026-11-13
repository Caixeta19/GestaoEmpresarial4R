# 4R Solutions

Backend do ERP interno da 4R Redes: registro de vendas de produtos e serviços Vivo,
controle de estoque serializado (IMEI), conciliação de inventário contra relatórios SAP,
protocolo documental pós-venda e disparo de campanhas de mailing via WhatsApp.

---

## Índice

1. [Conceito](#1-conceito)
2. [Escopo](#2-escopo)
3. [Regras de negócio](#3-regras-de-negócio)
4. [Decisões de arquitetura](#4-decisões-de-arquitetura)
5. [Stack](#5-stack)
6. [Arquitetura](#6-arquitetura)
7. [Estrutura de pastas](#7-estrutura-de-pastas)
8. [Modelo de dados](#8-modelo-de-dados)
9. [API](#9-api)
10. [Segurança](#10-segurança)
11. [Estado atual — módulo a módulo](#11-estado-atual--módulo-a-módulo)
12. [Como rodar](#12-como-rodar)
13. [Backlog / pendências conhecidas](#13-backlog--pendências-conhecidas)

---

## 1. Conceito

O problema: a operação de venda de uma filial passa por várias etapas que hoje vivem
espalhadas — carrinho de venda, baixa de serial no estoque, conferência de inventário
contra o relatório do SAP, protocolo documental pós-venda e campanhas de relacionamento
por WhatsApp. Sem um sistema único, cada etapa é uma planilha ou um controle manual
diferente, e nada conversa entre si.

A solução: um ERP modular onde a venda é o centro. Um vendedor abre um carrinho
(`ABERTA`), adiciona itens de categorias diferentes (produto físico, serviço Vivo,
acessório, recarga), registra pagamentos e lança a venda com a senha do vendedor. Cada item
reserva o serial no estoque (`RESERVADO`) e o lançamento o baixa (`VENDIDO`); a partir daí a
venda alimenta o protocolo documental e os KPIs do painel executivo.

O modelo mental inteiro cabe em duas frases:

> **A venda é a entidade central — tudo (estoque, documental, dashboard) deriva dela.**
> **Cada módulo é dono do seu próprio ciclo de vida e nunca decide por outro.**

Uma venda passa por uma máquina de estados própria: `ABERTA` (carrinho em montagem) →
`CONCLUIDA` (lançada) num passo só, pelo finalizar com a senha do vendedor, e `CANCELADA` a partir
de `ABERTA` ou `CONCLUIDA`. Não há etapa de aprovação. Só é possível adicionar ou remover item
enquanto a venda está `ABERTA`.

---

## 2. Escopo

### Faz (estado atual)

- Carrinho de venda multi-categoria (Produto Vivo, Serviço Vivo, Acessório, Recarga) com
  cálculo automático de valor total a partir dos itens
- Cadastro de cliente (PF/PJ) com validação de CPF/CNPJ e e-mail/telefone
- Cadastro de filial e de usuário/vendedor (com senha do vendedor para finalizar a venda e mudar o status)
- Registro de pagamentos por venda, com múltiplas formas (cartão, PIX, Vivo Pay, dinheiro, boleto)
- Transição de status da venda com validação de máquina de estados
- Avaliação de procedência da venda (BKO) pós-conclusão
- Controle de estoque serializado por IMEI/serial, com status `DISPONIVEL` /
  `RESERVADO` / `VENDIDO`
- Alertas de estoque crítico (saldo disponível ≤ estoque mínimo do produto)
- Importação de relatório de inventário do SAP (IQ09) com conciliação automática
  (`CORRETO` / `DIVERGENCIA` / `PENDENTE`)
- Importação de tabela de preços da Telefônica (planilha multi-aba) com resolução de SKU
- Protocolo documental vinculado 1:1 à venda (GED, digitalização, remuneração, vencimento)
- Campanhas de mailing via WhatsApp Business API: criação, importação de planilha de
  destinatários, disparo assíncrono via fila (RabbitMQ)
- Painel executivo (dashboard) com faturamento do mês, meta, ticket médio, SKUs
  críticos, evolução de vendas dos últimos 6 meses e atividades recentes

### Não faz (ainda)

- Autenticação/autorização real (JWT e Spring Security já estão no `pom.xml`, mas o
  filtro de segurança libera todas as rotas — ver [Segurança](#10-segurança))
- Filtro de regional/filial/período/categoria no dashboard (o front já tem os campos na
  tela, mas não manda nada no `fetch`, e o backend ignora regional/período/categoria)
- Persistência de auditoria (Hibernate Envers está no `pom.xml`, sem uso identificado)
- Geração de fatura/DANFE em PDF, exportação para Excel/CSV (openhtmltopdf e Apache POI
  estão no `pom.xml`, sem controller que os exponha)
- Reprocessamento/retry configurado para falha de webhook (spring-retry no `pom.xml`, sem
  uso identificado)
- Notificação de devolução, painel de estatísticas de estoque, app mobile

Isso tudo está listado como dependência presente mas não conectada — não é invenção,
é o que o `pom.xml` já declara. Antes de qualquer uma dessas linhas virar funcionalidade,
vale confirmar se a dependência ainda é necessária ou se pode sair.

---

## 3. Regras de negócio

### Cliente

- CPF/CNPJ é único (`uk_cliente_cpf_cnpj`) e imutável após o cadastro (`updatable = false`).
- Documento validado por `ValidadorCpfCnpj` (Caelum Stella) — documento inválido lança
  `DocumentoInvalidoException`.
- Documento duplicado lança `ClienteDuplicadoException`.

### Venda

- Só é possível adicionar/remover item enquanto `status == ABERTA`. Fora disso,
  `CarrinhoNaoEditavelException`.
- `valorTotal` nunca é setado diretamente pela API — é sempre recalculado a partir dos itens, dos chips
  (`valorChip`) e da condição de pagamento (`VendaService.reprecificar`).
- Toda venda precisa de ao menos um item para ser finalizada — senão,
  `VendaSemItemException`.
- Finalizar reprecifica a venda pela tabela vigente. Se o total mudou, o novo total fica gravado e a resposta é
  422 com os dois valores: o vendedor confere os pagamentos e finaliza de novo.
- A venda é montada sem senha. Abrir (`POST /vendas`) e editar (`PUT /vendas/{id}`) usam o usuário do JWT como
  vendedor provisório; uma autenticação enviada no corpo é ignorada. `PUT` só com a venda `ABERTA`; fora disso, 422.
- `PATCH /vendas/{id}/finalizar` exige `autenticacaoVendedor` e **lança a venda**: vai direto para `CONCLUIDA`, os
  seriais passam de `RESERVADO` a `VENDIDO` na mesma transação, quem se autentica passa a ser o vendedor da venda, e
  ficam gravados `finalizado_por_id` e `finalizado_em`. A ordem é autenticação → itens → Renova com número →
  reprecificação/total → pagamentos = total. Senha errada é 401 sem alterar nada (venda `ABERTA`, seriais
  `RESERVADO`); no 422 de total alterado, o vendedor continua o provisório.
- `PATCH /vendas/{id}/status` exige `autenticacaoUsuario` e grava `status_alterado_por_id` e `status_alterado_em`,
  sem trocar o vendedor. Serve para cancelar: `CANCELADA` devolve os seriais a `DISPONIVEL`. Pedir `PENDENTE` ou
  `APROVADA` é 422 ("Status não usado: a venda é lançada direto ao finalizar."), e pedir `CONCLUIDA` também é 422:
  lançar é só pelo finalizar. Procedência e expiração do carrinho não têm autenticação e não preenchem esses campos.
- Usuário inativo pela flag `ativo`, pelo cargo ou pela situação `INATIVO` não se autentica na venda.
- `SERVICO_VIVO` fica sempre com valor 0: o `valorUnitario` enviado é ignorado. O chip é cobrado só por `valorChip`.
- Transições de status permitidas, validadas por `StatusVenda.podeTransicionarPara()`:

```
ABERTA    → CONCLUIDA   (só pelo finalizar, com a senha do vendedor)
ABERTA    → CANCELADA   (carrinho expirado ou cancelado)
CONCLUIDA → CANCELADA   (cancelamento com senha, só no dia do lançamento; devolve o estoque. Improcedente no BKO não cancela)
CANCELADA → (terminal, sem saída)
```

  `PENDENTE` e `APROVADA` ficam no enum e no CHECK só por compatibilidade: nenhuma venda entra nesses status, e a
  V30 passou a `CONCLUIDA` as que estavam neles, baixando os seriais reservados.

  Transição fora dessa tabela lança `TransicaoStatusInvalidaException`.
- `numeroVenda` é um número público de 7 dígitos gerado por sequence de banco na
  abertura do carrinho — nunca calculado no front.

### Estoque

- Todo item de estoque nasce com status `DISPONIVEL` e depósito `DP01` (`@PrePersist`).
- Cada serial/IMEI é único no sistema (`idx_serial_unico`).
- `ItemEstoque` usa `@Version` (lock otimista) — duas baixas concorrentes no mesmo
  serial não silenciam uma a outra.
- Produto é considerado crítico quando `saldo disponível ≤ estoqueMinimo` (padrão: 2,
  se não informado).

### Inventário por filial (IQ09 × sistema × bipagem, V36)

- O inventário é **por filial** e cruza três fontes pelo serial, de forma exata (sem IA):
  a **IQ09** do SAP, o **estoque do sistema** da filial e a **contagem física** (bipagem).
- **Filial × centro SAP:** `filiais.centro_sap` (UNIQUE) e `filiais.deposito_sap` (opcional). Carga por CSV
  `ADABAS;CENTRO_SAP[;DEPOSITO_SAP]` em `POST /filiais/centros-sap`. Filial sem centro → 422 "Cadastre o centro SAP
  da filial antes de abrir o inventário".
- **Abrir** (`POST /estoque/inventario`, multipart `filialId` + IQ09): só entram as linhas do centro (e do depósito,
  se cadastrado) da filial; as de outro centro e as sem serial são contadas no resumo, que lista também os centros
  do arquivo sem filial. **Uma execução aberta por filial** (outra → 409).
  - Esperado da IQ09 = `Status usuário` **ADEP ou vazio** (no estoque da loja). RLIB, AECL e RESE ficam fora
    (`FORA_ESTOQUE_SAP`, informativo).
  - Esperado do sistema = estoque da filial em DISPONIVEL, RESERVADO ou DEFEITO (`filial_estoque` = nome da filial).
- **Bipar** (`POST /{id}/bipagens`): leitor (Enter) ou câmera. `INSERT … ON CONFLICT` no UNIQUE
  (`execucao_id`, `serial_normalizado`): o mesmo serial só conta uma vez, mesmo com vários celulares; a segunda leitura
  volta com `jaBipado` ("Já bipado às HH:mm por X"). A contagem fica salva entre sessões.
- **Normalização do serial:** sem apóstrofo, aspas e espaços, maiúsculas, e sem zeros à esquerda quando tem 16+ dígitos.
- **Resultado de cada serial:**

  | Resultado | IQ09 da loja | Sistema da loja | Bipado |
  |---|---|---|---|
  | `CONFERIDO` | ✓ | ✓ (DISPONIVEL/RESERVADO/DEFEITO) | ✓ |
  | `PENDENTE` (durante a contagem) | ✓ ou sistema | | ✗ |
  | `FALTA` (fechado) | ✓ | | ✗ (justificado) |
  | `SO_NO_SISTEMA` (fechado) | ✗ | ✓ | ✗ (justificado) |
  | `SEM_ENTRADA_SISTEMA` | ✓ | ✗ | ✓ |
  | `SOBRA_FISICA` | ✗ | ✓ ou ✗ | ✓ |
  | `VENDIDO_EM_ESTOQUE` | ✓ | VENDIDO | ✓ ou ✗ |
  | `STATUS_SAP_DIVERGENTE` | status usuário fora do estoque da loja | | ✓ |
  | `FORA_ESTOQUE_SAP` | status usuário fora do estoque da loja | | ✗ |

- **Fechar** (`POST /{id}/fechar`): só com todos os pendentes bipados ou justificados (senão 422). Pendente
  justificado vira `FALTA` (estava na IQ09) ou `SO_NO_SISTEMA` (só no sistema). Também há `cancelar`, desfazer
  bipagem, justificativa e exportação CSV com todas as linhas.
- **Diagnóstico pela API do Claude** (Messages API, saída JSON estruturada): roda ao fechar e pode ser refeito em
  `POST /{id}/diagnostico`. Vão as faltas e divergências, em lotes de até 100, com contexto de produto e estoque
  (status SAP, filial no sistema, outros centros da mesma IQ09, última venda só com número/data/status). **Nunca**
  vai dado de cliente. A IA só sugere a causa provável (lista fechada + "outra"), a confiança e a ação; não decide
  se um IMEI bate. Resposta inválida → nova tentativa, depois 502. Falha ao fechar não desfaz o fechamento.
- **Configuração:** `ANTHROPIC_API_KEY` (no `.env`, fora do git), `SYSCOR_IA_MODELO` (padrão `claude-opus-5-5`),
  `SYSCOR_IA_TIMEOUT` (120s), `SYSCOR_IA_LOTE` (100). Sem chave, o inventário funciona e mostra
  "Diagnóstico indisponível: configure a chave da API".
- O parser da IQ09 lê o cabeçalho do export real (`Nº de série`, `Texto breve material`, `Status do sistema`,
  `Status usuário`, `Ctg.equipamento`…) com normalização NFKD, lê `Modificado em` como data do Excel e recusa
  seriais em notação científica (`2.20181E+14`). Execuções antigas (V5) continuam legíveis.

### Tabela de preços

- Cada upload da planilha da Telefônica vira uma `TabelaPrecoExecucao`; a mais recente é a
  **vigente**. A importação é toda numa transação (se falhar, a vigente continua a anterior).
- O parser localiza abas, blocos (`TABELA REGULAR`/`TABELA RENOVA`) e colunas pelo texto do
  cabeçalho e grava, por execução: `tb_preco_item` (Nome Comercial × tabela × grupo do plano:
  preço cheio, PIX e 13x–21x), `tb_plano_vivo` (aba PLANOS) e `tb_produto_vivo` (aba PRODUTOS).
- O preço do item na venda é calculado no backend (`PrecoVigenteService`) por produto + grupo
  do plano + tabela + forma de pagamento; o `valorUnitario` do front é ignorado para aparelho e
  acessório. Sem preço na vigente → 422.
- `valor_venda` do estoque é a referência `REGULAR`/`PRE` cheia, atualizada no upload e ao
  importar estoque novo (só `DISPONIVEL`/`RESERVADO`; `VENDIDO` não muda).
- **`tb_produto_preco_plano` deixou de ser usada na V24 e será removida numa migration futura.**

### Acessório pelo IMEI/serial (V35)

- Na aba Acessório, o item entra pela leitura do serial, como o aparelho: `POST /vendas/{id}/itens` com
  `{ "categoria": "ACESSORIO", "imeiOuSerial": "...", "quantidade": 1 }`. O `produtoId` é opcional quando vem o serial.
- No estoque, o acessório tem **um serial por SKU** com quantidade N. Cada leitura prende **1 unidade**
  (`quantidade_reservada`); duas capinhas iguais são duas leituras. A linha só vira `RESERVADO` quando todas as
  unidades estão em carrinhos e só vira `VENDIDO` quando a quantidade zera.
- Concluir a venda tira a unidade da linha; remover o item ou cancelar o carrinho solta a reserva; cancelar a venda
  concluída devolve a unidade. A importação do relatório não deixa a quantidade abaixo das unidades reservadas.
- IMEI de aparelho na aba Acessório (ou serial de acessório na aba Produto Vivo) → 422. Serial sem unidade livre,
  preso no carrinho de outra venda → 409 "Serial já está reservado na venda #id".
- Sem serial, o acessório ainda baixa a linha inteira (fallback do modelo antigo).
- Também é acessório o item **sem** `codigo_acessorio` das categorias Essenciais, Acessórios, Áudio, Casa Inteligente,
  Bem Estar, Vestuário, Games e Demais Eletrônicos que **não tem IMEI** (capa, cabo, fone, Echo com serial SAP da
  unidade). Relógio LTE e tablet 5G têm IMEI e continuam na aba Produto Vivo, assim como Smartphone, Notebook e Tablets,
  TV e Box e Modem e FWT. `/estoque-geral/disponiveis` traz `acessorio: true/false` com essa regra.

### Renova (V25, V28)

- Todo item entra pela **TABELA REGULAR**. O aparelho (PRODUTO_VIVO com preço por plano) passa para a
  **TABELA RENOVA** com o número do voucher:
  `PATCH /vendas/{id}/renova { "ativo": true, "codigoVoucher": "ABC123" }`.
- O voucher **não é forma de pagamento** e não tem valor na venda: `POST /vendas/{id}/pagamentos` com `VOUCHER` é
  recusado (422) e nenhuma condição aceita essa forma. Ela fica só para o histórico das vendas antigas.
- Ligar exige o número (até 60 caracteres, sem espaços nas pontas) e ao menos um aparelho elegível; senão, 422.
  O mesmo número em outra venda não cancelada, inclusive em linha `VOUCHER` antiga, é 409 "Voucher já usado na
  venda #id". Finalizar venda com Renova sem o número é 422.
- `{ "ativo": false }` limpa o número e os aparelhos voltam para a Regular. Acessório, chip, serviço e itens
  `SEM_PLANO` não mudam.
- O bloco Renova não tem o grupo PRÉ: aparelho em plano pré-pago/sem plano → 422 "Renova não
  disponível para plano pré-pago.". Produto sem linha Renova fica no preço Regular do mesmo grupo.
- A coluna `DESCONTO` do bloco Renova é gravada em `tb_preco_item.desconto_fabricante`. O preço Renova
  **já tem** esse desconto: o `descontoFabricante` da venda (soma dos aparelhos na Renova) é só
  controle/conciliação e não abate o total de novo.
- Ligar ou desligar o Renova não apaga pagamentos: se passarem do novo total, a resposta traz `saldo`
  negativo e `aviso`, e a venda só finaliza depois do ajuste.
- A V28 leva o código da primeira linha `VOUCHER` para `vendas.codigo_voucher` nas vendas abertas com Renova ligado
  e apaga essa forma das vendas abertas. O total delas é acertado na próxima edição ou ao finalizar. Vendas
  finalizadas não mudam.

### Condição de pagamento (V26)

- A venda tem `condicaoPagamento`: `PIX` (PIX e Vivo Pay), `A_VISTA` (crédito à vista, débito, dinheiro e
  2x a 12x) ou `PARCELADO_13` a `PARCELADO_21`. O padrão é `A_VISTA`.
- `PATCH /vendas/{id}/condicao-pagamento { "condicao": "PIX" }` reprecifica aparelhos e acessórios com preço
  de tabela e recalcula o total (o nome antigo `condicaoPagamento` continua aceito). Chip, serviço e recarga
  não mudam. Se os pagamentos passarem do novo total, a resposta traz `saldo` negativo e `aviso`.
- Item novo segue a condição da venda. `formaPagamento` e `parcelas` enviados no item são ignorados.
- A planilha prevalece. O que faltar vem da fórmula: PIX = 90% do preço cheio; 13x–21x = preço cheio × fator;
  parcela = total ÷ n. Na Renova, PIX e 13x–21x saem do preço Regular menos o `DESCONTO`.
- O upload devolve `divergenciasCondicao` para PIX e 13x–21x com diferença acima de R$ 0,01 entre planilha e
  fórmula. `GET /produtos/precos/item` devolve `opcoes` com as 22 opções de pagamento.

### Pagamento por condição (V27)

- Linhas aceitas: `PIX` só `PIX` e `VIVO_PAY`. `A_VISTA` todas as formas menos `VOUCHER`, com crédito até 12x; PIX
  misturado com cartão paga o preço cheio. `PARCELADO_N` só entradas (`DINHEIRO`, `PIX`, `VIVO_PAY`,
  `CARTAO_DEBITO`) mais a linha `FINANCIADO`.
- Cada linha tem `tipo`: `PAGAMENTO`, `ENTRADA` ou `FINANCIADO`. `POST /vendas/{id}/financiar` cria a
  `FINANCIADO` (`CARTAO_CREDITO` em N parcelas, valor calculado pelo backend). `POST /pagamentos` recusa crédito
  em `PARCELADO_N`.
- Total com entrada E: `E + round((baseTabela − E) × fator[N], 2) − descontoFabricante + semTabela`. Sem entrada,
  vale a soma dos `parcelas[N]` da planilha. `baseTabela` é o preço Regular dos itens de tabela; `semTabela` são
  chip, serviço, recarga e item sem preço de tabela, sempre pelo valor cheio, sem juros nem desconto PIX.
- E fica limitada a `baseTabela`: o que passar disso abate os itens sem tabela. Entrada acima do total é recusada.
- No Renova em 21x com entrada de R$ 500, o total é R$ 12.019,41, não os R$ 12.081,12 da tabela.
- Toda mudança em item, Renova, condição ou entrada reprecifica a venda pela tabela vigente e recalcula a linha
  `FINANCIADO`. Ao trocar de parcelado ela acompanha N; ao sair dele, ou sem saldo, é apagada.
- O item fica no preço da condição sem entrada. Com entrada, a soma dos itens difere de `valorTotal`.
- `valorChip` soma em `valorTotal`, mas não em `valorTotalItem`.
- `valorItens` é o preço de tabela sem juros nem desconto PIX (na Renova, o preço cheio Renova), mais os itens
  sem tabela. `ajusteCondicao` = `valorTotal − valorItens`. Remuneração e comissão não mudaram nesta tarefa.
- `valorParcela`: financiado ÷ N no parcelado; total ÷ 12 em `A_VISTA`; o total em `PIX`.
- Caixa: `VIVO_PAY` é forma própria; o caixa abre com as seis formas (dinheiro, PIX, Vivo Pay, crédito, débito e
  boleto). A linha `FINANCIADO` entra como `CARTAO_CREDITO`.

### Protocolo documental

- Vínculo 1:1 com a venda (`uk` em `venda_id`) — uma venda tem no máximo um protocolo.
- `situacaoServico` (`CONFIRMADO`/`CANCELADO`) e `gerarComissao` (default `true`) ficam
  registrados por venda, independente do status da venda em si.
- Só venda lançada (`CONCLUIDA`) recebe protocolo; salvar em venda `ABERTA` ou `CANCELADA` é 422.
- Toda venda lançada nasce **Não avaliado** no BKO (o finalizar grava `Nao_avaliado`; a V31 marcou as antigas).
  A fila do BKO é `GET /documental/protocolos/busca?statusBko=Nao_avaliado`, e `statusBko` nunca vem nulo.
- A busca e a exportação só trazem vendas com pelo menos um item de serviço (número de acesso e serviço
  preenchidos); aparelho avulso e acessório ficam de fora. O período é o do lançamento.
- Tipo de plano: `PRE` → PRE; `CONTROLE_*` → CONTROLE; `POS_INDIVIDUAL`, `FAMILIA_*`, `VIVO_V`, `MULTIVIVO`
  e `VIM_*` → POS; sem grupo de preço, vale o segmento ("Pré", "Controle", "Pós").
- Importação em lote por CSV (`NUMERO_VENDA;PROTOCOLO_GED;STATUS_BKO;NUMERO_ACESSO;DATA_DIGITALIZACAO`): uma
  transação por linha, então uma linha com erro não desfaz as outras.
- Exportação do Relatório de Gestão Documental (`gestao_documental.csv`, `;`, Windows-1252, LF) no layout do
  sistema atual, período de até 93 dias. Lê a view `vw_gestao_documental` (V34): preâmbulo com emissão e revenda
  (`syscor.revenda.nome`), cabeçalho de **55 colunas**, **uma linha por serviço** (aparelho e acessório como
  "Troca de Aparelho") e rodapé. Datas sem hora (menos "Data de alteração"), valores com ponto, Sim/Não e as
  colunas de número-texto entre apóstrofos. Faturas, Valor Receita e TFP ainda saem vazias.
- V34: o item de venda ganhou tipo de linha, valor do plano e do plano antigo, nº portado, fidelização e COD; o
  protocolo ganhou status GED, motivo de reprovação, instalação (feita, agendada, armário), comissão de aparelho
  e data da última ativação.
- O protocolo ganhou (V32) ativação da primeira chamada, datas de ativação, baixa e instalação, GED da
  portabilidade e o usuário que salvou por último (`alteradoPorNome` na resposta).

### Relatórios Venda de Produtos, Venda de Serviços e Vendas (V37)

- A venda lançada aparece assim que é finalizada. **Produtos**: item `PRODUTO_VIVO`, `ACESSORIO` ou `RECARGA`;
  **Serviços**: `SERVICO_VIVO` ou item com tipo de serviço e nº de acesso; **Vendas**: uma linha por venda. Venda
  mista entra nos dois primeiros. A cancelada depois de lançada sai de Produtos/Serviços e fica em Vendas como
  "Cancelada"; carrinho cancelado não é venda.
- Views `vw_relatorio_itens` e `vw_relatorio_vendas` (só leitura). Tela com filtros (filiais, período, "Considerar"
  Data de Venda/Ativação, vendedor, cliente, status), **Resumo Analítico** ou **Sintético**, campos escolhidos e
  exportação Excel (`.xlsx`, até 93 dias). Produtos agrupa por Tipo de Produto ou Categoria, com subtotal de
  quantidade, venda, custo e lucro. Serviços filtra por Segmento Comercial (`SegmentoComercial`).
- Só entram campos com origem no banco: os fiscais (ICMS, IPI, PIS, Cofins, chave de acesso), voucher com valor,
  multiplicador, devolução e saúde da bateria ficam fora até haver dado. Produto Open não tem como ser distinguido.

### Caixa diário obrigatório (V38)

- **Sem caixa aberto hoje (America/Sao_Paulo) a filial não vende:** criar carrinho, adicionar item e lançar a venda
  respondem 409 `CAIXA_NAO_ABERTO`, para todos, admin inclusive. Caixa de dia anterior ainda aberto só gera aviso
  (`syscor.caixa.bloquear-com-caixa-anterior-aberto=true` passa a bloquear). A venda lançada grava
  `vendas.caixa_sessao_id`; as anteriores à V38 ficam sem caixa.
- **Perfis** (`syscor.caixa.*`, comparados pelo cargo sem acento): gerente de vendas (Júnior/Pleno/Sênior) abre e fecha só o
  caixa da própria filial; **auditoria de caixa** abre e fecha em qualquer filial; **conferir e reabrir** = auditoria e
  financeiro; o admin (`syscor.caixa.admins`, por login) faz tudo. Quem fechou o caixa não o confere (o admin é exceção).
  Perfil sem poder → 403 `CAIXA_SEM_PERMISSAO`; cada gerente só enxerga os caixas da própria filial.
- **Abrir** (`POST /financeiro/caixa/abrir`): a data é sempre a de hoje; um caixa por dia por nome de caixa da filial
  (409 `CAIXA_JA_ABERTO`); abre as seis formas: o **valor em dinheiro é obrigatório** (0 vale, mas tem de ser informado) e as outras formas não informadas abrem com 0. O dinheiro sugerido é o contabilizado do último
  caixa fechado desse caixa (`GET /sugestao-abertura`); abrir com outro valor exige `justificativaAbertura` (mín. 10).
- **Cálculo por sessão:** entradas = pagamentos das vendas `CONCLUIDA` lançadas no caixa (cancelada sai da soma).
  Dinheiro fica na gaveta: esperado = abertura + movimento + entradas − saídas. PIX, Vivo Pay, cartões e boleto
  liquidam na conciliação (saídas = entradas, contabilizado 0). `VOUCHER` não abre linha.
- **Conferência por forma (V39):** o gerente digita o valor de cada forma exigida (o dinheiro sempre; as demais se tiverem abertura,
  movimento ou entradas). O esperado de todas as formas é abertura + movimento + entradas − saídas; em PIX, Vivo Pay, cartões e boleto
  o digitado é o total recebido, conferido contra o extrato ou a maquininha. O que fica no caixa (total contabilizado) é só o dinheiro.
  O caixa só fecha com **diferença zero**: o gerente da loja é recusado (422 `CAIXA_COM_DIFERENCA`, só as formas que divergem, sem
  valores) e a recusa é gravada, no máximo 5 por caixa por dia; na 6ª, 423 `CAIXA_FECHAMENTO_BLOQUEADO` ("chame o financeiro"). Financeiro,
  gerente de área, auditoria e admin fecham com diferença informando `justificativa` (mín. 10): o caixa fica `AGUARDANDO` e marcado como
  divergente. A observação do fechamento é opcional. Com carrinho aberto da filial no dia, 409 `CAIXA_COM_CARRINHO_ABERTO` (só no último
  caixa aberto do dia). **Conferir** (`AGUARDANDO` → `CONFERIDO`) e **reabrir** (justificativa de no mínimo 10; volta a `ABERTO`, zera a
  conferência e a divergência, mas não as tentativas do dia) ficam em `tb_caixa_historico`. Cancelar venda já lançada exige o caixa dela
  `ABERTO` (409 `CAIXA_FECHADO`).
- **Fechamento cego:** com o caixa aberto, só quem vê o esperado (financeiro, gerente de área, auditoria e admin) recebe `movimento`,
  `entradas`, `saidas` e o esperado; o gerente da loja recebe `null` em todas as formas (somados, dariam o esperado) e o movimento
  detalhado do caixa aberto lhe dá 403. A abertura ele mesmo informou. Depois de fechado todos veem. Os valores digitados em cada recusa
  (`tentativasFechamento`) só aparecem a quem confere ou pode fechar com divergência. As telas de venda ainda mostram os pagamentos: o
  fechamento cego impede ler o esperado pelo caixa, não somar as vendas à mão.
- Filial com mais de um caixa aberto no dia: o `finalizar` recebe `caixaSessaoId` (sem ele, 422).

### Mailing (WhatsApp)

- Campanha nasce `RASCUNHO`, muda pra `ENVIANDO` ao disparar, e fecha em `CONCLUIDA` ou
  `FALHOU` conforme o resultado dos envios.
- Cada destinatário é processado de forma assíncrona por um listener RabbitMQ
  (`MailingWhatsAppListener`) — o disparo da campanha só enfileira, quem efetivamente
  chama a API do WhatsApp é o listener, um destinatário por vez.
- Envio sem `WHATSAPP_PHONE_NUMBER_ID`/`WHATSAPP_ACCESS_TOKEN` configurados lança
  `NegocioException` antes de tentar a chamada HTTP.

### Dashboard

- Faturamento e volume de vendas do mês consideram apenas vendas `CONCLUIDA`.
- Meta da operação é **fixa em R$ 85.000,00** no código — não vem de configuração nem de
  tabela (ver [Backlog](#13-backlog--pendências-conhecidas)).
- Evolução de vendas cobre sempre os últimos 6 meses corridos a partir do mês atual,
  arredondado em milhares (`"48k"`).
- Atividades recentes são as 5 últimas vendas (qualquer status), mais recente primeiro.

---

## 4. Decisões de arquitetura

**ADR-01 — Módulo por domínio, não por camada técnica global**

O código é organizado por módulo de negócio (`venda`, `estoque`, `gestao`, `mailing`,
`dashboard`), e dentro de cada módulo por camada técnica (`controller` / `service` /
`repository` / `model` / `dto` / `enums`). Isso evita um pacote `controller` gigante com
40 classes sem relação entre si, e deixa claro qual módulo é dono de qual regra.

**ADR-02 — Entity separada de DTO, mapeamento manual**

`Entity` é o espelho do banco (JPA). `DTO` é o contrato da API. Misturar os dois vaza
campos sensíveis (ex.: `senhaHash` de `Usuario`) e quebra com `LazyInitializationException`
em coleções `@OneToMany` fora de transação. O mapeamento é feito via método estático
`from()` dentro do próprio `ResponseDTO` (ex.: `VendaResponseDTO.from(venda, pagamentos)`)
— sem MapStruct nos DTOs de resposta, para manter o mapeamento explícito e fácil de
depurar quando um campo não bate com o front.

**ADR-03 — Máquina de estados explícita no enum, não em `if/else` no Service**

`StatusVenda` sabe suas próprias transições permitidas (`transicoesPermitidas()`). O
`Service` só pergunta `podeTransicionarPara()` — a regra de workflow fica em um único
lugar, testável isoladamente, em vez de espalhada em vários métodos de serviço.

**ADR-04 — Erros de domínio como exceções próprias + `@RestControllerAdvice`**

Cada situação de erro tem uma exceção própria (`VendaSemItemException`,
`CarrinhoNaoEditavelException`, `ProdutoDuplicadoException` etc.), a maioria estendendo
`BusinessException`. O `GlobalExceptionHandler` traduz `BusinessException` para 422 e
`RecursoNaoEncontradoException` para 404, sem stack trace vazando pro cliente.

> Atenção ao usar este padrão: `ClienteDuplicadoException`, `DocumentoInvalidoException`
> e `TransicaoStatusInvalidaException` hoje estendem `RuntimeException` diretamente, não
> `BusinessException` — não são capturadas por nenhum handler específico e caem no 500
> padrão do Spring em vez de um 422/409 com corpo padronizado. Ver
> [Backlog](#13-backlog--pendências-conhecidas).

**ADR-05 — Importação assíncrona/desacoplada via mensageria (mailing)**

O disparo de uma campanha não chama a API do WhatsApp de forma síncrona no request HTTP
— ele publica uma mensagem por destinatário na fila e devolve. Um listener processa um
de cada vez. Isso evita que uma campanha de 5 mil destinatários trave a thread HTTP ou
estoure o rate limit da API do WhatsApp (`rate-limit-ms` configurado).

**ADR-06 — `context-path` único para versionamento de API**

O prefixo de rota (`/api`) fica centralizado em `server.servlet.context-path`
(`application.yaml`), não repetido em cada `@RequestMapping`. Trocar ou remover a versão
da API é uma linha de configuração, não uma varredura em N controllers.

**ADR-07 — `@Version` (lock otimista) em recursos de alta concorrência**

`ItemEstoque` é o recurso mais disputado do sistema — várias vendas podem tentar baixar
o mesmo serial ao mesmo tempo. Lock otimista via `@Version` garante que a segunda
tentativa concorrente falhe em vez de silenciosamente sobrescrever a primeira.

---

## 5. Stack

| Camada | Escolha | Observação |
|---|---|---|
| Linguagem | Java 21 | LTS atual |
| Framework | Spring Boot 3.3.4 | Base do projeto (artifact `syscor`) |
| Banco | PostgreSQL | Via `spring-boot-starter-data-jpa` + driver `postgresql` |
| Migração de schema | Flyway | `db/migration`, `ddl-auto: validate` — schema é sempre do Flyway, nunca do Hibernate |
| Mensageria | RabbitMQ (`spring-boot-starter-amqp`) | Fila de disparo de mailing WhatsApp |
| Jobs | Quartz (`spring-boot-starter-quartz`) | Dependência presente |
| Segurança | Spring Security + JWT (`jjwt`) | Dependências presentes, filtro ainda libera tudo — ver [Segurança](#10-segurança) |
| HTTP reativo | WebFlux (`WebClient`) | Usado no `WhatsAppApiClient` para chamar a Graph API |
| Auditoria | Hibernate Envers | Dependência presente, sem uso identificado |
| Armazenamento de arquivo | AWS S3 SDK | Dependência presente, sem uso identificado |
| Excel/CSV | Apache POI | Dependência presente, sem controller identificado |
| PDF | openhtmltopdf | Dependência presente, sem controller identificado |
| Validação de documento | Caelum Stella | CPF/CNPJ (`ValidadorCpfCnpj`) |
| Retry | Spring Retry | Dependência presente, sem uso identificado |
| Mapeamento | MapStruct + Lombok | Lombok em todas as entidades; mapeamento de resposta é manual (ADR-02) |
| Documentação | springdoc-openapi | Swagger UI automático |
| Build | Maven (wrapper `mvnw`) | — |
| Testes | JUnit 5 + Mockito + Testcontainers (PostgreSQL, RabbitMQ) | — |
| Containers | Docker + Docker Compose | `syscor-app` + `syscor-db` + `syscor-rabbitmq` |

---

## 6. Arquitetura

### Camadas (dentro de cada módulo)

```
Controller  ──►  Service  ──►  Repository  ──►  PostgreSQL
  (HTTP)         (regra)          (SQL)
```

Regra de dependência: a seta só aponta pra direita. O `Service` não conhece
`HttpServletRequest`. O `Repository` não conhece regra de negócio. O `Controller` não
toma decisão — só traduz HTTP para chamada de Service e vice-versa. Toda exceção de
domínio nasce no `Service` e é traduzida pelo `GlobalExceptionHandler`.

### Fluxo de venda → estoque → documental

```
Front (syscor-frontend)         API Spring Boot                       Banco
      |                               |                                  |
      |-- POST /api/vendas ---------->|                                  |
      |   (abre carrinho ABERTA)      |-- INSERT vendas ---------------->|
      |<-- 201 VendaResponseDTO ------|                                  |
      |                               |                                  |
      |-- POST /api/vendas/{id}/itens>|-- valida status == ABERTA        |
      |   (repetido por item)         |-- INSERT itens_venda ----------->|
      |                               |-- recalcula valorTotal           |
      |                               |                                  |
      |-- PATCH .../finalizar ------->|-- autentica o vendedor           |
      |   (com vendedor e senha)      |-- valida itens, Renova, total    |
      |                               |-- e pagamentos                   |
      |                               |-- UPDATE status = CONCLUIDA,     |
      |                               |   RESERVADO->VENDIDO e vendedor  |
      |                               |   autenticado ------------------>|
      |                               |                                  |
      |-- PATCH .../status ---------->|-- autentica quem alterou         |
      |   (cancelar)                  |-- (CANCELADA: volta a DISPONIVEL)|
      |                               |                                  |
      |-- PUT /api/documental/        |                                  |
      |   protocolos/{vendaId} ------>|-- INSERT tb_protocolo_documental->|
      |                               |                                  |
      |-- GET /api/dashboard/         |-- soma vendas CONCLUIDA no mês   |
      |   executivo ----------------->|-- conta SKUs críticos ---------->|
      |<-- DashboardExecutivoDTO -----|                                  |
```

### Fluxo de mailing (assíncrono)

```
Front                    API Spring Boot            RabbitMQ           WhatsApp Graph API
  |                            |                        |                     |
  |-- POST .../disparar ------>|                        |                     |
  |                            |-- status = ENVIANDO     |                    |
  |                            |-- 1 msg por destinatário|                    |
  |                            |   PUBLISH -------------->|                   |
  |<-- 200 CampanhaResponseDTO-|                        |                     |
  |                            |                        |-- CONSUME ----------|
  |                            |                        |   (1 por vez)       |
  |                            |                        |-- enviarTemplate -->|
  |                            |<-- status por destinatário (ENVIADO/FALHA) --|
```

---

## 7. Estrutura de pastas

```
GestaoEmpresarial4R/
├── src/
│   ├── main/
│   │   ├── java/com/vivo4redes/syscor/
│   │   │   ├── config/
│   │   │   │   └── SegurancaVendasConfig.java     # Spring Security — hoje permitAll()
│   │   │   ├── dashboard/
│   │   │   │   ├── controller/DashboardController.java
│   │   │   │   ├── dto/                            # DashboardExecutivoDTO, EvolucaoMensalDTO, AtividadeRecenteDTO
│   │   │   │   └── service/DashboardService.java
│   │   │   ├── estoque/
│   │   │   │   ├── controller/                     # EstoqueController, InventarioController, ProdutoController, TabelaPrecoController
│   │   │   │   ├── dto/{request,response}/
│   │   │   │   ├── enums/                          # StatusSerial, ResultadoConciliacaoInventario
│   │   │   │   ├── model/                           # Produto, ItemEstoque, InventarioExecucao, InventarioItem, TabelaPrecoExecucao, ProdutoPrecoPlano
│   │   │   │   ├── repository/
│   │   │   │   └── service/
│   │   │   ├── gestao/
│   │   │   │   ├── controller/ProtocoloDocumentalController.java
│   │   │   │   ├── dto/{request,response}/
│   │   │   │   ├── enums/SituacaoServico.java
│   │   │   │   ├── model/ProtocoloDocumental.java
│   │   │   │   ├── repository/
│   │   │   │   └── service/
│   │   │   ├── mailing/
│   │   │   │   ├── client/WhatsAppApiClient.java   # WebClient para a Graph API
│   │   │   │   ├── config/                          # fila RabbitMQ, propriedades do WhatsApp
│   │   │   │   ├── controller/CampanhaMailingController.java
│   │   │   │   ├── dto/{request,response}/
│   │   │   │   ├── enums/                            # StatusCampanha, StatusEnvio
│   │   │   │   ├── listener/MailingWhatsAppListener.java
│   │   │   │   ├── model/                            # CampanhaMailing, DestinatarioMailing
│   │   │   │   ├── repository/
│   │   │   │   └── service/
│   │   │   ├── venda/
│   │   │   │   ├── controller/                       # VendaController, ClienteController, FilialController, UsuarioController
│   │   │   │   ├── dto/{request,response}/
│   │   │   │   ├── enums/                             # StatusVenda, CategoriaItemVenda, FormaPagamento, StatusScoreCliente, StatusAvaliacaoProcedencia, TipoPessoa
│   │   │   │   ├── model/                              # Venda, ItemVenda, PagamentoVenda, Cliente, Filial, Usuario
│   │   │   │   ├── repository/
│   │   │   │   └── service/
│   │   │   ├── exception/                              # exceções de domínio + GlobalExceptionHandler
│   │   │   └── util/ValidadorCpfCnpj.java
│   │   └── resources/
│   │       ├── application.yaml            # perfil default, context-path, Flyway, JPA
│   │       ├── application-dev.yaml        # datasource local, RabbitMQ local
│   │       ├── application-test.yaml       # perfil de teste (Testcontainers)
│   │       └── db/migration/               # V1..V9, um arquivo por schema/evolução
│   └── test/java/com/vivo4redes/syscor/    # testes unitários e de integração
├── Dockerfile                              # build multi-stage (Maven → JRE Alpine)
├── Docker-compose.yml                      # syscor-app + syscor-db + syscor-rabbitmq
└── pom.xml
```

---

## 8. Modelo de dados

Resumo das tabelas centrais (schema completo em `db/migration/V1..V9`):

```sql
-- V1: núcleo de venda
CREATE TABLE clientes (
    id             BIGSERIAL PRIMARY KEY,
    tipo_pessoa    VARCHAR(20)  NOT NULL,
    nome           VARCHAR(150) NOT NULL,
    cpf_cnpj       VARCHAR(14)  NOT NULL UNIQUE,
    email          VARCHAR(150),
    telefone       VARCHAR(20),
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE vendas (
    id                                    BIGSERIAL PRIMARY KEY,
    numero_venda                          BIGINT UNIQUE,              -- via sequence, V3
    cliente_id                            BIGINT NOT NULL REFERENCES clientes (id),
    filial_id                             BIGINT NOT NULL REFERENCES filiais (id),     -- V2
    usuario_id                            BIGINT NOT NULL REFERENCES usuarios (id),    -- V2
    finalizado_por_id                     BIGINT REFERENCES usuarios (id),             -- V29
    finalizado_em                         TIMESTAMP,                                   -- V29
    status_alterado_por_id                BIGINT REFERENCES usuarios (id),             -- V29
    status_alterado_em                    TIMESTAMP,                                   -- V29
    valor_total                           NUMERIC(12,2) NOT NULL DEFAULT 0,
    status                                VARCHAR(20) NOT NULL DEFAULT 'ABERTA'
        CHECK (status IN ('ABERTA','PENDENTE','APROVADA','CONCLUIDA','CANCELADA')),
    avaliacao_procedencia                 VARCHAR(20),
    estoque_avancado                      BOOLEAN NOT NULL DEFAULT FALSE,              -- V2
    status_score_cliente                  VARCHAR(20) NOT NULL DEFAULT 'NAO_REALIZADA',-- V2
    criado_em                             TIMESTAMP NOT NULL DEFAULT now(),
    atualizado_em                         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE itens_venda (
    id                 BIGSERIAL PRIMARY KEY,
    venda_id           BIGINT NOT NULL REFERENCES vendas (id) ON DELETE CASCADE,
    categoria          VARCHAR(20) NOT NULL
        CHECK (categoria IN ('PRODUTO_VIVO','SERVICO_VIVO','RECARGA')),  -- ver nota abaixo
    produto_id         BIGINT NOT NULL,
    descricao_produto  VARCHAR(150) NOT NULL,
    quantidade         NUMERIC(12,3) NOT NULL CHECK (quantidade > 0),
    valor_unitario     NUMERIC(12,2) NOT NULL CHECK (valor_unitario > 0),
    item_estoque_id    BIGINT REFERENCES tb_item_estoque (id),          -- V4
    serial_imei        VARCHAR(50)                                      -- V4
);

-- V4: estoque serializado
CREATE TABLE tb_produto (
    id              BIGSERIAL PRIMARY KEY,
    sku             VARCHAR(50) NOT NULL UNIQUE,
    descricao       VARCHAR(150) NOT NULL,
    categoria       VARCHAR(30) NOT NULL,
    estoque_minimo  INTEGER NOT NULL DEFAULT 2,
    preco_base      NUMERIC(12,2) NOT NULL,
    requer_serial   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE tb_item_estoque (
    id            BIGSERIAL PRIMARY KEY,
    produto_id    BIGINT NOT NULL REFERENCES tb_produto (id),
    serial_imei   VARCHAR(50) NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'DISPONIVEL'
        CHECK (status IN ('DISPONIVEL','RESERVADO','VENDIDO')),
    deposito_sap  VARCHAR(10) DEFAULT 'DP01',
    version       BIGINT NOT NULL DEFAULT 0      -- lock otimista (ADR-07)
);

-- demais tabelas: filiais/usuarios (V2), inventário SAP (V5), tabela de preço (V6),
-- mailing WhatsApp (V7), campos telecom de item_venda/pagamentos (V8),
-- protocolo documental (V9)
```

**Notas de projeto:**

- `TIMESTAMP` sem timezone nas tabelas de V1 a V4 — diferente da recomendação de usar
  `TIMESTAMPTZ`, aqui o projeto optou por `TIMESTAMP WITHOUT TIME ZONE` + `time_zone:
  America/Sao_Paulo` fixado no `application.yaml` (`hibernate.jdbc.time_zone`). Funciona
  enquanto a aplicação rodar num único fuso — vale reavaliar se houver operação
  multi-região.
- **Divergência encontrada:** o `CHECK` de `itens_venda.categoria` (V1) não inclui
  `'ACESSORIO'`, mas o enum `CategoriaItemVenda` no código tem `PRODUTO_VIVO`,
  `SERVICO_VIVO`, `ACESSORIO`, `RECARGA`. Inserir um item `ACESSORIO` hoje quebra a
  constraint no banco, mesmo sendo um valor válido no enum Java.
- **Divergência encontrada:** o `CHECK` de `vendas.avaliacao_procedencia` (V1) aceita
  `'EM_AVALIACAO'`, `'PROCEDENTE'`, `'IMPROCEDENTE'`, mas o enum
  `StatusAvaliacaoProcedencia` usa `Em_avaliacao_pelo_BKO`, `Procedente`, `Improcedente`,
  `Nao_avaliado` — os literais não coincidem letra por letra (case e nome). Gravar o
  enum via JPA vai falhar contra essa constraint.
- `serial_imei` é `UNIQUE` em `tb_item_estoque` — a busca por serial precisa ser O(1).
- Índices parciais/compostos (`idx_tb_item_estoque_status` em `produto_id, status`)
  aceleram exatamente a query de "saldo disponível por produto" usada no cálculo de
  SKU crítico do dashboard.

---

## 9. API

Todas as rotas abaixo são relativas ao `context-path` configurado em
`application.yaml` (`/api`). Exemplo: `GET /dashboard/executivo` é servido em
`http://localhost:8080/api/dashboard/executivo`.

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| GET | `/dashboard/executivo` | Painel executivo (faturamento, meta, ticket médio, SKUs críticos, evolução 6M, atividades recentes) |
| POST | `/vendas` | Abrir carrinho de venda (`ABERTA`), sem senha; o vendedor provisório é o usuário do JWT |
| GET | `/vendas` | Listar todas as vendas |
| GET | `/vendas/{id}` | Buscar venda por id |
| PUT | `/vendas/{id}` | Atualizar dados iniciais (cliente, filial, score, nota), sem senha; só com a venda `ABERTA` (senão 422) |
| POST | `/vendas/{id}/itens` | Adicionar item ao carrinho (com `imeiOuSerial`, o `produtoId` é opcional; acessório entra 1 unidade por leitura; aba errada → 422; serial em outro carrinho → 409) |
| DELETE | `/vendas/{id}/itens/{itemId}` | Remover item do carrinho |
| POST | `/vendas/{id}/pagamentos` | Registrar pagamento (`VOUCHER` → 422) |
| DELETE | `/vendas/{id}/pagamentos/{pagamentoId}` | Remover pagamento |
| PATCH | `/vendas/{id}/renova` | Liga/desliga a TABELA RENOVA nos aparelhos (`{ ativo, codigoVoucher }`; ligar exige o número; número em outra venda → 409) |
| PATCH | `/vendas/{id}/condicao-pagamento` | Troca a condição (`{ condicao }`; aceita também `{ condicaoPagamento }`) e reprecifica a venda. 422 com `pagamentosIncompativeis` se houver linha que a condição não aceita |
| GET | `/vendas/{id}/simulacao-pagamento` | Total, parcelas, `valorParcela` e `economiaOuJuros` em cada condição, com as entradas lançadas |
| POST | `/vendas/{id}/financiar` | Em `PARCELADO_N`, cria ou atualiza a linha `FINANCIADO` com o saldo |
| GET | `/vendas/{id}/resumo` | Resumo do carrinho (totais, saldo) |
| PATCH | `/vendas/{id}/finalizar` | Lançar a venda (`CONCLUIDA`, seriais `VENDIDO`) com `autenticacaoVendedor`; quem se autentica vira o vendedor (sem o campo 400, senha errada 401) |
| PATCH | `/vendas/{id}/status` | Cancelar com `autenticacaoUsuario` e gravar quem alterou (sem o campo 400, senha errada 401; `PENDENTE`/`APROVADA`/`CONCLUIDA` → 422) |
| PATCH | `/vendas/{id}/avaliacao-procedencia` | Registrar avaliação de procedência (BKO) |
| POST | `/clientes` | Cadastrar cliente |
| GET | `/clientes/{id}` | Buscar cliente |
| POST | `/filiais` | Cadastrar filial |
| GET | `/filiais` | Listar filiais ativas (`?incluirInativas=true` traz também as inativas) |
| GET | `/filiais/{id}` | Buscar filial |
| POST | `/usuarios` | Cadastrar usuário/vendedor |
| GET | `/usuarios` | Listar quem pode se autenticar (sem inativos pela flag, pelo cargo ou pela situação) |
| GET | `/usuarios/{id}` | Buscar usuário |
| GET | `/estoque/consolidado` | Estoque consolidado por produto (`disponivel`, `reservado`, `defeito`; `avariado` é alias obsoleto de `defeito`) |
| GET | `/estoque/alertas` | Produtos em nível crítico de estoque |
| POST | `/estoque/entrada-seriais` | Dar entrada em seriais/IMEIs |
| POST | `/estoque/inventario` | Abrir inventário da filial (multipart `filialId` + IQ09); outro aberto → 409 |
| GET | `/estoque/inventario?filialId=&status=` | Listar execuções |
| GET | `/estoque/inventario/{id}?resultado=&busca=&pagina=&tamanho=` | Resumo, contadores, itens paginados e últimas 10 bipagens |
| POST | `/estoque/inventario/{id}/bipagens` | Bipar `{ serial }` → resultado, modelo, mensagem, `jaBipado`, totais |
| DELETE | `/estoque/inventario/{id}/bipagens/{itemId}` | Desfazer bipagem |
| PATCH | `/estoque/inventario/{id}/itens/{itemId}/justificativa` | Justificar pendente `{ texto }` |
| POST | `/estoque/inventario/{id}/fechar` | Fechar (422 com pendente sem justificativa) e diagnosticar |
| POST | `/estoque/inventario/{id}/cancelar` | Cancelar |
| POST | `/estoque/inventario/{id}/diagnostico` | Refazer o diagnóstico da IA (502 se a API falhar) |
| GET | `/estoque/inventario/{id}/exportacao` | CSV com todas as linhas e o resultado |
| POST | `/filiais/centros-sap` | Carga do centro SAP das filiais (CSV `ADABAS;CENTRO_SAP`) |
| POST | `/produtos` | Cadastrar produto |
| GET | `/produtos/{id}` | Buscar produto |
| GET | `/produtos` | Listar produtos |
| POST | `/produtos/precos/importar-telefonica` | Importar tabela de preços da Telefônica (multipart `arquivo`); devolve o relatório da importação, com `divergenciasCondicao` |
| GET | `/produtos/precos/vigente` | Execução vigente: vigência, data da importação e totais |
| GET | `/produtos/precos/planos?busca=&disponivelAlta=` | Planos da vigente (combo "Plano Ativo / Linha") |
| GET | `/produtos/precos/item?estoqueId=&plano=&tabela=&pagamento=&parcelas=` | Preço de uma unidade do estoque, com `opcoes` de pagamento (422 se sem preço) |
| GET | `/produtos/precos/execucoes/{execucaoId}` | **Deprecado** — use `/vigente` |
| GET | `/produtos/precos/produtos/{produtoId}` | **Deprecado** — use `/item`. Agora devolve os preços REGULAR da unidade por grupo do plano |
| GET | `/produtos/precos/ofertas` | **Deprecado** — use `/planos`. Agora devolve os grupos de plano com preço (`PRE`, `VIVO_V`...) |
| GET | `/produtos/precos?oferta=` | **Deprecado** — use `/item`. `oferta` = grupo do plano; `produtoId` vem nulo |
| PUT | `/documental/protocolos/{vendaId}` | Salvar/atualizar protocolo documental da venda |
| GET | `/documental/protocolos/{vendaId}` | Buscar protocolo por venda |
| GET | `/documental/protocolos` | Buscar por número de protocolo GED |
| GET | `/documental/protocolos/busca` | Vendas lançadas com ou sem protocolo, paginadas, com os filtros da tela (período, filiais, vendedor, nº da venda, nº de acesso, GED, líder, status BKO, tipos de plano, serviços) |
| POST | `/documental/protocolos/importacao` | Importar protocolos em lote (CSV) → `{ total, sucessos, erros }` |
| GET | `/documental/protocolos/exportacao` | Relatório de Gestão Documental (`gestao_documental.csv`, Windows-1252, 55 colunas; período obrigatório, até 93 dias) |
| GET | `/relatorios/{produtos\|servicos\|vendas}/busca` | Tabela paginada do relatório, com filtros, modo analítico/sintético e campos escolhidos |
| GET | `/relatorios/{tipo}/contagem` | Total de registros com os mesmos filtros |
| GET | `/relatorios/{tipo}/exportacao` | Excel `.xlsx` com as colunas escolhidas (período obrigatório, até 93 dias) |
| GET | `/relatorios/{tipo}/opcoes` | Campos, segmentos e tipos de produto disponíveis |
| POST | `/mailing/campanhas` | Criar campanha |
| GET | `/mailing/campanhas/modelo-planilha` | Baixar modelo de planilha de destinatários |
| POST | `/mailing/campanhas/importar-planilha` | Importar destinatários (multipart) |
| POST | `/mailing/campanhas/{id}/disparar` | Disparar campanha (assíncrono via fila) |
| GET | `/mailing/campanhas/{id}` | Buscar campanha |
| GET | `/mailing/campanhas` | Listar campanhas |

### Códigos de resposta

| Status | Quando |
|---|---|
| 200 | Consulta bem-sucedida |
| 201 | Recurso criado com sucesso |
| 400 | Corpo inválido (`@Valid`/`ConstraintViolationException`) |
| 401 | Sem token, token inválido ou expirado, usuário inativado, login recusado ou senha do vendedor errada |
| 404 | Recurso não encontrado (`RecursoNaoEncontradoException`) |
| 422 | Violação de regra de negócio (`BusinessException` e subclasses) |
| 500 | Erro não tratado — inclui hoje as 3 exceções que não estendem `BusinessException` (ver [Backlog](#13-backlog--pendências-conhecidas)) |

### Formato de erro padronizado

```json
{
  "timestamp": "2026-09-21T18:30:00Z",
  "status": 422,
  "erro": "Unprocessable Entity",
  "mensagem": "Não é possível registrar uma venda sem itens",
  "campos": { "opcional": "presente apenas em erro de validação (400)" }
}
```

---

## 10. Segurança

| Item | Situação hoje |
|---|---|
| Autenticação JWT | Dependências (`jjwt-api`, `-impl`, `-jackson`) presentes no `pom.xml`, sem `JwtFilter`/`JwtUtil` implementados |
| Autorização por rota | `SegurancaVendasConfig` define `.anyRequest().permitAll()` — **toda rota está aberta**, sem exceção |
| Senha de usuário | `Usuario.senhaHash` é sempre BCrypt (`PasswordEncoder` já configurado como bean), nunca texto puro |
| Senha de venda | Abrir e editar a venda não pedem senha; finalizar e mudar o status exigem vendedor e senha, conferidos contra o hash |
| SQL Injection | Spring Data JPA com queries parametrizadas — nenhuma concatenação de string identificada |
| Exposição de campo sensível | `VendaResponseDTO`/`UsuarioResponseDTO` não devolvem `senhaHash` — mapeamento manual evita vazamento (ADR-02) |
| CORS | Liberado (`@CrossOrigin(origins = "*")`) no `DashboardController` — só nesse controller, não é global |

Ou seja: hoje qualquer cliente HTTP acessa qualquer rota sem token. Antes de expor esse
backend fora de uma rede confiável, ativar de fato o `JwtFilter` no
`SecurityFilterChain` é pré-requisito, não um "nice to have".

---

## 11. Estado atual — módulo a módulo

| Módulo | Controllers | Regra de negócio | Persistência (Flyway) | Observação |
|---|---|---|---|---|
| `venda` | ✅ completo (carrinho, itens, pagamentos, status, avaliação) | ✅ máquina de estados, validação de carrinho | ✅ V1, V2, V8 | Núcleo do sistema — mais maduro |
| `estoque` | ✅ produto, entrada de serial, alertas, inventário SAP, tabela de preços | ✅ status de serial, conciliação, resolução de SKU | ✅ V4, V5, V6 | — |
| `gestao` (documental) | ✅ salvar/buscar protocolo, busca com filtros, importação e exportação CSV | ✅ vínculo 1:1 com venda, fila do BKO | ✅ V9, V31, V32, V33/V34 (view do relatório) | — |
| `mailing` | ✅ criar, importar, disparar, listar campanha | ✅ fila + listener assíncrono | ✅ V7 | Depende de credenciais WhatsApp via env vars |
| `dashboard` | ✅ 1 endpoint (`/dashboard/executivo`) | ✅ implementado, meta fixa no código | — (lê de `venda`/`estoque`) | Ver limitação de filtros abaixo |

---

## 12. Como rodar

### Localmente (sem Docker)

```bash
# 1. Pré-requisitos: Java 21, Maven (ou use ./mvnw), PostgreSQL e RabbitMQ rodando

# 2. Banco de dados
createdb syscor

# 3. Configuração
# edite src/main/resources/application-dev.yaml com seu datasource/RabbitMQ locais,
# ou exporte as variáveis de ambiente equivalentes

# 4. Rodar
./mvnw spring-boot:run

# 5. Testar
curl http://localhost:8080/api/dashboard/executivo
```

### Com Docker

```bash
docker compose -f Docker-compose.yml up --build
# sobe syscor-app (8080), syscor-db (5432) e syscor-rabbitmq (5672/15672)
```

### Variáveis de ambiente (perfil `dev`/Docker)

| Variável | Obrigatória | Descrição |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Sim (Docker) | JDBC URL do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | Sim (Docker) | Credenciais do banco |
| `SPRING_RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | Sim (Docker) | Conexão com RabbitMQ |
| `WHATSAPP_PHONE_NUMBER_ID` | Sim, para disparar mailing | ID do número na Graph API |
| `WHATSAPP_ACCESS_TOKEN` | Sim, para disparar mailing | Token de acesso da Graph API |
| `WHATSAPP_WABA_ID` | Sim, para disparar mailing | ID da WhatsApp Business Account |
| `SYSCOR_CARRINHO_EXPIRACAO` | Não (padrão `24h`) | Tempo sem atividade para cancelar um carrinho `ABERTA` e liberar o estoque reservado (formato `Duration`: `30m`, `24h`, `2d`) |
| `SYSCOR_CARRINHO_VERIFICACAO_MS` | Não (padrão `300000`) | Intervalo, em ms, entre as verificações do job de expiração |
| `SYSCOR_PRECO_CHIP` | Não (padrão `13.00`) | Preço fixo do chip (`syscor.precos.chip`); o PVP do aparelho não inclui o chip |
| `SYSCOR_CAIXA_CARGOS_GERENTE` | Não (padrão: Gerente de Vendas Júnior/Pleno/Sênior) | Cargos que abrem e fecham o caixa da própria filial, separados por vírgula |
| `SYSCOR_CAIXA_CARGOS_AUDITORIA` | Não (padrão vazio) | Cargos da auditoria de caixa: abrem e fecham em qualquer filial, conferem e reabrem |
| `SYSCOR_CAIXA_CARGOS_FINANCEIRO` | Não (padrão vazio) | Cargos que conferem, reabrem, fecham (com divergência, se preciso) e veem o esperado do caixa aberto |
| `SYSCOR_CAIXA_CARGOS_AREA` | Não (padrão vazio) | Cargos de gerente de área: veem todas as filiais, recebem o esperado e fecham com divergência (não abrem, não conferem) |
| `SYSCOR_CAIXA_ADMINS` | Não (padrão vazio; no perfil `dev`, `GUILHERME.CAIXETA`) | Logins com todos os poderes sobre o caixa |
| `SYSCOR_CAIXA_BLOQUEAR_ANTERIOR_ABERTO` | Não (padrão `false`) | `true`: caixa de dia anterior ainda aberto impede vender hoje |

Sem as três variáveis do WhatsApp, a aplicação sobe normalmente — o disparo de
campanha que falha com `NegocioException` no momento do envio, não no boot.

---

## 13. Backlog / pendências conhecidas

Em ordem de risco (o que mais provavelmente causa um bug em produção primeiro):

- [ ] **Segurança:** ativar `JwtFilter` de fato — hoje `SegurancaVendasConfig` libera
      todas as rotas (`permitAll()`), apesar das dependências JWT já estarem no projeto
- [ ] **Constraint de banco divergente do enum:** `itens_venda.categoria` não aceita
      `'ACESSORIO'` no `CHECK` da V1, mas o enum Java tem essa categoria — inserir um
      item `ACESSORIO` quebra em runtime
- [ ] **Constraint de banco divergente do enum:** `vendas.avaliacao_procedencia` (V1)
      usa literais (`EM_AVALIACAO`, `PROCEDENTE`, `IMPROCEDENTE`) diferentes dos valores
      do enum `StatusAvaliacaoProcedencia` (`Em_avaliacao_pelo_BKO`, `Procedente`,
      `Improcedente`, `Nao_avaliado`) — gravar via JPA falha contra a constraint
- [ ] **Exceções não capturadas pelo handler específico:** `ClienteDuplicadoException`,
      `DocumentoInvalidoException` e `TransicaoStatusInvalidaException` estendem
      `RuntimeException` direto, não `BusinessException` — caem no 500 genérico do
      Spring em vez de um 400/409/422 com corpo padronizado
- [ ] **Meta do dashboard hardcoded:** `R$ 85.000,00` fixo no `DashboardService` —
      precisa virar configuração ou tabela quando houver mais de uma meta por período/filial
- [ ] **Filtros do dashboard incompletos:** o front (`Dashboard.jsx`) tem campos de
      regional/filial/período/categoria na tela, mas não os envia no `fetch`; o backend
      só aceita `filialId` como query param e ignora os demais
- [ ] Dependências presentes sem uso identificado — confirmar se ainda são necessárias
      ou remover do `pom.xml`: Hibernate Envers (auditoria), AWS S3 SDK, Apache POI,
      openhtmltopdf, Spring Retry, Quartz
- [ ] Rate limiting em endpoints de importação (`/estoque/inventario`, `/filiais/centros-sap`,
      `/produtos/precos/importar-telefonica`, `/mailing/campanhas/importar-planilha`) —
      todos aceitam upload multipart sem limite de frequência
