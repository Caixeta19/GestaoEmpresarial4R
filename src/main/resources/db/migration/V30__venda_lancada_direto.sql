-- Venda lançada direto (docs/ESPECIFICACAO.md, RN.19): o finalizar leva ABERTA -> CONCLUIDA num passo só.
-- PENDENTE e APROVADA ficam no CHECK só por compatibilidade; as vendas que estavam nesses status passam a CONCLUIDA,
-- e suas unidades reservadas viram VENDIDO, exatamente como VendaService.confirmarEstoqueDaVenda
-- (EstoqueGeral.confirmarVenda: RESERVADO -> VENDIDO, data_venda = agora; o save incrementa a version).
-- A ordem importa: as unidades são baixadas antes de as vendas mudarem de status.
-- Unidade que não está RESERVADO, ou que também está em outra venda ABERTA ou CONCLUIDA, fica como está.

UPDATE tb_estoque_geral e
SET status     = 'VENDIDO',
    data_venda = now(),
    version    = e.version + 1
WHERE e.status = 'RESERVADO'
  AND EXISTS (SELECT 1
              FROM itens_venda i
                       JOIN vendas v ON v.id = i.venda_id
              WHERE i.estoque_geral_id = e.id
                AND v.status IN ('PENDENTE', 'APROVADA'))
  AND NOT EXISTS (SELECT 1
                  FROM itens_venda i
                           JOIN vendas v ON v.id = i.venda_id
                  WHERE i.estoque_geral_id = e.id
                    AND v.status IN ('ABERTA', 'CONCLUIDA'));

UPDATE vendas
SET status             = 'CONCLUIDA',
    status_alterado_em = now()
WHERE status IN ('PENDENTE', 'APROVADA');
