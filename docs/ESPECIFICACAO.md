# GestaoEmpresarial4R — Documentação do Projeto

Nov 3, 2026 · @Guilherme

## 1. Resumo e contexto

O GestaoEmpresarial4R (artefato `syscor`, marca "4R Solutions") é o backend do ERP interno da 4R Redes / vivo4redes: vendas de produtos e serviços Vivo, estoque serializado por IMEI, conciliação com SAP, protocolo documental, caixa, conciliação bancária, remuneração variável e mailing via WhatsApp. Este documento descreve o sistema como ele está no código (commit `b3deace`, 29/10/2026) e o que falta construir, para servir de especificação ao Claude Code.

**Problema.** A operação de venda de uma filial passa por etapas que hoje vivem em planilhas e controles manuais separados: carrinho de venda, baixa do aparelho no estoque, conferência contra o relatório SAP, protocolo GED pós-venda, fechamento de caixa, baixa de títulos pagos e apuração de comissão. Nada conversa entre si, e a operação atende 85 lojas físicas.

**Solução.** Um ERP modular em que a venda é a entidade central. O vendedor abre um carrinho, adiciona itens de categorias diferentes (aparelho, serviço/plano, acessório, recarga), registra pagamentos e finaliza. A partir da venda derivam a baixa de estoque, o protocolo documental, a remuneração variável e os KPIs do painel executivo.

> **A venda é a entidade central — tudo (estoque, documental, financeiro, dashboard) deriva dela.** **Cada módulo é dono do seu próprio ciclo de vida e nunca decide por outro.**

**Justificativa.** Sem um sistema único, não há rastreabilidade do IMEI vendido, a comissão depende de cruzamento manual entre o consolidado da Vivo e o protocolo GED, e a baixa de títulos pagos é feita olhando extrato. Centralizar esses fluxos reduz divergência de estoque, perda de comissão e retrabalho financeiro.

