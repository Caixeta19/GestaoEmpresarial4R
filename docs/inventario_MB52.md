# Inventário: cruzar também com a MB52 (estoque do SAP por SKU) — BACKEND + FRONTEND

Repositórios: `GestaoEmpresarial4R` (back) e `FrontendGestaoEmpresarial4R` (front). **Backend primeiro** (branch `feature/inventario-mb52`, PR para `main`), front depois do merge. Siga o CLAUDE.md: **plan mode primeiro** (mostre o plano: arquivos, migration, endpoints, e as respostas às perguntas marcadas "confirme no código"; só implemente após minha aprovação), `mvnw.cmd verify` / `npm run build`, nunca editar migration antiga, erros de negócio por `NegocioException` (o que o projeto usar).

## Situação atual (verificada no código)

- O inventário (`estoque/service/InventarioService`, RN.30–RN.31/RN.64–RN.66 em `docs/ESPECIFICACAO.md`) é **por filial e por serial**: `POST /estoque/inventario` recebe `filialId` + a **IQ09** (`RelatorioSapParser`, colunas pelo cabeçalho normalizado), usa só as linhas do `centro_sap`/`deposito_sap` da filial, cruza **exatamente pelo serial normalizado** (`SerialInventario`) com o estoque do sistema (`tb_estoque_geral`, status DISPONIVEL/RESERVADO/DEFEITO, `filial_estoque` = nome da filial) e com a **bipagem**; `InventarioClassificador` decide o resultado por serial; fechar exige pendentes = 0; diagnóstico por IA nas faltas.
- A **MB52** é outro relatório do SAP: **estoque por material (SKU) × centro × depósito, em quantidade**, sem serial. Hoje o sistema não a usa.
- `tb_inventario_item` já guarda `material_sap` e `denominacao_sap` (vindos da IQ09, por serial). `tb_estoque_geral` tem `codigo_acessorio` (SKU do acessório) e **não tem coluna de material SAP para aparelho**; `tb_produto_vivo` (V24, aba PRODUTOS da tabela da Telefônica) liga `cod_sap` ↔ nome comercial. Acessório com SKU tem **uma linha por SKU com `quantidade` N** (RN.62), então um serial só não conta N unidades.
- Front: `src/pages/EstoqueInventario.jsx` (~670 linhas) mostra a execução, bipagem, justificativas e CSV; botão "Novo inventário (IQ09)".

## Objetivo

Importar a **MB52** na execução do inventário da filial e comparar **por SKU**: **quantidade no SAP (MB52) × quantidade da IQ09 × quantidade do sistema × quantidade contada (bipada)**, apontando onde cada fonte discorda. É uma **segunda conferência, informativa e por quantidade**: **não muda** o cruzamento por serial nem as regras de fechamento (decisão minha; me avise no plano se enxergar conflito).

## Decisões (me avise no plano se o código contradisser)

1. A MB52 é **opcional**: pode vir junto na abertura (`mb52` multipart opcional em `POST /estoque/inventario`) **ou depois**, enquanto a execução estiver ABERTO/EM_CONTAGEM, em `POST /estoque/inventario/{id}/mb52` (reenviar substitui a anterior, com auditoria de quem/quando).
2. Filtra pelo **mesmo centro e depósito** da filial (mesma regra da IQ09: sem depósito cadastrado, considera o centro todo). Linhas de outros centros são contadas no resumo, não entram.
3. Quantidade principal = **Utilização livre**. **Em controle de qualidade, Bloqueado e Em trânsito** são guardados e **mostrados como informação**, mas **não entram na conta da diferença** (só aparecem numa coluna "Outros estoques SAP"); me diga no plano se acha melhor somar algum deles.
4. SKU = **Material** do SAP normalizado (maiúsculo, sem espaços; **zeros à esquerda removidos só quando o material é numérico**, para casar "000000000012345" com "12345"; confirme com os dados reais como o `codigo_acessorio` e o material da IQ09 vêm escritos).

## BACKEND

### Fase A — importação e modelo

