package com.vivo4redes.syscor.estoque;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MB52 sintética no layout do export do SAP. Nunca use um arquivo real nos testes. Valores {@code Number} viram célula
 * numérica; {@code String} vira texto (para simular "5-", "1.234,000" e material com zeros).
 */
public final class Mb52Sintetica {

    public static final String[] CABECALHO = {
            "Material", "Texto breve material", "Centro", "Depósito", "Unidade de medida básica", "Utilização livre",
            "Em controle de qualidade", "Bloqueado", "Em trânsito/transferência", "Valor utilização livre"};

    private String[] cabecalho = CABECALHO;
    private final List<Object[]> linhas = new ArrayList<>();
    private final List<Object[]> antesDoCabecalho = new ArrayList<>();

    public static Mb52Sintetica nova() {
        return new Mb52Sintetica();
    }

    public Mb52Sintetica cabecalho(String... colunas) {
        this.cabecalho = colunas;
        return this;
    }

    /** Linha de título antes do cabeçalho (o SAP às vezes exporta assim). */
    public Mb52Sintetica titulo(String texto) {
        antesDoCabecalho.add(new Object[]{texto});
        return this;
    }

    /** Linha no layout padrão: material, centro, depósito, livre (UN), sem outros estoques, valor livre. */
    public Mb52Sintetica linha(Object material, String centro, String deposito, Object livre, Object valorLivre) {
        return bruta(material, "MATERIAL " + material, centro, deposito, "UN", livre, 0, 0, 0, valorLivre);
    }

    public Mb52Sintetica bruta(Object... valores) {
        linhas.add(valores);
        return this;
    }

    public byte[] bytes() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            int r = 0;
            for (Object[] titulo : antesDoCabecalho) escrever(sheet.createRow(r++), titulo);
            escrever(sheet.createRow(r++), Arrays.stream(cabecalho).toArray());
            for (Object[] linha : linhas) escrever(sheet.createRow(r++), linha);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escrever(Row row, Object[] valores) {
        for (int c = 0; c < valores.length; c++) {
            Object v = valores[c];
            if (v == null) continue;
            Cell cell = row.createCell(c);
            if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else {
                cell.setCellValue(v.toString());
            }
        }
    }

    public MockMultipartFile arquivo() {
        return arquivo("mb52");
    }

    public MockMultipartFile arquivo(String nomeParametro) {
        return new MockMultipartFile(nomeParametro, "MB52_20261119.XLSX",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes());
    }
}
