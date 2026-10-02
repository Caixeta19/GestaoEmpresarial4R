-- Procedencia: alinha o banco ao enum StatusAvaliacaoProcedencia
ALTER TABLE vendas ALTER COLUMN avaliacao_procedencia TYPE VARCHAR(30);

ALTER TABLE vendas DROP CONSTRAINT IF EXISTS chk_vendas_avaliacao_procedencia;

UPDATE vendas
SET avaliacao_procedencia = CASE avaliacao_procedencia
                                WHEN 'PROCEDENTE'   THEN 'Procedente'
                                WHEN 'IMPROCEDENTE' THEN 'Improcedente'
                                WHEN 'EM_AVALIACAO' THEN 'Em_avaliacao_pelo_BKO'
                                ELSE avaliacao_procedencia
    END
WHERE avaliacao_procedencia IS NOT NULL;

ALTER TABLE vendas ADD CONSTRAINT chk_vendas_avaliacao_procedencia
    CHECK (avaliacao_procedencia IS NULL OR avaliacao_procedencia IN
                                            ('Improcedente', 'Procedente', 'Em_avaliacao_pelo_BKO', 'Nao_avaliado'));

-- Categoria: adiciona ACESSORIO
ALTER TABLE itens_venda DROP CONSTRAINT IF EXISTS chk_itens_venda_categoria;

ALTER TABLE itens_venda ADD CONSTRAINT chk_itens_venda_categoria
    CHECK (categoria IN ('PRODUTO_VIVO', 'SERVICO_VIVO', 'ACESSORIO', 'RECARGA'));