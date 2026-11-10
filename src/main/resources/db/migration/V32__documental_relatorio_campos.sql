-- Relatório de Gestão Documental (docs/gestao.md): campos que a vw_gestao_documental precisa e o banco não tinha.
-- Nada é preenchido aqui: os valores de filial (área e rede) vêm do cadastro da 4R, e o Adabas já é filiais.codigo.

ALTER TABLE filiais
    ADD COLUMN area      VARCHAR(60),
    ADD COLUMN rede_id   INTEGER,
    ADD COLUMN rede_nome VARCHAR(100);

ALTER TABLE tb_protocolo_documental
    ADD COLUMN ativacao_primeira_chamada          BOOLEAN,
    ADD COLUMN data_ativacao                      DATE,
    ADD COLUMN data_baixa                         DATE,
    ADD COLUMN numero_protocolo_ged_portabilidade VARCHAR(50),
    ADD COLUMN data_instalacao                    DATE,
    ADD COLUMN alterado_por_id                    BIGINT,
    ADD CONSTRAINT fk_protocolo_documental_alterado_por FOREIGN KEY (alterado_por_id) REFERENCES usuarios (id);
