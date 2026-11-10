-- Relatório de Gestão Documental no layout do sistema atual (docs/gestao.md): 55 colunas, uma linha por serviço, e
-- aparelho/acessório vendidos como "Troca de Aparelho". Campos que o layout pede e o banco não tinha:
--   * por linha (itens_venda): tipo de linha, valor do plano e do plano antigo, nº portado, fidelização e COD;
--   * por venda (tb_protocolo_documental): status GED, motivo de reprovação, instalação (feita, agendada, armário),
--     comissão de aparelho e data da última ativação.
-- Situação do serviço ganha NAO_AVALIADO (venda ainda sem protocolo). O padrão continua CONFIRMADO, porque a
-- remuneração variável só paga serviço confirmado.

ALTER TABLE itens_venda
    ADD COLUMN tipo_linha         VARCHAR(30),
    ADD COLUMN valor_plano        NUMERIC(12, 2),
    ADD COLUMN valor_plano_antigo NUMERIC(12, 2),
    ADD COLUMN numero_portado     VARCHAR(20),
    ADD COLUMN fidelizacao        BOOLEAN,
    ADD COLUMN cod                BOOLEAN;

ALTER TABLE tb_protocolo_documental
    ADD COLUMN status_ged               VARCHAR(30)
        CHECK (status_ged IN ('NAO_DIGITALIZADO', 'AGUARDANDO_DIGITALIZACAO', 'DIGITALIZADO', 'CANCELADO')),
    ADD COLUMN motivo_reprovacao        VARCHAR(200),
    ADD COLUMN servico_instalado        BOOLEAN,
    ADD COLUMN data_agendada_instalacao DATE,
    ADD COLUMN armario                  VARCHAR(60),
    ADD COLUMN comissao_aparelho        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN data_ultima_ativacao     DATE;

ALTER TABLE tb_protocolo_documental DROP CONSTRAINT IF EXISTS tb_protocolo_documental_situacao_servico_check;
ALTER TABLE tb_protocolo_documental
    ADD CONSTRAINT tb_protocolo_documental_situacao_servico_check
        CHECK (situacao_servico IN ('NAO_AVALIADO', 'CONFIRMADO', 'CANCELADO'));

DROP VIEW vw_gestao_documental;

