package com.vivo4redes.syscor.financeiro.relatorio.service;

import com.vivo4redes.syscor.financeiro.relatorio.dto.DetalhePagamentoDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormasPagamentoExportacaoTest {

    private final FormasPagamentoExportacao exportacao = new FormasPagamentoExportacao();

    private static ExportacaoFormasPagamento dados() {
        LocalDate dia = LocalDate.of(2026, 11, 10);
        List<DetalhePagamentoDTO> linhas = List.of(
                new DetalhePagamentoDTO(dia.atTime(9, 0), 1000001L, "=Loja A", "CAIXA 1", "PIX", "PIX", "PAGAMENTO",
                        new BigDecimal("10.00"), 1),
                new DetalhePagamentoDTO(LocalDateTime.of(dia, java.time.LocalTime.NOON), 1000002L, "Loja B", "Sem caixa",
                        "DINHEIRO", "Dinheiro", "PAGAMENTO", new BigDecimal("1234.50"), null));
        return new ExportacaoFormasPagamento(dia, dia, OffsetDateTime.of(2026, 11, 17, 8, 0, 0, 0, ZoneOffset.ofHours(-3)),
                2, linhas, List.of(
                new ExportacaoFormasPagamento.TotalForma("Dinheiro", 1, new BigDecimal("1234.50"), new BigDecimal("99.20")),
                new ExportacaoFormasPagamento.TotalForma("PIX", 1, new BigDecimal("10.00"), new BigDecimal("0.80"))),
                new BigDecimal("1244.50"), 2, 2);
    }

    @Test
    @DisplayName("PDF: uma tabela com thead que repete a cada página, sem 'Agrupado por', e o bloco de totais por forma no fim")
    void htmlRepeteCabecalho() {
        String html = exportacao.html(dados(), "Fulano", "Filiais: todas");
        assertThat(html).contains("thead { display: table-header-group; }")
                .contains("<thead><tr><th>Nº da venda</th>")
                .doesNotContain("Agrupado por").doesNotContain("Total:")
                .contains("Atenção: 2 venda(s)");
        assertThat(html.indexOf("Total geral por forma de pagamento")).isGreaterThan(html.indexOf("1000002"));
        assertThat(html).contains("R$ 1.234,50").contains("Total geral (2 venda(s) distinta(s))");
    }

    @Test
    @DisplayName("CSV: BOM, ';', vírgula decimal, fórmula neutralizada e totais depois da lista")
    void csv() {
        byte[] bytes = exportacao.csv(dados());
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        String texto = new String(bytes, StandardCharsets.UTF_8).substring(1);
        assertThat(texto.lines().toList()).containsSubsequence(
                "Relatório de Formas de Pagamento",
                "Período: 10/11/2026 a 10/11/2026",
                "Nº da venda;Data/hora;Filial/PDV;Caixa;Forma de pagamento;Tipo;Parcelas;Valor",
                "1000001;10/11/2026 09:00;'=Loja A;CAIXA 1;PIX;PAGAMENTO;1;10,00",
                "1000002;10/11/2026 12:00;Loja B;Sem caixa;Dinheiro;PAGAMENTO;;1234,50",
                "",
                "Total geral por forma de pagamento",
                "Dinheiro;1;1234,50;99,20",
                "Total geral;2;1244,50;100,00",
                "Total de vendas distintas;2");
    }
}
