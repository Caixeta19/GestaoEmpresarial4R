# Gestão documental + Relatório de Gestão Documental ligados à API (saem do localStorage)

Leia o CLAUDE.md. Mostre o plano e espere aprovação. Faça o backend primeiro e o front em outra execução. A branch nos dois repositórios é `feature/documental-api`.

## Problema
A venda 1000003 (id 4, APROVADA) existe no banco, mas a tela **Busca de Serviços – Gestão documental** responde "não foi encontrada". O motivo é que `src/pages/Documental.jsx` ainda é tela de demonstração:
- busca as vendas em `localStorage('syscor_vendas')`;
- salva os protocolos em `localStorage('syscor_protocolos')`;
- tira vendedores e PDVs de `data/demoData`;
- faz a importação em lote também só no navegador.

O backend já tem o que a tela precisa:
- `GET /vendas` com os filtros `numeroVenda`, `filialId`, `vendedorId`, `dataInicio`, `dataFim`, `cliente` e `numeroAcesso`;
- `GET /documental/protocolos/{vendaId}`, que devolve os dados da venda e o protocolo (ou campos vazios se ainda não houver protocolo);
- `PUT /documental/protocolos/{vendaId}` para salvar; o `statusBko` também grava a avaliação de procedência;
- `GET /documental/protocolos?numeroProtocoloGed=`.

Atenção: `{vendaId}` é o **id** da venda, não o `numeroVenda` (1000003 ≠ 4).

## Backend (pequeno)
1. **`GET /documental/protocolos/busca`** com os mesmos filtros da tela:
   - período da venda;
   - PDV, vendedor, nº da venda, nº de acesso, nº do protocolo GED, nº da solicitação 360, nº de portabilidade e líder de equipe;
   - data de instalação de/até.

   Devolve uma lista paginada de `ProtocoloDocumentalResponseDTO`, incluindo as vendas que ainda não têm protocolo.
   - Só entram vendas lançadas (CONCLUIDA; rode antes o `prompt_lancar_venda_direto.md`). O parâmetro `incluirCanceladas=true` traz também as canceladas.
   - Novo filtro `statusBko`. Toda venda lançada nasce **Não avaliado** (o finalizar grava `Nao_avaliado`), então a fila do BKO é a busca com `statusBko=Nao_avaliado`.
   - Na resposta, `statusBko` nunca vem nulo: venda sem avaliação aparece como `Nao_avaliado`.
2. **`POST /documental/protocolos/importacao`** (multipart CSV `;` ou `,`):
   - colunas `NUMERO_VENDA;PROTOCOLO_GED;STATUS_BKO;NUMERO_ACESSO;DATA_DIGITALIZACAO` (data em dd/MM/yyyy);
   - procura a venda pelo `numeroVenda` e grava pelo mesmo serviço do PUT;
   - responde `{ total, sucessos, erros: [{ linha, numeroVenda, motivo }] }`;
   - uma linha com erro não desfaz as outras.
3. **Salvar protocolo de venda ABERTA ou CANCELADA** → 422 com mensagem clara.
4. **Testes:**
   - busca por `numeroVenda` → acha a venda id 4 / 1000003;
   - busca sem resultado → lista vazia (200, não 404);
   - importação com uma linha boa e uma ruim;
   - PUT em venda ABERTA → 422.
5. Rode `mvnw.cmd verify` e atualize README/ESPECIFICACAO.

## Frontend
1. **Criar `src/services/documentalApi.js`** com as funções `buscar(filtros)`, `buscarPorVenda(vendaId)`, `salvar(vendaId, dados)` e `importar(arquivo)`. Elas usam o `request` de `api.js`, igual a `vendasApi.js`.
2. **Em `Documental.jsx`:**
   - remover todo uso de `localStorage` e de `demoData`;
   - PDVs vêm de `GET /filiais` e vendedores de `GET /usuarios` (as mesmas funções da tela de Venda).
3. **Busca:**
   - envia os filtros para `/documental/protocolos/busca`;
   - com 1 resultado, abre o detalhe; com vários, mostra uma tabela (nº venda, data, cliente, vendedor, PDV, status da venda, protocolo GED, status BKO) e o clique abre o detalhe;
   - novo filtro **Status BKO** (Não avaliado, Em avaliação pelo BKO, Procedente, Improcedente);
   - ao abrir a tela, já buscar as vendas **Não avaliado** dos últimos 30 dias (fila do BKO);
   - "não encontrada" só aparece quando a API devolver a lista vazia;
   - erro de rede aparece como erro de rede.
