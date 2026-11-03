package com.vivo4redes.syscor.estoque.repository;

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
import org.springframework.data.domain.Pageable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
