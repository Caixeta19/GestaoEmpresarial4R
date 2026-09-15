-- V7__create_tabela_preco_plano_schema.sql
-- Importação da tabela de preço da Telefônica (planilha multi-aba). Escopo v1:
-- só TABELA REGULAR, um preço por (produto, oferta de comunicação/plano) —
-- sem forma de pagamento (PIX/parcelas) nem TABELA RENOVA (deferido).

CREATE TABLE tb_tabela_preco_execucao (
                                          id                          BIGSERIAL PRIMARY KEY,
                                          nome_arquivo                VARCHAR(255),
                                          vigencia_em                 VARCHAR(20),
                                          total_linhas_processadas    INTEGER     NOT NULL DEFAULT 0,
                                          total_produtos_atualizados  INTEGER     NOT NULL DEFAULT 0,
                                          total_nao_encontrados       INTEGER     NOT NULL DEFAULT 0,
                                          criado_em                   TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE tb_produto_preco_plano (
                                        id                    BIGSERIAL PRIMARY KEY,
                                        execucao_id           BIGINT      NOT NULL REFERENCES tb_tabela_preco_execucao (id),
                                        produto_id            BIGINT      REFERENCES tb_produto (id),
                                        sku_resolvido         VARCHAR(40),
                                        nome_comercial        VARCHAR(200),
                                        categoria_planilha    VARCHAR(60) NOT NULL,
    -- NULL = produto sem variação de preço por plano (Eletrônicos não conectados, Vitrine, Demo)
                                        oferta_comunicacao    VARCHAR(120),
                                        preco                 NUMERIC(12,2) NOT NULL,
                                        encontrado            BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_produto_preco_plano_execucao ON tb_produto_preco_plano (execucao_id);
CREATE INDEX idx_produto_preco_plano_produto ON tb_produto_preco_plano (produto_id);