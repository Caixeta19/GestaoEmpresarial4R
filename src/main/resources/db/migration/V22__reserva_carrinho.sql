-- Reserva no carrinho (docs/ESPECIFICACAO.md, seção 9, bug 4).
-- Até aqui o carrinho baixava a unidade para VENDIDO assim que o item entrava.
-- A partir de agora: RESERVADO ao adicionar, VENDIDO só quando a venda é CONCLUIDA.
-- Só dados: as unidades presas em vendas ainda não concluídas (ABERTA, PENDENTE, APROVADA)
-- voltam a RESERVADO, para que a conclusão dessas vendas consiga confirmar a baixa.
-- Unidade que também pertence a uma venda CONCLUIDA continua VENDIDO (não deveria acontecer).

UPDATE tb_estoque_geral e
SET status     = 'RESERVADO',
    data_venda = NULL
WHERE e.status = 'VENDIDO'
  AND EXISTS (SELECT 1
              FROM itens_venda i
                       JOIN vendas v ON v.id = i.venda_id
              WHERE i.estoque_geral_id = e.id
                AND v.status IN ('ABERTA', 'PENDENTE', 'APROVADA'))
  AND NOT EXISTS (SELECT 1
                  FROM itens_venda i
                           JOIN vendas v ON v.id = i.venda_id
                  WHERE i.estoque_geral_id = e.id
                    AND v.status = 'CONCLUIDA');
