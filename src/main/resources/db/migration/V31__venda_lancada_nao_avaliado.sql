-- Fila do BKO (docs/ESPECIFICACAO.md, RN.54): toda venda lançada nasce Não avaliado, e o finalizar passou a gravar
-- 'Nao_avaliado'. As vendas lançadas antes disso (inclusive as que a V30 passou de PENDENTE/APROVADA para CONCLUIDA)
-- ficaram sem avaliação: entram na fila com o mesmo valor. Avaliação já gravada não muda.

UPDATE vendas
SET avaliacao_procedencia = 'Nao_avaliado'
WHERE status = 'CONCLUIDA'
  AND avaliacao_procedencia IS NULL;
