V19__create_estoque_geral.sql
-- Estoque unificado: tb_estoque_geral passa a ser a ÚNICA fonte da verdade para
-- catálogo, controle unitário (serial/IMEI) e baixa no PDV.
-- Substitui tb_produto + tb_item_estoque (dados migrados abaixo, IDs preservados).
-- Sem tabelas de staging/temporárias/auxiliares.

-- 1. TABELA OFICIAL ---------------------------------------------------------
CREATE TABLE tb_estoque_geral (
                                  id                      BIGSERIAL     PRIMARY KEY,

    -- Identificação e classificação
                                  tipo_produto            VARCHAR(60),
                                  categoria               VARCHAR(80),
                                  subcategoria            VARCHAR(80),
                                  marca                   VARCHAR(80),
                                  modelo_comercial        VARCHAR(200),
                                  modelo_dpgc             VARCHAR(200),
                                  cor                     VARCHAR(60),
                                  ncm                     VARCHAR(20),

    -- Controle físico / unitário
                                  serial                  VARCHAR(60)   NOT NULL,
                                  codigo_acessorio        VARCHAR(60),
                                  numero_acesso           VARCHAR(30),

    -- Logística e compras
                                  filial_estoque          VARCHAR(100)  DEFAULT 'DP01',
                                  num_compra              VARCHAR(40),
                                  data_compra             DATE,
                                  data_emissao_nota       DATE,
                                  filial_compra           VARCHAR(100),
                                  fornecedor_compra       VARCHAR(200),

    -- Métricas temporais
                                  tempo_na_rede           VARCHAR(50),
                                  dias_na_rede            INTEGER,
                                  tempo_no_pdv            VARCHAR(50),
                                  dias_no_pdv             INTEGER,
                                  quantidade              INTEGER       NOT NULL DEFAULT 1,

    -- Valores e custos
                                  valor_venda             NUMERIC(12,2),
                                  valor_custo             NUMERIC(12,2),
                                  custo_ultima_compra_un  NUMERIC(12,2),
                                  custo_medio_un          NUMERIC(12,2),

    -- Controle operacional
                                  status                  VARCHAR(20)   NOT NULL DEFAULT 'DISPONIVEL',
                                  criado_em               TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
                                  data_venda              TIMESTAMP WITHOUT TIME ZONE,
                                  version                 BIGINT        NOT NULL DEFAULT 0,   -- lock otimista (@Version)

                                  CONSTRAINT idx_serial_unico            UNIQUE (serial),
                                  CONSTRAINT chk_estoque_geral_status    CHECK (status IN ('DISPONIVEL', 'VENDIDO', 'RESERVADO', 'AVARIADO')),
                                  CONSTRAINT chk_estoque_geral_quantidade CHECK (quantidade >= 0)
);

CREATE INDEX idx_estoque_geral_status_modelo ON tb_estoque_geral (status, (upper(modelo_comercial)));
CREATE INDEX idx_estoque_geral_filial_status ON tb_estoque_geral (filial_estoque, status);
CREATE INDEX idx_estoque_geral_codigo_acessorio ON tb_estoque_geral (codigo_acessorio);

-- 2. MIGRAÇÃO DOS DADOS EXISTENTES (tb_item_estoque x tb_produto) ------------
INSERT INTO tb_estoque_geral (
    id, tipo_produto, categoria, modelo_comercial, serial, filial_estoque,
    quantidade, valor_venda, status, criado_em, data_venda, version)
SELECT i.id,
       p.categoria,
       p.categoria,
       p.descricao,
       upper(regexp_replace(i.serial_imei, '\s+', '', 'g')),
       COALESCE(i.deposito_sap, 'DP01'),
       1,
       p.preco_base,
       i.status,
       i.data_entrada,
       i.data_saida,
       i.version
FROM tb_item_estoque i
         JOIN tb_produto p ON p.id = i.produto_id;

SELECT setval(pg_get_serial_sequence('tb_estoque_geral', 'id'),
              COALESCE((SELECT MAX(id) FROM tb_estoque_geral), 0) + 1, false);

-- 3. REAPONTA AS FKs QUE APONTAVAM PARA tb_item_estoque ----------------------
DO $$
DECLARE r RECORD;
BEGIN
FOR r IN SELECT conrelid::regclass AS tabela, conname
         FROM pg_constraint
         WHERE contype = 'f' AND confrelid = 'tb_item_estoque'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tabela, r.conname);
END LOOP;
END $$;

ALTER TABLE itens_venda
    ADD CONSTRAINT fk_itens_venda_estoque_geral
        FOREIGN KEY (item_estoque_id) REFERENCES tb_estoque_geral (id);

ALTER TABLE tb_inventario_item
    ADD CONSTRAINT fk_inventario_item_estoque_geral
        FOREIGN KEY (item_estoque_id) REFERENCES tb_estoque_geral (id);

-- 4. TABELA DE PREÇO POR OFERTA: deixa de depender de tb_produto -------------
-- O vínculo passa a ser o modelo comercial (texto normalizado), não um SKU.
ALTER TABLE tb_produto_preco_plano ADD COLUMN modelo_comercial VARCHAR(200);
UPDATE tb_produto_preco_plano SET modelo_comercial = nome_comercial;
ALTER TABLE tb_produto_preco_plano DROP COLUMN produto_id;
ALTER TABLE tb_produto_preco_plano DROP COLUMN sku_resolvido;
CREATE INDEX idx_produto_preco_plano_modelo ON tb_produto_preco_plano (upper(modelo_comercial));

-- 5. REMOVE O MODELO ANTIGO --------------------------------------------------
DROP TABLE tb_item_estoque;
DROP TABLE tb_produto;