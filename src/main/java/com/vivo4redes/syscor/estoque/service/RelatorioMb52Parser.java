package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.exception.NegocioException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lê a MB52 exportada do SAP (.xlsx/.xls, ou .csv/.txt delimitado) — estoque por material x centro x depósito.
 * <p>
 * Colunas pelo cabeçalho normalizado (mesma regra da IQ09, RN.66), não pela posição; o cabeçalho pode estar depois de
 * linhas de título. Obrigatórias: Material, Centro e Utilização livre. Tolera o jeito SAP de exportar: linhas em branco,
 * de total/subtotal (sem material ou com "Total"/"*") e cabeçalho repetido (ignoradas e contadas), sinal no fim
 * ({@code 5-}), número texto com vírgula decimal ({@code 1.234,000}) e material numérico com zeros à esquerda.
 * Quantidade fracionada numa unidade de contagem (UN, PC...) recusa o arquivo.
 */
@Component
public class RelatorioMb52Parser {

    private static final String MATERIAL = "material";
    private static final String CENTRO = "centro";
    private static final String LIVRE = "livre";

    private static final Map<String, String> ALIAS_COLUNAS = Map.ofEntries(
            Map.entry("material", MATERIAL),
            Map.entry("codigo material", MATERIAL),
            Map.entry("codigo do material", MATERIAL),
            Map.entry("cod material", MATERIAL),
            Map.entry("texto breve material", "denominacao"),
            Map.entry("texto breve de material", "denominacao"),
            Map.entry("texto breve", "denominacao"),
            Map.entry("denominacao", "denominacao"),
            Map.entry("descricao", "denominacao"),
            Map.entry("descricao material", "denominacao"),
            Map.entry("centro", CENTRO),
            Map.entry("deposito", "deposito"),
            Map.entry("unidade medida basica", "unidade"),
            Map.entry("unidade de medida basica", "unidade"),
            Map.entry("unidade de medida", "unidade"),
            Map.entry("unidade medida", "unidade"),
            Map.entry("umb", "unidade"),
            Map.entry("um basica", "unidade"),
            Map.entry("unidade", "unidade"),
            Map.entry("utilizacao livre", LIVRE),
            Map.entry("livre utilizacao", LIVRE),
            Map.entry("util livre", LIVRE),
            Map.entry("estoque livre", LIVRE),
            Map.entry("estoque utilizacao livre", LIVRE),
            Map.entry("em controle de qualidade", "qualidade"),
            Map.entry("em controle qualidade", "qualidade"),
            Map.entry("controle de qualidade", "qualidade"),
            Map.entry("controle qualidade", "qualidade"),
            Map.entry("bloqueado", "bloqueado"),
            Map.entry("estoque bloqueado", "bloqueado"),
            Map.entry("em transito", "transito"),
            Map.entry("transito", "transito"),
            Map.entry("em transito transferencia", "transito"),
            Map.entry("em transito e transferencia", "transito"),
            Map.entry("transito e transferencia", "transito"),
            Map.entry("transito transferencia", "transito"),
            Map.entry("em transferencia", "transito"),
            Map.entry("valor utilizacao livre", "valorLivre"),
            Map.entry("valor livre utilizacao", "valorLivre"),
            Map.entry("val utilizacao livre", "valorLivre"),
            Map.entry("valor util livre", "valorLivre"),
            Map.entry("valor estoque livre", "valorLivre")
    );

    private static final String COLUNAS_ESPERADAS = "Material, Texto breve material, Centro, Depósito, Unidade de medida básica, "
            + "Utilização livre, Em controle de qualidade, Bloqueado, Em trânsito/transferência, Valor utilização livre "
            + "(obrigatórias: Material, Centro, Utilização livre)";

    /** Unidades contadas por peça: quantidade fracionada nelas é erro de exportação. */
    private static final Set<String> UNIDADES_DE_CONTAGEM = Set.of("UN", "UNI", "UNID", "UND", "PC", "PÇ", "PEC", "PECA", "PÇA", "PCE", "ST");

    /** Quantas linhas olhar procurando o cabeçalho (a MB52 pode vir com título antes). */
    private static final int LINHAS_PROCURA_CABECALHO = 30;

    private static final Pattern MILHAR_COM_PONTO = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");
    private static final Pattern NUMERO = Pattern.compile("^\\d+(\\.\\d+)?$");

    /** Célula lida: texto limpo e, se a célula do Excel era numérica, o número exato. */
    private record Valor(String texto, BigDecimal numero) {
        static final Valor VAZIO = new Valor("", null);
    }

