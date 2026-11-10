-- Relatório de Gestão Documental: uma linha por item de serviço Vivo (item com número de acesso e serviço), e não
-- por venda. Mesma estrutura da chview_gestao_documental_mt_4redes do sistema anterior: as 45 primeiras colunas, com
-- estes nomes e nesta ordem (inclusive 33/34/35 = primeira, terceira, segunda fatura), são o CSV da exportação.
-- Colunas sem origem no sistema entram como NULL tipado. As colunas técnicas do fim só servem aos filtros.
-- O protocolo é por venda: os campos dele se repetem em cada serviço da mesma venda.
-- vendas.criado_em/finalizado_em guardam o Instant em UTC (Hibernate 6); a view os mostra na hora de São Paulo.
-- tipo_plano segue o enum TipoPlano (grupo do preço; sem grupo ou SEM_PLANO, o segmento); um teste confere os dois.

CREATE VIEW vw_gestao_documental AS
SELECT
    i.id                                                                   AS "ID do Serviço",
    v.numero_venda                                                         AS "ID da Venda",
    i.tipo_servico                                                         AS "Serviço",
    i.plano                                                                AS "Plano",
    f.area                                                                 AS "Área de loja",
    f.rede_id                                                              AS "REDE_ID",
    f.rede_nome                                                            AS "REDE",
    f.id                                                                   AS "PDV_ID",
    f.nome                                                                 AS "PDV",
    f.codigo                                                               AS "Adabas",
    c.nome                                                                 AS "Cliente",
    CASE c.tipo_pessoa WHEN 'FISICA' THEN 'PF' WHEN 'JURIDICA' THEN 'PJ' END AS "Tipo Cliente",
    c.cpf_cnpj                                                             AS "CPF/CNPJ do cliente",
    u.nome                                                                 AS "Vendedor",
    CASE
        WHEN u.ativo AND upper(coalesce(u.cargo, '')) <> 'INATIVO' AND upper(coalesce(u.situacao, '')) <> 'INATIVO'
            THEN 'Ativo'
        ELSE 'Inativo'
    END                                                                    AS "Status Vendedor",
    (coalesce(v.finalizado_em, v.criado_em) AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo' AS "Data da Venda",
    (v.criado_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo'      AS "DATA_SYS",
    p.ativacao_primeira_chamada                                            AS "Ativação da Primeira Chamada",
    i.numero_acesso                                                        AS "Nº de Acesso",
    p.data_ativacao                                                        AS "Data de Ativação",
    p.data_baixa                                                           AS "Data de Baixa",
    p.zerar_remuneracao                                                    AS "Zerar Remuneração",
    p.data_digitalizacao                                                   AS "Data de Digitalização",
    p.numero_protocolo_ged                                                 AS "Nº Protocolo GED",
    p.numero_protocolo_ged_portabilidade                                   AS "Nº Protocolo GED (Portabilidade)",
    CASE coalesce(v.avaliacao_procedencia, 'Nao_avaliado')
        WHEN 'Improcedente' THEN 'Improcedente'
        WHEN 'Procedente' THEN 'Procedente'
        WHEN 'Em_avaliacao_pelo_BKO' THEN 'Em avaliação pelo BKO'
        ELSE 'Não avaliado'
    END                                                                    AS "Status BKO",
    p.observacoes                                                          AS "Observação",
    p.observacoes_importacao                                               AS "Observação Importação",
    p.situacao_servico                                                     AS "Situação do Serviço",
    replace(p.motivos_cancelamento, ';', ', ')                             AS "Motivo do Cancelamento",
    p.gerar_comissao                                                       AS "Gerar comissão de serviço do vendedor",
    (SELECT sum(r.valor_comissao)
       FROM tb_item_remuneracao r
      WHERE r.venda_id = v.id
        AND r.status = 'LIBERADO'
        AND regexp_replace(r.numero_acesso_vivo, '[^0-9]', '', 'g') = regexp_replace(i.numero_acesso, '[^0-9]', '', 'g')
    )::NUMERIC(12, 2)                                                      AS "Valor Remuneração",
    NULL::VARCHAR(50)                                                      AS "status_primeira_fatura",
    NULL::VARCHAR(50)                                                      AS "status_terceira_fatura",
    NULL::VARCHAR(50)                                                      AS "status_segunda_fatura",
    NULL::DATE                                                             AS "data_primeira_fatura",
    NULL::DATE                                                             AS "data_segunda_fatura",
    NULL::DATE                                                             AS "data_terceira_fatura",
    NULL::NUMERIC(12, 2)                                                   AS "valor_receita",
    NULL::NUMERIC(12, 2)                                                   AS "valor_considerado_tfp",
    NULL::NUMERIC(12, 2)                                                   AS "valor_penalizado_tfp",
    CASE WHEN v.status = 'CANCELADA' THEN uc.nome END                      AS "Usuário Cancelamento",
    ui.nome                                                                AS "Usuário Inserção",
    ua.nome                                                                AS "Usuário Alteração",
    p.data_instalacao                                                      AS "Data Instalação",
    -- colunas técnicas (filtros; não vão para o CSV)
    v.id                                                                   AS venda_id,
    v.filial_id                                                            AS filial_id,
    v.status                                                               AS status_venda,
    CASE
        WHEN i.grupo_plano = 'PRE' THEN 'PRE'
        WHEN i.grupo_plano IN ('CONTROLE_BTL', 'CONTROLE_ENTRADA', 'CONTROLE_ALTO_VALOR') THEN 'CONTROLE'
        WHEN i.grupo_plano IN ('POS_INDIVIDUAL', 'FAMILIA_2', 'FAMILIA_3', 'FAMILIA_4_5', 'VIVO_V', 'MULTIVIVO',
                               'VIM_8GB', 'VIM_14GB', 'VIM_20GB', 'VIM_40GB', 'VIM_100GB') THEN 'POS'
        WHEN i.grupo_plano IS NOT NULL AND i.grupo_plano <> 'SEM_PLANO' THEN NULL
        WHEN translate(lower(trim(i.segmento)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') LIKE 'pre%' THEN 'PRE'
        WHEN translate(lower(trim(i.segmento)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') LIKE 'controle%' THEN 'CONTROLE'
        WHEN translate(lower(trim(i.segmento)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') LIKE 'pos%' THEN 'POS'
    END                                                                    AS tipo_plano,
    (coalesce(v.finalizado_em, v.criado_em) AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo' AS data_lancamento,
    v.usuario_id                                                           AS vendedor_id,
    coalesce(v.avaliacao_procedencia, 'Nao_avaliado')                      AS status_bko,
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
WHERE nullif(trim(i.numero_acesso), '') IS NOT NULL
  AND nullif(trim(i.tipo_servico), '') IS NOT NULL;
