-- Relatório de Formas de Pagamento: filtra vendas CONCLUIDA por finalizado_em (intervalo de dias).
CREATE INDEX IF NOT EXISTS idx_vendas_status_finalizado_em ON vendas (status, finalizado_em);
