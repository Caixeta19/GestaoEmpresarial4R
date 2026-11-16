package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.dev.DevExtratoSeedService;
import com.vivo4redes.syscor.financeiro.dto.response.SincronizacaoExtratoDTO;
import com.vivo4redes.syscor.financeiro.enums.OrigemSincronizacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extrato FAKE de dev de ponta a ponta: o JSON {@code dev/extrato-fake-dev.json} (datas relativas) + o seed de dev,
 * com banco real e SEM mock do cliente. Duas sincronizações seguidas não duplicam nada.
 */
@SpringBootTest(properties = {
        "syscor.financeiro.bb-extrato.enabled=true",
        "syscor.financeiro.bb-extrato.modo=FAKE",
        "syscor.financeiro.bb-extrato.fake-recurso=dev/extrato-fake-dev.json",
        "syscor.financeiro.bb-extrato.fake-datas-relativas=true",
        "syscor.financeiro.bb-extrato.agencia=297",
        "syscor.financeiro.bb-extrato.conta=1213",
        "syscor.financeiro.bb-extrato.sincronizacao-ms=3600000",
        "syscor.financeiro.conciliacao.codigos-historico-pagamento=144,501"
})
@ActiveProfiles("test")
@Testcontainers
class ExtratoFakeDevIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    @Autowired ExtratoSincronizacaoService sincronizacao;
    @Autowired DevExtratoSeedService seed;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("FAKE de dev: 1 conciliada, 2 ambíguas (uma é a divergência), 1 sem correspondência; a 2ª rodada não duplica")
    void fakeDeDev() {
        assertThat(seed.semear()).isEqualTo(4);
        assertThat(seed.semear()).as("seed idempotente").isZero();

        SincronizacaoExtratoDTO primeira = sincronizacao.executar(OrigemSincronizacao.MANUAL, null);
        assertThat(primeira.erro()).isNull();
        assertThat(primeira.lancamentosNovos()).as("só débitos: o crédito e os saldos ficam de fora").isEqualTo(4);
        assertThat(primeira.conciliados()).isEqualTo(1);
        assertThat(primeira.ambiguos()).isEqualTo(2);
        assertThat(primeira.semCorrespondencia()).isEqualTo(1);

        assertThat(situacao("DEV-FAKE-0001")).isEqualTo("CONCILIADO");
        assertThat(situacao("DEV-FAKE-0002")).isEqualTo("AMBIGUO");
        assertThat(situacao("DEV-FAKE-0003")).isEqualTo("SEM_CORRESPONDENCIA");
        assertThat(situacao("DEV-FAKE-0004")).isEqualTo("AMBIGUO");
        assertThat(jdbc.queryForObject("select count(*) from tb_extrato_lancamento where id_externo = 'DEV-FAKE-0099'",
                Integer.class)).as("crédito ignorado").isZero();
        assertThat(statusConta("[DEMO-EXTRATO] Fornecedor conciliado")).isEqualTo("BAIXADO");
        assertThat(statusConta("[DEMO-EXTRATO] Fornecedor divergente")).isEqualTo("DIVERGENTE");

        long lancamentos = contar("tb_extrato_lancamento");
        long titulos = contar("tb_titulo_financeiro");

        SincronizacaoExtratoDTO segunda = sincronizacao.executar(OrigemSincronizacao.MANUAL, null);
        assertThat(segunda.erro()).isNull();
        assertThat(segunda.lancamentosNovos()).isZero();
        assertThat(segunda.conciliados()).isZero();
        assertThat(contar("tb_extrato_lancamento")).isEqualTo(lancamentos);
        assertThat(contar("tb_titulo_financeiro")).isEqualTo(titulos);
        assertThat(situacao("DEV-FAKE-0001")).isEqualTo("CONCILIADO");
    }

    private String situacao(String idExterno) {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "select situacao from tb_extrato_lancamento where id_externo = ?", idExterno);
        assertThat(linhas).as(idExterno).hasSize(1);
        return (String) linhas.get(0).get("situacao");
    }

    private String statusConta(String descricao) {
        return jdbc.queryForObject("select status from tb_titulo_financeiro where descricao = ?", String.class,
                descricao);
    }

    private long contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Long.class);
    }
}
