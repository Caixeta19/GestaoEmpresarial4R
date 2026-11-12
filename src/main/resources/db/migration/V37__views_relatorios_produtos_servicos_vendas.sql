-- Relatórios "Venda de Produtos", "Venda de Serviços" e "Vendas" (docs/relatorios_produtos_servicos.md).
-- Só views de leitura: nenhuma tabela muda. A venda aparece assim que é lançada (CONCLUIDA); a cancelada depois de
-- lançada sai de Produtos/Serviços pelo filtro de status e continua em "Vendas" com status CANCELADA. Carrinho
-- cancelado (nunca finalizado, finalizado_em nulo) não é venda e não entra.
--
-- vw_relatorio_itens: uma linha por item de venda que é produto (PRODUTO_VIVO, ACESSORIO, RECARGA) e/ou serviço
-- (SERVICO_VIVO, ou item com tipo_servico e nº de acesso, a mesma condição da vw_gestao_documental). O tipo de plano,
-- o serviço normalizado e a Receita vêm da vw_gestao_documental (V34), sem repetir a regra.
-- Datas e horas na hora de São Paulo (vendas.*_em guardam o Instant em UTC).

CREATE VIEW vw_relatorio_itens AS
SELECT
    i.id                                                                   AS item_id,
    v.id                                                                   AS venda_id,
    v.numero_venda                                                         AS numero_venda,
    v.filial_id                                                            AS filial_id,
    f.nome                                                                 AS filial,
    f.ativo                                                                AS filial_ativa,
    v.usuario_id                                                           AS vendedor_id,
    u.nome                                                                 AS vendedor,
    v.cliente_id                                                           AS cliente_id,
    c.nome                                                                 AS cliente,
    c.cpf_cnpj                                                             AS cpf_cnpj,
    c.uf                                                                   AS uf,
    v.status                                                               AS status_venda,
    coalesce(v.avaliacao_procedencia, 'Nao_avaliado')                      AS status_bko_cod,
    CASE v.avaliacao_procedencia
        WHEN 'Improcedente' THEN 'Improcedente'
        WHEN 'Procedente' THEN 'Procedente'
        WHEN 'Em_avaliacao_pelo_BKO' THEN 'Em avaliação pelo BKO'
        ELSE 'Não avaliado'
    END                                                                    AS status_bko,
    i.categoria                                                            AS categoria_item,
    i.categoria IN ('PRODUTO_VIVO', 'ACESSORIO', 'RECARGA')                AS eh_produto,
    (i.categoria = 'SERVICO_VIVO'
        OR (nullif(trim(i.numero_acesso), '') IS NOT NULL AND nullif(trim(i.tipo_servico), '') IS NOT NULL))
                                                                           AS eh_servico,
    -- Tipo de Produto do filtro: Sim Card pelo estoque (tipo_produto); sem critério para "Produto Open"
    CASE
        WHEN i.categoria = 'RECARGA' THEN 'RECARGA'
        WHEN translate(lower(coalesce(e.tipo_produto, '')), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc')
                 LIKE '%sim%card%' THEN 'SIM_CARD'
        WHEN i.categoria = 'ACESSORIO' THEN 'ACESSORIOS'
        WHEN i.categoria = 'PRODUTO_VIVO' THEN 'PRODUTO_VIVO'
    END                                                                    AS tipo_produto_filtro,
    a.data_venda                                                           AS data_venda,
    a.data_venda::TIME                                                     AS hora_venda,
    (v.criado_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo'      AS data_input,
    p.data_ativacao                                                        AS data_ativacao,
    CASE WHEN v.status = 'CANCELADA'
        THEN ((v.status_alterado_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo')::DATE END
                                                                           AS data_cancelamento,
    ui.nome                                                                AS usuario_autorizacao,
    -- produto
    e.marca                                                                AS marca,
    coalesce(e.modelo_comercial, i.descricao_produto)                      AS modelo_comercial,
    i.descricao_produto                                                    AS descricao_produto,
    e.modelo_dpgc                                                          AS modelo_dpgc,
    i.plano                                                                AS plano,
    i.serial_imei                                                          AS serial_imei,
    e.fornecedor_compra                                                    AS fornecedor,
    CASE WHEN e.valor_custo IS NOT NULL THEN e.valor_custo * i.quantidade END::NUMERIC(14, 2) AS valor_custo,
    i.desconto_fabricante                                                  AS desconto_fabricante,
    i.desconto                                                             AS desconto,
    i.valor_adicional                                                      AS valor_adicional,
    (i.valor_unitario * i.quantidade)::NUMERIC(14, 2)                      AS valor_venda,
    CASE WHEN e.valor_custo IS NOT NULL
        THEN (i.valor_unitario * i.quantidade - e.valor_custo * i.quantidade) END::NUMERIC(14, 2) AS valor_lucro,
    CASE WHEN coalesce(e.custo_ultima_compra_un, e.custo_medio_un) IS NOT NULL
        THEN coalesce(e.custo_ultima_compra_un, e.custo_medio_un) * i.quantidade END::NUMERIC(14, 2) AS custo_liquido,
    e.cor                                                                  AS cor,
    i.quantidade                                                           AS quantidade,
    e.data_compra                                                          AS data_compra,
    e.filial_compra                                                        AS filial_compra,
    e.ncm                                                                  AS ncm,
    e.codigo_acessorio                                                     AS sku,
    v.numero_nota                                                          AS numero_nota,
    v.numero_serie_nota                                                    AS serie_nota,
    e.data_emissao_nota                                                    AS data_emissao_nota,
    i.tabela_preco                                                         AS tabela_preco,
    i.numero_acesso                                                        AS numero_acesso,
    e.tipo_produto                                                         AS tipo_produto,
    e.categoria                                                            AS categoria_estoque,
    e.subcategoria                                                         AS subcategoria,
    v.condicao_pagamento                                                   AS condicao_pagamento,
    i.valor_unitario                                                       AS valor_produto,
    i.cupom                                                                AS cupom,
    v.codigo_voucher                                                       AS codigo_voucher,
    -- serviço
    i.tipo_servico                                                         AS tipo_servico,
    d.servico_norm                                                         AS servico_norm,
    d.tipo_plano                                                           AS tipo_plano,
    i.tipo_linha                                                           AS tipo_linha,
    i.valor_plano                                                          AS valor_plano,
    i.numero_portado                                                       AS numero_portado,
    i.sistema_origem                                                       AS sistema_origem,
    i.simcard_4g                                                           AS iccid_4g,
    i.simcard_3g                                                           AS iccid_3g,
    coalesce(nullif(trim(i.simcard_4g), ''), nullif(trim(i.simcard_3g), '')) AS iccid,
    i.vencimento_fatura                                                    AS vencimento_fatura,
    d."Receita"                                                            AS valor_receita,
    coalesce(i.fidelizacao, FALSE)                                         AS fidelizacao,
    i.grupo_plano                                                          AS grupo_plano,
    p.numero_protocolo_ged                                                 AS numero_protocolo_ged,
    p.data_digitalizacao                                                   AS data_digitalizacao,
    p.data_instalacao                                                      AS data_instalacao,
    coalesce(p.servico_instalado, FALSE)                                   AS servico_instalado,
    p.lider_equipe                                                         AS lider_equipe
FROM itens_venda i
         JOIN vendas v ON v.id = i.venda_id
         JOIN filiais f ON f.id = v.filial_id
         JOIN usuarios u ON u.id = v.usuario_id
         LEFT JOIN clientes c ON c.id = v.cliente_id
         LEFT JOIN usuarios ui ON ui.id = v.finalizado_por_id
         LEFT JOIN tb_estoque_geral e ON e.id = i.estoque_geral_id
         LEFT JOIN tb_protocolo_documental p ON p.venda_id = v.id
         LEFT JOIN vw_gestao_documental d ON d.item_id = i.id
         CROSS JOIN LATERAL (
    SELECT (coalesce(v.finalizado_em, v.criado_em) AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo' AS data_venda
    ) a
WHERE (v.status = 'CONCLUIDA' OR (v.status = 'CANCELADA' AND v.finalizado_em IS NOT NULL))
  AND (i.categoria IN ('PRODUTO_VIVO', 'ACESSORIO', 'RECARGA', 'SERVICO_VIVO')
       OR (nullif(trim(i.numero_acesso), '') IS NOT NULL AND nullif(trim(i.tipo_servico), '') IS NOT NULL));

-- vw_relatorio_vendas: uma linha por venda lançada (concluída ou cancelada depois de lançada), com o status.
CREATE VIEW vw_relatorio_vendas AS
SELECT
    v.id                                                                   AS venda_id,
    v.numero_venda                                                         AS numero_venda,
    v.filial_id                                                            AS filial_id,
    f.nome                                                                 AS filial,
    f.ativo                                                                AS filial_ativa,
    v.usuario_id                                                           AS vendedor_id,
    u.nome                                                                 AS vendedor,
    v.cliente_id                                                           AS cliente_id,
    c.nome                                                                 AS cliente,
    c.cpf_cnpj                                                             AS cpf_cnpj,
    c.uf                                                                   AS uf,
    v.status                                                               AS status_venda,
    coalesce(v.avaliacao_procedencia, 'Nao_avaliado')                      AS status_bko_cod,
    CASE v.avaliacao_procedencia
        WHEN 'Improcedente' THEN 'Improcedente'
        WHEN 'Procedente' THEN 'Procedente'
        WHEN 'Em_avaliacao_pelo_BKO' THEN 'Em avaliação pelo BKO'
        ELSE 'Não avaliado'
    END                                                                    AS status_bko,
    a.data_venda                                                           AS data_venda,
    a.data_venda::TIME                                                     AS hora_venda,
    (v.criado_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo'      AS data_input,
    p.data_ativacao                                                        AS data_ativacao,
    CASE WHEN v.status = 'CANCELADA'
        THEN ((v.status_alterado_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo')::DATE END
                                                                           AS data_cancelamento,
    ui.nome                                                                AS usuario_autorizacao,
    uc.nome                                                                AS usuario_cancelamento,
    v.valor_itens                                                          AS valor_itens,
    v.ajuste_condicao                                                      AS ajuste_condicao,
    v.valor_total                                                          AS valor_total,
    v.condicao_pagamento                                                   AS condicao_pagamento,
    v.codigo_voucher                                                       AS codigo_voucher,
    v.numero_nota                                                          AS numero_nota,
    v.numero_serie_nota                                                    AS serie_nota,
    t.qtd_itens                                                            AS qtd_itens,
    t.qtd_produtos                                                         AS qtd_produtos,
    t.qtd_servicos                                                         AS qtd_servicos
FROM vendas v
         JOIN filiais f ON f.id = v.filial_id
         JOIN usuarios u ON u.id = v.usuario_id
         LEFT JOIN clientes c ON c.id = v.cliente_id
         LEFT JOIN usuarios ui ON ui.id = v.finalizado_por_id
         LEFT JOIN usuarios uc ON uc.id = v.status_alterado_por_id AND v.status = 'CANCELADA'
         LEFT JOIN tb_protocolo_documental p ON p.venda_id = v.id
         CROSS JOIN LATERAL (
    SELECT (coalesce(v.finalizado_em, v.criado_em) AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo' AS data_venda
    ) a
         CROSS JOIN LATERAL (
    SELECT count(*)                                                                  AS qtd_itens,
           count(*) FILTER (WHERE i.categoria IN ('PRODUTO_VIVO', 'ACESSORIO', 'RECARGA')) AS qtd_produtos,
           count(*) FILTER (WHERE i.categoria = 'SERVICO_VIVO'
               OR (nullif(trim(i.numero_acesso), '') IS NOT NULL AND nullif(trim(i.tipo_servico), '') IS NOT NULL))
                                                                                     AS qtd_servicos
    FROM itens_venda i
    WHERE i.venda_id = v.id
    ) t
WHERE v.status = 'CONCLUIDA' OR (v.status = 'CANCELADA' AND v.finalizado_em IS NOT NULL);
