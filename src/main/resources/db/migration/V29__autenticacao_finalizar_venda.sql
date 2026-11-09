-- Quem finalizou a venda e quem fez a última mudança de status por PATCH /vendas/{id}/status.
-- Ficam nulas nas vendas antigas, porque esse dado não existe para elas.

ALTER TABLE vendas
    ADD COLUMN finalizado_por_id      BIGINT,
    ADD COLUMN finalizado_em          TIMESTAMP,
    ADD COLUMN status_alterado_por_id BIGINT,
    ADD COLUMN status_alterado_em     TIMESTAMP,
    ADD CONSTRAINT fk_vendas_finalizado_por FOREIGN KEY (finalizado_por_id) REFERENCES usuarios (id),
    ADD CONSTRAINT fk_vendas_status_alterado_por FOREIGN KEY (status_alterado_por_id) REFERENCES usuarios (id);