1. **Migration nova** (próximo número):
    - `tb_inventario_sku` (`execucao_id`, `material_normalizado`, `material_sap`, `denominacao`, `unidade`, `qtd_mb52_livre`, `qtd_mb52_qualidade`, `qtd_mb52_bloqueado`, `qtd_mb52_transito`, `valor_unitario_sap` nullable, `qtd_iq09`, `qtd_sistema`, `qtd_bipada`, `resultado`, `observacao`; `UNIQUE (execucao_id, material_normalizado)`; índice por `execucao_id, resultado`).
    - Em `tb_inventario_execucao`: `mb52_nome_arquivo`, `mb52_importada_em`, `mb52_importada_por_id`, `mb52_total_linhas`, `mb52_linhas_outro_centro`, e contadores por resultado de SKU.
2. **`RelatorioMb52Parser`** seguindo o padrão do `RelatorioSapParser` (colunas pelo cabeçalho normalizado, não pela posição; `.xlsx`; erro 422 claro listando as colunas esperadas e as encontradas). Colunas esperadas (aliases a confirmar com um arquivo real): **Material, Texto breve material (ou Descrição), Centro, Depósito, Unidade de medida, Utilização livre (ou "Livre utilização"), Em controle de qualidade, Bloqueado, Em trânsito/transferência, Valor utilização livre**. Só **Material, Centro e Utilização livre** são obrigatórias.
    - Tolerar o jeito SAP de exportar: **linhas de total/subtotal** (sem material ou com texto "Total"), **linhas em branco**, cabeçalho repetido, **sinal negativo no fim ("5-")**, número com vírgula decimal ("1.234,000") quando vier como texto, material numérico como texto com zeros à esquerda, e quantidade **decimal** (rejeitar com erro claro se a unidade for inteira e vier fracionada? decida no plano).
    - Duplicidade do mesmo material/centro/depósito em linhas diferentes (ex.: lotes): **somar**.
3. **Mapa de SKU do sistema** (confirme no código e proponha no plano; é o ponto mais delicado):
    - **Acessório:** `codigo_acessorio` = SKU; quantidade do sistema = soma de `quantidade` (as reservadas continuam contando como estoque, como no inventário por serial).
    - **Aparelho/produto serializado:** como `tb_estoque_geral` não guarda o material, proponha a fonte do SKU **sem inventar**: (a) o `material_sap` da IQ09 **pelo serial** (só vale para os seriais que estão na IQ09 desta execução); (b) para os que só existem no sistema, via `tb_produto_vivo` (cod_sap ↔ nome comercial ↔ `modelo_comercial`), se o casamento for confiável; (c) sobrando, **SKU "não identificado"**, agrupado numa linha à parte com a contagem, em vez de chutar. Se a melhor solução for **gravar o material SAP em `tb_estoque_geral` na importação do estoque** (a planilha do estoque tem esse dado?), diga no plano o custo e deixe como recomendação, sem fazer agora.
4. **Reimportar/recalcular**: o cálculo roda ao importar a MB52 **e** é recalculável a cada bipagem sem custo alto (agregação por SKU em uma consulta; não faça N+1 nem reprocesse a IQ09). Para a contagem física por SKU: `qtd_bipada` = bipagens da execução agrupadas pelo SKU do item (mesmo mapa acima); para acessório com quantidade N, a bipagem do serial do SKU hoje conta 1: **me diga no plano como o inventário trata esse caso** e proponha uma **contagem manual por SKU** (`PATCH /estoque/inventario/{id}/skus/{skuId}/contagem {quantidade, justificativa}`) para esses itens, sem mexer na bipagem por serial.

### Fase B — classificação por SKU

Para cada SKU na **união** (MB52 da loja ∪ materiais da IQ09 da loja ∪ SKUs do sistema da filial), calcule `qtd_mb52_livre`, `qtd_iq09` (seriais da IQ09 da loja com status usuário ADEP/vazio, como em `esperadoIq09`), `qtd_sistema`, `qtd_bipada` e classifique (`ResultadoSku`, tabela única no código, testada):

| Resultado | Quando |
| --- | --- |
| `CONFERIDO` | MB52 = IQ09 = sistema (e, se já contou, = bipada) |
| `MB52_DIFERE_IQ09` | MB52 ≠ IQ09 (serial fora da IQ09, item sem série, estoque não serializado, ou IQ09 desatualizada) |
| `SAP_ACIMA_SISTEMA` | MB52 > sistema (o SAP tem mais do que o sistema) |
| `SAP_ABAIXO_SISTEMA` | MB52 < sistema |
| `SO_NO_SAP` | SKU na MB52 sem nada no sistema |
| `SO_NO_SISTEMA` | SKU no sistema sem linha na MB52 |
| `SKU_NAO_IDENTIFICADO` | itens do sistema sem SKU resolvido (linha agrupada) |
| `CONTAGEM_DIVERGENTE` | já existe contagem física e ela ≠ MB52 (ou ≠ sistema, definir no plano qual referência) |

