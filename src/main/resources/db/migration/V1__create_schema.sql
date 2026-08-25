CREATE TABLE clientes (
                          id                              BIGSERIAL PRIMARY KEY,
                          tipo_pessoa                     VARCHAR(20)  NOT NULL,
                          nome                            VARCHAR(150) NOT NULL,
                          cpf_cnpj                        VARCHAR(14)  NOT NULL,
                          email                           VARCHAR(150),
                          telefone                        VARCHAR(20),
                          ativo                           BOOLEAN      NOT NULL DEFAULT TRUE,

    -- LGPD / consentimento (US-301)
                          consentimento_marketing         BOOLEAN      NOT NULL DEFAULT FALSE,
                          consentimento_data_hora         TIMESTAMP,
                          consentimento_versao_termo      VARCHAR(20),

                          criado_em                       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          atualizado_em                   TIMESTAMPTZ  NOT NULL DEFAULT now(),

                          CONSTRAINT uk_cliente_cpf_cnpj UNIQUE (cpf_cnpj)
);

CREATE TABLE vendas (
                        id                                       BIGSERIAL PRIMARY KEY,
                        cliente_id                               BIGINT NOT NULL,
                        valor_total                              NUMERIC(12,2) NOT NULL DEFAULT 0,
                        status                                   VARCHAR(20) NOT NULL,
                        avaliacao_procedencia                    VARCHAR(20),
                        cliente_tinha_consentimento_marketing    BOOLEAN,

                        criado_em                                TIMESTAMPTZ NOT NULL DEFAULT now(),
                        atualizado_em                            TIMESTAMPTZ NOT NULL DEFAULT now(),

                        CONSTRAINT fk_venda_cliente FOREIGN KEY (cliente_id)
                            REFERENCES clientes (id)
);

CREATE TABLE itens_venda (
                             id                   BIGSERIAL PRIMARY KEY,
                             venda_id             BIGINT NOT NULL,
                             produto_id           BIGINT NOT NULL,
                             descricao_produto    VARCHAR(150) NOT NULL,
                             quantidade           NUMERIC(12,3) NOT NULL,
                             valor_unitario       NUMERIC(12,2) NOT NULL,

                             CONSTRAINT fk_item_venda_venda FOREIGN KEY (venda_id)
                                 REFERENCES vendas (id)
                                 ON DELETE CASCADE
);

-- Índices auxiliares para consultas frequentes
CREATE INDEX idx_vendas_cliente_id ON vendas (cliente_id);
CREATE INDEX idx_itens_venda_venda_id ON itens_venda (venda_id);
CREATE INDEX idx_clientes_ativo ON clientes (ativo);