package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.exception.NegocioException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * Lê a IQ09 exportada do SAP (.xlsx, ou .csv/.txt delimitado por ; , ou tab, em UTF-8 ou Latin-1).
 * <p>
 * Resolve as colunas pelo nome do cabeçalho normalizado (NFKD, sem acento, sem {@code º°.()}, minúsculo), não pela
 * posição. Cabeçalho do export real: Material, Texto breve material, Nº de série, Centro, Depósito,
 * Tipo estoque (reg.principal), Status do sistema, Modificado em, Modificado por, Ctg.equipamento, Status usuário.
 */
@Component
public class RelatorioSapParser {

    private static final Map<String, String> ALIAS_COLUNAS = Map.ofEntries(
            Map.entry("material", "material"),
            Map.entry("texto breve material", "denominacao"),
            Map.entry("texto breve", "denominacao"),
            Map.entry("denominacao", "denominacao"),
            Map.entry("descricao", "denominacao"),
            Map.entry("no de serie", "serial"),
            Map.entry("n de serie", "serial"),
            Map.entry("no serie", "serial"),
            Map.entry("n serie", "serial"),
            Map.entry("numero de serie", "serial"),
            Map.entry("nr serie", "serial"),
            Map.entry("serial", "serial"),
            Map.entry("imei", "serial"),
            Map.entry("centro", "centro"),
            Map.entry("deposito", "deposito"),
            Map.entry("tipo estoque reg principal", "tipoEstoque"),
            Map.entry("tipo estoque", "tipoEstoque"),
            Map.entry("tipo de estoque", "tipoEstoque"),
            Map.entry("status do sistema", "statusSistema"),
            Map.entry("status sistema", "statusSistema"),
            Map.entry("status", "statusSistema"),
            Map.entry("status usuario", "statusUsuario"),
            Map.entry("status do usuario", "statusUsuario"),
            Map.entry("ctg equipamento", "categoriaEquipamento"),
            Map.entry("categoria equipamento", "categoriaEquipamento"),
            Map.entry("categoria de equipamento", "categoriaEquipamento"),
            Map.entry("modificado em", "modificadoEm"),
            Map.entry("modificado por", "modificadoPor")
    );

    /** Excel exportando número longo como texto científico (ex.: 2.20181E+14): os dígitos finais já se perderam. */
    private static final Pattern NOTACAO_CIENTIFICA = Pattern.compile("^[0-9]+([.,][0-9]+)?[eE][+-]?[0-9]+$");