Mostre também `diferenca_sap_sistema`, `diferenca_sap_iq09`, `diferenca_contagem` e, quando houver `valor_unitario_sap`, o **valor da diferença** (estimativa; rotule como tal). **Não** altere `InventarioClassificador` nem o fechamento.

### Fase C — endpoints, exportação e testes

1. `POST /estoque/inventario/{id}/mb52` (multipart) → resumo da importação (`{linhas, daFilial, outroCentro, ignoradas, skusComDiferenca}`); `GET /estoque/inventario/{id}/skus` paginado (`resultado`, `busca` por SKU/descrição, `somenteDiferenca`, ordenação por valor/quantidade da diferença) + totais por resultado; `GET /estoque/inventario/{id}/exportacao-sku` (CSV `;` UTF-8 com BOM e/ou XLSX, valores numéricos; mesmas colunas da tela); `PATCH …/skus/{skuId}/contagem` (se aprovado no plano). Perfis: os mesmos do inventário.
2. O resumo da execução (`GET /estoque/inventario/{id}`) passa a informar `mb52 {importada, arquivo, em, por, totaisPorResultado}` sem pesar a resposta atual.
3. **Testes sintéticos**: parser com cabeçalhos em outra ordem, linha de total, sinal negativo no fim, material com zeros à esquerda, duas linhas do mesmo material, centro de outra loja, coluna obrigatória faltando (422); classificação com um SKU de cada resultado; MB52 reimportada substitui a anterior; bipagem depois da MB52 atualiza `qtd_bipada`; inventário **sem** MB52 continua idêntico ao de hoje (regressão); acessório com quantidade > 1.
4. Atualizar `docs/ESPECIFICACAO.md` (nova RN + endpoints + tabela).

## FRONTEND (depois do merge do backend)

Em `EstoqueInventario.jsx` (extraia para `components/inventario/`, sem quebrar o que existe): adicionar uma **aba/seção "Por SKU (MB52)"** na execução, com:
1. Botão **"Importar MB52"** (`.xlsx`, loading, toast com o resumo; permitir também no "Novo inventário" como arquivo opcional ao lado da IQ09). Mensagem clara se o arquivo não tiver as colunas esperadas.
2. Cards: SKUs conferidos / com diferença / só no SAP / só no sistema / não identificados e **valor estimado das diferenças**.
3. Tabela por SKU: **SKU, descrição, MB52 (livre), IQ09, Sistema, Contagem, diferenças, outros estoques SAP, resultado (badge)**, filtros por resultado e "somente com diferença", busca por SKU, ordenação, paginação, **exportar** (CSV/XLSX do backend via fetch com Bearer; nunca `<a href>` direto).
4. Para SKU com quantidade (acessório), campo de **contagem manual** com justificativa (se aprovado no backend). Atualizar a aba a cada bipagem/contagem sem recarregar a página toda.
5. Estados: sem MB52 ("Importe a MB52 para conferir por SKU"), loading, vazio, erro com "Tentar de novo"; só tokens/classes do tema (claro/escuro); nada de cor fixa nova.

## Não fazer

- Não mudar o cruzamento por serial, as regras de fechamento, a bipagem nem o diagnóstico por IA (o diagnóstico por SKU fica como possível etapa futura).
- Não commitar MB52/IQ09 reais; testes só com planilhas sintéticas geradas no teste.
- Não inventar o SKU dos aparelhos quando não houver fonte confiável (use "não identificado").

## Verificação

- Back: `mvnw.cmd verify` verde; abrir inventário só com IQ09 (igual a hoje) e depois importar uma MB52 sintética; conferir um SKU de cada resultado e a exportação.
- Front: `npm run build` e lint limpos; fluxo: abrir → importar MB52 → bipar → ver `Contagem` subir → exportar; claro/escuro.
- Resumo final em 5 linhas: o que mudou, como o SKU dos aparelhos foi resolvido, como ficou a contagem de acessório com quantidade, o que ficou pendente.