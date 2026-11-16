package com.vivo4redes.syscor.financeiro.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Monta o HTML (XHTML) do comprovante de pagamento. Todo texto que vem de fora (fornecedor, histórico do banco,
 * justificativa...) passa por {@link #escapar}: sem isso um fornecedor chamado {@code <script>} quebraria o XML do
 * renderizador ou injetaria marcação no PDF. O código de verificação é calculado sobre os mesmos dados impressos.
 */
final class ComprovanteContaPagarHtml {

    static final String RODAPE = "Documento gerado pelo SYSCOR a partir da conciliação com o extrato bancário. "
            + "Não substitui o comprovante de autenticação emitido pelo banco.";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private ComprovanteContaPagarHtml() {
    }

    /**
     * Dados impressos no comprovante. {@code contaMascarada}, {@code historicoLancamento} e
     * {@code identificadorLancamento} só existem quando a baixa veio de um lançamento do extrato; nulos não saem no PDF.
     */
    record Dados(
            Long contaId,
            String descricao,
            String fornecedor,
            String documentoFornecedor,
            String formaPagamento,
            LocalDate vencimento,
            BigDecimal valor,
            BigDecimal valorPago,
            LocalDate dataPagamento,
            String banco,
            String contaMascarada,
            String historicoLancamento,
            String identificadorLancamento,
            String origem,
            String baixadoPor,
            String baixadoEm) {
    }

    /** Linhas rótulo/valor na ordem impressa; as nulas ficam de fora. Base do HTML e do código de verificação. */
    static List<String[]> linhas(Dados d) {
        java.util.ArrayList<String[]> linhas = new java.util.ArrayList<>();
        linhas.add(new String[]{"Conta nº", String.valueOf(d.contaId())});
        linhas.add(new String[]{"Descrição", d.descricao()});
        linhas.add(new String[]{"Fornecedor", d.fornecedor()});
        if (d.documentoFornecedor() != null) {
            linhas.add(new String[]{"CPF/CNPJ do fornecedor", formatarDocumento(d.documentoFornecedor())});
        }
        if (d.formaPagamento() != null) linhas.add(new String[]{"Forma de pagamento", d.formaPagamento()});
        linhas.add(new String[]{"Vencimento", d.vencimento().format(DATA)});
        linhas.add(new String[]{"Valor da conta", moeda(d.valor())});
        linhas.add(new String[]{"Valor pago", moeda(d.valorPago())});
        linhas.add(new String[]{"Data do pagamento", d.dataPagamento().format(DATA)});
        if (d.banco() != null) linhas.add(new String[]{"Banco", d.banco()});
        if (d.contaMascarada() != null) linhas.add(new String[]{"Conta", d.contaMascarada()});
        if (d.historicoLancamento() != null) linhas.add(new String[]{"Histórico do lançamento", d.historicoLancamento()});
        if (d.identificadorLancamento() != null) {
            linhas.add(new String[]{"Identificador do lançamento", d.identificadorLancamento()});
        }
        linhas.add(new String[]{"Origem", d.origem()});
        if (d.baixadoPor() != null) linhas.add(new String[]{"Baixado por", d.baixadoPor()});
        if (d.baixadoEm() != null) linhas.add(new String[]{"Baixado em", d.baixadoEm()});
        return linhas;
    }

    /** Número da conta + 8 primeiros caracteres do SHA-256 dos dados impressos. */
    static String codigoVerificacao(Dados d) {
        StringBuilder base = new StringBuilder();
        for (String[] linha : linhas(d)) {
            base.append(linha[0]).append('=').append(sanitizar(linha[1])).append('\n');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.toString().getBytes(StandardCharsets.UTF_8));
            return d.contaId() + "-" + HexFormat.of().withUpperCase().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    static String montar(Dados d) {
        StringBuilder html = new StringBuilder(2048);
        html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>")
                .append("<title>Comprovante de pagamento</title><style>")
                .append("@page { size: A4; margin: 22mm 18mm; }")
                .append("body { font-family: sans-serif; font-size: 11pt; color: #1b1b1b; }")
                .append("h1 { font-size: 20pt; margin: 0 0 4mm 0; }")
                .append(".sub { color: #555; margin-bottom: 8mm; }")
                .append("table { width: 100%; border-collapse: collapse; }")
                .append("td { padding: 2.2mm 2mm; border-bottom: 0.3mm solid #d8d8d8; vertical-align: top; }")
                .append("td.rotulo { width: 38%; color: #555; }")
                .append(".verificacao { margin-top: 9mm; padding: 4mm; border: 0.4mm solid #1b1b1b; }")
                .append(".rodape { margin-top: 10mm; font-size: 8.5pt; color: #555; }")
                .append("</style></head><body>")
                .append("<h1>Comprovante de pagamento</h1>")
                .append("<div class=\"sub\">Conta a pagar nº ").append(escapar(String.valueOf(d.contaId())))
                .append("</div><table>");
        for (String[] linha : linhas(d)) {
            html.append("<tr><td class=\"rotulo\">").append(escapar(linha[0])).append("</td><td>")
                    .append(escapar(linha[1])).append("</td></tr>");
        }
        html.append("</table><div class=\"verificacao\">Código de verificação: <b>")
                .append(escapar(codigoVerificacao(d))).append("</b></div>")
                .append("<div class=\"rodape\">").append(escapar(RODAPE)).append("</div>")
                .append("</body></html>");
        return html.toString();
    }

    // ------------------------------------------------------------------ apoio
    /** Escapa para XML e tira o que o XML ou a fonte padrão do PDF não aceitam. */
    static String escapar(String texto) {
        String limpo = sanitizar(texto);
        StringBuilder sb = new StringBuilder(limpo.length() + 16);
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Remove caracteres de controle (inválidos em XML) e troca por "?" o que a fonte padrão (Latin-1 + alguns sinais
     * do Windows-1252) não desenha, para o PDF nunca falhar por causa de texto do banco ou do fornecedor.
     */
    static String sanitizar(String texto) {
        if (texto == null) return "";
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else if (c < 0x20 || c == 0x7F) {
                // descarta controle
            } else if (c <= 0xFF || c == '•' || c == '–' || c == '—' || c == '€') {
                sb.append(c);
            } else if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c)) sb.append('?');
            } else {
                sb.append('?');
            }
        }
        return sb.toString().trim();
    }

    /** "••1234": só os 4 últimos caracteres aparecem. */
    static String mascarar(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String limpo = valor.replaceAll("\\s", "");
        String fim = limpo.length() <= 4 ? limpo : limpo.substring(limpo.length() - 4);
        return "••" + fim;
    }

    static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor).replace(' ', ' ');
    }

    static String formatarDocumento(String digitos) {
        if (digitos.length() == 11) {
            return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-"
                    + digitos.substring(9);
        }
        if (digitos.length() == 14) {
            return digitos.substring(0, 2) + "." + digitos.substring(2, 5) + "." + digitos.substring(5, 8) + "/"
                    + digitos.substring(8, 12) + "-" + digitos.substring(12);
        }
        return digitos;
    }
}
