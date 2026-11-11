package com.vivo4redes.syscor.estoque;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * IQ09 sintética no layout do export real do SAP (mesmo cabeçalho, aba Sheet1). Nunca use um arquivo real nos testes.
 */
public final class Iq09Sintetica {

    public static final String[] CABECALHO = {
            "Material", "Texto breve material", "Nº de série", "Centro", "Depósito", "Tipo estoque (reg.principal)",
            "Status do sistema", "Modificado em", "Modificado por", "Ctg.equipamento", "Status usuário"};

    private final List<Object[]> linhas = new ArrayList<>();

    public static Iq09Sintetica nova() {
        return new Iq09Sintetica();
    }

    /** Linha no estoque da loja (status usuário ADEP). */
    public Iq09Sintetica linha(String serial, String centro) {
        return linha(serial, centro, "ADEP", "DEPS");
    }

    public Iq09Sintetica linha(String serial, String centro, String statusUsuario, String statusSistema) {
        linhas.add(new Object[]{"22018137", "APARELHO SINTETICO " + serial, serial, centro, "RPAR", "01", statusSistema,
                LocalDate.of(2026, 11, 3), "GOT00000001", "S", statusUsuario});
        return this;
    }

    /** Linha com o Nº de série gravado como número (o Excel faz isso com IMEI). */
    public Iq09Sintetica linhaComSerialNumerico(long serial, String centro) {
        linhas.add(new Object[]{"22018137", "APARELHO NUMERICO", serial, centro, "RPAR", "01", "DEPS",
                LocalDate.of(2026, 11, 3), "GOT00000001", "S", ""});
        return this;
    }

    public byte[] bytes() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            CellStyle data = wb.createCellStyle();
            data.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd.mm.yyyy"));
            Row cabecalho = sheet.createRow(0);
            for (int c = 0; c < CABECALHO.length; c++) {
                cabecalho.createCell(c).setCellValue(CABECALHO[c]);
            }
            for (int r = 0; r < linhas.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Object[] valores = linhas.get(r);
                for (int c = 0; c < valores.length; c++) {
                    Cell cell = row.createCell(c);
                    Object v = valores[c];
                    if (v instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(data);
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else if (v != null) {
                        cell.setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public MockMultipartFile arquivo() {
        return new MockMultipartFile("arquivo", "EXPORT_20261110_090000.XLSX",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes());
    }
}
