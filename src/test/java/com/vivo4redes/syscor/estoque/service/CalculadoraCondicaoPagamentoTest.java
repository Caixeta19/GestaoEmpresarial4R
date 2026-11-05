package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.OpcaoPagamentoDTO;
import com.vivo4redes.syscor.estoque.service.CalculadoraCondicaoPagamento.ValoresCondicao;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exemplo da tarefa: iPhone 17 Pro Max 512GB, Controle Alto Valor, Regular R$ 11.199,00, desconto R$ 500,00. */
class CalculadoraCondicaoPagamentoTest {

    private static final BigDecimal REGULAR = new BigDecimal("11199.00");
    private static final BigDecimal RENOVA = new BigDecimal("10699.00");
    private static final BigDecimal DESCONTO = new BigDecimal("500.00");

    @Test
    @DisplayName("Regular: PIX, 12x, 13x, 18x e 21x do exemplo")
    void exemploRegular() {
        ValoresCondicao v = CalculadoraCondicaoPagamento.completar(REGULAR, null, null, REGULAR, null);

        assertThat(v.pix()).isEqualByComparingTo("10079.10");
        assertThat(opcao(v, CondicaoPagamento.A_VISTA, 12).total()).isEqualByComparingTo("11199.00");
        assertThat(opcao(v, CondicaoPagamento.A_VISTA, 12).valorParcela()).isEqualByComparingTo("933.25");
        assertThat(v.totais().get(13)).isEqualByComparingTo("12066.36");
        assertThat(v.totais().get(18)).isEqualByComparingTo("12386.46");
        assertThat(v.totais().get(21)).isEqualByComparingTo("12581.12");
        assertThat(opcao(v, CondicaoPagamento.PARCELADO_21, 21).valorParcela()).isEqualByComparingTo("599.10");
    }

    @Test
    @DisplayName("Renova: PIX e 13x–21x sobre o Regular menos o desconto; até 12x vale o preço Renova")
    void exemploRenova() {
        ValoresCondicao v = CalculadoraCondicaoPagamento.completar(RENOVA, null, null, REGULAR, DESCONTO);

        assertThat(v.pix()).isEqualByComparingTo("9579.10");
        assertThat(opcao(v, CondicaoPagamento.A_VISTA, 12).total()).isEqualByComparingTo("10699.00");
        assertThat(v.totais().get(13)).isEqualByComparingTo("11566.36");
        assertThat(v.totais().get(21)).isEqualByComparingTo("12081.12");
    }

    @Test
    @DisplayName("A planilha prevalece: a fórmula só preenche o que faltou")
    void planilhaPrevalece() {
        ValoresCondicao v = CalculadoraCondicaoPagamento.completar(REGULAR, new BigDecimal("10000.00"),
                Map.of(13, new BigDecimal("12000.00")), REGULAR, null);

        assertThat(v.pix()).isEqualByComparingTo("10000.00");
        assertThat(v.totais().get(13)).isEqualByComparingTo("12000.00");
        assertThat(v.totais().get(21)).isEqualByComparingTo("12581.12");
    }

    @Test
    @DisplayName("Opções: PIX, A_VISTA de 1x a 12x e PARCELADO_13 a 21, em ordem")
    void listaCompleta() {
        List<OpcaoPagamentoDTO> opcoes = CalculadoraCondicaoPagamento.completar(REGULAR, null, null, REGULAR, null).opcoes();

        assertThat(opcoes).hasSize(22);
        assertThat(opcoes.get(0).condicao()).isEqualTo(CondicaoPagamento.PIX);
        assertThat(opcoes.get(1).parcelas()).isEqualTo(1);
        assertThat(opcoes.get(12).parcelas()).isEqualTo(12);
        assertThat(opcoes.get(13).condicao()).isEqualTo(CondicaoPagamento.PARCELADO_13);
        assertThat(opcoes.get(21).condicao()).isEqualTo(CondicaoPagamento.PARCELADO_21);
    }

    @Test
    @DisplayName("Divergência só acima de R$ 0,01")
    void tolerancia() {
        assertThat(CalculadoraCondicaoPagamento.diverge(new BigDecimal("100.01"), new BigDecimal("100.00"))).isFalse();
        assertThat(CalculadoraCondicaoPagamento.diverge(new BigDecimal("100.00"), new BigDecimal("100.01"))).isFalse();
        assertThat(CalculadoraCondicaoPagamento.diverge(new BigDecimal("100.02"), new BigDecimal("100.00"))).isTrue();
    }

    @Test
    @DisplayName("Sem fator fora de 13x–21x")
    void semFator() {
        assertThatThrownBy(() -> CalculadoraCondicaoPagamento.totalParcelado(REGULAR, 12, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OpcaoPagamentoDTO opcao(ValoresCondicao v, CondicaoPagamento condicao, int parcelas) {
        return v.opcoes().stream()
                .filter(o -> o.condicao() == condicao && o.parcelas() == parcelas)
                .findFirst().orElseThrow();
    }
}
