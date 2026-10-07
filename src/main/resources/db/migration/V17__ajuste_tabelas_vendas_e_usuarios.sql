-- =========================================================================
-- Migration: Suporte à tela "Início" da venda e atualização da tabela usuários
-- =========================================================================

-- 1. Criação da tabela de filiais
CREATE TABLE IF NOT EXISTS filiais (
                                       id      BIGSERIAL PRIMARY KEY,
                                       codigo  VARCHAR(20)  NOT NULL,
    nome    VARCHAR(150) NOT NULL,
    ativo   BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_filial_codigo UNIQUE (codigo)
    );

-- 2. Atualização ou criação segura da tabela de usuários com os novos campos
-- (Se a tabela já existir de migrações anteriores, adicionamos as colunas que faltam)
CREATE TABLE IF NOT EXISTS usuarios (
                                        id              BIGSERIAL PRIMARY KEY,
                                        nome            VARCHAR(150) NOT NULL,
    login           VARCHAR(150),
    email           VARCHAR(150) NOT NULL,
    senha_hash      VARCHAR(100) NOT NULL,
    filial          VARCHAR(255),
    nome_prestadora VARCHAR(255),
    cnpj_prestadora VARCHAR(50),
    pdv_pagamento   VARCHAR(100),
    celular         VARCHAR(50),
    cargo           VARCHAR(100),
    situacao        VARCHAR(50),
    cpf             VARCHAR(50),
    rg              VARCHAR(50),
    rua             VARCHAR(255),
    numero          VARCHAR(20),
    bairro          VARCHAR(255),
    cep             VARCHAR(20),
    uf              VARCHAR(10),
    cidade          VARCHAR(255),
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_usuario_email UNIQUE (email)
    );

-- Caso a tabela já exista sem as novas colunas, garantimos o DDL idempotente:
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS nome_prestadora VARCHAR(255);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS cnpj_prestadora VARCHAR(50);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS pdv_pagamento VARCHAR(100);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS celular VARCHAR(50);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS situacao VARCHAR(50);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS cpf VARCHAR(50);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS rg VARCHAR(50);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS rua VARCHAR(255);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS numero VARCHAR(20);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS bairro VARCHAR(255);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS cep VARCHAR(20);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS uf VARCHAR(10);
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS cidade VARCHAR(255);

-- 3. Adição das colunas complementares na tabela vendas (caso ainda não existam)
ALTER TABLE vendas
    ADD COLUMN IF NOT EXISTS filial_id                 BIGINT,
    ADD COLUMN IF NOT EXISTS usuario_id                BIGINT,
    ADD COLUMN IF NOT EXISTS estoque_avancado          BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS status_score_cliente      VARCHAR(20) NOT NULL DEFAULT 'NAO_REALIZADA',
    ADD COLUMN IF NOT EXISTS numero_serie_nota         VARCHAR(10),
    ADD COLUMN IF NOT EXISTS numero_nota               VARCHAR(20);

-- 4. Adição de chaves estrangeiras e restrições de validação em vendas
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_vendas_filial') THEN
ALTER TABLE vendas ADD CONSTRAINT fk_vendas_filial FOREIGN KEY (filial_id) REFERENCES filiais (id);
END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_vendas_usuario') THEN
ALTER TABLE vendas ADD CONSTRAINT fk_vendas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id);
END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_vendas_status_score_cliente') THEN
ALTER TABLE vendas ADD CONSTRAINT chk_vendas_status_score_cliente
    CHECK (status_score_cliente IN ('NAO_REALIZADA', 'CONSULTANDO', 'APROVADO', 'REPROVADO'));
END IF;
END $$;

-- 5. Criação de índices para otimização de consultas nas vendas
CREATE INDEX IF NOT EXISTS idx_vendas_filial_id ON vendas (filial_id);
CREATE INDEX IF NOT EXISTS idx_vendas_usuario_id ON vendas (usuario_id);