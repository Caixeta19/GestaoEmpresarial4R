CREATE TABLE tipo_plano (
     id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     nome  VARCHAR(100) NOT NULL,
     ativo  BOOLEAN NOT NULL DEFAULT TRUE,
     criado_em TIMESTAMP NOT NULL DEFAULT now(),
     atualizado_em  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_tipo_plano_nome ON tipo_plano (nome);