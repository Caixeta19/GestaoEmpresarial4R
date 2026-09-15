package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.LinhaPrecoDTO;
import com.vivo4redes.syscor.exception.NegocioException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê a planilha de preço enviada pela Telefônica (export manual diário) e
 * extrai, por aba, o preço da TABELA REGULAR por (produto, oferta de
 * comunicação/plano) — sem forma de pagamento (PIX/parcelas ficam de fora,
 * por decisão de escopo) e sem TABELA RENOVA (deferida).
 * <p>
 * Cada aba de preço só tem "Nome Comercial" como identificador — a
 * correlação com o Cod_Sap (nosso SKU) vem da aba PRODUTOS, que funciona
 * como um dicionário Nome Comercial → Cod_Sap.
 */
@Component
public class TabelaPrecoTelefonicaParser {

    /** Nome da aba -> rótulo salvo em ProdutoPrecoPlano.categoriaPlanilha. Fora dessa lista, a aba é ignorada. */
    private static final Map<String, String> ABAS_DE_PRECO = Map.of(
            "SMARTPHONES", "SMARTPHONES",
            "ELETRÔNICOS_LP_Conectados", "ELETRONICOS_CONECTADOS",
            "ELETRÔNICOS_LP_Não Conectados", "ELETRONICOS_NAO_CONECTADOS",
            "VITRINE", "VITRINE",
            "SMARTPHONES_Demo", "SMARTPHONES_DEMO"
    );

    private static final Set<String> ALIASES_PRECO = Set.of("pre", "pvp", "pvp base", "pvd");
    private static final Pattern PADRAO_VIGENCIA = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");

    public ResultadoParseTabelaPreco parsear(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie a planilha de preço da Telefônica (.xlsx).");
        }

        try (InputStream is = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            DataFormatter formatter = new DataFormatter();

            Map<String, Set<String>> indiceSku = parsearIndiceProdutos(wb, formatter);
            String vigencia = null;
            List<LinhaPrecoDTO> linhas = new ArrayList<>();

            for (Map.Entry<String, String> abaAlvo : ABAS_DE_PRECO.entrySet()) {
                Sheet sheet = wb.getSheet(abaAlvo.getKey());
                if (sheet == null) {
                    continue; // aba opcional ausente nessa entrega específica — segue sem quebrar
                }
                if (vigencia == null) {
                    vigencia = extrairVigencia(sheet, formatter);
                }
                linhas.addAll(parsearAbaDePreco(sheet, abaAlvo.getValue(), formatter));
            }

            if (linhas.isEmpty()) {
                throw new NegocioException("Nenhuma linha de preço foi encontrada nas abas esperadas (" +
                        String.join(", ", ABAS_DE_PRECO.keySet()) + ").");
            }

            return new ResultadoParseTabelaPreco(indiceSku, linhas, vigencia);
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler a planilha enviada: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ aba PRODUTOS (dicionário SKU)

    private Map<String, Set<String>> parsearIndiceProdutos(Workbook wb, DataFormatter formatter) {
        Sheet sheet = wb.getSheet("PRODUTOS");
        if (sheet == null) {
            throw new NegocioException("A aba 'PRODUTOS' (Cod_Sap x Nome Comercial) não foi encontrada na planilha — ela é obrigatória para resolver o SKU.");
        }

        int linhaCabecalho = localizarLinhaCabecalho(sheet, formatter, "cod_sap");
        Row headerRow = sheet.getRow(linhaCabecalho);

        Integer idxCodSap = null, idxNomeComercial = null;
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String nome = normalizar(formatter.formatCellValue(headerRow.getCell(c)));
            if (nome.equals("cod_sap") || nome.equals("cod sap") || nome.equals("codsap")) idxCodSap = c;
            if (nome.equals("nome comercial")) idxNomeComercial = c;
        }
        if (idxCodSap == null || idxNomeComercial == null) {
            throw new NegocioException("Não encontrei as colunas Cod_Sap e/ou Nome Comercial na aba PRODUTOS.");
        }

        Map<String, Set<String>> indice = new HashMap<>();
        for (int r = linhaCabecalho + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String codSap = valorTexto(row, idxCodSap, formatter);
            String nomeComercial = valorTexto(row, idxNomeComercial, formatter);
            if (codSap.isBlank() || nomeComercial.isBlank()) continue;
            indice.computeIfAbsent(normalizar(nomeComercial), k -> new HashSet<>()).add(codSap);
        }
        return indice;
    }

