package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.service.ComprovanteContaPagarHtml.Dados;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprovante em PDF: conteúdo, escape do texto que vem de fora e código de verificação, sem banco. */
class ComprovanteContaPagarHtmlTest {

    private static Dados dados(String fornecedor, String valorPago) {
        return new Dados(42L, "Aluguel da loja", fornecedor, "12345678000195", "BOLETO",
                LocalDate.of(2026, 11, 10), new BigDecimal("1234.50"), new BigDecimal(valorPago),
                LocalDate.of(2026, 11, 9), "BANCO_BRASIL", ComprovanteContaPagarHtml.mascarar("123456"),
                "PAGAMENTO DE BOLETO", "ID-LANC-001", "Conciliação automática com extrato do Banco do Brasil",
                "Financeiro Teste", "09/11/2026 14:30");
    }

    private static String texto(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
    }

    @Test
    @DisplayName("PDF começa com %PDF e traz título, número da conta, valores, origem, código de verificação e o rodapé fixo")
    void conteudo() throws Exception {
        Dados d = dados("Imobiliária Sintética", "1234.50");

        byte[] pdf = ComprovanteContaPagarService.renderizar(ComprovanteContaPagarHtml.montar(d));

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String texto = texto(pdf);
        assertThat(texto).contains("Comprovante de pagamento")
                .contains("Conta a pagar nº 42")
                .contains("Imobiliária Sintética")
                .contains("12.345.678/0001-95")
                .contains("R$ 1.234,50")
                .contains("09/11/2026")
                .contains("••3456")
                .contains("PAGAMENTO DE BOLETO")
                .contains("ID-LANC-001")
                .contains("Conciliação automática com extrato do Banco do Brasil")
                .contains("Código de verificação: " + ComprovanteContaPagarHtml.codigoVerificacao(d))
                .contains(ComprovanteContaPagarHtml.RODAPE);
    }

    @Test
    @DisplayName("Texto malicioso no fornecedor sai escapado no HTML e aparece como texto no PDF, sem quebrar a geração")
    void escape() throws Exception {
        String malicioso = "<script>alert('x')</script> & \"aspas\" </td></tr><img src=http://evil/x.png>";
        Dados d = dados(malicioso, "1234.50");

        String html = ComprovanteContaPagarHtml.montar(d);

        assertThat(html).doesNotContain("<script>").doesNotContain("<img")
                .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;aspas&quot;");
        byte[] pdf = ComprovanteContaPagarService.renderizar(html);
        assertThat(texto(pdf)).contains("<script>alert('x')</script> & \"aspas\"");
    }

    @Test
    @DisplayName("Caracteres de controle, emoji e ideogramas não quebram o PDF (viram '?' ou somem)")
    void caracteresEstranhos() throws Exception {
        Dados d = dados("Forn\u0000ecedor 😀 中文 \u0007ok", "1234.50");

        byte[] pdf = ComprovanteContaPagarService.renderizar(ComprovanteContaPagarHtml.montar(d));

        assertThat(texto(pdf)).contains("Fornecedor").contains("ok");
    }

    @Test
    @DisplayName("Código de verificação: id-8 hex do SHA-256, estável para os mesmos dados e diferente se um dado impresso muda")
    void codigoDeVerificacao() {
        String a = ComprovanteContaPagarHtml.codigoVerificacao(dados("Fornecedor", "1234.50"));
        String b = ComprovanteContaPagarHtml.codigoVerificacao(dados("Fornecedor", "1234.50"));
        String alterado = ComprovanteContaPagarHtml.codigoVerificacao(dados("Fornecedor", "1234.51"));

        assertThat(a).matches("42-[0-9A-F]{8}").isEqualTo(b).isNotEqualTo(alterado);
    }

    @Test
    @DisplayName("Linhas sem dado (conta, histórico e identificador do lançamento) não saem no comprovante")
    void linhasOpcionais() {
        Dados semExtrato = new Dados(7L, "Energia", "Concessionária", null, null, LocalDate.of(2026, 11, 5),
                new BigDecimal("80.00"), new BigDecimal("80.00"), LocalDate.of(2026, 11, 5), "Caixa", null, null, null,
                "Baixa manual por Fulano em 05/11/2026: pago no balcão", "Fulano", null);

        String html = ComprovanteContaPagarHtml.montar(semExtrato);

        assertThat(html).doesNotContain("Histórico do lançamento").doesNotContain("Identificador do lançamento")
                .doesNotContain("CPF/CNPJ").doesNotContain("Forma de pagamento")
                .contains("Baixa manual por Fulano em 05/11/2026: pago no balcão");
    }

    @Test
    @DisplayName("Máscara mostra só os 4 últimos caracteres; vazio não gera linha")
    void mascara() {
        assertThat(ComprovanteContaPagarHtml.mascarar("0001234567")).isEqualTo("••4567");
        assertThat(ComprovanteContaPagarHtml.mascarar("12")).isEqualTo("••12");
        assertThat(ComprovanteContaPagarHtml.mascarar("  ")).isNull();
        assertThat(ComprovanteContaPagarHtml.mascarar(null)).isNull();
    }
}