    /** Maior inteiro que um double representa sem perda (2^53). */
    private static final double MAIOR_INTEIRO_EXATO = 9_007_199_254_740_992d;

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE);

    public List<LinhaSapDTO> parsear(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie a IQ09 exportada do SAP em .xlsx, .csv ou .txt.");
        }
        String nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        try {
            if (nome.endsWith(".xlsx") || nome.endsWith(".xls")) {
                return parsearExcel(arquivo);
            }
            return parsearTexto(arquivo);
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler o arquivo enviado: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- CSV/TXT

    private List<LinhaSapDTO> parsearTexto(MultipartFile arquivo) throws IOException {
        String conteudo = decodificar(arquivo.getBytes());
        List<String> linhas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(conteudo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                linhas.add(linha);
            }
        }
        while (!linhas.isEmpty() && linhas.get(0).isBlank()) linhas.remove(0);
        if (linhas.isEmpty()) {
            throw new NegocioException("O arquivo enviado está vazio.");
        }

        String delimitador = Pattern.quote(String.valueOf(detectarDelimitador(linhas.get(0))));
        Map<String, Integer> indices = mapearIndices(linhas.get(0).replace("﻿", "").split(delimitador, -1));

        List<LinhaSapDTO> resultado = new ArrayList<>();
        for (int i = 1; i < linhas.size(); i++) {
            if (linhas.get(i).isBlank()) continue;
            String[] campos = linhas.get(i).split(delimitador, -1);
            int numeroLinha = i + 1;
            resultado.add(montarLinha(numeroLinha, indices,
                    idx -> idx < campos.length ? limpar(campos[idx]) : "",
                    idx -> idx < campos.length ? lerData(limpar(campos[idx])) : null));
        }
        return resultado;
    }

    /** SAP GUI exporta em Latin-1 quando há acento. Tenta UTF-8 primeiro, cai para Latin-1. */
    private String decodificar(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private char detectarDelimitador(String linhaCabecalho) {
        char melhor = ';';
        long melhorContagem = -1;
        for (char candidato : new char[]{';', ',', '\t'}) {
            long contagem = linhaCabecalho.chars().filter(c -> c == candidato).count();
            if (contagem > melhorContagem) {
                melhorContagem = contagem;
                melhor = candidato;
            }
        }
        return melhor;
    }

    // ---------------------------------------------------------------- XLSX

    private List<LinhaSapDTO> parsearExcel(MultipartFile arquivo) throws IOException {
        try (InputStream is = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);

            Row linhaCabecalho = sheet.getRow(sheet.getFirstRowNum());
            if (linhaCabecalho == null) {
                throw new NegocioException("A planilha enviada está vazia.");
            }
            String[] cabecalho = new String[Math.max(linhaCabecalho.getLastCellNum(), 0)];
            for (int c = 0; c < cabecalho.length; c++) {
                cabecalho[c] = formatter.formatCellValue(linhaCabecalho.getCell(c));
            }
            Map<String, Integer> indices = mapearIndices(cabecalho);

            List<LinhaSapDTO> resultado = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row linha = sheet.getRow(r);
                if (linha == null || linhaVazia(linha, formatter)) continue;
                int numeroLinha = r + 1;
                resultado.add(montarLinha(numeroLinha, indices,
                        idx -> textoDaCelula(linha.getCell(idx), formatter, numeroLinha),
                        idx -> dataDaCelula(linha.getCell(idx), formatter)));
            }
            return resultado;
        }
    }

    private boolean linhaVazia(Row linha, DataFormatter formatter) {
        for (Cell celula : linha) {
            if (!formatter.formatCellValue(celula).isBlank()) return false;
        }
        return true;
    }

    /** Texto da célula sem depender do formato de exibição: número inteiro vira texto exato (nunca 2.2E+14). */
    private String textoDaCelula(Cell celula, DataFormatter formatter, int numeroLinha) {
        if (celula == null) return "";
        CellType tipo = celula.getCellType() == CellType.FORMULA ? celula.getCachedFormulaResultType() : celula.getCellType();
        if (tipo == CellType.STRING) {
            return limpar(celula.getStringCellValue());
        }
        if (tipo == CellType.NUMERIC && !DateUtil.isCellDateFormatted(celula)) {
            double valor = celula.getNumericCellValue();
            if (valor == Math.rint(valor)) {
                if (Math.abs(valor) >= MAIOR_INTEIRO_EXATO) {
                    throw new NegocioException("Linha " + numeroLinha + ": o número " + formatter.formatCellValue(celula)
                            + " está gravado como número e perdeu dígitos no Excel. Exporte a IQ09 com o Nº de série como texto.");
                }
                return BigDecimal.valueOf(valor).toBigInteger().toString();
            }
            return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
        }
        return limpar(formatter.formatCellValue(celula));
    }

    private LocalDate dataDaCelula(Cell celula, DataFormatter formatter) {
        if (celula == null) return null;
        CellType tipo = celula.getCellType() == CellType.FORMULA ? celula.getCachedFormulaResultType() : celula.getCellType();
        if (tipo == CellType.NUMERIC && DateUtil.isCellDateFormatted(celula)) {
            return celula.getLocalDateTimeCellValue().toLocalDate();
        }
        if (tipo == CellType.NUMERIC) {
            double serialExcel = celula.getNumericCellValue();
            return DateUtil.isValidExcelDate(serialExcel) ? DateUtil.getLocalDateTime(serialExcel).toLocalDate() : null;
        }
        return lerData(limpar(formatter.formatCellValue(celula)));
    }

    // ---------------------------------------------------------------- Comum

    private Map<String, Integer> mapearIndices(String[] cabecalho) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < cabecalho.length; i++) {
            String campo = ALIAS_COLUNAS.get(normalizarCabecalho(cabecalho[i]));
            if (campo != null) {
                indices.putIfAbsent(campo, i);
            }
        }
        if (!indices.containsKey("serial")) {
            throw new NegocioException("Não encontrei a coluna \"Nº de série\" na IQ09. Colunas esperadas: Material, "
                    + "Texto breve material, Nº de série, Centro, Depósito, Tipo estoque (reg.principal), Status do sistema, "
                    + "Modificado em, Modificado por, Ctg.equipamento, Status usuário.");
        }
        if (!indices.containsKey("centro")) {
            throw new NegocioException("Não encontrei a coluna \"Centro\" na IQ09: sem ela não dá para separar a filial.");
        }
        return indices;
    }

    private LinhaSapDTO montarLinha(int numeroLinha, Map<String, Integer> indices,
                                    IntFunction<String> texto, IntFunction<LocalDate> data) {
        String serial = valor(indices, "serial", texto);
        if (NOTACAO_CIENTIFICA.matcher(serial).matches()) {
            throw new NegocioException("Linha " + numeroLinha + ": o Nº de série \"" + serial + "\" está em notação científica "
                    + "e perdeu dígitos no Excel. Exporte a IQ09 com o Nº de série como texto.");
        }
        Integer idxData = indices.get("modificadoEm");
        return new LinhaSapDTO(
                numeroLinha,
                valor(indices, "material", texto),
                valor(indices, "denominacao", texto),
                serial,
                valor(indices, "centro", texto).toUpperCase(Locale.ROOT),
                valor(indices, "deposito", texto).toUpperCase(Locale.ROOT),
                valor(indices, "tipoEstoque", texto),
                valor(indices, "statusSistema", texto),
                valor(indices, "statusUsuario", texto).toUpperCase(Locale.ROOT),
                valor(indices, "categoriaEquipamento", texto),
                idxData == null ? null : data.apply(idxData),
                valor(indices, "modificadoPor", texto));
    }

    private String valor(Map<String, Integer> indices, String campo, IntFunction<String> texto) {
        Integer idx = indices.get(campo);
        return idx == null ? "" : texto.apply(idx);
    }

    private static String limpar(String texto) {
        return texto == null ? "" : texto.replace("﻿", "").trim().replaceAll("\\s+", " ");
    }

    private static LocalDate lerData(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String data = texto.length() > 10 ? texto.substring(0, 10) : texto;
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return LocalDate.parse(data, formato);
            } catch (DateTimeParseException ignorada) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    /** "Nº de série" -> "no de serie"; "Tipo estoque (reg.principal)" -> "tipo estoque reg principal". */
    static String normalizarCabecalho(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto.replace("﻿", ""), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[º°.()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