**Repositórios.** Backend: [Caixeta19/GestaoEmpresarial4R](https://github.com/Caixeta19/GestaoEmpresarial4R) (este documento). Frontend: repositório separado `syscor-frontend` (React + Vite + Tailwind v4), citado no README.

## 2. Objetivos e escopo

O objetivo geral é entregar um ERP único para a operação de varejo Vivo da 4R, em que cada venda registrada alimenta automaticamente estoque, documental, financeiro e indicadores.

**Objetivos específicos**

- **Centralizar a venda no PDV:** carrinho multicategoria com reautenticação do vendedor, pagamentos múltiplos e máquina de estados explícita.
- **Rastrear cada aparelho por IMEI:** estoque unificado em `tb_estoque_geral`, baixa transacional com lock e estorno no cancelamento.
- **Conciliar com sistemas externos:** relatório SAP (IQ09), tabela de preços da Telefônica, consolidado de comissão da Vivo e webhooks de pagamento bancário.
- **Controlar o caixa da loja:** abertura e fechamento por forma de pagamento, com conferência do valor apurado.
- **Relacionamento com o cliente:** cadastro PF/PJ com consentimento LGPD e campanhas de mailing via WhatsApp Business.
- **Dar visibilidade gerencial:** painel executivo com faturamento, meta, ticket médio, SKUs críticos e evolução de 6 meses.

**Público-alvo (personas)**

| Persona | O que faz no sistema |
| --- | --- |
| Vendedor / consultor de loja | Abre carrinho, lê IMEI, registra pagamento, finaliza venda com a própria senha |
| Gerente de loja / caixa | Abre e fecha o caixa do PDV, acompanha vendas da filial |
| BKO (backoffice) | Avalia procedência da venda, preenche protocolo GED |
| Estoque / logística | Importa relatório de estoque Vivo e SAP, atualiza preços |
| Financeiro | Títulos a pagar/receber, conciliação bancária, remuneração variável |
| Diretoria | Painel executivo |
| Marketing / relacionamento | Campanhas de WhatsApp |

**Fora do escopo da v1 (ainda não feito)**

- Controle de acesso por perfil (RBAC): hoje qualquer usuário autenticado acessa qualquer rota.
- Auditoria persistida (Hibernate Envers está no `pom.xml`, sem uso).
- Emissão fiscal (NF-e/NFC-e, SPED) e geração de PDF/Excel (openhtmltopdf e Apache POI presentes, sem endpoint).
- Armazenamento de arquivos em S3 (SDK presente, sem uso).
- Filtros de regional, período e categoria no dashboard; meta configurável.
- Aplicativo mobile.

## 3. Stack e arquitetura

O backend é um monólito modular em Spring Boot 3.3.4 sobre Java 21, com PostgreSQL, RabbitMQ e autenticação JWT stateless. Cada módulo de domínio tem suas próprias camadas `controller → service → repository → model/dto/enums`.

&#91;embedded content: arquitetura de containers · 7 módulos, 4 integrações\]

O front chama a API por REST e recebe a conciliação bancária em tempo real por SSE; bancos entram por webhook em uma cadeia de segurança própria, e o mailing sai pela fila RabbitMQ.

| Camada | Tecnologia | Uso no projeto |
| --- | --- | --- |
| Linguagem / framework | Java 21, Spring Boot 3.3.4 | Artefato `syscor`, pacote `com.vivo4redes.syscor` |
| Banco e schema | PostgreSQL 16, Flyway | `ddl-auto: validate`; schema só por migration |
| Segurança | Spring Security, jjwt | JWT de 60 min, BCrypt, CORS configurável |
| Mensageria | RabbitMQ (AMQP) | Fila de envio do WhatsApp, `concurrency = 1` |
| HTTP externo | WebFlux `WebClient` | Graph API do WhatsApp v20.0 |
| Assíncrono | `@Async`, `@Scheduled`, eventos de transação | Processamento e reprocesso de webhooks |
| Validação | Bean Validation, Caelum Stella | DTOs e CPF/CNPJ |
| Produtividade | Lombok, MapStruct (sem uso) | Entidades e builders |
| Documentação | springdoc-openapi | Swagger UI |
| Observabilidade | Actuator | `health`, `info` |
| Testes | JUnit 5, Mockito, Testcontainers | PostgreSQL e RabbitMQ em container |
| Entrega | Docker multi-stage, Docker Compose, GitHub Actions | CI ativo; CD desativado |
| Presentes sem uso | Envers, AWS S3, Apache POI, openhtmltopdf, Spring Retry, Quartz | Reservados para épicos futuros |

**Decisões de arquitetura (ADRs)**

- **ADR-01 — Módulo por domínio:** pacotes `venda`, `estoque`, `gestao`, `financeiro`, `mailing`, `dashboard`, `security`; cada um é dono das suas regras.
- **ADR-02 — Entity separada de DTO:** mapeamento manual via `ResponseDTO.from()`, sem expor `senhaHash` nem coleções lazy.
- **ADR-03 — Máquina de estados no enum:** `StatusVenda.transicoesPermitidas()`; o service só pergunta `podeTransicionarPara()`.
- **ADR-04 — Exceções de domínio + `@RestControllerAdvice`:** `BusinessException` vira 422 com corpo padrão.
- **ADR-05 — Mensageria para envio em massa:** o disparo só enfileira; o listener respeita o rate limit do WhatsApp.
- **ADR-06 — `context-path` único (`/api`):** nenhum controller repete o prefixo.
- **ADR-07 — Concorrência no serial:** `SELECT … FOR UPDATE` + `@Version` em `tb_estoque_geral`.
- **ADR-08 — Webhook bancário com adapter por banco:** `BancoAdapter` (autenticar + parse) registrado no `BancoRegistry`; ingestão grava log e processa depois do commit.

**Fluxo da venda**

&#91;embedded content: máquina de estados da venda · 5 status\]

O serial fica `RESERVADO` ao adicionar o item e vira `VENDIDO` quando a venda é `CONCLUIDA`; remover o item ou cancelar a venda devolve a unidade a `DISPONIVEL` (bug 4 da seção 9, corrigido). Carrinho parado por mais de 24h é cancelado automaticamente (RN.49).

**Fluxo de autenticação (JWT)**

1. O front envia `POST /api/auth/login` com login e senha.
2. O `AuthenticationManager` carrega o usuário pelo `UsuarioDetailsService` e compara o hash BCrypt.
3. O `JwtService` gera um token assinado com `SYSCOR_JWT_SECRET`, válido por 60 min, e a resposta informa `primeiroAcesso`.
4. Se `primeiroAcesso = true`, o front obriga `POST /api/auth/alterar-senha` antes de seguir.
5. Em cada requisição, o `JwtAuthFilter` valida o `Authorization: Bearer` e popula o `SecurityContext`; sem token válido, 401.
6. Operações sensíveis de venda (finalizar, mudar status) pedem de novo login e senha do vendedor no corpo da requisição.

## 4. Regras de negócio

Cada regra abaixo está implementada no código, salvo as marcadas como **(a fazer)**. A coluna Onde aponta a classe responsável.

**Cliente**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.01 | CPF/CNPJ único e imutável após o cadastro (`updatable = false`) | `Cliente`, `ClienteService` |
| RN.02 | Documento validado com Caelum Stella; inválido gera `DocumentoInvalidoException` (400) | `ValidadorCpfCnpj` |
| RN.03 | Documento duplicado gera `ClienteDuplicadoException` (422) | `ClienteService` |
| RN.04 | Consentimento de marketing exige versão do termo e data; inconsistente gera `ConsentimentoInvalidoException` | `ClienteService`, V12 |

**Usuário e autenticação**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.05 | Senha sempre em hash BCrypt, nunca em texto | `SecurityConfig`, `UsuarioService` |
| RN.06 | E-mail de vendedor único (`VendedorDuplicadoException`) | `UsuarioService` |
| RN.07 | Login por `login`/`email`/`usuario` + senha devolve JWT de 60 min e a flag `primeiroAcesso` | `AuthController`, `JwtService` |
| RN.08 | No primeiro acesso o usuário troca a senha (mínimo 8 caracteres); a flag vira `false` | `AuthController` |
| RN.09 | Finalizar ou mudar status de venda exige reautenticação do vendedor (login + senha) | `VendaService.exigirVendedorAutenticado` |
| RN.10 | **(a fazer)** Autorização por cargo: o papel `ROLE_<CARGO>` já é montado no login, falta aplicá-lo nas rotas | — |

**Venda**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.11 | `numeroVenda` público de 7 dígitos gerado por sequence do banco na abertura do carrinho | `NumeroVendaGenerator`, V3 |
| RN.12 | Item só entra ou sai com a venda em `ABERTA` (`CarrinhoNaoEditavelException`) | `VendaService` |
| RN.13 | `valorTotal` nunca vem da API: é recalculado pela soma dos itens | `Venda.recalcularValorTotal()` |
| RN.14 | Serviço Vivo (plano) entra sem produto e com valor 0; demais categorias exigem `produtoId` e valor > 0 | `VendaService.adicionarItem` |
| RN.15 | Item com IMEI/serial tem quantidade 1; aparelho sem serial informado é recusado | `VendaService.adicionarItem` |
| RN.16 | Acessório sem serial baixa a linha inteira do estoque; a quantidade tem que bater com a da linha | `VendaService.adicionarItem` |
| RN.17 | O serial lido precisa ser da mesma linha de estoque escolhida (impede vender IMEI com preço de outro) | `VendaService.adicionarItem` |
| RN.18 | Pagamento não pode exceder o saldo restante da venda | `VendaService.adicionarPagamento` |
| RN.19 | Finalizar exige ≥ 1 item e soma dos pagamentos igual ao total; a venda vai para `PENDENTE` | `VendaService.finalizar` |
| RN.20 | Transições válidas ficam no enum `StatusVenda` (diagrama na seção 3); fora delas, `TransicaoStatusInvalidaException` | `StatusVenda` |
| RN.21 | Cancelar a venda (de qualquer status não terminal, inclusive `CONCLUIDA`) devolve a `DISPONIVEL` todas as unidades reservadas ou vendidas; remover o item do carrinho também | `VendaService.liberarEstoqueDaVenda` |
| RN.22 | Avaliação de procedência `Improcedente` cancela a venda e libera o estoque | `VendaService.avaliarProcedencia` |
| RN.49 | Carrinho (`ABERTA`) sem atividade há mais de `syscor.carrinho.expiracao` (padrão 24h) é cancelado automaticamente e libera as unidades reservadas. Conta como atividade qualquer mudança na venda (itens, dados iniciais, pagamentos). Edição do carrinho e expiração travam a venda (`FOR UPDATE`) para não concorrerem | `ExpiracaoCarrinhoScheduler`, `VendaService.expirarCarrinho` |

**Estoque**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.23 | `tb_estoque_geral` é a única fonte da verdade (catálogo + unidade + baixa) | V19 |
| RN.24 | Serial único, normalizado em maiúsculas e sem espaços | `idx_serial_unico`, `EstoqueGeralService` |
| RN.50 | Na importação, célula de texto com marcador de vazio (`''`, `""`, `'`, `"`, `-`, `null`) é tratada como vazia. Uma linha só é acessório se `codigo_acessorio` tiver valor real; com marcador de vazio, o item é aparelho | `EstoqueUtil.textoVazio` / `ehAcessorio`, `ConversorValores.texto`, V23 |
| RN.25 | Item nasce `DISPONIVEL` na filial `DP01` | V19 |
| RN.26 | A baixa usa `SELECT … FOR UPDATE` + `@Version`: duas vendas do mesmo IMEI nunca passam juntas | `EstoqueGeralRepository.findBySerialForUpdate` |
| RN.27 | Ciclo da unidade no PDV: carrinho `DISPONIVEL → RESERVADO`; venda `CONCLUIDA` `RESERVADO → VENDIDO` com `data_venda`; remoção/cancelamento `RESERVADO`/`VENDIDO → DISPONIVEL` (limpa `data_venda`). `DEFEITO` nunca volta por esse caminho | `EstoqueGeral`, `EstoqueGeralService` |
| RN.28 | Modelo crítico quando o saldo disponível é ≤ estoque mínimo | `EstoqueGeralService.listarAlertas` |
| RN.29 | Importação do relatório Vivo e da tabela de preços resolve colunas por nome; coluna obrigatória ausente gera erro de negócio, linhas ruins entram na lista de erros sem abortar o lote | `EstoqueGeralService`, `LeitorTabular` |

**Inventário SAP e tabela de preços**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.30 | Cada upload do IQ09 gera uma `InventarioExecucao` com totalizadores | `InventarioService` |
| RN.31 | Cada linha vira `CORRETO` (disponível aqui e no SAP), `DIVERGENCIA` (estados diferentes, ou disponível aqui e ausente no SAP) ou `PENDENTE` (no SAP, nunca cadastrado aqui) | `ResultadoConciliacaoInventario` |
| RN.32 | Linha da tabela da Telefônica sem SKU correspondente é mantida com `encontrado = false` | `TabelaPrecoService` |

**Protocolo documental**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.33 | No máximo um protocolo por venda (único em `venda_id`) | V9 |
| RN.34 | `situacaoServico` (`CONFIRMADO`/`CANCELADO`) e `gerarComissao` (padrão `true`) independem do status da venda | `ProtocoloDocumental` |

**Financeiro**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.35 | Caixa abre por filial + PDV com saldo inicial por forma de pagamento | `CaixaSessaoService.abrir` |
| RN.36 | Fechar exige o valor conferido de cada forma; caixa já fechado não fecha de novo, aberto não reabre | `CaixaSessaoService` |
| RN.37 | **(a fazer)** Impedir dois caixas abertos no mesmo PDV e filial | — |
| RN.38 | Webhook só é aceito autenticado: BB por mTLS com certificado A1 + IP permitido (a implementar; hoje compara um parâmetro), banco próprio por segredo; rejeitado vira log `REJEITADO` | `WebhookIngestService`, `BancoAdapter` |
| RN.39 | Idempotência em duas camadas: hash SHA-256 do payload e `provider + idExterno` da transação | `WebhookIngestService`, `MatchingService` |
| RN.40 | Matching por identificador de pagamento; código de barras como alternativa | `MatchingService` |
| RN.41 | Valor igual baixa o título (`CONCILIADA`); diferente marca `DIVERGENTE` sem baixar; título já baixado = `DUPLICADA`; sem título = `NAO_ENCONTRADA` | `MatchingService` |
| RN.42 | Webhook é processado após o commit (evento assíncrono) e reprocessado a cada 60 s se ficou pendente | `WebhookListener`, `ConciliacaoProcessor` |
| RN.43 | Comissão do consolidado Vivo é `LIBERADO` só quando a venda existe pelo número de acesso e tem protocolo GED preenchido com serviço `CONFIRMADO`; senão `PENDENTE_SISTEMA` | `RemuneracaoVariavelService` |

**Mailing e dashboard**

| ID | Regra | Onde |
| --- | --- | --- |
| RN.44 | Campanha: `RASCUNHO → ENVIANDO → CONCLUIDA` ou `FALHOU` | `CampanhaMailingService` |
| RN.45 | Disparar só enfileira; o listener RabbitMQ envia um destinatário por vez, com intervalo de 1 s | `MailingWhatsAppListener` |
| RN.46 | Sem credenciais do WhatsApp o envio falha com `NegocioException` (o boot não falha) | `WhatsAppApiClient` |
| RN.47 | Faturamento do mês conta só vendas `CONCLUIDA`; meta fixa em R$ 85.000,00 **(tornar configurável)** | `DashboardService` |
| RN.48 | Evolução dos últimos 6 meses em milhares; atividades recentes = 5 últimas vendas | `DashboardService` |

## 5. Requisitos

### 5.1 Requisitos funcionais

| ID | Requisito | Status |
| --- | --- | --- |
| RF.01 | Login com JWT e troca de senha no primeiro acesso | Feito |
| RF.02 | Cadastro e listagem de usuários/vendedores e filiais | Feito |
| RF.03 | Perfis de acesso e autorização por rota | A fazer |
| RF.04 | Cadastro de cliente PF/PJ com endereço, RG/IE e consentimento; busca paginada | Feito |
| RF.05 | Edição e inativação de cliente | A fazer |
| RF.06 | Carrinho de venda: abrir, editar dados iniciais, adicionar/remover item e pagamento, resumo | Feito |
| RF.07 | Finalizar venda, avançar status e avaliar procedência | Feito |
| RF.08 | Busca de vendas com filtros (filial, vendedor, número, período, cliente, acesso, serial, simcard, acessório) | Feito |
| RF.09 | Importar relatório de estoque Vivo e tabela de preços para o estoque geral | Feito |
| RF.10 | Consultar estoque disponível por serial, modelo, marca ou filial; consolidado e alertas | Feito |
| RF.11 | Importar IQ09 do SAP e conciliar | Feito |
| RF.12 | Importar tabela de preços da Telefônica por oferta | Feito |
| RF.13 | Protocolo documental por venda | Feito |
| RF.14 | Abrir, fechar, reabrir e listar caixas | Feito |
| RF.15 | Receber webhooks bancários, conciliar, listar logs, reprocessar e acompanhar em tempo real (SSE) | Feito |
| RF.16 | CRUD de títulos a pagar/receber via API (o service existe, falta controller) | A fazer |
| RF.17 | Importar consolidado Vivo e classificar remuneração | Feito |
| RF.18 | Campanhas de WhatsApp: criar, importar planilha, disparar, listar | Feito |
| RF.19 | Painel executivo | Feito (filtros parciais) |
| RF.20 | Auditoria de alterações (quem mudou o quê) | A fazer |
| RF.21 | Exportar relatórios em Excel/PDF | A fazer |
| RF.22 | Emissão fiscal NF-e/NFC-e | A fazer (épico futuro) |

### 5.2 Requisitos não funcionais

| ID | Requisito | Situação |
| --- | --- | --- |
| RNF.01 | Autenticação stateless via JWT; todas as rotas exigem token, exceto `/auth/login` e `/webhooks/**` | Feito |
| RNF.02 | Segredos só por variável de ambiente (`SYSCOR_JWT_SECRET`, segredos de webhook, WhatsApp) | Feito |
| RNF.03 | Schema gerido só pelo Flyway (`ddl-auto: validate`) | Feito |
| RNF.04 | Lock otimista e pessimista no recurso disputado (serial) | Feito |
| RNF.05 | Entity separada de DTO, mapeamento manual com `from()` | Feito |
| RNF.06 | Erros padronizados por `GlobalExceptionHandler`, sem stack trace | Feito (com lacunas, seção 9) |
| RNF.07 | Fuso fixo `America/Sao_Paulo` | Feito |
| RNF.08 | Upload máximo de 25 MB | Feito |
| RNF.09 | Conformidade LGPD: consentimento, minimização e direito de exclusão | Parcial |
| RNF.10 | Cobertura de testes de serviço e integração (estoque e venda cobertos; demais módulos a fazer) | Em andamento |
| RNF.11 | Rate limit nos endpoints de importação | A fazer |
| RNF.12 | Documentação OpenAPI (springdoc já no `pom.xml`) | Feito |

### 5.3 Erros e códigos de resposta

Formato padrão devolvido pelo `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-09-21T18:30:00Z",
  "status": 422,
  "erro": "Unprocessable Entity",
  "mensagem": "Não é possível registrar uma venda sem itens",
  "campos": { "campo": "mensagem (só em erro de validação)" }
}
```

| HTTP | Quando acontece | Exceção |
| --- | --- | --- |
| 400 | Corpo inválido, JSON malformado, tipo errado no parâmetro, documento inválido | `MethodArgumentNotValidException`, `DocumentoInvalidoException` |
| 401 | Sem token, token expirado, ou senha do vendedor errada na reautenticação | filtro JWT, `AutenticacaoInvalidaException` |
| 403 | **(a fazer)** Usuário sem perfil para a rota | — |
| 404 | Recurso inexistente | `RecursoNaoEncontradoException` |
| 422 | Regra de negócio violada (carrinho não editável, venda sem item, transição inválida, duplicidade, serial indisponível) | `BusinessException` e subclasses |
| 500 | Erro não tratado (mensagem genérica) | `Exception` |

## 6. Modelo de dados

O schema tem 24 migrations Flyway (V1 a V24) em `src/main/resources/db/migration`. Nenhuma migration já aplicada deve ser editada: toda mudança entra como V25 em diante.

| Tabela | Módulo | Conteúdo | Migrations |
| --- | --- | --- | --- |
| `clientes` | venda | PF/PJ, documento único, contato, endereço, RG/IE, consentimento LGPD | V1, V12, V15 |
| `filiais` | venda | Lojas da rede | V2, V17 |
| `usuarios` | venda | Vendedores: login, e-mail, `senha_hash`, cargo, filial, prestadora, `primeiro_acesso`, `ativo` | V2, V17, V18 |
| `vendas` | venda | Cabeçalho: `numero_venda`, cliente, filial, usuário, total, status, procedência, score | V1–V3, V13 |
| `itens_venda` | venda | Itens com categoria, valores, campos telecom (plano, DDD, acesso, GED, simcard) e `estoque_geral_id` | V1, V4, V8, V13, V14, V19, V20 |
| `pagamentos_venda` | venda | Pagamentos por forma | V8 |
| `tb_estoque_geral` | estoque | Unidade de estoque: classificação, serial único, filial, compra, custos, status (`DISPONIVEL`, `VENDIDO`, `RESERVADO`, `DEFEITO`), `version` | V19, V21, V22, V23 |
| `tb_inventario_execucao` / `tb_inventario_item` | estoque | Execuções e linhas da conciliação SAP | V5 |
| `tb_tabela_preco_execucao` | estoque | Uma linha por upload da tabela da Telefônica; a mais recente é a vigente (`vigencia_em DATE`) | V6, V24 |
| `tb_preco_item` | estoque | Preço por Nome Comercial × tabela (`REGULAR`/`RENOVA`) × grupo do plano: cheio, PIX e 13x–21x (`parcelas JSONB`) | V24 |
| `tb_plano_vivo` / `tb_produto_vivo` | estoque | Abas PLANOS (plano → grupo de preço) e PRODUTOS (Cod_Sap → Nome Comercial), por execução | V24 |
| `tb_produto_preco_plano` | estoque | **Fora de uso desde a V24** (será removida numa migration futura) | V6, V19 |
| `tb_protocolo_documental` | gestao | Protocolo 1:1 com a venda | V9 |
| `tb_campanha_mailing` / `tb_destinatario_mailing` | mailing | Campanhas e destinatários | V7 |
| `tb_titulo_financeiro` | financeiro | Títulos a pagar/receber, identificador de pagamento, código de barras | V10, V16 |
| `tb_execucao_remuneracao` / `tb_item_remuneracao` | financeiro | Importações do consolidado Vivo e classificação por linha | V10 |
| `tb_caixa_sessao` / `tb_forma_pagamento_caixa` | financeiro | Sessão de caixa e saldo por forma | V11 |
| `webhook_log` / `transacao_bancaria` | financeiro | Webhooks recebidos e transações conciliadas | V16 |

**Relacionamentos principais**

- `vendas` N:1 `clientes`, `filiais`, `usuarios`; `vendas` 1:N `itens_venda` (cascade) e `pagamentos_venda`; `vendas` 1:1 `tb_protocolo_documental`.
- `itens_venda` N:1 `tb_estoque_geral` (nulo para serviço).
- `tb_inventario_item` N:1 `tb_estoque_geral` (nulo quando `PENDENTE`).
- `tb_item_remuneracao.venda_id` aponta para a venda localizada pelo número de acesso.
- `transacao_bancaria` N:1 `webhook_log` e N:1 `tb_titulo_financeiro`.

**Enums de domínio (valores exatos do código)**

| Enum | Valores |
| --- | --- |
| `StatusVenda` | ABERTA, PENDENTE, APROVADA, CONCLUIDA, CANCELADA |
| `CategoriaItemVenda` | PRODUTO\_VIVO, SERVICO\_VIVO, ACESSORIO, RECARGA |
| `FormaPagamento` | CARTAO\_CREDITO, CARTAO\_DEBITO, PIX, DINHEIRO, BOLETO, VOUCHER (com `codigo_voucher`, V25) |
| `StatusAvaliacaoProcedencia` | Improcedente, Procedente, Em\_avaliacao\_pelo\_BKO, Nao\_avaliado |
| `StatusScoreCliente` | NAO\_REALIZADA, CONSULTANDO, APROVADO, REPROVADO |
| `TipoPessoa` | FISICA, JURIDICA |
| `StatusEstoque` | DISPONIVEL, VENDIDO, RESERVADO, DEFEITO |
| `ResultadoConciliacaoInventario` | CORRETO, DIVERGENCIA, PENDENTE |
| `SituacaoServico` | CONFIRMADO, CANCELADO |
| `StatusCampanha` / `StatusEnvio` | RASCUNHO, ENVIANDO, CONCLUIDA, FALHOU / PENDENTE, ENVIADO, FALHA |
| `TipoTitulo` / `StatusTitulo` | RECEBER, PAGAR / ABERTO, BAIXADO, DIVERGENTE |
| `ResultadoConciliacao` | CONCILIADA, DIVERGENTE, NAO\_ENCONTRADA, DUPLICADA |
| `StatusWebhook` | RECEBIDO, PROCESSADO, ERRO, REJEITADO |
| `StatusCaixaSessao` | ABERTO, FECHADO |
| `StatusRemuneracao` | LIBERADO, PENDENTE\_SISTEMA, PENDENTE\_VIVO |

**Notas de projeto:** `TIMESTAMP` sem fuso + `hibernate.jdbc.time_zone: America/Sao_Paulo`; `globally_quoted_identifiers: true`; `numero_venda` vem da sequence `seq_numero_venda`.

## 7. Contratos de API

Todas as rotas usam o prefixo `/api` (`server.servlet.context-path`) e exigem `Authorization: Bearer <token>`, exceto `POST /auth/login` e `/webhooks/**`. Swagger UI via springdoc.

**Autenticação**

| Método | Rota | Corpo | Retorno |
| --- | --- | --- | --- |
| POST | `/auth/login` | `{ "login": "", "senha": "" }` (aceita `email`/`usuario`) | 200 `{ token, tipo, expiraEmSegundos, primeiroAcesso }` |
| POST | `/auth/alterar-senha` | `{ "senhaAtual": "", "novaSenha": "" }` | 200 |

**Cadastros**

| Método | Rota | Descrição |
| --- | --- | --- |
| POST / GET | `/clientes` | Cadastrar / buscar paginado (`?busca=&page=&size=`) |
| GET | `/clientes/{id}` | Buscar cliente |
| POST / GET | `/filiais` | Cadastrar / listar ativas |
| GET | `/filiais/{id}` | Buscar filial |
| POST / GET | `/usuarios` | Cadastrar / listar ativos |
| GET | `/usuarios/{id}` | Buscar usuário |

**Vendas**

| Método | Rota | Descrição |
| --- | --- | --- |
| POST | `/vendas` | Abrir carrinho (`ABERTA`) |
| GET | `/vendas` | Listar com filtros (`filialId`, `vendedorId`, `numeroVenda`, `dataInicio`, `dataFim`, `cliente`, `numeroAcesso`, `serialProdutoVivo`, `serialSimcard`, `modeloAcessorio`) |
| GET / PUT | `/vendas/{id}` | Buscar / atualizar dados iniciais (filial, vendedor, score) |
| POST | `/vendas/{id}/itens` | Adicionar item (reserva o serial; a baixa é na conclusão) |
| DELETE | `/vendas/{id}/itens/{itemId}` | Remover item (estorna o serial) |
| POST | `/vendas/{id}/pagamentos` | Registrar pagamento |
| DELETE | `/vendas/{id}/pagamentos/{pagamentoId}` | Remover pagamento (remover o último voucher desliga o Renova) |
| PATCH | `/vendas/{id}/renova` | Liga/desliga a TABELA RENOVA nos aparelhos (`{ ativo }`; exige voucher) |
| GET | `/vendas/{id}/resumo` | Totais e saldo do carrinho |
| PATCH | `/vendas/{id}/finalizar` | Finalizar (exige reautenticação) |
| PATCH | `/vendas/{id}/status` | Avançar status (exige reautenticação) |
| PATCH | `/vendas/{id}/avaliacao-procedencia` | Avaliação do BKO |

**Estoque**

| Método | Rota | Descrição |
| --- | --- | --- |
| POST | `/estoque-geral/importacoes/relatorio` | Importar relatório de estoque Vivo (multipart `arquivo`) |
| POST | `/estoque-geral/importacoes/precos` | Atualizar preços (multipart `arquivo`) |
| GET | `/estoque-geral/disponiveis` | Unidades disponíveis (`?busca=&filial=&limite=`) |
| GET | `/estoque-geral/disponiveis/{serial}` | Unidade disponível por serial |
| GET | `/estoque-geral/consolidado` | Saldo por modelo |
| GET | `/estoque-geral/alertas` | Modelos críticos |
| GET | `/produtos`, `/produtos/{id}` | Catálogo (derivado do estoque geral) |
| POST | `/estoque/inventario/importar-sap` | Importar IQ09 e conciliar |
| GET | `/estoque/inventario/{execucaoId}` | Resumo da execução |
| POST | `/produtos/precos/importar-telefonica` | Importar tabela da Telefônica (relatório da importação) |
| GET | `/produtos/precos/vigente` | Execução vigente e totais |
| GET | `/produtos/precos/planos?busca=&disponivelAlta=` | Planos da vigente (combo "Plano Ativo / Linha") |
| GET | `/produtos/precos/item?estoqueId=&plano=&tabela=&pagamento=&parcelas=` | Preço de uma unidade (422 se sem preço) |
| GET | `/produtos/precos/execucoes/{id}`, `/produtos/precos/produtos/{produtoId}`, `/produtos/precos/ofertas`, `/produtos/precos?oferta=` | **Deprecados**: formato antigo, respondendo da tabela nova (oferta = grupo do plano) |

**Documental, mailing e dashboard**

| Método | Rota | Descrição |
| --- | --- | --- |
| PUT / GET | `/documental/protocolos/{vendaId}` | Salvar / buscar protocolo da venda |
| GET | `/documental/protocolos?numeroProtocoloGed=` | Buscar por número GED |
| POST / GET | `/mailing/campanhas` | Criar / listar campanhas |
| GET | `/mailing/campanhas/modelo-planilha` | Baixar modelo de planilha |
| POST | `/mailing/campanhas/importar-planilha` | Importar destinatários |
| POST | `/mailing/campanhas/{id}/disparar` | Disparar (assíncrono) |
| GET | `/mailing/campanhas/{id}` | Buscar campanha |
| GET | `/dashboard/executivo?filialId=` | Painel executivo |

**Financeiro**

| Método | Rota | Descrição |
| --- | --- | --- |
| POST | `/financeiro/caixa/abrir` | Abrir caixa (filial, PDV, saldos iniciais) |
| GET | `/financeiro/caixa/abertos`, `/financeiro/caixa/fechados` | Listar por filial |
| GET | `/financeiro/caixa/{id}` | Detalhe |
| POST | `/financeiro/caixa/{id}/fechar`, `/financeiro/caixa/{id}/reabrir` | Fechar com conferência / reabrir |
| GET | `/financeiro/contas-pagar` | Títulos a pagar |
| GET | `/financeiro/conciliacao/logs?tamanho=50` | Logs de webhook |
| POST | `/financeiro/conciliacao/logs/{id}/reprocessar` | Reprocessar log |
| GET | `/financeiro/conciliacao/stream` | Eventos em tempo real (SSE) |
| POST | `/financeiro/remuneracao/importar-consolidado` | Importar consolidado Vivo |
| GET | `/financeiro/remuneracao/{execucaoId}` | Resultado da apuração |
| POST | `/webhooks/banco-brasil`, `/webhooks/banco-proprio` | Entrada de webhooks (público, validado por assinatura) |

## 8. Estado atual por módulo

O backend está funcional nos sete módulos, mas o README está atrasado em relação ao código: ele ainda descreve `tb_produto`/`tb_item_estoque`, migrations até V9, segurança com `permitAll()` e não menciona o módulo financeiro.

| Módulo | Pacote | O que existe | O que falta |
| --- | --- | --- | --- |
| Segurança | `security` | JWT (60 min), login, troca de senha no primeiro acesso, CORS por `app.cors.allowed-origins`, cadeia separada para webhooks | Perfis/RBAC, bloqueio por tentativas, refresh token, endpoint `/auth/me` |
| Venda | `venda` | Carrinho completo, pagamentos, máquina de estados, reautenticação, procedência, filtros | Edição/inativação de cliente, filial e usuário; expiração de carrinhos abandonados |
| Estoque | `estoque` | Estoque geral unificado, importações Vivo/preços/SAP/Telefônica, consolidado e alertas | Transferência entre filiais, movimentação manual (avaria, devolução), histórico de movimentos |
| Documental | `gestao` | Protocolo 1:1 com a venda | Anexos (S3), fila de pendências GED |
| Financeiro | `financeiro` | Caixa, conciliação bancária por webhook com SSE, remuneração variável, `TituloFinanceiroService` | Controller de títulos (criar, listar, baixar, reabrir), contas a receber, relatórios |
| Mailing | `mailing` | Campanhas WhatsApp com RabbitMQ | Webhook de status de entrega da Meta, opt-out |
| Dashboard | `dashboard` | Painel executivo por filial | Meta configurável, filtros de regional/período/categoria |

**Infraestrutura**

- `Dockerfile` multi-stage e `Docker-compose.yml` com `syscor-app` (8080), `syscor-db` (PostgreSQL 16, 5432), `syscor-pgadmin` (5050) e `syscor-rabbitmq` (5672/15672).
- CI no GitHub Actions (`.github/workflows/CI.yml`): `mvn -B clean verify` com PostgreSQL 16 em push/PR para `develop` e `main`. CD existe só como `CD.yml.disabled`.
- Testes: apenas `SyscorApplicationTests.contextLoads` com Testcontainers.

## 9. Divergências e bugs encontrados no código

Em ordem de risco. Os itens 1 a 4 podem quebrar em produção.

| # | Problema | Evidência | Correção sugerida |
| --- | --- | --- | --- |
| 1 | Enum `StatusEstoque` tem `DEFEITO`, mas o `CHECK` da V19 aceita `AVARIADO` | `StatusEstoque.java` x `chk_estoque_geral_status` | ✅ **Corrigido (V21).** `CHECK` aceita `DEFEITO`; linhas `AVARIADO` foram convertidas |
| 2 | Sem RBAC: qualquer token válido acessa financeiro, importações e cadastro de usuário | `UsuarioDetailsService.papel()` já gera `ROLE_<CARGO>` (ex.: GERENTE DE ÁREA → `ROLE_GERENTE_DE_AREA`), mas nenhuma rota usa `hasRole` | Levantar os cargos com `SELECT DISTINCT cargo FROM usuarios`, montar a matriz cargo × rota e aplicar `@EnableMethodSecurity` + `@PreAuthorize` |
| 3 | Webhook do BB autentica comparando o parâmetro `hmac` com um segredo, mas o BB autentica por **mTLS com certificado A1** e não envia segredo | `BancoDoBrasilAdapter.autenticar` | Exigir certificado de cliente do BB: terminar o mTLS no proxy/load balancer (ou `server.ssl.client-auth=need` no Tomcat), validar a cadeia da AC do BB, repassar o subject do certificado ao app e checar no `BancoDoBrasilAdapter`; manter allowlist de IP e remover o `hmac` |
| 4 | O carrinho baixa o serial para `VENDIDO` no momento em que o item entra, antes de a venda concluir | `VendaService.adicionarItem` chama `baixarNaVenda` | ✅ **Corrigido (V22 + RN.49).** `RESERVADO` ao adicionar, `VENDIDO` ao concluir, volta a `DISPONIVEL` ao remover/cancelar; carrinho parado por mais de 24h é cancelado automaticamente |
| 5 | `DocumentoInvalidoException` e `RecursoNaoEncontradoException` estendem `RuntimeException` (têm handler próprio, ok); o README diz o contrário | `exception/` | Atualizar README |
| 6 | Login com usuário inexistente após autenticar lança `RuntimeException` genérica (500) | `AuthController.login` | Lançar `AutenticacaoInvalidaException` |
| 7 | `alterar-senha` não confere `senhaAtual` | `AuthController.alterarSenha` | Validar com `passwordEncoder.matches` antes de trocar |
| 8 | Abrir caixa não impede dois caixas abertos no mesmo PDV | `CaixaSessaoService.abrir` | Checar sessão aberta + índice único parcial |
| 9 | Meta do dashboard fixa em R$ 85.000,00 | `DashboardService` | Tabela de metas por nível da hierarquia de cargos e mês |
| 10 | Dependências sem uso: Envers, S3, POI, openhtmltopdf, Spring Retry, Quartz, MapStruct | `pom.xml` | Usar nos épicos previstos ou remover |
| 11 | Arquivos de lixo versionados: `boot.log`, `body.json`, `{`, `[Help`, `erros-branch.txt`, `contexto-estoque.txt` | raiz do repo | Remover e incluir no `.gitignore` |
| 12 | `tb_inventario_item.item_estoque_id` manteve o nome antigo, enquanto `itens_venda` foi renomeado para `estoque_geral_id` (V20) | V19, V20, `InventarioItem` | Padronizar o nome numa V21 (opcional) |
| 13 | README desatualizado (estoque, segurança, financeiro, migrations) | `README.md` | Reescrever a partir deste documento |
| 14 | Primeiro acesso aceita a senha fixa 00000000 para qualquer usuário com primeiroAcesso = true | UsuarioDetailsService.loadUserByUsername | Gerar senha temporária aleatória por usuário (gravada em hash, com validade) e obrigar a troca |

## 10. Plano de construção

A ordem segue o backlog do produto (Épico 0 → Clientes → Estoque → Vendas → Financeiro → Documental → Fiscal → Integrações → BI), começando pelos riscos da seção 9. Cada etapa termina com testes passando no CI.

1. **Estabilização (1 sprint)**
   - ✅ V21: trocar `AVARIADO` por `DEFEITO` no `chk_estoque_geral_status` (bug 1).
   - Corrigir `AuthController` (bugs 6 e 7), a autenticação do webhook BB por mTLS (bug 3) e a senha fixa de primeiro acesso (bug 14).
   - Remover arquivos de lixo e reescrever o README a partir deste documento.
   - Critério de aceite: `./mvnw verify` verde, `docker compose up --build` sobe e `GET /api/actuator/health` retorna `UP` (liberar /actuator/health no SecurityConfig).
2. **Épico 0 — Fundação**
   - Perfis = os cargos reais de `usuarios.cargo`, que o `UsuarioDetailsService` já converte em `ROLE_<CARGO>`. Levantar com `SELECT DISTINCT cargo FROM usuarios`, padronizar os nomes (tabela de cargos com nível hierárquico) e incluir o papel no JWT.
   - Matriz cargo × rota com `@EnableMethodSecurity` + `@PreAuthorize`, e erro 403 padronizado.
   - `GET /auth/me`, bloqueio após 5 senhas erradas, auditoria com Envers nas entidades de venda, estoque e financeiro.
3. **Cadastros (Épico 3)**
   - Editar e inativar cliente, filial e usuário (`PUT`, `PATCH /{id}/inativar`, soft delete).
   - LGPD: revogação de consentimento e anonimização sob pedido.
4. **Estoque e Vendas (Épicos 2 e 3)**
   - ✅ Reserva no carrinho (`RESERVADO`) com baixa em `CONCLUIDA` (bug 4) e expiração de carrinhos abandonados após 24h (RN.49).
   - Transferência entre filiais e histórico de movimentação do serial.
5. **Financeiro (Épico 1)**
   - Controller de títulos (`/financeiro/titulos`: criar, listar, baixar, reabrir) sobre o `TituloFinanceiroService` existente.
   - Impedir dois caixas abertos no mesmo PDV (bug 8); relatório de fechamento.
   - Metas por nível da hierarquia de cargos e mês, com o dashboard mostrando a meta do nível de quem está logado (bug 9).
6. **Documental (Épico 4)** — anexos do protocolo no S3 e fila de pendências GED.
7. **Fiscal (Épico 6)** — NF-e/NFC-e e SPED (requer escolha de provedor).
8. **Integrações e BI (Épicos 5 e 7)** — webhook de status da Meta, exportações Excel/PDF com POI e openhtmltopdf, visões para o Power BI.

**Decisões (03/11/2026)**

- [x] O aparelho deve ficar `RESERVADO` no carrinho e só virar `VENDIDO` na conclusão? Sim.
- [x] Qual o nome correto do status de avaria: `DEFEITO` ou `AVARIADO`? Defeito
- [x] Qual o esquema oficial de assinatura do webhook do Banco do Brasil? mTLS com certificado digital A1 (não há secret em cabeçalho).
- [x] Quais perfis existem na operação real e o que cada um pode ver? Os cargos da tabela usuarios (levantar no banco).
- [x] A meta é por filial, por regional ou por vendedor? Uma meta para cada nível da hierarquia.

## 10.1 Fase 1 — Venda (detalhada)

A venda é a primeira fase. Ela só fica utilizável no PDV depois de corrigir a busca por IMEI e o preço vindo da tabela da Telefônica.

**Problemas encontrados no código**

| # | Sintoma no PDV | Causa no código | Correção |
| --- | --- | --- | --- |
| V1 | Digita o IMEI e o produto não aparece para selecionar | `EstoqueGeralService.buscarDisponiveis` e `listarCatalogo` chamam `repository.buscarDisponiveis(status, termo, filial, page)`. Com 4 argumentos o Java escolhe a sobrecarga `(status, categoria, termo, page)`: o IMEI vira filtro de **categoria** e a busca volta vazia. O commit `b3deace` criou `buscarDisponiveisPorFiltro` para isso, mas o service não foi trocado | ✅ **Corrigido.** Os dois métodos chamam `buscarDisponiveisPorFiltro(status, serial, termo, filial, page)`: `serial` = termo normalizado com `serial()` (comparado com o IMEI), `termo` = texto com `trim()` (modelo/marca/código, preserva espaços como em "Galaxy A15"); sobrecargas antigas apagadas; testes em `EstoqueGeralRepositoryTest` (Testcontainers, IMEI completo e parcial) e `EstoqueGeralServiceBuscaTest` |
| V2 | Item que entrou no estoque depois da última tabela fica sem preço | `TabelaPrecoService.importar` gravava o preço só nas unidades existentes no upload | ✅ **Corrigido (V24).** O upload atualiza `valor_venda` de todas as unidades `DISPONIVEL`/`RESERVADO` e `importarRelatorioVivo` aplica o preço vigente ao estoque novo |
| V3 | Combo "Tabela de Preço" vazio ou com preços antigos | `tb_produto_preco_plano` apontava para uma unidade "representante" e não filtrava pela importação mais recente | ✅ **Corrigido (V24).** Preço guardado por Nome Comercial (`chave_nome`) em `tb_preco_item`, sempre lido da execução vigente |
| V4 | O preço do item pode ser digitado/alterado pelo front | `VendaService.adicionarItem` aceitava `valorUnitario` do request | ✅ **Corrigido.** Aparelho e acessório têm o preço calculado no backend (plano, tabela, pagamento); o valor do front é ignorado |
| V5 | Consolidado de estoque pode falhar | `consolidarPorModelo` usa o literal `'AVARIADO'` | ✅ **Corrigido.** A query usa as constantes do enum (`StatusEstoque.DEFEITO`); o DTO expõe `defeito` e mantém `avariado` como alias `@Deprecated` até o syscor-frontend migrar |

**Regra de preço (implementada na V24)**

O preço de um aparelho depende de: produto (Nome Comercial) + grupo do plano do cliente + tabela (Regular/Renova) + forma de pagamento.

1. Cada upload da tabela da Telefônica cria uma nova execução; a mais recente é a **vigente**.
2. Estoque → Nome Comercial: `codigo_acessorio` presente em PRODUTOS.Cod_Sap usa aquele nome; senão, o `modelo_comercial` normalizado. Plano → grupo pelo `GRUPAMENTO ATUAL` da aba PLANOS (nome, COD SISTEMA ou COD SAP); sem plano/pré-pago = `PRE`. Tabela: todo item entra pela `REGULAR`; o aparelho com preço por plano vai para a `RENOVA` quando a venda está com o Renova ligado (`ResolvedorTabela`, ver regra 6). Itens que só existem nas abas sem plano usam `SEM_PLANO`/`REGULAR`. Pagamento: PIX → `preco_pix`; crédito até 12x, débito, dinheiro → `preco`; 13x–21x → `parcelas`. Chip com preço fixo `syscor.precos.chip` (13,00). A "Oferta de Comunicação" da planilha não entra no cálculo.
3. `valor_venda` (referência) = preço `REGULAR`/`PRE` cheio da vigente, atualizado a cada upload em todas as unidades `DISPONIVEL` e `RESERVADO` e ao importar estoque novo; unidades `VENDIDO` não mudam (guardam o preço da época, também em `itens_venda`).
4. Unidade sem preço na tabela vigente não pode ser vendida: `adicionarItem` responde 422 "Item sem preço na tabela vigente (<nome comercial>)."
5. O upload devolve `estoqueSemPreco` (modelos do estoque sem preço) e `planilhaSemEstoque` (itens da planilha sem estoque), para correção do cadastro. A importação é toda numa transação: se falhar, a vigente continua a anterior.
6. **Renova (V25, decisão de 04/11/2026):** o aparelho passa para a `RENOVA` com Desconto fabricante + Voucher. O vendedor lança o pagamento `VOUCHER` (valor + código) e chama `PATCH /vendas/{id}/renova`; sem voucher → 422; remover o último voucher desliga o Renova. Só aparelho com preço por plano muda (acessório, chip, serviço e `SEM_PLANO` não). Pré-pago não tem Renova → 422 "Renova não disponível para plano pré-pago.". A coluna `DESCONTO` do bloco Renova vai para `tb_preco_item.desconto_fabricante`; o preço Renova já tem o desconto, então `vendas.desconto_fabricante` (soma dos aparelhos na Renova) é só controle e não abate o total. A resposta da venda traz `renova`, `descontoFabricante`, `saldo` e `aviso` (pagamentos acima do total após reprecificar).

**Ordem de execução no Claude Code**

1. V1 (busca por IMEI) — correção pequena, destrava o PDV.
2. V21: `DEFEITO` no `CHECK` + V5.
3. ✅ Reserva no carrinho: `RESERVADO` ao adicionar, `VENDIDO` ao concluir (bug 4), com expiração de carrinhos abandonados após 24h (RN.49).
4. ✅ V2, V3 e V4: preço pela tabela vigente (V24).
5. Testes de `VendaService` e `EstoqueGeralService` cobrindo RN.11 a RN.27.

## 11. Instruções para o Claude Code

Salve este documento no repositório (por exemplo `docs/ESPECIFICACAO.md`, exportando como Markdown) e crie um `CLAUDE.md` na raiz com o conteúdo abaixo.

```markdown
# CLAUDE.md — GestaoEmpresarial4R (syscor)

## Contexto
Backend do ERP da 4R Redes (varejo Vivo, 85 lojas). Especificação completa em docs/ESPECIFICACAO.md — leia antes de qualquer mudança.
Frontend React/Vite em repositório separado (syscor-frontend); não quebre contratos de API sem avisar.

## Stack
Java 21, Spring Boot 3.3.4, PostgreSQL 16, Flyway, RabbitMQ, JWT (jjwt), Lombok, springdoc. Build: ./mvnw.

## Regras de código
- Organização por módulo de domínio (venda, estoque, gestao, financeiro, mailing, dashboard, security), e dentro dele controller/service/repository/model/dto/enums.
- Controller só traduz HTTP; regra de negócio fica no Service; máquina de estados fica no enum.
- Nunca exponha Entity na API: use ResponseDTO com método estático from().
- Erro de negócio = exceção que estende BusinessException (vira 422 no GlobalExceptionHandler).
- Schema só via Flyway: nunca edite V1..V20; crie a próxima versão (V21+). ddl-auto continua validate.
- Enum Java e CHECK do banco precisam ter exatamente os mesmos literais.
- Toda reserva/baixa de serial passa por EstoqueGeralService (FOR UPDATE + @Version).
- Valores monetários em BigDecimal, comparados com compareTo.
- Segredos só por variável de ambiente.
- Mensagens de erro e nomes de domínio em português.

## Como trabalhar
1. Antes de codar, diga quais arquivos vai mudar e por quê.
2. Toda regra nova ganha teste (JUnit 5 + Mockito no service; Testcontainers para repositório/integração).
3. Rode ./mvnw verify antes de concluir.
4. Commits no padrão feat:/fix:/refactor:/docs:, em português.
5. Atualize docs/ESPECIFICACAO.md e README.md quando mudar regra, endpoint ou tabela.
```

**Primeiro pedido sugerido ao Claude Code**

```text
Leia CLAUDE.md e docs/ESPECIFICACAO.md. Execute a etapa 1 (Estabilização) da seção 10:
corrija os bugs 1, 3, 6, 7 e 14 da seção 9 com migration V21 e testes, remova os arquivos de lixo
da raiz e reescreva o README. Antes de alterar, liste o plano. Depois, para a etapa 2, rode SELECT DISTINCT cargo FROM usuarios
no banco de dev e me proponha a matriz cargo x rota e os níveis de hierarquia para as metas.
```
