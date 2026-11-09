package com.vivo4redes.syscor.gestao.enums;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TipoPlanoTest {

    @ParameterizedTest
    @CsvSource({
            "PRE, PRE",
            "CONTROLE_BTL, CONTROLE", "CONTROLE_ENTRADA, CONTROLE", "CONTROLE_ALTO_VALOR, CONTROLE",
            "POS_INDIVIDUAL, POS", "FAMILIA_2, POS", "FAMILIA_3, POS", "FAMILIA_4_5, POS", "VIVO_V, POS", "MULTIVIVO, POS",
            "VIM_8GB, POS", "VIM_14GB, POS", "VIM_20GB, POS", "VIM_40GB, POS", "VIM_100GB, POS"})
    @DisplayName("Grupo de preço -> tipo de plano (VIM conta como Pós)")
    void peloGrupo(GrupoPlano grupo, TipoPlano esperado) {
        assertThat(TipoPlano.de(grupo, "Controle")).as("o grupo manda sobre o segmento").contains(esperado);
    }

    @Test
    @DisplayName("Todo grupo de plano, menos SEM_PLANO, tem tipo")
    void todoGrupoTemTipo() {
        Arrays.stream(GrupoPlano.values()).filter(g -> g != GrupoPlano.SEM_PLANO)
                .forEach(g -> assertThat(TipoPlano.de(g, null)).as(g.name()).isPresent());
    }

    @ParameterizedTest
    @CsvSource({"Pré, PRE", "PRE, PRE", "Controle, CONTROLE", "Pós, POS", "pos-pago, POS"})
    @DisplayName("Sem grupo (ou SEM_PLANO), vale o segmento gravado pela tela de Venda")
    void peloSegmento(String segmento, TipoPlano esperado) {
        assertThat(TipoPlano.de(null, segmento)).contains(esperado);
        assertThat(TipoPlano.de(GrupoPlano.SEM_PLANO, segmento)).contains(esperado);
    }

    @Test
    @DisplayName("Sem grupo e sem segmento: sem tipo")
    void semTipo() {
        assertThat(TipoPlano.de(null, null)).isEmpty();
        assertThat(TipoPlano.de(GrupoPlano.SEM_PLANO, " ")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(StatusAvaliacaoProcedencia.class)
    @DisplayName("Status BKO aceita o literal do enum e o rótulo")
    void statusBkoPorLiteralERotulo(StatusAvaliacaoProcedencia status) {
        assertThat(StatusAvaliacaoProcedencia.deTexto(status.name())).contains(status);
        assertThat(StatusAvaliacaoProcedencia.deTexto(status.rotulo())).contains(status);
        assertThat(StatusAvaliacaoProcedencia.deTexto(status.rotulo().toUpperCase())).contains(status);
    }

    @Test
    @DisplayName("Status BKO sem acento e com espaço também vale; texto desconhecido não")
    void statusBkoVariacoes() {
        assertThat(StatusAvaliacaoProcedencia.deTexto("nao avaliado")).contains(StatusAvaliacaoProcedencia.Nao_avaliado);
        assertThat(StatusAvaliacaoProcedencia.deTexto("Em avaliacao pelo BKO")).contains(StatusAvaliacaoProcedencia.Em_avaliacao_pelo_BKO);
        assertThat(StatusAvaliacaoProcedencia.deTexto("Talvez")).isEmpty();
        assertThat(StatusAvaliacaoProcedencia.deTexto("")).isEmpty();
    }
}
