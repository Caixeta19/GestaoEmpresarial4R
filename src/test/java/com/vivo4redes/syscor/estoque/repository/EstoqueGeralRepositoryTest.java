package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Busca do PDV (V1): IMEI completo ou parcial precisa encontrar a unidade DISPONIVEL.
 * Roda no PostgreSQL real com o schema criado pelo Flyway.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EstoqueGeralRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String IMEI_A15 = "356789012345678";
    private static final String IMEI_IPHONE = "351234567890123";
    private static final String IMEI_VENDIDO = "356789000000001";
    private static final Pageable PAGINA = PageRequest.of(0, 50);

    @Autowired
    private EstoqueGeralRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void popular() {
        repository.deleteAll();
        repository.saveAll(List.of(
                unidade(IMEI_A15, "Galaxy A15", "Samsung", "LOJA01", StatusEstoque.DISPONIVEL),
                unidade(IMEI_IPHONE, "iPhone 15", "Apple", "LOJA02", StatusEstoque.DISPONIVEL),
                unidade(IMEI_VENDIDO, "Galaxy A15", "Samsung", "LOJA01", StatusEstoque.VENDIDO)));
    }

    @Test
    @DisplayName("IMEI completo encontra a unidade disponível")
    void imeiCompleto() {
        assertThat(seriais(IMEI_A15, IMEI_A15, "")).containsExactly(IMEI_A15);
    }

    @Test
    @DisplayName("Trecho do IMEI (início ou meio) encontra a unidade disponível")
    void imeiParcial() {
        assertThat(seriais("3567890", "3567890", "")).containsExactly(IMEI_A15);
        assertThat(seriais("01234567", "01234567", "")).containsExactly(IMEI_A15);
    }

    @Test
    @DisplayName("Unidade fora de DISPONIVEL não aparece na busca")
    void ignoraOutrosStatus() {
        assertThat(seriais(IMEI_VENDIDO, IMEI_VENDIDO, "")).isEmpty();
    }

    @Test
    @DisplayName("Filtro de filial restringe o resultado")
    void filtraPorFilial() {
        assertThat(seriais("35", "35", "loja02")).containsExactly(IMEI_IPHONE);
        assertThat(seriais(IMEI_A15, IMEI_A15, "LOJA02")).isEmpty();
    }

    @Test
    @DisplayName("Modelo com espaço continua sendo encontrado pelo texto original")
    void buscaPorModelo() {
        assertThat(seriais("GALAXYA15", "galaxy a15", "")).containsExactly(IMEI_A15);
    }

    @Test
    @DisplayName("Termo vazio traz todas as unidades disponíveis")
    void termoVazio() {
        assertThat(seriais("", "", "")).containsExactlyInAnyOrder(IMEI_A15, IMEI_IPHONE);
    }

    // ---- V21: literais do enum x CHECK do banco

    @Test
    @DisplayName("Todo valor de StatusEstoque é aceito pelo CHECK do banco")
    void checkAceitaTodosOsStatusDoEnum() {
        int i = 0;
        for (StatusEstoque status : StatusEstoque.values()) {
            repository.saveAndFlush(unidade("CHK" + i++, "Modelo", "Marca", "LOJA01", status));
        }
        assertThat(repository.findByStatus(StatusEstoque.DEFEITO)).hasSize(1);
    }

    @Test
    @DisplayName("CHECK rejeita o literal antigo AVARIADO")
    void checkRejeitaAvariado() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tb_estoque_geral (serial, status) VALUES ('AVR1', 'AVARIADO')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_estoque_geral_status");
    }

    // ---- V5: consolidado por modelo

    @Test
    @DisplayName("Consolidado conta disponível, reservado e defeito por modelo, sem contar vendido")
    void consolidaPorModelo() {
        repository.saveAllAndFlush(List.of(
                unidade("M1", "Galaxy A15", "Samsung", "LOJA01", StatusEstoque.RESERVADO),
                unidade("M2", "Galaxy A15", "Samsung", "LOJA01", StatusEstoque.DEFEITO),
                unidade("M3", "Galaxy A15", "Samsung", "LOJA01", StatusEstoque.DEFEITO)));

        Map<String, EstoqueConsolidadoModeloDTO> porModelo = repository.consolidarPorModelo().stream()
                .collect(Collectors.toMap(EstoqueConsolidadoModeloDTO::modeloComercial, Function.identity()));

        // Galaxy A15: 1 disponível (popular), 1 vendido (popular, não entra), 1 reservado, 2 defeito
        EstoqueConsolidadoModeloDTO a15 = porModelo.get("Galaxy A15");
        assertThat(a15.disponivel()).isEqualTo(1L);
        assertThat(a15.reservado()).isEqualTo(1L);
        assertThat(a15.defeito()).isEqualTo(2L);

        EstoqueConsolidadoModeloDTO iphone = porModelo.get("iPhone 15");
        assertThat(iphone.disponivel()).isEqualTo(1L);
        assertThat(iphone.reservado()).isZero();
        assertThat(iphone.defeito()).isZero();
    }

    // ---- V22: unidades presas em vendas não concluídas voltam a RESERVADO

    @Test
    @DisplayName("V22 passa a RESERVADO só as unidades VENDIDO de vendas ABERTA/PENDENTE/APROVADA")
    void v22ConverteSoVendasNaoConcluidas() throws Exception {
        // FKs de cliente/filial/usuário não interessam aqui: desligadas só nesta transação.
        jdbc.execute("SET LOCAL session_replication_role = replica");

        long aberta = venda("ABERTA"), pendente = venda("PENDENTE"), aprovada = venda("APROVADA");
        long concluida = venda("CONCLUIDA"), cancelada = venda("CANCELADA");
        long uAberta = vendida("V22A", aberta), uPendente = vendida("V22P", pendente), uAprovada = vendida("V22R", aprovada);
        long uConcluida = vendida("V22C", concluida), uCancelada = vendida("V22X", cancelada);

        jdbc.execute(new String(new ClassPathResource("db/migration/V22__reserva_carrinho.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        assertThat(statusDe(uAberta)).isEqualTo("RESERVADO");
        assertThat(statusDe(uPendente)).isEqualTo("RESERVADO");
        assertThat(statusDe(uAprovada)).isEqualTo("RESERVADO");
        assertThat(statusDe(uConcluida)).isEqualTo("VENDIDO");
        assertThat(statusDe(uCancelada)).isEqualTo("VENDIDO");
        assertThat(jdbc.queryForObject("SELECT data_venda FROM tb_estoque_geral WHERE id = ?", Object.class, uAberta)).isNull();
    }

    // ---- V30: venda lançada direto; PENDENTE/APROVADA viram CONCLUIDA e baixam o estoque

    @Test
    @DisplayName("V30 conclui as vendas PENDENTE/APROVADA e baixa só as unidades RESERVADO delas")
    void v30ConcluiPendentesEAprovadas() throws Exception {
        jdbc.execute("SET LOCAL session_replication_role = replica");

        long pendente = venda("PENDENTE"), aprovada = venda("APROVADA"), aberta = venda("ABERTA");
        long concluida = venda("CONCLUIDA"), cancelada = venda("CANCELADA");
        long uPendente = naVenda("V30P", pendente, "RESERVADO"), uAprovada = naVenda("V30R", aprovada, "RESERVADO");
        long uDisponivel = naVenda("V30D", aprovada, "DISPONIVEL");
        long uAberta = naVenda("V30A", aberta, "RESERVADO");
        long uCompartilhada = naVenda("V30S", aprovada, "RESERVADO");
        incluirNaVenda(aberta, uCompartilhada);
        long uCancelada = naVenda("V30X", cancelada, "DISPONIVEL");

        jdbc.execute(new String(new ClassPathResource("db/migration/V30__venda_lancada_direto.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        assertThat(statusDaVenda(pendente)).isEqualTo("CONCLUIDA");
        assertThat(statusDaVenda(aprovada)).isEqualTo("CONCLUIDA");
        assertThat(statusDaVenda(aberta)).isEqualTo("ABERTA");
        assertThat(statusDaVenda(concluida)).isEqualTo("CONCLUIDA");
        assertThat(statusDaVenda(cancelada)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT status_alterado_em FROM vendas WHERE id = ?", Object.class, aprovada)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status_alterado_em FROM vendas WHERE id = ?", Object.class, concluida)).isNull();

        assertThat(statusDe(uPendente)).isEqualTo("VENDIDO");
        assertThat(statusDe(uAprovada)).isEqualTo("VENDIDO");
        assertThat(jdbc.queryForObject("SELECT data_venda FROM tb_estoque_geral WHERE id = ?", Object.class, uAprovada)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT version FROM tb_estoque_geral WHERE id = ?", Long.class, uAprovada)).isEqualTo(1L);
        assertThat(statusDe(uDisponivel)).as("fora de RESERVADO fica como está").isEqualTo("DISPONIVEL");
        assertThat(statusDe(uAberta)).as("carrinho aberto não é tocado").isEqualTo("RESERVADO");
        assertThat(statusDe(uCompartilhada)).as("também está num carrinho aberto").isEqualTo("RESERVADO");
        assertThat(statusDe(uCancelada)).isEqualTo("DISPONIVEL");
    }

    // ---- V23: codigo_acessorio com marcador de vazio vira NULL

    @Test
    @DisplayName("V23 limpa codigo_acessorio com marcadores de vazio e mantém códigos reais")
    void v23LimpaCodigoAcessorioVazio() throws Exception {
        String[] marcadores = {"''", "\"\"", "'", "\"", "-", "", " '' ", "null", "NULL"};
        for (int i = 0; i < marcadores.length; i++) {
            jdbc.update("INSERT INTO tb_estoque_geral (serial, codigo_acessorio) VALUES (?, ?)", "V23M" + i, marcadores[i]);
        }
        jdbc.update("INSERT INTO tb_estoque_geral (serial, codigo_acessorio) VALUES ('V23OK', 'ACC123')");

        jdbc.execute(new String(new ClassPathResource("db/migration/V23__limpa_codigo_acessorio_vazio.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_estoque_geral WHERE serial LIKE 'V23M%' AND codigo_acessorio IS NOT NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT codigo_acessorio FROM tb_estoque_geral WHERE serial = 'V23OK'", String.class)).isEqualTo("ACC123");
    }

    private long venda(String status) {
        return jdbc.queryForObject("""
                INSERT INTO vendas (cliente_id, filial_id, usuario_id, numero_venda, status)
                VALUES (1, 1, 1, nextval('seq_numero_venda'), ?) RETURNING id
                """, Long.class, status);
    }

    private long vendida(String serial, long vendaId) {
        long id = jdbc.queryForObject("""
                INSERT INTO tb_estoque_geral (serial, status, data_venda) VALUES (?, 'VENDIDO', now()) RETURNING id
                """, Long.class, serial);
        jdbc.update("""
                INSERT INTO itens_venda (venda_id, categoria, produto_id, descricao_produto, quantidade, valor_unitario, estoque_geral_id)
                VALUES (?, 'PRODUTO_VIVO', ?, 'Aparelho', 1, 100, ?)
                """, vendaId, id, id);
        return id;
    }

    private long naVenda(String serial, long vendaId, String status) {
        long id = jdbc.queryForObject("INSERT INTO tb_estoque_geral (serial, status) VALUES (?, ?) RETURNING id",
                Long.class, serial, status);
        incluirNaVenda(vendaId, id);
        return id;
    }

    private void incluirNaVenda(long vendaId, long estoqueId) {
        jdbc.update("""
                INSERT INTO itens_venda (venda_id, categoria, produto_id, descricao_produto, quantidade, valor_unitario, estoque_geral_id)
                VALUES (?, 'PRODUTO_VIVO', ?, 'Aparelho', 1, 100, ?)
                """, vendaId, estoqueId, estoqueId);
    }

    private String statusDaVenda(long id) {
        return jdbc.queryForObject("SELECT status FROM vendas WHERE id = ?", String.class, id);
    }

    private String statusDe(long id) {
        return jdbc.queryForObject("SELECT status FROM tb_estoque_geral WHERE id = ?", String.class, id);
    }

    private List<String> seriais(String serial, String termo, String filial) {
        return repository.buscarDisponiveisPorFiltro(StatusEstoque.DISPONIVEL, serial, termo, filial, PAGINA)
                .stream().map(EstoqueGeral::getSerial).toList();
    }

    private static EstoqueGeral unidade(String serial, String modelo, String marca, String filial, StatusEstoque status) {
        return EstoqueGeral.builder()
                .serial(serial)
                .modeloComercial(modelo)
                .marca(marca)
                .categoria("PRODUTO_VIVO")
                .filialEstoque(filial)
                .status(status)
                .build();
    }
}