    public LinhaMb52DTO.Leitura parsear(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie a MB52 exportada do SAP em .xlsx.");
        }
        String nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        List<List<Valor>> grade;
        try {
            grade = nome.endsWith(".csv") || nome.endsWith(".txt") ? lerTexto(arquivo) : lerExcel(arquivo);
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler a MB52 enviada: " + e.getMessage());
        } catch (NegocioException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new NegocioException("Não foi possível ler a MB52 enviada: envie a planilha .xlsx exportada do SAP.");
        }
        return interpretar(grade);
    }

    // ---------------------------------------------------------------- leitura bruta

    private List<List<Valor>> lerExcel(MultipartFile arquivo) throws IOException {
        try (InputStream is = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<List<Valor>> grade = new ArrayList<>();
            for (int r = Math.max(sheet.getFirstRowNum(), 0); r <= sheet.getLastRowNum(); r++) {
                Row linha = sheet.getRow(r);
                List<Valor> valores = new ArrayList<>();
                if (linha != null) {
                    for (int c = 0; c < Math.max(linha.getLastCellNum(), 0); c++) {
                        valores.add(valorDaCelula(linha.getCell(c), formatter));
                    }
                }
                grade.add(valores);
            }
            return grade;
        }
    }

    private static Valor valorDaCelula(Cell celula, DataFormatter formatter) {
        if (celula == null) return Valor.VAZIO;
        CellType tipo = celula.getCellType() == CellType.FORMULA ? celula.getCachedFormulaResultType() : celula.getCellType();
        if (tipo == CellType.NUMERIC && !DateUtil.isCellDateFormatted(celula)) {
            BigDecimal numero = BigDecimal.valueOf(celula.getNumericCellValue());
            String texto = numero.signum() == 0 || numero.stripTrailingZeros().scale() <= 0
                    ? numero.toBigInteger().toString() : numero.stripTrailingZeros().toPlainString();
            return new Valor(texto, numero);
        }
        if (tipo == CellType.STRING) {
            return new Valor(limpar(celula.getStringCellValue()), null);
        }
        return new Valor(limpar(formatter.formatCellValue(celula)), null);
    }

    private List<List<Valor>> lerTexto(MultipartFile arquivo) throws IOException {
        String conteudo = RelatorioSapParser.decodificar(arquivo.getBytes());
        String[] linhas = conteudo.split("\\r?\\n", -1);
        String primeira = Arrays.stream(linhas).filter(l -> !l.isBlank()).findFirst().orElse("");
        String delimitador = Pattern.quote(String.valueOf(RelatorioSapParser.detectarDelimitador(primeira)));
        List<List<Valor>> grade = new ArrayList<>();
        for (String linha : linhas) {
            List<Valor> valores = new ArrayList<>();
            if (!linha.isBlank()) {
                for (String campo : linha.split(delimitador, -1)) {
                    valores.add(new Valor(limpar(campo), null));
                }
            }
            grade.add(valores);
        }
        return grade;
    }

    // ---------------------------------------------------------------- interpretação

    private LinhaMb52DTO.Leitura interpretar(List<List<Valor>> grade) {
        int idxCabecalho = -1;
        Map<String, Integer> indices = Map.of();
        List<String> primeiroCabecalho = null;
        for (int r = 0; r < Math.min(grade.size(), LINHAS_PROCURA_CABECALHO); r++) {
            List<Valor> linha = grade.get(r);
            if (linhaVazia(linha)) continue;
            Map<String, Integer> candidatos = mapearIndices(linha);
            if (primeiroCabecalho == null) {
                primeiroCabecalho = linha.stream().map(Valor::texto).filter(t -> !t.isBlank()).toList();
            }
            if (candidatos.containsKey(MATERIAL) && candidatos.containsKey(CENTRO) && candidatos.containsKey(LIVRE)) {
                idxCabecalho = r;
                indices = candidatos;
                break;
            }
            if (candidatos.containsKey(MATERIAL) || candidatos.containsKey(CENTRO)) {
                primeiroCabecalho = linha.stream().map(Valor::texto).filter(t -> !t.isBlank()).toList();
            }
        }
        if (primeiroCabecalho == null) {
            throw new NegocioException("A MB52 enviada está vazia.");
        }
        if (idxCabecalho < 0) {
            throw new NegocioException("A planilha não tem as colunas da MB52. Colunas esperadas: " + COLUNAS_ESPERADAS
                    + ". Colunas encontradas: " + String.join(", ", primeiroCabecalho) + ".");
        }

        List<LinhaMb52DTO> linhas = new ArrayList<>();
        int ignoradas = 0;
        for (int r = idxCabecalho + 1; r < grade.size(); r++) {
            List<Valor> linha = grade.get(r);
            if (linhaVazia(linha)) continue;
            int numeroLinha = r + 1;
            String material = texto(linha, indices, MATERIAL);
            String centro = texto(linha, indices, CENTRO).toUpperCase(Locale.ROOT);
            if (linhaDeTotalOuCabecalho(material, centro)) {
                ignoradas++;
                continue;
            }
            String unidade = texto(linha, indices, "unidade").toUpperCase(Locale.ROOT);
            BigDecimal livre = quantidade(linha, indices, LIVRE, "Utilização livre", unidade, numeroLinha);
            BigDecimal qualidade = quantidade(linha, indices, "qualidade", "Em controle de qualidade", unidade, numeroLinha);
            BigDecimal bloqueado = quantidade(linha, indices, "bloqueado", "Bloqueado", unidade, numeroLinha);
            BigDecimal transito = quantidade(linha, indices, "transito", "Em trânsito", unidade, numeroLinha);
            BigDecimal valorLivre = indices.containsKey("valorLivre")
                    ? numero(valor(linha, indices, "valorLivre"), "Valor utilização livre", numeroLinha) : null;
            linhas.add(new LinhaMb52DTO(numeroLinha, material, texto(linha, indices, "denominacao"), centro,
                    texto(linha, indices, "deposito").toUpperCase(Locale.ROOT), unidade,
                    livre, qualidade, bloqueado, transito, valorLivre));
        }
        return new LinhaMb52DTO.Leitura(linhas, ignoradas);
    }

    /** Sem material, sem centro, "Total"/"*" do ALV, ou o cabeçalho repetido. */
    private static boolean linhaDeTotalOuCabecalho(String material, String centro) {
        if (material.isBlank() || centro.isBlank()) return true;
        String m = material.toUpperCase(Locale.ROOT);
        if (m.contains("TOTAL") || m.contains("*")) return true;
        return MATERIAL.equals(ALIAS_COLUNAS.get(normalizar(material)));
    }

    private static BigDecimal quantidade(List<Valor> linha, Map<String, Integer> indices, String campo, String rotulo,
                                         String unidade, int numeroLinha) {
        if (!indices.containsKey(campo)) return BigDecimal.ZERO;
        BigDecimal qtd = numero(valor(linha, indices, campo), rotulo, numeroLinha);
        if (qtd == null) return BigDecimal.ZERO;
        if (UNIDADES_DE_CONTAGEM.contains(unidade) && qtd.stripTrailingZeros().scale() > 0) {
            throw new NegocioException("Linha " + numeroLinha + ": quantidade fracionada (" + qtd.stripTrailingZeros().toPlainString()
                    + ") em \"" + rotulo + "\" para a unidade " + unidade + ". Confira a exportação da MB52.");
        }
        return qtd;
    }

    /**
     * Número da célula: o do Excel quando numérica; texto no formato SAP ({@code 1.234,000}, {@code 5-}, {@code -5}).
     * Vazio = nulo.
     */
    private static BigDecimal numero(Valor valor, String rotulo, int numeroLinha) {
        if (valor.numero() != null) return valor.numero();
        return numeroDeTexto(valor.texto(), rotulo, numeroLinha);
    }

    static BigDecimal numeroDeTexto(String texto, String rotulo, int numeroLinha) {
        String s = texto == null ? "" : texto.replace(" ", "").replace(" ", "");
        if (s.isEmpty()) return null;
        boolean negativo = false;
        if (s.endsWith("-")) {
            negativo = true;
            s = s.substring(0, s.length() - 1);
        } else if (s.startsWith("-")) {
            negativo = true;
            s = s.substring(1);
        }
        int virgula = s.lastIndexOf(',');
        int ponto = s.lastIndexOf('.');
        if (virgula >= 0 && ponto >= 0) {
            s = virgula > ponto ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (virgula >= 0) {
            s = s.replace(',', '.');
        } else if (ponto >= 0 && MILHAR_COM_PONTO.matcher(s).matches()) {
            s = s.replace(".", "");
        }
        if (!NUMERO.matcher(s).matches()) {
            throw new NegocioException("Linha " + numeroLinha + ": valor \"" + texto + "\" inválido em \"" + rotulo + "\".");
        }
        BigDecimal n = new BigDecimal(s);
        return negativo ? n.negate() : n;
    }

    private static Map<String, Integer> mapearIndices(List<Valor> linha) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < linha.size(); i++) {
            String campo = ALIAS_COLUNAS.get(normalizar(linha.get(i).texto()));
            if (campo != null) indices.putIfAbsent(campo, i);
        }
        return indices;
    }

    /** Regra da IQ09 + barra, hífen e dois-pontos viram espaço ("Em trânsito/transferência"). */
    static String normalizar(String cabecalho) {
        return RelatorioSapParser.normalizarCabecalho(cabecalho).replaceAll("[/\\-_:]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Valor valor(List<Valor> linha, Map<String, Integer> indices, String campo) {
        Integer idx = indices.get(campo);
        return idx == null || idx >= linha.size() ? Valor.VAZIO : linha.get(idx);
    }

    private static String texto(List<Valor> linha, Map<String, Integer> indices, String campo) {
        return valor(linha, indices, campo).texto();
    }

    private static boolean linhaVazia(List<Valor> linha) {
        return linha.stream().allMatch(v -> v.texto().isBlank());
    }

    private static String limpar(String texto) {
        return texto == null ? "" : texto.replace("﻿", "").trim().replaceAll("\\s+", " ");
    }
}
