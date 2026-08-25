CREATE TABLE loja (
   id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   nome VARCHAR(150) NOT NULL,
   cnpj  VARCHAR(20)  NOT NULL,
   ativo BOOLEAN  NOT NULL DEFAULT TRUE,
   criado_em  TIMESTAMP NOT NULL DEFAULT now(),
   atualizado_em  TIMESTAMP  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_loja_cnpj ON loja (cnpj);