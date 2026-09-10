package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.exception.NegocioException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * US-206: lê o relatório de inventário exportado do SAP via transação IQ09.
 * Aceita .csv/.txt (delimitado por ; , ou tab, com acentuação em Latin-1 —
 * export clássico do SAP GUI) e .xlsx.
 * <p>
 * O parser é tolerante à ordem e à grafia exata das colunas: resolve pelo
 * nome do cabeçalho normalizado (sem acento, minúsculo), não pela posição.
 */
@Component
public class RelatorioSapParser {

    private static final Map<String, String> ALIAS_COLUNAS = Map.ofEntries(
            Map.entry("material", "material"),
            Map.entry("denominacao", "denominacao"),
            Map.entry("descricao", "denominacao"),
            Map.entry("no de serie", "serial"),
            Map.entry("numero de serie", "serial"),
            Map.entry("nr serie", "serial"),
            Map.entry("serial", "serial"),
            Map.entry("imei", "serial"),
            Map.entry("centro", "centro"),
            Map.entry("deposito", "deposito"),
            Map.entry("tipo de estoque", "tipoEstoque"),
            Map.entry("status sistema", "statusSistema"),
            Map.entry("status", "statusSistema"),
            Map.entry("modificado em", "modificadoEm"),
            Map.entry("modificado por", "modificadoPor")
    );

    public List<LinhaSapDTO> parsear(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie o relatório SAP (IQ09) exportado em .csv, .xlsx ou .txt.");
        }

        String nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase();
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
        byte[] bytes = arquivo.getBytes();
        String conteudo = decodificar(bytes);

        List<String> linhas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(conteudo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (!linha.isBlank()) {
                    linhas.add(linha);
                }
            }
        }

        if (linhas.isEmpty()) {
            throw new NegocioException("O arquivo enviado está vazio.");
        }

        char delimitador = detectarDelimitador(linhas.get(0));
        String[] cabecalho = linhas.get(0).split(java.util.regex.Pattern.quote(String.valueOf(delimitador)), -1);
        Map<String, Integer> indices = mapearIndices(cabecalho);

        List<LinhaSapDTO> resultado = new ArrayList<>();
        for (int i = 1; i < linhas.size(); i++) {
            String[] campos = linhas.get(i).split(java.util.regex.Pattern.quote(String.valueOf(delimitador)), -1);
            resultado.add(montarLinha(indices, idx -> idx < campos.length ? campos[idx].trim() : ""));
        }
        return resultado;
    }

    /** SAP exporta em Latin-1/ISO-8859-1 quando tem acentuação (Denominação, Depósito etc.). Tenta UTF-8 primeiro, cai para Latin-1. */
    private String decodificar(byte[] bytes) {
        try {
            java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private char detectarDelimitador(String linhaCabecalho) {
        char melhor = ';';
        int melhorContagem = -1;
        for (char candidato : new char[]{';', ',', '\t'}) {
            int contagem = (int) linhaCabecalho.chars().filter(c -> c == candidato).count();
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
            DataFormatter formatter = new DataFormatter();

            Row linhaCabecalho = sheet.getRow(sheet.getFirstRowNum());
            if (linhaCabecalho == null) {
                throw new NegocioException("A planilha enviada está vazia.");
            }
            int totalColunas = linhaCabecalho.getLastCellNum();
            String[] cabecalho = new String[totalColunas];
            for (int c = 0; c < totalColunas; c++) {
                cabecalho[c] = formatter.formatCellValue(linhaCabecalho.getCell(c));
            }
            Map<String, Integer> indices = mapearIndices(cabecalho);

            List<LinhaSapDTO> resultado = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row linha = sheet.getRow(r);
                if (linha == null) continue;
                final Row linhaFinal = linha;
                resultado.add(montarLinha(indices, idx -> formatter.formatCellValue(linhaFinal.getCell(idx)).trim()));
            }
            return resultado;
        }
    }

    // ---------------------------------------------------------------- Comum

    private Map<String, Integer> mapearIndices(String[] cabecalho) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < cabecalho.length; i++) {
            String chaveNormalizada = normalizar(cabecalho[i]);
            String campo = ALIAS_COLUNAS.get(chaveNormalizada);
            if (campo != null) {
                indices.put(campo, i);
            }
        }
        if (!indices.containsKey("serial")) {
            throw new NegocioException(
                    "Não encontrei a coluna de número de série/IMEI no arquivo. " +
                            "Colunas esperadas (nome pode variar): Material, Denominação, Nº de série, Centro, Depósito, Status sistema.");
        }
        return indices;
    }

    private LinhaSapDTO montarLinha(Map<String, Integer> indices, java.util.function.IntFunction<String> valorNaColuna) {
        return new LinhaSapDTO(
                valorEm(indices, "material", valorNaColuna),
                valorEm(indices, "denominacao", valorNaColuna),
                valorEm(indices, "serial", valorNaColuna),
                valorEm(indices, "centro", valorNaColuna),
                valorEm(indices, "deposito", valorNaColuna),
                valorEm(indices, "tipoEstoque", valorNaColuna),
                valorEm(indices, "statusSistema", valorNaColuna),
                valorEm(indices, "modificadoEm", valorNaColuna),
                valorEm(indices, "modificadoPor", valorNaColuna)
        );
    }

    private String valorEm(Map<String, Integer> indices, String campo, java.util.function.IntFunction<String> valorNaColuna) {
        Integer idx = indices.get(campo);
        return idx == null ? "" : valorNaColuna.apply(idx);
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.trim().toLowerCase(Locale.ROOT);
    }
}