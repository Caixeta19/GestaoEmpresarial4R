package com.vivo4redes.syscor.financeiro.relatorio.service;

import com.vivo4redes.syscor.financeiro.relatorio.dto.DetalhePagamentoDTO;
import com.vivo4redes.syscor.financeiro.service.ComprovanteContaPagarHtml;
import com.vivo4redes.syscor.financeiro.service.ComprovanteContaPagarService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Arquivos do relatório de formas de pagamento (XLSX, CSV e PDF), todos no mesmo layout: uma linha por pagamento,
 * ordenada por nº da venda, sem subtotal por loja, e no fim só o total geral por forma de pagamento.
 */
@Component
public class FormasPagamentoExportacao {

    static final String TITULO = "Relatório de Formas de Pagamento";
    static final String TITULO_TOTAIS = "Total geral por forma de pagamento";
    static final String SEM_VENDAS = "Nenhuma venda no período.";
    static final String[] COLUNAS =
            {"Nº da venda", "Data/hora", "Filial/PDV", "Caixa", "Forma de pagamento", "Tipo", "Parcelas", "Valor"};
    static final String[] COLUNAS_TOTAIS = {"Forma de pagamento", "Quantidade de pagamentos", "Valor", "% do total"};

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String RODAPE = "Relatório gerencial; não substitui documento fiscal.";

    // ------------------------------------------------------------------ XLSX

    byte[] xlsx(ExportacaoFormasPagamento r) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            Sheet aba = wb.createSheet("Formas de Pagamento");
            int n = 0;
            n = texto(aba, n, TITULO, e.negrito);
            n = texto(aba, n, periodo(r), null);
            if (r.semDataFinalizacao() > 0) n = texto(aba, n, aviso(r), null);
            n++;

            int cabecalho = n;
            cabecalho(aba, n++, e, COLUNAS);
            aba.createFreezePane(0, cabecalho + 1);
            for (DetalhePagamentoDTO d : r.linhas()) {
                Row row = aba.createRow(n++);
                numero(row, 0, BigDecimal.valueOf(d.numeroVenda()), null);
                Cell data = row.createCell(1);
                data.setCellValue(d.dataHora());
                data.setCellStyle(e.dataHora);
                row.createCell(2).setCellValue(d.filial());
                row.createCell(3).setCellValue(d.caixa());
                row.createCell(4).setCellValue(d.formaRotulo());
                row.createCell(5).setCellValue(d.tipo());
                if (d.parcelas() != null) numero(row, 6, BigDecimal.valueOf(d.parcelas()), null);
                numero(row, 7, d.valor(), e.decimal);
            }
            // filtro só nas linhas de dados: os totais ficam fora
            aba.setAutoFilter(new CellRangeAddress(cabecalho, Math.max(cabecalho, n - 1), 0, COLUNAS.length - 1));
            if (r.linhas().isEmpty()) n = texto(aba, n, SEM_VENDAS, null);

            n++;
            n = texto(aba, n, TITULO_TOTAIS, e.negrito);
            cabecalho(aba, n++, e, COLUNAS_TOTAIS);
            for (ExportacaoFormasPagamento.TotalForma t : r.totaisPorForma()) {
                Row row = aba.createRow(n++);
                row.createCell(0).setCellValue(t.formaRotulo());
                numero(row, 1, BigDecimal.valueOf(t.quantidade()), null);
                numero(row, 2, t.valor(), e.decimal);
                numero(row, 3, t.percentual(), e.decimal);
            }
            Row geral = aba.createRow(n++);
            celulaNegrito(geral, 0, "Total geral", e);
            numero(geral, 1, BigDecimal.valueOf(r.quantidadePagamentos()), e.negrito);
            numero(geral, 2, r.totalGeral(), e.decimalNegrito);
            numero(geral, 3, r.totalGeral().signum() == 0 ? BigDecimal.ZERO : new BigDecimal("100.00"),
                    e.decimalNegrito);
            Row vendas = aba.createRow(n);
            celulaNegrito(vendas, 0, "Total de vendas distintas", e);
            numero(vendas, 1, BigDecimal.valueOf(r.quantidadeVendas()), e.negrito);