    // ------------------------------------------------------------ abas de preço

    private List<LinhaPrecoDTO> parsearAbaDePreco(Sheet sheet, String categoriaPlanilha, DataFormatter formatter) {
        int linhaCabecalho = localizarLinhaCabecalho(sheet, formatter, "nome comercial");
        int limiteColunaExclusivo = localizarLimiteTabelaRegular(sheet, linhaCabecalho, formatter);

        Row headerRow = sheet.getRow(linhaCabecalho);
        Integer idxNomeComercial = null, idxOferta = null, idxPreco = null;
        int ultimaColuna = Math.min(headerRow.getLastCellNum(), limiteColunaExclusivo);

        for (int c = 0; c < ultimaColuna; c++) {
            String nomeCol = normalizar(formatter.formatCellValue(headerRow.getCell(c)));
            if (nomeCol.equals("nome comercial") && idxNomeComercial == null) idxNomeComercial = c;
            if (nomeCol.equals("oferta de comunicacao") && idxOferta == null) idxOferta = c;
            if (ALIASES_PRECO.contains(nomeCol) && idxPreco == null) idxPreco = c;
        }

        if (idxNomeComercial == null || idxPreco == null) {
            throw new NegocioException("Não encontrei as colunas de Nome Comercial e/ou preço (PRÉ/PVP/PVD) na aba '"
                    + sheet.getSheetName() + "' dentro do bloco TABELA REGULAR.");
        }

        List<LinhaPrecoDTO> linhas = new ArrayList<>();
        for (int r = linhaCabecalho + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String nomeComercial = valorTexto(row, idxNomeComercial, formatter);
            if (nomeComercial.isBlank()) continue;

            BigDecimal preco = valorNumerico(row, idxPreco, formatter);
            if (preco == null) continue;

            String oferta = idxOferta == null ? null : valorTexto(row, idxOferta, formatter);
            if (oferta != null && (oferta.isBlank() || oferta.equals("-"))) oferta = null;

            linhas.add(new LinhaPrecoDTO(nomeComercial, categoriaPlanilha, oferta, preco));
        }
        return linhas;
    }

    /** Localiza a linha (0-based) cujo texto de alguma célula, normalizado, é igual a `marcador`. */
    private int localizarLinhaCabecalho(Sheet sheet, DataFormatter formatter, String marcador) {
        int limiteVarredura = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 10);
        for (int r = sheet.getFirstRowNum(); r <= limiteVarredura; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                if (normalizar(formatter.formatCellValue(cell)).equals(marcador)) {
                    return r;
                }
            }
        }
        throw new NegocioException("Não encontrei a linha de cabeçalho ('" + marcador + "') na aba " + sheet.getSheetName() + ".");
    }

    /** Colunas do bloco TABELA REGULAR terminam onde começa RENOVA/ANTERIOR/tabela por bandeira de cartão. */
    private int localizarLimiteTabelaRegular(Sheet sheet, int linhaCabecalho, DataFormatter formatter) {
        int limite = Integer.MAX_VALUE;
        for (int r = sheet.getFirstRowNum(); r < linhaCabecalho; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String texto = normalizar(formatter.formatCellValue(cell));
                if (texto.contains("renova") || texto.contains("anterior")
                        || texto.contains("cartao") || texto.contains("cartoes")) {
                    limite = Math.min(limite, cell.getColumnIndex());
                }
            }
        }
        return limite;
    }

    private String extrairVigencia(Sheet sheet, DataFormatter formatter) {
        int limiteVarredura = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 3);
        for (int r = sheet.getFirstRowNum(); r <= limiteVarredura; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String texto = formatter.formatCellValue(cell);
                if (texto != null && normalizar(texto).contains("vigencia")) {
                    Matcher m = PADRAO_VIGENCIA.matcher(texto);
                    if (m.find()) return m.group(1);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ utilitários

    private String valorTexto(Row row, int idx, DataFormatter formatter) {
        Cell cell = row.getCell(idx);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private BigDecimal valorNumerico(Row row, int idx, DataFormatter formatter) {
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String texto = formatter.formatCellValue(cell).trim();
        if (texto.isBlank()) return null;
        texto = texto.replace("R$", "").trim().replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}