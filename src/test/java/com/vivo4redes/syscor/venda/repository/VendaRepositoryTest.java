package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import com.vivo4redes.syscor.venda.model.Venda;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VendaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private VendaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("Carrinhos abandonados: só vendas ABERTA paradas antes do limite, mais antigas primeiro")
    void buscaCarrinhosAbandonados() {
        // FKs de cliente/filial/usuário não interessam aqui: desligadas só nesta transação.
        jdbc.execute("SET LOCAL session_replication_role = replica");
        Instant limite = Instant.parse("2026-11-02T12:00:00Z");

        long abertaAntiga = venda("ABERTA", limite.minus(Duration.ofDays(2)));
        long abertaParada = venda("ABERTA", limite.minus(Duration.ofMinutes(1)));
        venda("ABERTA", limite.plusSeconds(1));                       // ainda ativa
        venda("PENDENTE", limite.minus(Duration.ofDays(2)));          // já finalizada pelo vendedor
        venda("CANCELADA", limite.minus(Duration.ofDays(2)));

        assertThat(repository.buscarIdsCarrinhosAbandonados(limite)).containsExactly(abertaAntiga, abertaParada);
    }

    @Test
    @DisplayName("V26: condição padrão A_VISTA e CHECK igual aos literais do enum")
    void condicaoPagamentoNoBanco() {
        jdbc.execute("SET LOCAL session_replication_role = replica");
        long id = venda("ABERTA", Instant.parse("2026-11-02T12:00:00Z"));

        assertThat(repository.findById(id)).get()
                .extracting(Venda::getCondicaoPagamento).isEqualTo(CondicaoPagamento.A_VISTA);
        for (CondicaoPagamento condicao : CondicaoPagamento.values()) {
            assertThat(jdbc.update("UPDATE vendas SET condicao_pagamento = ? WHERE id = ?", condicao.name(), id)).isEqualTo(1);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE vendas SET condicao_pagamento = 'PARCELADO_22' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Insere a venda por SQL e grava atualizado_em pelo próprio Hibernate, como faz o @UpdateTimestamp:
     * gravação e consulta convertem o Instant do mesmo jeito, independente do fuso da JVM/sessão.
     */
    private long venda(String status, Instant atualizadoEm) {
        long id = jdbc.queryForObject("""
                INSERT INTO vendas (cliente_id, filial_id, usuario_id, numero_venda, status)
                VALUES (1, 1, 1, nextval('seq_numero_venda'), ?) RETURNING id
                """, Long.class, status);
        em.createQuery("update Venda v set v.atualizadoEm = :t where v.id = :id")
                .setParameter("t", atualizadoEm)
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }
}
