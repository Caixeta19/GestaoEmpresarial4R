package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.financeiro.dto.LinhaConsolidadoVivoDTO;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lê o relatório consolidado de comissão da Vivo (export manual, .csv/.txt/.xlsx).
 * Resolve colunas pelo nome do cabeçalho, tolerante a grafia/ordem — mesmo
 * padrão do RelatorioSapParser (Inventário) e do TabelaPrecoTelefonicaParser.
 */
@Component
public class ConsolidadoVivoParser {

    private static final Map<String, String> ALIAS_COLUNAS = Map.ofEntries(
            Map.entry("id transacao", "idTransacao"),
            Map.entry("idtransacao", "idTransacao"),
            Map.entry("id", "idTransacao"),
            Map.entry("acesso", "acesso"),
            Map.entry("numero de acesso", "acesso"),
            Map.entry("nº de acesso", "acesso"),
            Map.entry("cliente", "cliente"),
            Map.entry("nome", "cliente"),
            Map.entry("cpf", "cpf"),
            Map.entry("cpf/cnpj", "cpf"),
            Map.entry("plano", "plano"),
            Map.entry("valor comissao", "valorComissao"),
            Map.entry("comissao", "valorComissao"),
            Map.entry("valor", "valorComissao"),
            Map.entry("data venda", "dataVenda"),
            Map.entry("data", "dataVenda")
    );

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    public List<LinhaConsolidadoVivoDTO> parsear(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie o relatório consolidado de comissão da Vivo (.csv, .xlsx ou .txt).");
        }

        String nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase();
        try {
            List<LinhaConsolidadoVivoDTO> linhas = nome.endsWith(".xlsx") || nome.endsWith(".xls")
                    ? parsearExcel(arquivo)
                    : parsearTexto(arquivo);

            if (linhas.isEmpty()) {
                throw new NegocioException("O relatório consolidado não contém nenhuma linha de dados.");
            }
            return linhas;
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler o arquivo enviado: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- CSV/TXT

    private List<LinhaConsolidadoVivoDTO> parsearTexto(MultipartFile arquivo) throws IOException {
        String conteudo = decodificar(arquivo.getBytes());

        List<String> linhas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(conteudo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (!linha.isBlank()) linhas.add(linha);
            }
        }
        if (linhas.isEmpty()) {
            throw new NegocioException("O arquivo enviado está vazio.");
        }

        char delimitador = detectarDelimitador(linhas.get(0));
        String[] cabecalho = linhas.get(0).split(Pattern.quote(String.valueOf(delimitador)), -1);
        Map<String, Integer> indices = mapearIndices(cabecalho);

        List<LinhaConsolidadoVivoDTO> resultado = new ArrayList<>();
        for (int i = 1; i < linhas.size(); i++) {
            String[] campos = linhas.get(i).split(Pattern.quote(String.valueOf(delimitador)), -1);
            resultado.add(montarLinha(indices, idx -> idx < campos.length ? campos[idx].trim() : ""));
        }
        return resultado;
    }

    private String decodificar(byte[] bytes) {
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
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

    private List<LinhaConsolidadoVivoDTO> parsearExcel(MultipartFile arquivo) throws IOException {
        try (InputStream is = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row cabecalho = sheet.getRow(sheet.getFirstRowNum());
            if (cabecalho == null) {
                throw new NegocioException("A planilha enviada está vazia.");
            }
            int totalColunas = cabecalho.getLastCellNum();
            String[] nomesColunas = new String[totalColunas];
            for (int c = 0; c < totalColunas; c++) {
                nomesColunas[c] = formatter.formatCellValue(cabecalho.getCell(c));
            }
            Map<String, Integer> indices = mapearIndices(nomesColunas);

            List<LinhaConsolidadoVivoDTO> resultado = new ArrayList<>();
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
            String campo = ALIAS_COLUNAS.get(normalizar(cabecalho[i]));
            if (campo != null && !indices.containsKey(campo)) {
                indices.put(campo, i);
            }
        }
        if (!indices.containsKey("acesso")) {
            throw new NegocioException(
                    "Não encontrei a coluna de número de acesso no arquivo. " +
                            "Colunas esperadas (nome pode variar): ID Transação, Acesso, Cliente, CPF, Plano, Valor Comissão, Data Venda.");
        }
        return indices;
    }

    private LinhaConsolidadoVivoDTO montarLinha(Map<String, Integer> indices, java.util.function.IntFunction<String> valorNaColuna) {
        String idTransacao = valorEm(indices, "idTransacao", valorNaColuna);
        String acesso = valorEm(indices, "acesso", valorNaColuna);
        String cliente = valorEm(indices, "cliente", valorNaColuna);
        String cpf = valorEm(indices, "cpf", valorNaColuna);
        String plano = valorEm(indices, "plano", valorNaColuna);
        String valorTexto = valorEm(indices, "valorComissao", valorNaColuna);
        String dataTexto = valorEm(indices, "dataVenda", valorNaColuna);

        return new LinhaConsolidadoVivoDTO(
                idTransacao, acesso, cliente, cpf, plano,
                parsearValor(valorTexto), parsearData(dataTexto)
        );
    }

    private String valorEm(Map<String, Integer> indices, String campo, java.util.function.IntFunction<String> valorNaColuna) {
        Integer idx = indices.get(campo);
        return idx == null ? "" : valorNaColuna.apply(idx);
    }

    private BigDecimal parsearValor(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String limpo = texto.replace("R$", "").trim().replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(limpo).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parsearData(String texto) {
        if (texto == null || texto.isBlank()) return null;
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return LocalDate.parse(texto.trim(), formato);
            } catch (DateTimeParseException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}