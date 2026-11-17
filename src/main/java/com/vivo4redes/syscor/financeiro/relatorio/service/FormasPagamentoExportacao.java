package com.vivo4redes.syscor.financeiro.relatorio.service;

import com.vivo4redes.syscor.financeiro.relatorio.dto.AgruparPor;
import com.vivo4redes.syscor.financeiro.relatorio.dto.DetalhePagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.RelatorioFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.service.ComprovanteContaPagarHtml;
import com.vivo4redes.syscor.financeiro.service.ComprovanteContaPagarService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Geração dos arquivos do relatório de formas de pagamento: XLSX (Resumo + Detalhe), PDF e CSV. */
@Component
public class FormasPagamentoExportacao {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String RODAPE = "Relatório gerencial; não substitui documento fiscal.";

    // ------------------------------------------------------------------ XLSX

    byte[] xlsx(RelatorioFormasPagamentoDTO r, List<DetalhePagamentoDTO> detalhe) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            resumo(wb.createSheet("Resumo"), r, e);
            detalhe(wb.createSheet("Detalhe"), detalhe, e);
            wb.write(saida);
            wb.dispose();
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gerar o Excel do relatório de formas de pagamento.", ex);
        }
    }

    private void resumo(Sheet aba, RelatorioFormasPagamentoDTO r, Estilos e) {
        int n = 0;
        n = texto(aba, n, "Relatório de Formas de Pagamento", e.negrito);
        n = texto(aba, n, "Período: " + r.periodo().de().format(DATA) + " a " + r.periodo().ate().format(DATA)
                + " · Agrupado por " + rotuloAgrupar(r.agruparPor()), null);
        if (r.semDataFinalizacao() > 0) {
            n = texto(aba, n, "Atenção: " + r.semDataFinalizacao()
                    + " venda(s) concluída(s) sem data de finalização ficaram fora do relatório.", null);
        }
        n++;
        cabecalho(aba, n++, e, "Grupo", "Linha", "Valor", "% do grupo", "Pagamentos", "Vendas");
        for (RelatorioFormasPagamentoDTO.Grupo g : r.grupos()) {
            for (RelatorioFormasPagamentoDTO.Linha l : g.linhas()) {
                Row row = aba.createRow(n++);
                row.createCell(0).setCellValue(g.rotulo());
                row.createCell(1).setCellValue(l.rotulo());
                numero(row, 2, l.valor(), e.decimal);
                numero(row, 3, l.percentual(), e.decimal);
                numero(row, 4, BigDecimal.valueOf(l.quantidade()), null);
            }
            Row total = aba.createRow(n++);
            celulaNegrito(total, 0, "Total: " + g.rotulo(), e);
            numero(total, 2, g.total(), e.decimalNegrito);
            numero(total, 3, new BigDecimal("100.00"), e.decimalNegrito);
            numero(total, 4, BigDecimal.valueOf(g.quantidadePagamentos()), e.negrito);
            numero(total, 5, BigDecimal.valueOf(g.quantidadeVendas()), e.negrito);
        }
        n++;
        Row geral = aba.createRow(n++);
        celulaNegrito(geral, 0, "Total geral", e);
        numero(geral, 2, r.totalGeral(), e.decimalNegrito);
        numero(geral, 4, BigDecimal.valueOf(r.quantidadePagamentos()), e.negrito);
        numero(geral, 5, BigDecimal.valueOf(r.quantidadeVendas()), e.negrito);
        n++;
        cabecalho(aba, n++, e, "Totais por forma", "", "Valor", "% do total");
        for (RelatorioFormasPagamentoDTO.TotalForma t : r.totaisPorForma()) {
            Row row = aba.createRow(n++);
            row.createCell(0).setCellValue(t.formaRotulo());
            numero(row, 2, t.valor(), e.decimal);
            numero(row, 3, t.percentual(), e.decimal);
        }
        larguras(aba, 38, 28, 16, 12, 12, 10);
    }

    private void detalhe(Sheet aba, List<DetalhePagamentoDTO> detalhe, Estilos e) {
        cabecalho(aba, 0, e, "Data/hora", "Nº da venda", "Filial", "Caixa", "Forma", "Tipo", "Valor", "Parcelas");
        int n = 1;
        for (DetalhePagamentoDTO d : detalhe) {
            Row row = aba.createRow(n++);
            Cell data = row.createCell(0);
            data.setCellValue(d.dataHora());
            data.setCellStyle(e.dataHora);
            numero(row, 1, BigDecimal.valueOf(d.numeroVenda()), null);
            row.createCell(2).setCellValue(d.filial());
            row.createCell(3).setCellValue(d.caixa());
            row.createCell(4).setCellValue(d.formaRotulo());
            row.createCell(5).setCellValue(d.tipo());
            numero(row, 6, d.valor(), e.decimal);
            if (d.parcelas() != null) numero(row, 7, BigDecimal.valueOf(d.parcelas()), null);
        }
        larguras(aba, 18, 14, 34, 18, 20, 14, 14, 10);
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

    /** Valor numérico de verdade (não texto), com o formato monetário quando informado. */
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
    byte[] csv(RelatorioFormasPagamentoDTO r) {
        StringBuilder sb = new StringBuilder("﻿");
        linha(sb, "Grupo", "Linha", "Valor", "% do grupo", "Pagamentos", "Vendas");
        for (RelatorioFormasPagamentoDTO.Grupo g : r.grupos()) {
            for (RelatorioFormasPagamentoDTO.Linha l : g.linhas()) {
                linha(sb, g.rotulo(), l.rotulo(), decimal(l.valor()), decimal(l.percentual()),
                        String.valueOf(l.quantidade()), "");
            }
            linha(sb, "Total: " + g.rotulo(), "", decimal(g.total()), "100,00",
                    String.valueOf(g.quantidadePagamentos()), String.valueOf(g.quantidadeVendas()));
        }
        linha(sb, "Total geral", "", decimal(r.totalGeral()), "", String.valueOf(r.quantidadePagamentos()),
                String.valueOf(r.quantidadeVendas()));
        for (RelatorioFormasPagamentoDTO.TotalForma t : r.totaisPorForma()) {
            linha(sb, "Total por forma", t.formaRotulo(), decimal(t.valor()), decimal(t.percentual()), "", "");
        }
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
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    // ------------------------------------------------------------------ PDF

    byte[] pdf(RelatorioFormasPagamentoDTO r, String usuario, String filtros) {
        return ComprovanteContaPagarService.renderizar(html(r, usuario, filtros));
    }

    String html(RelatorioFormasPagamentoDTO r, String usuario, String filtros) {
        StringBuilder h = new StringBuilder(8192);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head>")
                .append("<meta charset=\"UTF-8\"/><style>")
                .append("@page { size: A4; margin: 16mm 14mm; } body { font-family: sans-serif; font-size: 10pt; }")
                .append(" h1 { font-size: 15pt; margin: 0 0 4px 0; } h2 { font-size: 11pt; margin: 14px 0 4px 0; }")
                .append(" .meta { font-size: 8.5pt; color: #444; margin-bottom: 2px; }")
                .append(" .aviso { font-size: 8.5pt; color: #8a4b00; margin: 6px 0; }")
                .append(" table { width: 100%; border-collapse: collapse; } th, td { border-bottom: 1px solid #ccc;")
                .append(" padding: 3px 4px; text-align: left; } th { background: #eee; font-size: 9pt; }")
                .append(" .num { text-align: right; } tr.total td { font-weight: bold; background: #f6f6f6; }")
                .append(" .rodape { margin-top: 18px; font-size: 8.5pt; color: #555; border-top: 1px solid #aaa;")
                .append(" padding-top: 4px; }")
                .append("</style></head><body>");
        h.append("<h1>Relatório de Formas de Pagamento</h1>");
        h.append("<div class=\"meta\">Período: ").append(esc(r.periodo().de().format(DATA))).append(" a ")
                .append(esc(r.periodo().ate().format(DATA))).append(" · Agrupado por ")
                .append(esc(rotuloAgrupar(r.agruparPor()))).append("</div>");
        h.append("<div class=\"meta\">").append(esc(filtros)).append("</div>");
        h.append("<div class=\"meta\">Gerado por ").append(esc(usuario)).append(" em ")
                .append(esc(r.geradoEm().format(DATA_HORA))).append("</div>");
        if (r.semDataFinalizacao() > 0) {
            h.append("<div class=\"aviso\">Atenção: ").append(r.semDataFinalizacao())
                    .append(" venda(s) concluída(s) sem data de finalização ficaram fora do relatório.</div>");
        }
        if (r.grupos().isEmpty()) h.append("<p>Nenhuma venda no período.</p>");
        for (RelatorioFormasPagamentoDTO.Grupo g : r.grupos()) {
            h.append("<h2>").append(esc(g.rotulo())).append("</h2><table><tr><th>")
                    .append(esc(r.agruparPor() == AgruparPor.FORMA ? "Filial" : "Forma"))
                    .append("</th><th class=\"num\">Valor</th><th class=\"num\">%</th>")
                    .append("<th class=\"num\">Pagamentos</th></tr>");
            for (RelatorioFormasPagamentoDTO.Linha l : g.linhas()) {
                h.append("<tr><td>").append(esc(l.rotulo())).append("</td><td class=\"num\">")
                        .append(esc(moeda(l.valor()))).append("</td><td class=\"num\">")
                        .append(esc(decimal(l.percentual()))).append("%</td><td class=\"num\">")
                        .append(l.quantidade()).append("</td></tr>");
            }
            h.append("<tr class=\"total\"><td>Total (").append(g.quantidadeVendas()).append(" venda(s))</td>")
                    .append("<td class=\"num\">").append(esc(moeda(g.total()))).append("</td><td class=\"num\">100,00%")
                    .append("</td><td class=\"num\">").append(g.quantidadePagamentos()).append("</td></tr></table>");
        }
        h.append("<h2>Total geral</h2><table><tr><th>Forma</th><th class=\"num\">Valor</th><th class=\"num\">%</th></tr>");
        for (RelatorioFormasPagamentoDTO.TotalForma t : r.totaisPorForma()) {
            h.append("<tr><td>").append(esc(t.formaRotulo())).append("</td><td class=\"num\">")
                    .append(esc(moeda(t.valor()))).append("</td><td class=\"num\">").append(esc(decimal(t.percentual())))
                    .append("%</td></tr>");
        }
        h.append("<tr class=\"total\"><td>Total (").append(r.quantidadeVendas()).append(" venda(s), ")
                .append(r.quantidadePagamentos()).append(" pagamento(s))</td><td class=\"num\">")
                .append(esc(moeda(r.totalGeral()))).append("</td><td></td></tr></table>");
        h.append("<div class=\"rodape\">").append(esc(RODAPE)).append("</div></body></html>");
        return h.toString();
    }

    private static String esc(String texto) {
        return ComprovanteContaPagarHtml.escapar(texto);
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor).replace(' ', ' ');
    }

    private static String rotuloAgrupar(AgruparPor a) {
        return switch (a) {
            case FILIAL -> "Filial/PDV";
            case FORMA -> "Forma de pagamento";
            case CAIXA -> "Caixa";
        };
    }
}
