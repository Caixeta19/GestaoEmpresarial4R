CREATE TABLE plano (
   id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   tipo_plano_id  UUID NOT NULL REFERENCES tipo_plano(id),
   nome  VARCHAR(150) NOT NULL,
   valor  DECIMAL(10,2)  NOT NULL,
   ativo  BOOLEAN  NOT NULL DEFAULT TRUE,
   criado_em TIMESTAMP  NOT NULL DEFAULT now(),
   atualizado_em  TIMESTAMP NOT NULL DEFAULT now()
);