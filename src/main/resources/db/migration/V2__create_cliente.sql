CREATE TABLE cliente (
                         id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         tipo_pessoa                     VARCHAR(10)  NOT NULL,
                         nome                            VARCHAR(150) NOT NULL,
                         cpf_cnpj                        VARCHAR(20)  NOT NULL,
                         email                           VARCHAR(150),
                         telefone                        VARCHAR(20),
                         consentimento_lgpd              BOOLEAN      NOT NULL DEFAULT FALSE,
                         data_consentimento              TIMESTAMP,
                         data_revogacao_consentimento    TIMESTAMP,
                         ativo                           BOOLEAN      NOT NULL DEFAULT TRUE,
                         criado_em                       TIMESTAMP    NOT NULL DEFAULT now(),
                         atualizado_em                   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_cliente_cpf_cnpj ON cliente (cpf_cnpj);