4. **Detalhe:**
   - carrega `GET /documental/protocolos/{vendaId}` e preenche o formulário com os campos da resposta (`clienteNome` + `clienteDocumento`, `vendedorNome`, `dataVenda` em dd/MM/yyyy, `servico` e `plano`);
   - mostra o status da venda; para CANCELADA, o formulário fica somente leitura.
5. **Salvar:**
   - `PUT /documental/protocolos/{vendaId}` convertendo os campos: `zerarRemuneracao` e `gerarPrice` de 'sim'/'nao' para boolean, `gerarComissao` de 'Sim'/'Não' para boolean, e `dataDigitalizacao` em ISO;
   - `statusBko` aceita os valores do enum do backend;
   - mensagem de sucesso ou o erro do backend na tela, sem `alert()`.
6. **Importação:** envia o CSV para `/documental/protocolos/importacao`, mostra o resumo e oferece o download do CSV de erros, montado a partir da resposta.
7. **"Ir para a venda"** abre `/venda/lancar` com `state: { vendaId }` (o id), que a tela de Venda já sabe carregar.
8. Rode `npm run build` e faça um commit por etapa.

## Relatório de Gestão Documental (Relatórios → Gestão Documental)
Hoje a tela `TelaExtracaoGestaoDocumentalSyscor` em `src/pages/Relatorios.jsx` é só demonstração:
- lojas fixas em `LOJAS_PRINCIPAIS_VENDA`;
- registros fixos em `REGISTROS_BASE_DOCUMENTAL`;
- datas fixas em 01/09–02/09/2026;
- e, quando o filtro não acha nada, exporta **todos** os registros (`baseFinal = ... : REGISTROS_BASE_DOCUMENTAL`).

Ela passa a usar os mesmos dados da gestão documental.

### Backend
1. **Filtros novos na busca** `/documental/protocolos/busca`:
   - `filialIds` (lista);
   - `tiposPlano` (lista: PRE, CONTROLE, POS);
   - `servicos` (lista de `tipoServico`).
   - O tipo de plano sai do item: use `segmento` e/ou `grupoPlano`. Confira os valores reais e me mostre o mapeamento no plano (ex.: PRÉ → PRE; CONTROLE BTL/ENTRADA/ALTO VALOR → CONTROLE; PÓS INDIVIDUAL, FAMILIA e VIVO V → POS).
   - Os serviços são os valores de `tipoServico` que a tela de Venda grava (Alta, Migração, Reativação, Troca de Plano etc.). Confira e alinhe com a lista do relatório.
   - Aparelho e acessório vendidos contam como serviço "Troca de Aparelho" (ver layout abaixo), então também entram no relatório.
