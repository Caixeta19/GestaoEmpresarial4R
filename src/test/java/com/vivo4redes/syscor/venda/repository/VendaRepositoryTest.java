package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.TipoPagamento;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    private PagamentoVendaRepository pagamentoRepository;

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
        venda("CONCLUIDA", limite.minus(Duration.ofDays(2)));         // já lançada pelo vendedor
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

    @Test
    @DisplayName("V27: tipo padrão PAGAMENTO e CHECKs de forma e tipo iguais aos literais dos enums")
    void pagamentoNoBanco() {
        jdbc.execute("SET LOCAL session_replication_role = replica");
        long venda = venda("ABERTA", Instant.parse("2026-11-02T12:00:00Z"));

        long id = jdbc.queryForObject("INSERT INTO pagamentos_venda (venda_id, forma, valor) VALUES (?, 'PIX', 10) RETURNING id",
                Long.class, venda);

        assertThat(pagamentoRepository.findById(id)).get()
                .extracting(PagamentoVenda::getTipo).isEqualTo(TipoPagamento.PAGAMENTO);
        for (FormaPagamento forma : FormaPagamento.values()) {
            assertThat(jdbc.update("UPDATE pagamentos_venda SET forma = ? WHERE id = ?", forma.name(), id)).isEqualTo(1);
        }
        for (TipoPagamento tipo : TipoPagamento.values()) {
            assertThat(jdbc.update("UPDATE pagamentos_venda SET tipo = ? WHERE id = ?", tipo.name(), id)).isEqualTo(1);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE pagamentos_venda SET tipo = 'OUTRO' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Caixa: VIVO_PAY soma como forma própria e a linha FINANCIADO entra como crédito")
    void entradasDoCaixa() {
        jdbc.execute("SET LOCAL session_replication_role = replica");
        long venda = venda("ABERTA", Instant.parse("2026-11-02T12:00:00Z"));
        pagamento(venda, "VIVO_PAY", "PAGAMENTO", "100.00");
        pagamento(venda, "PIX", "ENTRADA", "30.00");
        pagamento(venda, "CARTAO_CREDITO", "FINANCIADO", "70.00");
        pagamento(venda, "CARTAO_CREDITO", "PAGAMENTO", "5.00");

        assertThat(entradas(FormaPagamento.VIVO_PAY)).isEqualByComparingTo("100.00");
        assertThat(entradas(FormaPagamento.PIX)).isEqualByComparingTo("30.00");
        assertThat(entradas(FormaPagamento.CARTAO_CREDITO)).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("V31: venda lançada sem avaliação entra na fila do BKO como Nao_avaliado; o resto não muda")
    void v31NaoAvaliado() throws Exception {
        jdbc.execute("SET LOCAL session_replication_role = replica");
        Instant t = Instant.parse("2026-11-02T12:00:00Z");
        long semAvaliacao = venda("CONCLUIDA", t), procedente = venda("CONCLUIDA", t);
        long aberta = venda("ABERTA", t), cancelada = venda("CANCELADA", t);
        jdbc.update("UPDATE vendas SET avaliacao_procedencia = 'Procedente' WHERE id = ?", procedente);

        jdbc.execute(new String(new ClassPathResource("db/migration/V31__venda_lancada_nao_avaliado.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        assertThat(avaliacao(semAvaliacao)).isEqualTo("Nao_avaliado");
        assertThat(avaliacao(procedente)).isEqualTo("Procedente");
        assertThat(avaliacao(aberta)).isNull();
        assertThat(avaliacao(cancelada)).isNull();
    }

    private String avaliacao(long id) {
        return jdbc.queryForObject("SELECT avaliacao_procedencia FROM vendas WHERE id = ?", String.class, id);
    }

    private void pagamento(long venda, String forma, String tipo, String valor) {
        jdbc.update("INSERT INTO pagamentos_venda (venda_id, forma, tipo, valor) VALUES (?, ?, ?, ?)",
                venda, forma, tipo, new BigDecimal(valor));
    }

    private BigDecimal entradas(FormaPagamento forma) {
        Instant agora = Instant.now();
        return pagamentoRepository.somarPorFilialFormaEData(1L, forma,
                agora.minus(Duration.ofDays(1)), agora.plus(Duration.ofDays(1)));
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
