package com.vivo4redes.syscor.util;

import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EstoqueUtilTest {

    @ParameterizedTest(name = "[{0}] é vazio")
    @NullSource
    @ValueSource(strings = {"", "   ", "''", "\"\"", "'", "\"", "-", "null", "Null", " '' ", "\t\"\"\t"})
    @DisplayName("textoVazio reconhece null, branco e marcadores de vazio")
    void marcadoresSaoVazios(String valor) {
        assertThat(EstoqueUtil.textoVazio(valor)).isTrue();
    }

    @ParameterizedTest(name = "[{0}] não é vazio")
    @ValueSource(strings = {"ACC123", "0", "'ACC'", "--", "nulo", "a-b"})
    @DisplayName("textoVazio não descarta valores reais")
    void valoresReaisNaoSaoVazios(String valor) {
        assertThat(EstoqueUtil.textoVazio(valor)).isFalse();
    }

    @ParameterizedTest(name = "codigo_acessorio [{0}] não é acessório")
    @NullSource
    @ValueSource(strings = {"''", "\"\"", "'", "\"", "-", "null", ""})
    @DisplayName("ehAcessorio ignora marcadores de vazio")
    void marcadorNaoEhAcessorio(String codigo) {
        assertThat(EstoqueUtil.ehAcessorio(EstoqueGeral.builder().codigoAcessorio(codigo).build())).isFalse();
    }

    @Test
    @DisplayName("ehAcessorio aceita código real e trata linha nula como não acessório")
    void codigoRealEhAcessorio() {
        assertThat(EstoqueUtil.ehAcessorio(EstoqueGeral.builder().codigoAcessorio("ACC123").build())).isTrue();
        assertThat(EstoqueUtil.ehAcessorio(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Essenciais", "Acessórios", "Áudio", "Casa Inteligente", "Bem Estar", "Vestuário", "Games",
            "Demais Eletrônicos", " acessorios "})
    @DisplayName("Categoria de acessório com serial SAP (material + sequência) vai na aba Acessório")
    void categoriaDeAcessorioComSerialSap(String categoria) {
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade(categoria, "220252761000161"))).isTrue();
    }

    @Test
    @DisplayName("Relógio LTE e tablet 5G têm IMEI: continuam na aba Produto Vivo")
    void comImeiContinuaProdutoVivo() {
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade("Bem Estar", "356789012345678"))).isFalse();
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade("Notebook e Tablets", "861234567890123"))).isFalse();
    }

    @Test
    @DisplayName("Smartphone, TV, Box e Modem e Notebook sem IMEI ficam na aba Produto Vivo")
    void outrasCategoriasProdutoVivo() {
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade("Smartphone", "356789012345678"))).isFalse();
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade("TV", "0ABC12345678"))).isFalse();
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade("Box e Modem e FWT", "356789012345678"))).isFalse();
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade("Notebook e Tablets", "220233240005309"))).isFalse();
        assertThat(EstoqueUtil.vendidoComoAcessorio(unidade(null, "220233240005309"))).isFalse();
        assertThat(EstoqueUtil.vendidoComoAcessorio(null)).isFalse();
    }

    @Test
    @DisplayName("Com código de acessório é acessório em qualquer categoria")
    void codigoDeAcessorioSempre() {
        assertThat(EstoqueUtil.vendidoComoAcessorio(EstoqueGeral.builder().codigoAcessorio("'0007899716398012'")
                .serial("7899716398012").build())).isTrue();
    }

    private static EstoqueGeral unidade(String categoria, String serial) {
        return EstoqueGeral.builder().categoria(categoria).serial(serial).build();
    }
}