            larguras(aba, 30, 18, 34, 18, 22, 14, 10, 14);
            wb.write(saida);
            wb.dispose();
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gerar o Excel do relatório de formas de pagamento.", ex);
        }
    }

    private static int texto(Sheet aba, int linha, String texto, CellStyle estilo) {
        Cell c = aba.createRow(linha).createCell(0);
        c.setCellValue(texto);
        if (estilo != null) c.setCellStyle(estilo);
        return linha + 1;
    }

    private static void cabecalho(Sheet aba, int linha, Estilos e, String... rotulos) {
        Row row = aba.createRow(linha);
        for (int i = 0; i < rotulos.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(rotulos[i]);
            c.setCellStyle(e.negrito);
        }
    }

    private static void celulaNegrito(Row row, int coluna, String valor, Estilos e) {
        Cell c = row.createCell(coluna);
        c.setCellValue(valor);
        c.setCellStyle(e.negrito);
    }

    /** Valor numérico de verdade (não texto), com o formato informado. */
    private static void numero(Row row, int coluna, BigDecimal valor, CellStyle estilo) {
        Cell c = row.createCell(coluna);
        c.setCellValue(valor.doubleValue());
        if (estilo != null) c.setCellStyle(estilo);
    }

    private static void larguras(Sheet aba, int... caracteres) {
        for (int i = 0; i < caracteres.length; i++) aba.setColumnWidth(i, caracteres[i] * 256);
    }

    private static final class Estilos {
        final CellStyle negrito;
        final CellStyle decimal;
        final CellStyle decimalNegrito;
        final CellStyle dataHora;

        Estilos(SXSSFWorkbook wb) {
            DataFormat f = wb.createDataFormat();
            Font fonte = wb.createFont();
            fonte.setBold(true);
            negrito = wb.createCellStyle();
            negrito.setFont(fonte);
            decimal = wb.createCellStyle();
            decimal.setDataFormat(f.getFormat("#,##0.00"));
            decimalNegrito = wb.createCellStyle();
            decimalNegrito.setFont(fonte);
            decimalNegrito.setDataFormat(f.getFormat("#,##0.00"));
            dataHora = wb.createCellStyle();
            dataHora.setDataFormat(f.getFormat("dd/mm/yyyy hh:mm"));
        }
    }

    // ------------------------------------------------------------------ CSV

    /** UTF-8 com BOM (o Excel BR abre acentuado), separador {@code ;} e vírgula decimal. */
    byte[] csv(ExportacaoFormasPagamento r) {
        StringBuilder sb = new StringBuilder("﻿");
        linha(sb, TITULO);
        linha(sb, periodo(r));
        if (r.semDataFinalizacao() > 0) linha(sb, aviso(r));
        sb.append("\r\n");
        linha(sb, COLUNAS);
        for (DetalhePagamentoDTO d : r.linhas()) {
            linha(sb, String.valueOf(d.numeroVenda()), d.dataHora().format(DATA_HORA), d.filial(), d.caixa(),
                    d.formaRotulo(), d.tipo(), d.parcelas() == null ? "" : String.valueOf(d.parcelas()),
                    decimal(d.valor()));
        }
        if (r.linhas().isEmpty()) linha(sb, SEM_VENDAS);
        sb.append("\r\n");
        linha(sb, TITULO_TOTAIS);
        linha(sb, COLUNAS_TOTAIS);
        for (ExportacaoFormasPagamento.TotalForma t : r.totaisPorForma()) {
            linha(sb, t.formaRotulo(), String.valueOf(t.quantidade()), decimal(t.valor()), decimal(t.percentual()));
        }
        linha(sb, "Total geral", String.valueOf(r.quantidadePagamentos()), decimal(r.totalGeral()),
                r.totalGeral().signum() == 0 ? "0,00" : "100,00");
        linha(sb, "Total de vendas distintas", String.valueOf(r.quantidadeVendas()));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void linha(StringBuilder sb, String... campos) {
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(campoCsv(campos[i]));
        }
        sb.append("\r\n");
    }

    /** Aspas quando preciso e neutraliza fórmula (=, +, -, @) em texto vindo do cadastro. */
    static String campoCsv(String valor) {
        if (valor == null || valor.isEmpty()) return "";
        String v = valor.replace('\n', ' ').replace('\r', ' ');
        char primeiro = v.charAt(0);
        if (primeiro == '=' || primeiro == '+' || primeiro == '@' || (primeiro == '-' && !numerico(v))) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"")) return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    private static boolean numerico(String v) {
        return v.matches("-\\d+(,\\d+)?");
    }

    private static String decimal(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    // ------------------------------------------------------------------ PDF

    byte[] pdf(ExportacaoFormasPagamento r, String usuario, String filtros) {
        return ComprovanteContaPagarService.renderizar(html(r, usuario, filtros));
    }

    String html(ExportacaoFormasPagamento r, String usuario, String filtros) {
        StringBuilder h = new StringBuilder(8192 + r.linhas().size() * 300);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head>")
                .append("<meta charset=\"UTF-8\"/><style>")
                .append("@page { size: A4 landscape; margin: 12mm 10mm; } body { font-family: sans-serif; font-size: 8.5pt; }")
                .append(" h1 { font-size: 14pt; margin: 0 0 4px 0; } h2 { font-size: 10.5pt; margin: 14px 0 4px 0; }")
                .append(" .meta { font-size: 8pt; color: #444; margin-bottom: 2px; }")
                .append(" .aviso { font-size: 8pt; color: #8a4b00; margin: 6px 0; }")
                .append(" table { width: 100%; border-collapse: collapse; margin-top: 8px; }")
                .append(" thead { display: table-header-group; } tr { page-break-inside: avoid; }")
                .append(" th, td { border-bottom: 1px solid #ccc; padding: 2px 4px; text-align: left; }")
                .append(" th { background: #eee; font-size: 8pt; }")
                .append(" .num { text-align: right; } tr.total td { font-weight: bold; background: #f6f6f6; }")
                .append(" .rodape { margin-top: 18px; font-size: 8pt; color: #555; border-top: 1px solid #aaa;")
                .append(" padding-top: 4px; }")
                .append("</style></head><body>");
        h.append("<h1>").append(esc(TITULO)).append("</h1>");
        h.append("<div class=\"meta\">").append(esc(periodo(r))).append("</div>");
        h.append("<div class=\"meta\">").append(esc(filtros)).append("</div>");
        h.append("<div class=\"meta\">Gerado por ").append(esc(usuario)).append(" em ")
                .append(esc(r.geradoEm().format(DATA_HORA))).append("</div>");
        if (r.semDataFinalizacao() > 0) h.append("<div class=\"aviso\">").append(esc(aviso(r))).append("</div>");

        h.append("<table><thead><tr>");
        for (int i = 0; i < COLUNAS.length; i++) {
            h.append(i >= 6 ? "<th class=\"num\">" : "<th>").append(esc(COLUNAS[i])).append("</th>");
        }
        h.append("</tr></thead><tbody>");
        for (DetalhePagamentoDTO d : r.linhas()) {
            h.append("<tr><td>").append(d.numeroVenda()).append("</td><td>").append(esc(d.dataHora().format(DATA_HORA)))
                    .append("</td><td>").append(esc(d.filial())).append("</td><td>").append(esc(d.caixa()))
                    .append("</td><td>").append(esc(d.formaRotulo())).append("</td><td>").append(esc(d.tipo()))
                    .append("</td><td class=\"num\">").append(d.parcelas() == null ? "" : d.parcelas())
                    .append("</td><td class=\"num\">").append(esc(moeda(d.valor()))).append("</td></tr>");
        }
        if (r.linhas().isEmpty()) {
            h.append("<tr><td colspan=\"").append(COLUNAS.length).append("\">").append(esc(SEM_VENDAS))
                    .append("</td></tr>");
        }
        h.append("</tbody></table>");

        h.append("<h2>").append(esc(TITULO_TOTAIS)).append("</h2><table><thead><tr>");
        for (int i = 0; i < COLUNAS_TOTAIS.length; i++) {
            h.append(i > 0 ? "<th class=\"num\">" : "<th>").append(esc(COLUNAS_TOTAIS[i])).append("</th>");
        }
        h.append("</tr></thead><tbody>");
        for (ExportacaoFormasPagamento.TotalForma t : r.totaisPorForma()) {
            h.append("<tr><td>").append(esc(t.formaRotulo())).append("</td><td class=\"num\">").append(t.quantidade())
                    .append("</td><td class=\"num\">").append(esc(moeda(t.valor()))).append("</td><td class=\"num\">")
                    .append(esc(decimal(t.percentual()))).append("%</td></tr>");
        }
        h.append("<tr class=\"total\"><td>Total geral (").append(r.quantidadeVendas())
                .append(" venda(s) distinta(s))</td><td class=\"num\">").append(r.quantidadePagamentos())
                .append("</td><td class=\"num\">").append(esc(moeda(r.totalGeral()))).append("</td><td class=\"num\">")
                .append(r.totalGeral().signum() == 0 ? "0,00" : "100,00").append("%</td></tr></tbody></table>");
        h.append("<div class=\"rodape\">").append(esc(RODAPE)).append("</div></body></html>");
        return h.toString();
    }

    // ------------------------------------------------------------------ apoio

    private static String periodo(ExportacaoFormasPagamento r) {
        return "Período: " + r.de().format(DATA) + " a " + r.ate().format(DATA);
    }

    private static String aviso(ExportacaoFormasPagamento r) {
        return "Atenção: " + r.semDataFinalizacao()
                + " venda(s) concluída(s) sem data de finalização ficaram fora do relatório.";
    }

    private static String esc(String texto) {
        return ComprovanteContaPagarHtml.escapar(texto);
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor).replace(' ', ' ');
    }
}
