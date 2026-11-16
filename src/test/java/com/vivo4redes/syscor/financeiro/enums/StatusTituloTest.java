package com.vivo4redes.syscor.financeiro.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Máquina de estados do título: as transições ficam no enum. */
class StatusTituloTest {

    @Test
    @DisplayName("ABERTO baixa, diverge ou cancela; BAIXADO só reabre; CANCELADO é final")
    void transicoes() {
        assertThat(StatusTitulo.ABERTO.podeTransicionarPara(StatusTitulo.BAIXADO)).isTrue();
        assertThat(StatusTitulo.ABERTO.podeTransicionarPara(StatusTitulo.DIVERGENTE)).isTrue();
        assertThat(StatusTitulo.ABERTO.podeTransicionarPara(StatusTitulo.CANCELADO)).isTrue();

        assertThat(StatusTitulo.BAIXADO.podeTransicionarPara(StatusTitulo.ABERTO)).isTrue();
        assertThat(StatusTitulo.BAIXADO.podeTransicionarPara(StatusTitulo.CANCELADO)).isFalse();
        assertThat(StatusTitulo.BAIXADO.podeTransicionarPara(StatusTitulo.DIVERGENTE)).isFalse();

        assertThat(StatusTitulo.DIVERGENTE.podeTransicionarPara(StatusTitulo.BAIXADO)).isTrue();
        assertThat(StatusTitulo.DIVERGENTE.podeTransicionarPara(StatusTitulo.CANCELADO)).isTrue();

        for (StatusTitulo destino : StatusTitulo.values()) {
            assertThat(StatusTitulo.CANCELADO.podeTransicionarPara(destino)).as("CANCELADO -> " + destino).isFalse();
        }
    }

    @Test
    @DisplayName("Só ABERTO e DIVERGENTE são editáveis; os literais batem com o CHECK da V41")
    void editavelELiterais() {
        assertThat(StatusTitulo.ABERTO.editavel()).isTrue();
        assertThat(StatusTitulo.DIVERGENTE.editavel()).isTrue();
        assertThat(StatusTitulo.BAIXADO.editavel()).isFalse();
        assertThat(StatusTitulo.CANCELADO.editavel()).isFalse();
        assertThat(StatusTitulo.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("ABERTO", "BAIXADO", "DIVERGENTE", "CANCELADO");
    }
}
