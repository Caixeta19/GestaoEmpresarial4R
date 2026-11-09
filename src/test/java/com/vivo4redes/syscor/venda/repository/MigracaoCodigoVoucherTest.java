package com.vivo4redes.syscor.venda.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V28 sobre dados gravados no schema da V27. */
@Testcontainers
class MigracaoCodigoVoucherTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Test
    @DisplayName("V28: venda aberta com Renova leva o código; só as abertas perdem a linha VOUCHER")
    void migraVendasAbertas() {
        migrar("27");
        SingleConnectionDataSource conexao = new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        JdbcTemplate jdbc = new JdbcTemplate(conexao);
        // FKs de cliente/filial/usuário não interessam aqui: desligadas só nesta conexão.
        jdbc.execute("SET session_replication_role = replica");

        long abertaComRenova = venda(jdbc, "ABERTA", true);
        pagamento(jdbc, abertaComRenova, "VOUCHER", " V-1 ");
        pagamento(jdbc, abertaComRenova, "VOUCHER", "V-2");
        pagamento(jdbc, abertaComRenova, "DINHEIRO", null);
        long abertaSemRenova = venda(jdbc, "ABERTA", false);
        pagamento(jdbc, abertaSemRenova, "VOUCHER", "V-3");
        long finalizada = venda(jdbc, "PENDENTE", true);
        pagamento(jdbc, finalizada, "VOUCHER", "V-4");

        migrar(null);

        assertThat(codigo(jdbc, abertaComRenova)).isEqualTo("V-1");
        assertThat(formas(jdbc, abertaComRenova)).containsExactly("DINHEIRO");
        assertThat(codigo(jdbc, abertaSemRenova)).isNull();
        assertThat(formas(jdbc, abertaSemRenova)).isEmpty();
        assertThat(codigo(jdbc, finalizada)).isNull();
        assertThat(formas(jdbc, finalizada)).containsExactly("VOUCHER");
        conexao.destroy();
    }

    private void migrar(String versao) {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        if (versao != null) {
            config.target(versao);
        }
        config.load().migrate();
    }

    private long venda(JdbcTemplate jdbc, String status, boolean renova) {
        return jdbc.queryForObject("""
                INSERT INTO vendas (cliente_id, filial_id, usuario_id, numero_venda, status, renova)
                VALUES (1, 1, 1, nextval('seq_numero_venda'), ?, ?) RETURNING id
                """, Long.class, status, renova);
    }

    private void pagamento(JdbcTemplate jdbc, long venda, String forma, String codigo) {
        jdbc.update("INSERT INTO pagamentos_venda (venda_id, forma, valor, codigo_voucher) VALUES (?, ?, ?, ?)",
                venda, forma, new BigDecimal("100.00"), codigo);
    }

    private String codigo(JdbcTemplate jdbc, long venda) {
        return jdbc.queryForObject("SELECT codigo_voucher FROM vendas WHERE id = ?", String.class, venda);
    }

    private List<String> formas(JdbcTemplate jdbc, long venda) {
        return jdbc.queryForList("SELECT forma FROM pagamentos_venda WHERE venda_id = ? ORDER BY id", String.class, venda);
    }
}
