package com.vivo4redes.syscor.venda.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CondicaoPagamentoTest {

    @Test
    @DisplayName("Parcelas e fator de cada condição")
    void parcelasEFator() {
        assertThat(CondicaoPagamento.PIX.parcelas()).isEqualTo(1);
        assertThat(CondicaoPagamento.A_VISTA.parcelas()).isEqualTo(12);
        assertThat(CondicaoPagamento.PARCELADO_21.parcelas()).isEqualTo(21);
        assertThat(CondicaoPagamento.PIX.fator()).isNull();
        assertThat(CondicaoPagamento.A_VISTA.fator()).isNull();
        assertThat(CondicaoPagamento.PARCELADO_13.fator()).isEqualByComparingTo("1.0774494");
        assertThat(CondicaoPagamento.PARCELADO_21.fator()).isEqualByComparingTo("1.1234148");
    }

    @Test
    @DisplayName("PIX aceita só PIX e Vivo Pay")
    void linhasDoPix() {
        assertThat(CondicaoPagamento.PIX.aceita(FormaPagamento.PIX, 1)).isTrue();
        assertThat(CondicaoPagamento.PIX.aceita(FormaPagamento.VIVO_PAY, 1)).isTrue();
        assertThat(CondicaoPagamento.PIX.aceita(FormaPagamento.VOUCHER, 1)).isFalse();
        assertThat(CondicaoPagamento.PIX.aceita(FormaPagamento.CARTAO_CREDITO, 1)).isFalse();
        assertThat(CondicaoPagamento.PIX.aceita(FormaPagamento.CARTAO_DEBITO, 1)).isFalse();
        assertThat(CondicaoPagamento.PIX.aceita(FormaPagamento.DINHEIRO, 1)).isFalse();
        assertThat(CondicaoPagamento.PIX.aceita(FormaPagamento.BOLETO, 1)).isFalse();
    }

    @Test
    @DisplayName("A_VISTA aceita todas as formas menos voucher, com crédito até 12x")
    void linhasDoAVista() {
        for (FormaPagamento forma : FormaPagamento.values()) {
            assertThat(CondicaoPagamento.A_VISTA.aceita(forma, 1)).isEqualTo(forma != FormaPagamento.VOUCHER);
        }
        assertThat(CondicaoPagamento.A_VISTA.aceita(FormaPagamento.CARTAO_CREDITO, 12)).isTrue();
        assertThat(CondicaoPagamento.A_VISTA.aceita(FormaPagamento.CARTAO_CREDITO, 13)).isFalse();
    }

    @Test
    @DisplayName("PARCELADO_N aceita as entradas e recusa crédito digitado e boleto")
    void linhasDoParcelado() {
        CondicaoPagamento condicao = CondicaoPagamento.PARCELADO_21;

        assertThat(condicao.aceita(FormaPagamento.DINHEIRO, 1)).isTrue();
        assertThat(condicao.aceita(FormaPagamento.PIX, 1)).isTrue();
        assertThat(condicao.aceita(FormaPagamento.VIVO_PAY, 1)).isTrue();
        assertThat(condicao.aceita(FormaPagamento.CARTAO_DEBITO, 1)).isTrue();
        assertThat(condicao.aceita(FormaPagamento.VOUCHER, 1)).isFalse();
        assertThat(condicao.aceita(FormaPagamento.CARTAO_CREDITO, 21)).isFalse();
        assertThat(condicao.aceita(FormaPagamento.BOLETO, 1)).isFalse();
    }
}
