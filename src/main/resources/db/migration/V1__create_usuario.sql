CREATE TABLE usuario (
      id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      nome VARCHAR(150) NOT NULL,
      email VARCHAR(150) NOT NULL,
      senha_hash VARCHAR(255) NOT NULL,
      perfil  VARCHAR(30)  NOT NULL,
      ativo  BOOLEAN  NOT NULL DEFAULT TRUE,
      criado_em  TIMESTAMP    NOT NULL DEFAULT now(),
      atualizado_em  TIMESTAMP NOT NULL DEFAULT now()
);

-- email é o login: precisa ser único, e a comparação deve ignorar caixa
CREATE UNIQUE INDEX uk_usuario_email ON usuario (LOWER(email));