2. **Layout do arquivo = o relatório do sistema atual.** O exemplo real que o Guilherme exportou tem dados de clientes: **não** copie esse arquivo para o repositório e não use dados reais em teste. O layout está descrito abaixo e os testes usam dados sintéticos.

   **Estrutura do arquivo** (texto, **ISO-8859-1/Windows-1252**, separador `;`, quebra de linha LF, sem aspas duplas):
   ```
   Data da Emissão do relatório;10/11/2026 09:37
   (linha vazia)
   Revenda: 4 REDES
   (linha vazia)
   <cabeçalho: 55 colunas>
   <uma linha por serviço>
   (linha vazia)
   (linha vazia)
   (*) Campos obrigatórios;
   (**) Campos que podem ser alterados;
   ```

   **Cabeçalho, exatamente assim e nesta ordem (55 colunas):**
   ```
   Filial;Data Venda(*);Data Ativação(**);Status BKO(*)(**);Serviço(*);ID Venda;Modelo;IMEI;Plano;Valor plano(**);Plano Antigo;Valor plano antigo(**);Vencimento Fatura(**);Vendedor;Cliente;CPF/CNPJ;Nº Acesso(*);Nº Portado;Servico Instalado;Data Agendada de Instalação;Armário;Data Instalação;Situação do serviço(**);Receita;Status GED;Sim Card;COD;Fidelização;Zerar Remuneração;Gerar Price;Nº Solicitação NEXT / RPON;Nº Protocolo GED(**);Nº Protocolo GED Portabilidade (**);Motivo Reprovação(**);Data Digitalização;Usuário Inserção;Usuário Alteração;Observação;Motivo Cancelamento(**);Comissão de Serviço do Vendedor;Comissão de Aparelho do Vendedor(**);Data Última Ativação(**);Data de alteração;Usuário Cancelamento;Data Cancelamento;Líder de Equipe;Status Primeira Fatura;Data Primeira Fatura;Status Segunda Fatura;Data Segunda Fatura;Status Terceira Fatura;Data Terceira Fatura;Valor Receita;Valor Considerado TFP;Valor Penalizado TFP
   ```

   **Regras de formato** (tiradas do arquivo real):
   - **Linhas:** uma linha por **serviço**, então o mesmo `ID Venda` se repete. Aparelho e acessório vendidos entram como serviço **"Troca de Aparelho …"**, com `Modelo` e `IMEI` preenchidos; nas outras linhas esses dois ficam vazios.
   - **Datas:** `dd/MM/yyyy`. As colunas "Data de alteração" e a data de emissão usam `dd/MM/yyyy HH:mm`.
   - **Valores:** com **ponto** decimal e 2 casas (`24.99`, `0.00`), sem separador de milhar.
   - **Sim/Não:** Servico Instalado, COD, Fidelização, Zerar Remuneração, Gerar Price, Comissão de Serviço do Vendedor e Comissão de Aparelho do Vendedor.
   - **Colunas de texto** que o Excel transformaria em número vão **entre apóstrofos**, inclusive quando vazias (`''`): Sim Card, Nº Solicitação NEXT / RPON, Nº Protocolo GED, Nº Protocolo GED Portabilidade e Motivo Reprovação.
   - **Sem plano antigo:** `Plano Antigo` = `-` e `Valor plano antigo` = `0.00`.
   - **Pré-pago:** Plano = `PRE PAGO`, Valor plano `0.00` e Vencimento Fatura `0`.
   - **Receita:**
      - Troca de Simcard, Troca de Aparelho, Troca de número e Troca de titularidade → `0.00`;
      - nos demais serviços → `max(0, Valor plano − Valor plano antigo)`.
   - **Serviço:** é o nome composto **`<tipo> <tipo de linha> <segmento>`**, por exemplo "Troca de Plano Smartphone Controle", "SVA Outros serviços Pós", "Troca de Aparelho Voz Pré", "Alta Internet Fibra Pós".
      - Tipos: Alta, Migração, Reativação, SVA, Seguro, Troca de Plano, Troca de Aparelho, Troca de Simcard, Troca de número, Troca de titularidade.
      - Tipos de linha: Smartphone, Voz, Modem, Internet Fibra, TV GVT, Fixa GVT, Outros serviços.
      - Segmento: Pré, Controle, Pós. A Reativação não tem tipo de linha ("Reativação Controle").
      - Confira o que o item de serviço já grava (`tipoServico`, `segmento`) e proponha o campo de **tipo de linha**, se não existir.
   - **Domínios:**
      - Status BKO: `Não avaliado`, `Em avaliação pelo BKO`, `Procedente`, `Improcedente`;
      - Situação do serviço: `Não avaliado`, `Confirmado`, `Cancelado`;
      - Status GED: `Serviço Não digitalizado`, `Aguardando digitalização do serviço`, `Serviço Digitalizado`, `Serviço cancelado`.
   - **Vazios:** colunas sem dado no sistema saem vazias, mas presentes: faturas, TFP, Armário, Data Agendada etc.
   - **Ordem das linhas:** filial, Data Venda, Status BKO (os avaliados antes de "Não avaliado") e ID Venda.

   **Origem de cada coluna:**
   - **Fase A:** antes de implementar, monte a tabela coluna → origem (venda, item, cliente, protocolo, usuário, filial) e marque as que **não têm origem** no banco. Pare e me mostre.
   - **Campos novos:** para os que fazem sentido gravar, proponha as colunas novas em `tb_protocolo_documental` ou `itens_venda`:
      - Data Ativação, Data Última Ativação, Nº Portado, Servico Instalado, Data Agendada/Instalação, Armário;
      - Status GED, Situação do serviço, Motivo Reprovação;
      - Fidelização, COD, Comissão de Aparelho;
      - Usuário/Data de alteração e de cancelamento;
      - Líder de Equipe.
   - **Status BKO:** sem valor sai `Não avaliado`. "Data Venda" é a data em que a venda foi lançada.
   - Uma view `vw_gestao_documental` com essas colunas e as colunas técnicas para filtro (`venda_id`, `filial_id`, `status_venda`, `tipo_plano`, `data_lancamento`) é bem-vinda, se simplificar.
