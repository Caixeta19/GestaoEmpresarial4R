package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.OpcaoPagamentoDTO;
import com.vivo4redes.syscor.estoque.service.CalculadoraCondicaoPagamento.ComposicaoVenda;
import com.vivo4redes.syscor.estoque.service.CalculadoraCondicaoPagamento.TotalVenda;
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

    @Test
    @DisplayName("Venda Regular sem entrada: PIX, à vista e 21x, com o ajuste de cada condição")
    void vendaRegular() {
        TotalVenda pix = total(CondicaoPagamento.PIX, regular("10079.10", "0.00"), "0.00");
        TotalVenda aVista = total(CondicaoPagamento.A_VISTA, regular("11199.00", "0.00"), "0.00");
        TotalVenda parcelado = total(CondicaoPagamento.PARCELADO_21, regular("12581.12", "0.00"), "0.00");

        assertThat(pix.total()).isEqualByComparingTo("10079.10");
        assertThat(pix.ajuste()).isEqualByComparingTo("-1119.90");
        assertThat(aVista.total()).isEqualByComparingTo("11199.00");
        assertThat(aVista.ajuste()).isEqualByComparingTo("0.00");
        assertThat(parcelado.total()).isEqualByComparingTo("12581.12");
        assertThat(parcelado.ajuste()).isEqualByComparingTo("1382.12");
        assertThat(parcelado.financiado()).isEqualByComparingTo("12581.12");
        assertThat(parcelado.valorParcela()).isEqualByComparingTo("599.10");
    }

    @Test
    @DisplayName("Venda Renova sem entrada: PIX, à vista e 21x de R$ 12.081,12")
    void vendaRenovaSemEntrada() {
        assertThat(total(CondicaoPagamento.PIX, renova("9579.10"), "0.00").total()).isEqualByComparingTo("9579.10");
        assertThat(total(CondicaoPagamento.A_VISTA, renova("10699.00"), "0.00").total()).isEqualByComparingTo("10699.00");
        assertThat(total(CondicaoPagamento.PARCELADO_21, renova("12081.12"), "0.00").total()).isEqualByComparingTo("12081.12");
    }

    @Test
    @DisplayName("Renova 21x com voucher de R$ 500: o voucher é entrada e o total cai para R$ 12.019,41")
    void vendaRenovaComVoucher() {
        TotalVenda t = total(CondicaoPagamento.PARCELADO_21, renova("12081.12"), "500.00");

        assertThat(t.total()).isEqualByComparingTo("12019.41");
        assertThat(t.financiado()).isEqualByComparingTo("11519.41");
    }

    @Test
    @DisplayName("Entrada de R$ 2.000 em 21x: juros só sobre o financiado")
    void entrada() {
        TotalVenda t = total(CondicaoPagamento.PARCELADO_21, regular("12581.12", "0.00"), "2000.00");

        assertThat(t.financiado()).isEqualByComparingTo("10334.29");
        assertThat(t.total()).isEqualByComparingTo("12334.29");
        assertThat(t.valorParcela()).isEqualByComparingTo("492.11");
    }

    @Test
    @DisplayName("Item sem tabela não tem juros nem desconto PIX, com e sem entrada")
    void semTabela() {
        assertThat(total(CondicaoPagamento.PIX, regular("10079.10", "33.00"), "0.00").total()).isEqualByComparingTo("10112.10");
        assertThat(total(CondicaoPagamento.PARCELADO_21, regular("12581.12", "33.00"), "0.00").total()).isEqualByComparingTo("12614.12");

        TotalVenda t = total(CondicaoPagamento.PARCELADO_21, regular("12581.12", "33.00"), "2000.00");
        assertThat(t.total()).isEqualByComparingTo("12367.29");
        assertThat(t.financiado()).isEqualByComparingTo("10367.29");
        assertThat(t.ajuste()).isEqualByComparingTo("1135.29");
    }

    @Test
    @DisplayName("Entrada acima da base de tabela: sem juros, e o excesso abate os itens sem tabela")
    void entradaAcimaDaBase() {
        TotalVenda t = total(CondicaoPagamento.PARCELADO_21, regular("12581.12", "33.00"), "11210.00");

        assertThat(t.total()).isEqualByComparingTo("11232.00");
        assertThat(t.ajuste()).isEqualByComparingTo("0.00");
        assertThat(t.financiado()).isEqualByComparingTo("22.00");
    }

    @Test
    @DisplayName("Entrada só conta no parcelado")
    void entradaForaDoParcelado() {
        TotalVenda t = total(CondicaoPagamento.A_VISTA, regular("11199.00", "0.00"), "2000.00");

        assertThat(t.total()).isEqualByComparingTo("11199.00");
        assertThat(t.financiado()).isEqualByComparingTo("0.00");
        assertThat(t.valorParcela()).isEqualByComparingTo("933.25");
    }

    private static TotalVenda total(CondicaoPagamento condicao, ComposicaoVenda venda, String entrada) {
        return CalculadoraCondicaoPagamento.totalDaVenda(condicao, venda, new BigDecimal(entrada));
    }

    private static ComposicaoVenda regular(String naCondicao, String semTabela) {
        return new ComposicaoVenda(REGULAR, new BigDecimal(naCondicao), BigDecimal.ZERO, new BigDecimal(semTabela));
    }

    private static ComposicaoVenda renova(String naCondicao) {
        return new ComposicaoVenda(RENOVA, new BigDecimal(naCondicao), DESCONTO, BigDecimal.ZERO);
    }

    private static OpcaoPagamentoDTO opcao(ValoresCondicao v, CondicaoPagamento condicao, int parcelas) {
        return v.opcoes().stream()
                .filter(o -> o.condicao() == condicao && o.parcelas() == parcelas)
                .findFirst().orElseThrow();
    }
}
