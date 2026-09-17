CREATE TABLE tb_campanha_mailing (
                                     id                     BIGSERIAL PRIMARY KEY,
                                     nome                   VARCHAR(150) NOT NULL,
                                     template_nome          VARCHAR(100) NOT NULL,
                                     template_idioma        VARCHAR(10)  NOT NULL DEFAULT 'pt_BR',
                                     status                 VARCHAR(20)  NOT NULL DEFAULT 'RASCUNHO'
                                         CHECK (status IN ('RASCUNHO', 'ENVIANDO', 'CONCLUIDA', 'FALHOU')),
                                     total_destinatarios    INTEGER      NOT NULL DEFAULT 0,
                                     total_enviados         INTEGER      NOT NULL DEFAULT 0,
                                     total_falhas           INTEGER      NOT NULL DEFAULT 0,
                                     criado_em              TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE tb_destinatario_mailing (
                                         id                     BIGSERIAL PRIMARY KEY,
                                         campanha_id            BIGINT       NOT NULL REFERENCES tb_campanha_mailing (id),
                                         cliente_id             BIGINT,
                                         telefone               VARCHAR(20)  NOT NULL,
                                         parametros_template    TEXT,
                                         status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE'
                                             CHECK (status IN ('PENDENTE', 'ENVIADO', 'FALHA')),
                                         whatsapp_message_id    VARCHAR(100),
                                         erro                   VARCHAR(500),
                                         enviado_em             TIMESTAMP
);

CREATE INDEX idx_destinatario_mailing_campanha ON tb_destinatario_mailing (campanha_id);
CREATE INDEX idx_destinatario_mailing_status ON tb_destinatario_mailing (status);