-- Status de avaria do estoque passa a ser DEFEITO (decisão registrada em docs/ESPECIFICACAO.md, seção 9, bug 1).
-- O enum StatusEstoque já usa DEFEITO; o CHECK da V19 aceitava AVARIADO, então gravar DEFEITO violava a constraint.
-- Literais do CHECK precisam ser idênticos aos do enum Java.

ALTER TABLE tb_estoque_geral DROP CONSTRAINT chk_estoque_geral_status;

UPDATE tb_estoque_geral SET status = 'DEFEITO' WHERE status = 'AVARIADO';

ALTER TABLE tb_estoque_geral
    ADD CONSTRAINT chk_estoque_geral_status
        CHECK (status IN ('DISPONIVEL', 'VENDIDO', 'RESERVADO', 'DEFEITO'));