3. **`GET /documental/protocolos/exportacao`**:
   - mesmos filtros da busca, sem paginação;
   - devolve o arquivo no layout acima, com `Content-Type: text/csv; charset=windows-1252`;
   - nome do arquivo: `gestao_documental.csv`;
   - período obrigatório e de no máximo 93 dias (422 se passar);
   - a "Revenda" vem de configuração (`syscor.revenda.nome`, padrão `4 REDES`).
4. **Testes** (dados sintéticos):
   - preâmbulo, cabeçalho (55 colunas, mesmo texto) e rodapé iguais ao layout;
   - arquivo em ISO-8859-1: "Não" e "Situação" com acento correto;
   - venda com troca de plano 49.00 → 35.00 → Receita `14.00`; migração 50.00 → 75.00 → Receita `0.00`; troca de simcard → `0.00`;
   - venda com aparelho + 2 serviços → 3 linhas com o mesmo ID Venda, e só a de Troca de Aparelho tem Modelo e IMEI;
   - protocolo GED `''` quando vazio e `'123'` quando preenchido;
   - o filtro `statusBko=Nao_avaliado` traz a venda recém-lançada; com `Procedente`, não traz.

### Frontend
1. **Filiais:**
   - vêm de `GET /filiais`;
   - o select UF é montado com as UFs das filiais;
   - filial inativa aparece em vermelho com "(INATIVA)";
   - remover `LOJAS_PRINCIPAIS_VENDA` e `REGISTROS_BASE_DOCUMENTAL`.
2. **Filtros:**
   - o período começa no 1º dia do mês atual até hoje;
   - **Status BKO** com as opções: Todos, Não avaliado, Em avaliação pelo BKO, Procedente e Improcedente (enviar os valores do enum);
   - Tipo de plano e Serviço enviam as listas marcadas; com "Selecionar todos" marcado, não enviar o filtro.
3. **Exportar:**
   - chama `/documental/protocolos/exportacao` e baixa o arquivo;
   - com 0 vendas, mostra "Nenhuma venda encontrada para esses filtros" e **não** exporta nada.
4. **Salvar:** guarda os filtros como padrão do usuário (`localStorage` com o login, em try/catch), e eles voltam ao abrir a tela.
5. **Apagar:** limpa o padrão salvo e redefine os filtros.
6. Mostrar a quantidade de vendas encontradas antes de exportar: chamar a busca com `size=1` e ler o total.
7. **"Venda de Produtos", "Venda de Serviços" e "Vendas"** (`TelaExtracaoGenerica`): continuam no `localStorage` por enquanto. Coloque só um aviso "Dados de demonstração", porque elas serão ligadas no passo de relatórios.

## Teste manual
1. Abrir a tela: a lista mostra a venda 1000003 (cliente 438328, lançada) como **Não avaliado**. Buscar 1000003 abre o detalhe.
2. Lançar uma venda nova: ela aparece na fila como Não avaliado.
3. Salvar o protocolo GED, sair, buscar de novo: os dados voltam do banco.
4. Buscar só por período: aparece a lista com as vendas do período, sem as 3 canceladas.
5. Importar um CSV com uma venda que existe e uma que não existe: 1 sucesso, 1 erro e o CSV de erros para baixar.
6. Relatórios → Gestão Documental, filial da venda 1000003, período do lançamento, Status BKO "Não avaliado" → Exportar: o CSV traz a venda. Com "Procedente": mensagem de nenhuma venda e nenhum arquivo.
7. Desmarcar "Pré" em Tipo de plano: vendas de plano pré-pago somem do CSV.