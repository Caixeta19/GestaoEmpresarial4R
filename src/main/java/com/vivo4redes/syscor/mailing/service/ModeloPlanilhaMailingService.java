package com.vivo4redes.syscor.mailing.service;

import com.vivo4redes.syscor.exception.NegocioException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ModeloPlanilhaMailingService {

    private static final int QUANTIDADE_PARAMETROS_MODELO = 3;

    public byte[] gerarModelo() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet planilha = wb.createSheet("Destinatarios");

            CellStyle estiloCabecalho = wb.createCellStyle();
            Font fonteCabecalho = wb.createFont();
            fonteCabecalho.setBold(true);
            fonteCabecalho.setColor(IndexedColors.WHITE.getIndex());
            estiloCabecalho.setFont(fonteCabecalho);
            estiloCabecalho.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            estiloCabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row cabecalho = planilha.createRow(0);
            String[] colunas = new String[2 + QUANTIDADE_PARAMETROS_MODELO];
            colunas[0] = "Telefone";
            colunas[1] = "ClienteId";
            for (int i = 0; i < QUANTIDADE_PARAMETROS_MODELO; i++) {
                colunas[2 + i] = "Parametro" + (i + 1);
            }
            for (int c = 0; c < colunas.length; c++) {
                Cell cell = cabecalho.createCell(c);
                cell.setCellValue(colunas[c]);
                cell.setCellStyle(estiloCabecalho);
                planilha.setColumnWidth(c, 20 * 256);
            }

            preencherLinhaExemplo(planilha.createRow(1), "11999998888", "", "Maria", "iPhone 15", "");
            preencherLinhaExemplo(planilha.createRow(2), "", "42", "João", "Galaxy S24", "");

            Sheet instrucoes = wb.createSheet("Instruções");
            String[] textos = {
                    "Como preencher:",
                    "- Preencha Telefone OU ClienteId em cada linha (não precisa dos dois).",
                    "- Telefone: DDD + número, com ou sem 55 na frente (o sistema completa sozinho).",
                    "- ClienteId: use quando quiser puxar o telefone do cadastro do cliente automaticamente.",
                    "- Parametro1, Parametro2... substituem {{1}}, {{2}}... do corpo do template, na ordem.",
                    "- Pode apagar colunas de parâmetro que não usar, ou adicionar mais (Parametro4, Parametro5...).",
                    "- Não precisa preencher todas as linhas de parâmetro se o template usar menos.",
                    "- Linhas totalmente em branco são ignoradas na importação."
            };
            for (int i = 0; i < textos.length; i++) {
                instrucoes.createRow(i).createCell(0).setCellValue(textos[i]);
            }
            instrucoes.setColumnWidth(0, 100 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new NegocioException("Não foi possível gerar o modelo de planilha: " + e.getMessage());
        }
    }

    private void preencherLinhaExemplo(Row row, String... valores) {
        for (int c = 0; c < valores.length; c++) {
            row.createCell(c).setCellValue(valores[c]);
        }
    }
}