-- As 55 primeiras colunas são o arquivo, com estes nomes e nesta ordem. Os valores saem tipados (DATE, NUMERIC,
-- BOOLEAN) e a exportação formata. As colunas técnicas do fim só servem a filtro e ordem.
-- vendas.*_em e tb_protocolo_documental.atualizado_em guardam o Instant em UTC (Hibernate 6): mostrados na hora de
-- São Paulo. tipo_plano segue o enum TipoPlano (grupo do preço; sem grupo ou SEM_PLANO, o segmento).
CREATE VIEW vw_gestao_documental AS
SELECT
    f.nome                                                                 AS "Filial",
    x.data_lancamento::DATE                                                AS "Data Venda(*)",
    p.data_ativacao                                                        AS "Data Ativação(**)",
    CASE x.status_bko
        WHEN 'Improcedente' THEN 'Improcedente'
        WHEN 'Procedente' THEN 'Procedente'
        WHEN 'Em_avaliacao_pelo_BKO' THEN 'Em avaliação pelo BKO'
        ELSE 'Não avaliado'
    END                                                                    AS "Status BKO(*)(**)",
    concat_ws(' ', x.servico_base,
              CASE WHEN x.servico_norm LIKE 'reativacao%' THEN NULL ELSE nullif(trim(i.tipo_linha), '') END,
              CASE x.tipo_plano WHEN 'PRE' THEN 'Pré' WHEN 'CONTROLE' THEN 'Controle' WHEN 'POS' THEN 'Pós' END)
                                                                           AS "Serviço(*)",
    v.numero_venda                                                         AS "ID Venda",
    CASE WHEN x.produto THEN i.descricao_produto END                       AS "Modelo",
    CASE WHEN x.produto THEN i.serial_imei END                             AS "IMEI",
    CASE WHEN x.tipo_plano = 'PRE' THEN 'PRE PAGO' ELSE i.plano END        AS "Plano",
    x.valor_plano                                                          AS "Valor plano(**)",
    coalesce(nullif(trim(i.plano_antigo), ''), '-')                        AS "Plano Antigo",
    x.valor_plano_antigo                                                   AS "Valor plano antigo(**)",
    CASE WHEN x.tipo_plano = 'PRE' THEN '0' ELSE coalesce(nullif(trim(i.vencimento_fatura), ''), p.vencimento) END
                                                                           AS "Vencimento Fatura(**)",
    u.nome                                                                 AS "Vendedor",
    c.nome                                                                 AS "Cliente",
    c.cpf_cnpj                                                             AS "CPF/CNPJ",
    i.numero_acesso                                                        AS "Nº Acesso(*)",
    i.numero_portado                                                       AS "Nº Portado",
    coalesce(p.servico_instalado, FALSE)                                   AS "Servico Instalado",
    p.data_agendada_instalacao                                             AS "Data Agendada de Instalação",
    p.armario                                                              AS "Armário",
    p.data_instalacao                                                      AS "Data Instalação",
    CASE p.situacao_servico
        WHEN 'CONFIRMADO' THEN 'Confirmado'
        WHEN 'CANCELADO' THEN 'Cancelado'
        ELSE 'Não avaliado'
    END                                                                    AS "Situação do serviço(**)",
    CASE
        WHEN x.servico_norm IN ('troca de simcard', 'troca de aparelho', 'troca de numero', 'troca de titularidade')
            THEN 0
        ELSE greatest(0, x.valor_plano - x.valor_plano_antigo)
    END::NUMERIC(12, 2)                                                    AS "Receita",
    CASE
        WHEN p.status_ged = 'NAO_DIGITALIZADO' THEN 'Serviço Não digitalizado'
        WHEN p.status_ged = 'AGUARDANDO_DIGITALIZACAO' THEN 'Aguardando digitalização do serviço'
        WHEN p.status_ged = 'DIGITALIZADO' THEN 'Serviço Digitalizado'
        WHEN p.status_ged = 'CANCELADO' THEN 'Serviço cancelado'
        -- sem status gravado, sai do que o protocolo já tem
        WHEN v.status = 'CANCELADA' OR p.situacao_servico = 'CANCELADO' THEN 'Serviço cancelado'
        WHEN p.data_digitalizacao IS NOT NULL THEN 'Serviço Digitalizado'
        WHEN nullif(trim(p.numero_protocolo_ged), '') IS NOT NULL THEN 'Aguardando digitalização do serviço'
        ELSE 'Serviço Não digitalizado'
    END                                                                    AS "Status GED",
    coalesce(nullif(trim(i.simcard_4g), ''), nullif(trim(i.simcard_3g), '')) AS "Sim Card",
    coalesce(i.cod, FALSE)                                                 AS "COD",
    coalesce(i.fidelizacao, FALSE)                                         AS "Fidelização",
    coalesce(p.zerar_remuneracao, FALSE)                                   AS "Zerar Remuneração",
    coalesce(p.gerar_price, FALSE)                                         AS "Gerar Price",
    i.num_ordem_next                                                       AS "Nº Solicitação NEXT / RPON",
    p.numero_protocolo_ged                                                 AS "Nº Protocolo GED(**)",
    p.numero_protocolo_ged_portabilidade                                   AS "Nº Protocolo GED Portabilidade (**)",
    p.motivo_reprovacao                                                    AS "Motivo Reprovação(**)",
    p.data_digitalizacao                                                   AS "Data Digitalização",
    ui.nome                                                                AS "Usuário Inserção",
    ua.nome                                                                AS "Usuário Alteração",
    p.observacoes                                                          AS "Observação",
    replace(p.motivos_cancelamento, ';', ', ')                             AS "Motivo Cancelamento(**)",
    coalesce(p.gerar_comissao, TRUE)                                       AS "Comissão de Serviço do Vendedor",
    coalesce(p.comissao_aparelho, TRUE)                                    AS "Comissão de Aparelho do Vendedor(**)",
    p.data_ultima_ativacao                                                 AS "Data Última Ativação(**)",
    (p.atualizado_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo'  AS "Data de alteração",
    CASE WHEN v.status = 'CANCELADA' THEN uc.nome END                      AS "Usuário Cancelamento",
    CASE WHEN v.status = 'CANCELADA'
        THEN ((v.status_alterado_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo')::DATE END
                                                                           AS "Data Cancelamento",
    p.lider_equipe                                                         AS "Líder de Equipe",
    NULL::VARCHAR(50)                                                      AS "Status Primeira Fatura",
    NULL::DATE                                                             AS "Data Primeira Fatura",
    NULL::VARCHAR(50)                                                      AS "Status Segunda Fatura",
    NULL::DATE                                                             AS "Data Segunda Fatura",
    NULL::VARCHAR(50)                                                      AS "Status Terceira Fatura",
    NULL::DATE                                                             AS "Data Terceira Fatura",
    NULL::NUMERIC(12, 2)                                                   AS "Valor Receita",
    NULL::NUMERIC(12, 2)                                                   AS "Valor Considerado TFP",
    NULL::NUMERIC(12, 2)                                                   AS "Valor Penalizado TFP",
    -- colunas técnicas (filtro e ordem; não vão para o arquivo)
    i.id                                                                   AS item_id,
    v.id                                                                   AS venda_id,
    v.filial_id                                                            AS filial_id,
    v.status                                                               AS status_venda,
    x.tipo_plano                                                           AS tipo_plano,
    x.servico_norm                                                         AS servico_norm,
    x.data_lancamento                                                      AS data_lancamento,
    v.usuario_id                                                           AS vendedor_id,
    x.status_bko                                                           AS status_bko,
    p.lider_equipe                                                         AS lider_equipe
FROM itens_venda i
         JOIN vendas v ON v.id = i.venda_id
         JOIN filiais f ON f.id = v.filial_id
         LEFT JOIN clientes c ON c.id = v.cliente_id
         JOIN usuarios u ON u.id = v.usuario_id
         LEFT JOIN tb_protocolo_documental p ON p.venda_id = v.id
         LEFT JOIN usuarios uc ON uc.id = v.status_alterado_por_id
         LEFT JOIN usuarios ui ON ui.id = v.finalizado_por_id
         LEFT JOIN usuarios ua ON ua.id = p.alterado_por_id
         CROSS JOIN LATERAL (
    SELECT i.categoria IN ('PRODUTO_VIVO', 'ACESSORIO')                                       AS produto,
           (coalesce(v.finalizado_em, v.criado_em) AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo' AS data_lancamento,
           coalesce(v.avaliacao_procedencia, 'Nao_avaliado')                                  AS status_bko
    ) a
         CROSS JOIN LATERAL (
    SELECT CASE
               WHEN i.grupo_plano = 'PRE' THEN 'PRE'
               WHEN i.grupo_plano IN ('CONTROLE_BTL', 'CONTROLE_ENTRADA', 'CONTROLE_ALTO_VALOR') THEN 'CONTROLE'
               WHEN i.grupo_plano IN ('POS_INDIVIDUAL', 'FAMILIA_2', 'FAMILIA_3', 'FAMILIA_4_5', 'VIVO_V', 'MULTIVIVO',
                                      'VIM_8GB', 'VIM_14GB', 'VIM_20GB', 'VIM_40GB', 'VIM_100GB') THEN 'POS'
               WHEN i.grupo_plano IS NOT NULL AND i.grupo_plano <> 'SEM_PLANO' THEN NULL
               WHEN translate(lower(trim(i.segmento)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') LIKE 'pre%' THEN 'PRE'
               WHEN translate(lower(trim(i.segmento)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') LIKE 'controle%' THEN 'CONTROLE'
               WHEN translate(lower(trim(i.segmento)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') LIKE 'pos%' THEN 'POS'
           END                                                                                 AS tipo_plano,
           CASE WHEN a.produto THEN 'Troca de Aparelho' ELSE trim(i.tipo_servico) END          AS servico_base
    ) b
         CROSS JOIN LATERAL (
    SELECT a.produto,
           a.data_lancamento,
           a.status_bko,
           b.tipo_plano,
           b.servico_base,
           translate(lower(b.servico_base), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc')  AS servico_norm,
           CASE WHEN b.tipo_plano = 'PRE' THEN 0 ELSE coalesce(i.valor_plano, 0) END::NUMERIC(12, 2) AS valor_plano,
           CASE WHEN nullif(trim(i.plano_antigo), '') IS NULL THEN 0 ELSE coalesce(i.valor_plano_antigo, 0) END::NUMERIC(12, 2)
                                                                                               AS valor_plano_antigo
    ) x
WHERE i.categoria IN ('PRODUTO_VIVO', 'ACESSORIO')
   OR (nullif(trim(i.numero_acesso), '') IS NOT NULL AND nullif(trim(i.tipo_servico), '') IS NOT NULL);
