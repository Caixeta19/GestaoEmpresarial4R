CREATE TABLE tb_protocolo_documental (
                                         id                       BIGSERIAL PRIMARY KEY,
                                         venda_id                 BIGINT       NOT NULL UNIQUE REFERENCES vendas (id),
                                         numero_protocolo_ged     VARCHAR(50),
                                         data_digitalizacao       DATE,
                                         zerar_remuneracao        BOOLEAN,
                                         gerar_price              BOOLEAN,
                                         vencimento               VARCHAR(2),
                                         lider_equipe             VARCHAR(100),
                                         observacoes              TEXT,
                                         observacoes_importacao   TEXT,
                                         situacao_servico         VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMADO'
                                             CHECK (situacao_servico IN ('CONFIRMADO', 'CANCELADO')),
                                         gerar_comissao           BOOLEAN      NOT NULL DEFAULT TRUE,
                                         motivos_cancelamento     TEXT,
                                         criado_em                TIMESTAMP    NOT NULL DEFAULT now(),
                                         atualizado_em            TIMESTAMP
);

CREATE INDEX idx_protocolo_documental_numero_ged ON tb_protocolo_documental (numero_protocolo_ged);