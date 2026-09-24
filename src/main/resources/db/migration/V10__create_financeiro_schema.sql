-- V10__create_financeiro_schema.sql
-- Financeiro: contas a pagar/receber (US-101/102/104) + cruzamento de
-- remuneração variável (consolidado Vivo x protocolo documental/GED).
-- Numeração: no repositório real do projeto o V9 é o protocolo documental,
-- então este é o próximo (V10). Ajuste o número se a numeração real do seu
-- projeto já tiver avançado desde a última vez que conferimos.

CREATE TABLE tb_titulo_financeiro (
                                      id            BIGSERIAL PRIMARY KEY,
                                      tipo          VARCHAR(10)  NOT NULL CHECK (tipo IN ('RECEBER', 'PAGAR')),
                                      descricao     VARCHAR(200) NOT NULL,
                                      quem          VARCHAR(150) NOT NULL,
                                      vencimento    DATE         NOT NULL,
                                      valor         NUMERIC(12,2) NOT NULL,
                                      status        VARCHAR(10)  NOT NULL DEFAULT 'ABERTO' CHECK (status IN ('ABERTO', 'BAIXADO')),
                                      data_baixa    DATE,
                                      venda_id      BIGINT REFERENCES vendas (id),
                                      criado_em     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_titulo_financeiro_tipo_status ON tb_titulo_financeiro (tipo, status);
CREATE INDEX idx_titulo_financeiro_vencimento ON tb_titulo_financeiro (vencimento);

CREATE TABLE tb_execucao_remuneracao (
                                         id                     BIGSERIAL PRIMARY KEY,
                                         nome_arquivo           VARCHAR(255),
                                         total_linhas           INTEGER   NOT NULL DEFAULT 0,
                                         total_liberados        INTEGER   NOT NULL DEFAULT 0,
                                         total_glosados         INTEGER   NOT NULL DEFAULT 0,
                                         total_pendentes_vivo   INTEGER   NOT NULL DEFAULT 0,
                                         criado_em              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tb_item_remuneracao (
                                     id                   BIGSERIAL PRIMARY KEY,
                                     execucao_id          BIGINT       NOT NULL REFERENCES tb_execucao_remuneracao (id),
                                     id_transacao_vivo    VARCHAR(50),
                                     numero_acesso_vivo   VARCHAR(20),
                                     cliente_vivo         VARCHAR(150),
                                     cpf_vivo             VARCHAR(20),
                                     plano_vivo           VARCHAR(100),
                                     valor_comissao       NUMERIC(12,2),
                                     data_venda_vivo      DATE,
                                     status               VARCHAR(20)  NOT NULL
                                         CHECK (status IN ('LIBERADO', 'GLOSADO_GED', 'PENDENTE_VIVO')),
                                     diagnostico          VARCHAR(300),
                                     venda_id             BIGINT REFERENCES vendas (id)
);

CREATE INDEX idx_item_remuneracao_execucao ON tb_item_remuneracao (execucao_id);
CREATE INDEX idx_item_remuneracao_status ON tb_item_remuneracao (status);