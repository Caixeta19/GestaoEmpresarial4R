package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.exception.NegocioException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

/**
 * Lê CSV/TXT (delimitador ; , ou tab; UTF-8 ou Latin-1; campos entre aspas) e
 * XLSX/XLS (primeira aba) e devolve linhas indexadas pelo cabeçalho NORMALIZADO
 * (sem acento, minúsculo, só letras/dígitos separados por espaço).
 * Reaproveitável por qualquer importador do módulo.
 */
@Component
public class LeitorTabular {

    /** Linha de dados com o número da linha no arquivo (1 = cabeçalho) para mensagens de erro. */
    public record Linha(int numero, Map<String, String> valores) {
    }

    /** Resultado da leitura: colunas presentes no arquivo + linhas de dados. */
    public record Tabela(Set<String> colunas, List<Linha> linhas) {
    }

    public Tabela ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie um arquivo .csv, .txt ou .xlsx com dados.");
        }
        String nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        try {
            Tabela tabela = (nome.endsWith(".xlsx") || nome.endsWith(".xls")) ? lerExcel(arquivo) : lerTexto(arquivo);
            if (tabela.linhas().isEmpty()) {
                throw new NegocioException("O arquivo não contém nenhuma linha de dados.");
            }
            return tabela;
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler o arquivo enviado: " + e.getMessage());
        }
    }

    /** Normaliza um nome de coluna/alias: "Nº Compra" e "no compra" viram a mesma chave. */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    // ------------------------------------------------------------------ texto

    private Tabela lerTexto(MultipartFile arquivo) throws IOException {
        String conteudo = decodificar(arquivo.getBytes());
        List<String> linhasBrutas = new ArrayList<>();
        for (String l : conteudo.split("\\R")) {
            if (!l.isBlank()) linhasBrutas.add(l);
        }
        if (linhasBrutas.isEmpty()) {
            throw new NegocioException("O arquivo enviado está vazio.");
        }
        char delimitador = detectarDelimitador(linhasBrutas.get(0));
        List<String> cabecalho = dividir(linhasBrutas.get(0), delimitador);

        Set<String> colunas = new LinkedHashSet<>();
        for (String c : cabecalho) colunas.add(normalizar(c));

        List<Linha> linhas = new ArrayList<>();
        for (int i = 1; i < linhasBrutas.size(); i++) {
            List<String> campos = dividir(linhasBrutas.get(i), delimitador);
            linhas.add(new Linha(i + 1, montar(cabecalho, campos::get, campos.size())));
        }
        return new Tabela(colunas, linhas);
    }

    private String decodificar(byte[] bytes) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private char detectarDelimitador(String cabecalho) {
        char melhor = ';';
        long maior = -1;
        for (char c : new char[]{';', ',', '\t'}) {
            long n = cabecalho.chars().filter(x -> x == c).count();
            if (n > maior) {
                maior = n;
                melhor = c;
            }
        }
        return melhor;
    }

    /** Split que respeita campos entre aspas (aspas duplas escapadas como ""). */
    private List<String> dividir(String linha, char delimitador) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean emAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (emAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    emAspas = !emAspas;
                }
            } else if (c == delimitador && !emAspas) {
                campos.add(atual.toString().trim());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString().trim());
        return campos;
    }

    // ------------------------------------------------------------------ excel

    private Tabela lerExcel(MultipartFile arquivo) throws IOException {
        try (InputStream is = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            int primeira = sheet.getFirstRowNum();
            Row cab = sheet.getRow(primeira);
            if (cab == null) {
                throw new NegocioException("A planilha enviada está vazia.");
            }
            int total = cab.getLastCellNum();
            List<String> cabecalho = new ArrayList<>();
            Set<String> colunas = new LinkedHashSet<>();
            for (int c = 0; c < total; c++) {
                String nome = formatter.formatCellValue(cab.getCell(c));
                cabecalho.add(nome);
                colunas.add(normalizar(nome));
            }

            List<Linha> linhas = new ArrayList<>();
            for (int r = primeira + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                final Row rowFinal = row;
                Map<String, String> valores = montar(cabecalho, idx -> textoCelula(rowFinal.getCell(idx), formatter), total);
                if (valores.values().stream().allMatch(String::isBlank)) continue;
                linhas.add(new Linha(r + 1, valores));
            }
            return new Tabela(colunas, linhas);
        }
    }

    /**
     * Texto da célula preservando o que importa para estoque:
     * números grandes (IMEI de 15 dígitos) sem notação científica e datas em ISO.
     */
    private String textoCelula(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        CellType tipo = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (tipo == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDate data = cell.getLocalDateTimeCellValue().toLocalDate();
                return data.toString();
            }
            return new BigDecimal(String.valueOf(cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    // ------------------------------------------------------------------ comum

    private Map<String, String> montar(List<String> cabecalho,
                                       java.util.function.IntFunction<String> valorNaColuna,
                                       int disponiveis) {
        Map<String, String> valores = new LinkedHashMap<>();
        for (int i = 0; i < cabecalho.size(); i++) {
            String chave = normalizar(cabecalho.get(i));
            if (chave.isEmpty()) continue;
            String valor = i < disponiveis ? valorNaColuna.apply(i) : "";
            valores.putIfAbsent(chave, valor == null ? "" : valor.trim());
        }
        return valores;
    }
}