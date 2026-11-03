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
}
