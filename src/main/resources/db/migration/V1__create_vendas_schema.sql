-- V1__create_vendas_schema.sql
-- Módulo Vendas: Cliente, Venda (carrinho por categoria), ItemVenda.

CREATE TABLE clientes (
                          id                              BIGSERIAL PRIMARY KEY,
                          tipo_pessoa                     VARCHAR(20)  NOT NULL,
                          nome                            VARCHAR(150) NOT NULL,
                          cpf_cnpj                        VARCHAR(14)  NOT NULL,
                          email                           VARCHAR(150),
                          telefone                        VARCHAR(20),
                          ativo                           BOOLEAN      NOT NULL DEFAULT TRUE,
                          criado_em                       TIMESTAMP    NOT NULL DEFAULT now(),
                          atualizado_em                   TIMESTAMP    NOT NULL DEFAULT now(),
                          CONSTRAINT uk_cliente_cpf_cnpj UNIQUE (cpf_cnpj)
);

CREATE INDEX idx_clientes_cpf_cnpj ON clientes (cpf_cnpj);

CREATE TABLE vendas (
                        id                                          BIGSERIAL PRIMARY KEY,
                        cliente_id                                  BIGINT      NOT NULL REFERENCES clientes (id),
                        valor_total                                 NUMERIC(12,2) NOT NULL DEFAULT 0,
                        status                                       VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
                        avaliacao_procedencia                       VARCHAR(20),
                        cliente_tinha_consentimento_marketing       BOOLEAN,
                        criado_em                                   TIMESTAMP   NOT NULL DEFAULT now(),
                        atualizado_em                               TIMESTAMP   NOT NULL DEFAULT now(),
                        CONSTRAINT chk_vendas_status
                            CHECK (status IN ('ABERTA', 'PENDENTE', 'APROVADA', 'CONCLUIDA', 'CANCELADA')),
                        CONSTRAINT chk_vendas_avaliacao_procedencia
                            CHECK (avaliacao_procedencia IS NULL OR avaliacao_procedencia IN ('EM_AVALIACAO', 'PROCEDENTE', 'IMPROCEDENTE'))
);

CREATE INDEX idx_vendas_cliente_id ON vendas (cliente_id);
CREATE INDEX idx_vendas_status ON vendas (status);

CREATE TABLE itens_venda (
                             id                  BIGSERIAL PRIMARY KEY,
                             venda_id            BIGINT       NOT NULL REFERENCES vendas (id) ON DELETE CASCADE,
                             categoria           VARCHAR(20)  NOT NULL,
                             produto_id          BIGINT       NOT NULL,
                             descricao_produto   VARCHAR(150) NOT NULL,
                             quantidade          NUMERIC(12,3) NOT NULL CHECK (quantidade > 0),
                             valor_unitario      NUMERIC(12,2) NOT NULL CHECK (valor_unitario > 0),
                             CONSTRAINT chk_itens_venda_categoria
                                 CHECK (categoria IN ('PRODUTO_VIVO', 'SERVICO_VIVO', 'RECARGA'))
);

CREATE INDEX idx_itens_venda_venda_id ON itens_venda (venda_id);
CREATE INDEX idx_itens_venda_categoria ON itens_venda (categoria);