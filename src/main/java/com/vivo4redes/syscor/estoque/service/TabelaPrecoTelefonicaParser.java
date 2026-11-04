package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaPlano;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaPreco;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaProduto;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.util.TextoUtil;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê a planilha de preços da Telefônica (.xlsx, várias abas).
 * <p>
 * Tudo é localizado pelo texto do cabeçalho (normalizado: sem acento, minúsculas, espaços colapsados),
 * nunca por posição fixa — as colunas mudam entre entregas.
 * <ul>
 *   <li>ÍNDICE: vigência ("Vigência a partir de").</li>
 *   <li>PLANOS e PRODUTOS (obrigatórias): plano -> grupo de preço; Cod_Sap -> Nome Comercial.</li>
 *   <li>SMARTPHONES e ELETRÔNICOS_LP_Conectados: blocos TABELA REGULAR e TABELA RENOVA (linha acima do
 *       cabeçalho); em cada bloco, por grupo do plano: preço cheio, PIX e Vivo Pay, 13x–21x. No bloco Renova a
 *       coluna DESCONTO (valor único por produto, já aplicado nos preços Renova) vira o desconto do fabricante.
 *       Outros blocos (TABELA ANTERIOR...) e a coluna "Oferta de Comunicação" são ignorados.</li>
 *   <li>ELETRÔNICOS_LP_Não Conectados, VITRINE e SMARTPHONES_Demo: sem plano (SEM_PLANO / REGULAR).</li>
 * </ul>
 */
@Component
public class TabelaPrecoTelefonicaParser {

    private static final Logger log = LoggerFactory.getLogger(TabelaPrecoTelefonicaParser.class);

    private static final String ABA_INDICE = "indice";
    private static final String ABA_PLANOS = "planos";
    private static final String ABA_PRODUTOS = "produtos";
    private static final List<String> ABAS_COM_PLANO = List.of("smartphones", "eletronicos_lp_conectados");
    private static final List<String> ABAS_SEM_PLANO = List.of("eletronicos_lp_nao conectados", "vitrine", "smartphones_demo");

    private static final String COL_NOME_COMERCIAL = "nome comercial";
    private static final String COL_PIX = "pix e vivo pay";
    private static final List<String> COLS_PRECO_SEM_PLANO = List.of("pvp base", "pvp", "pvd");
    private static final Pattern COL_PARCELA = Pattern.compile("^(\\d{1,2})\\s*x$");
    private static final int PRIMEIRA_PARCELA_A_PARTE = 13;
    private static final int ULTIMA_PARCELA = 21;

    private static final Pattern DATA = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LINHAS_VARRIDAS_NO_CABECALHO = 10;

    private final DataFormatter formatter = new DataFormatter();

    public ResultadoParseTabelaPreco parsear(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie a planilha de preço da Telefônica (.xlsx).");
        }
        try (InputStream is = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            return parsear(wb);
        } catch (IOException | RuntimeException e) {
            if (e instanceof NegocioException ne) throw ne;
            throw new NegocioException("Não foi possível ler a planilha enviada: " + e.getMessage());
        }
    }

    ResultadoParseTabelaPreco parsear(Workbook wb) {
        Sheet planos = aba(wb, ABA_PLANOS)
                .orElseThrow(() -> new NegocioException("A planilha não tem a aba PLANOS."));
        Sheet produtos = aba(wb, ABA_PRODUTOS)
                .orElseThrow(() -> new NegocioException("A planilha não tem a aba PRODUTOS."));

        LocalDate vigencia = aba(wb, ABA_INDICE).map(this::lerVigencia).orElse(null);
        List<LinhaPlano> linhasPlano = lerPlanos(planos);
        List<LinhaProduto> linhasProduto = lerProdutos(produtos);

        List<LinhaPreco> precos = new ArrayList<>();
        Map<String, Integer> linhasPorAba = new LinkedHashMap<>();
        for (String nome : ABAS_COM_PLANO) {
            aba(wb, nome).ifPresent(s -> linhasPorAba.put(s.getSheetName(), lerAbaComPlano(s, precos)));
        }
        for (String nome : ABAS_SEM_PLANO) {
            aba(wb, nome).ifPresent(s -> linhasPorAba.put(s.getSheetName(), lerAbaSemPlano(s, precos)));
        }
        if (precos.isEmpty()) {
            throw new NegocioException("Nenhum preço foi encontrado nas abas SMARTPHONES, ELETRÔNICOS, VITRINE ou Demo.");
        }
        return new ResultadoParseTabelaPreco(vigencia, precos, linhasPlano, linhasProduto, linhasPorAba);
    }

    // ------------------------------------------------------------------ ÍNDICE

    private LocalDate lerVigencia(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String texto = texto(cell);
                if (!TextoUtil.normalizar(texto).contains("vigencia a partir de")) continue;
                LocalDate naPropriaCelula = dataNoTexto(texto);
                if (naPropriaCelula != null) return naPropriaCelula;
                // Valor na célula à direita (a primeira preenchida).
                for (int c = cell.getColumnIndex() + 1; c <= row.getLastCellNum(); c++) {
                    LocalDate data = data(row.getCell(c));
                    if (data != null) return data;
                }
            }
        }
        return null;
    }

    private LocalDate data(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        return dataNoTexto(texto(cell));
    }

    private LocalDate dataNoTexto(String texto) {
        Matcher m = DATA.matcher(texto == null ? "" : texto);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group(1), DD_MM_YYYY);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ PLANOS

    private List<LinhaPlano> lerPlanos(Sheet sheet) {
        Cabecalho cab = cabecalho(sheet, "grupamento atual")
                .orElseThrow(() -> new NegocioException("Aba PLANOS sem a coluna 'GRUPAMENTO ATUAL'."));
        int colNome = cab.exigir("nome do plano cliente sistema"::equals, "PLANOS", "NOME DO PLANO CLIENTE SISTEMA");
        int colGrupo = cab.coluna("grupamento atual"::equals);
        int colTipo = cab.coluna("tipo de plano"::equals);
        int colSistema = cab.coluna("sistema"::equals);
        int colCodSistema = cab.coluna("cod sistema"::equals);
        int colCodSap = cab.coluna(h -> h.startsWith("cod sap"));

        List<LinhaPlano> planos = new ArrayList<>();
        for (int r = cab.linha() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String nome = texto(row, colNome);
            if (nome.isBlank()) continue;
            Optional<GrupoPlano> grupo = GrupoPlano.deRotulo(texto(row, colGrupo));
            if (grupo.isEmpty()) {
                log.warn("Plano '{}' com GRUPAMENTO ATUAL desconhecido ('{}') — ignorado.", nome, texto(row, colGrupo));
                continue;
            }
            String tipo = TextoUtil.normalizar(texto(row, colTipo));
            boolean disponivelAlta = tipo.contains("alta") || tipo.contains("migracao");
            planos.add(new LinhaPlano(nome, vazioComoNull(texto(row, colSistema)), vazioComoNull(texto(row, colCodSistema)),
                    vazioComoNull(texto(row, colCodSap)), grupo.get(), disponivelAlta));
        }
        return planos;
    }

    // ------------------------------------------------------------------ PRODUTOS

    private List<LinhaProduto> lerProdutos(Sheet sheet) {
        Cabecalho cab = cabecalho(sheet, "cod_sap")
                .orElseThrow(() -> new NegocioException("Aba PRODUTOS sem a coluna 'Cod_Sap'."));
        int colCodSap = cab.coluna("cod_sap"::equals);
        int colNome = cab.exigir(COL_NOME_COMERCIAL::equals, "PRODUTOS", "Nome Comercial");
        int colDescricao = cab.coluna("descricao_sap"::equals);
        int colModelo = cab.coluna(h -> h.startsWith("modelo"));
        int colCor = cab.coluna("cor"::equals);
        int colCategoria = cab.coluna("categoria"::equals);

        List<LinhaProduto> produtos = new ArrayList<>();
        for (int r = cab.linha() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String codSap = texto(row, colCodSap);
            String nome = texto(row, colNome);
            if (codSap.isBlank() || nome.isBlank()) continue;
            produtos.add(new LinhaProduto(codSap, vazioComoNull(texto(row, colDescricao)), vazioComoNull(texto(row, colModelo)),
                    vazioComoNull(texto(row, colCor)), nome, vazioComoNull(texto(row, colCategoria))));
        }
        return produtos;
    }

    // ------------------------------------------------------------------ abas com plano

    /** Bloco de preços (TABELA REGULAR/RENOVA) e, dentro dele, as colunas de cada grupo do plano. */
    private record Bloco(TabelaPreco tabela, Map<GrupoPlano, ColunasGrupo> grupos, Integer colDesconto) {
    }

    private record ColunasGrupo(int preco, Integer pix, Map<Integer, Integer> parcelas) {
    }

    private int lerAbaComPlano(Sheet sheet, List<LinhaPreco> destino) {
        Optional<Cabecalho> cabOpt = cabecalho(sheet, COL_NOME_COMERCIAL);
        if (cabOpt.isEmpty() || cabOpt.get().linha() == 0) {
            log.warn("Aba '{}' sem cabeçalho 'Nome Comercial' com a linha de blocos acima — ignorada.", sheet.getSheetName());
            return 0;
        }
        Cabecalho cab = cabOpt.get();
        int colNome = cab.coluna(COL_NOME_COMERCIAL::equals);
        List<Bloco> blocos = localizarBlocos(sheet.getRow(cab.linha() - 1), cab);

        int itens = 0;
        for (int r = cab.linha() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String nome = texto(row, colNome);
            if (nome.isBlank()) continue;
            boolean algumPreco = false;
            for (Bloco bloco : blocos) {
                BigDecimal desconto = descontoFabricante(row, bloco);
                for (Map.Entry<GrupoPlano, ColunasGrupo> g : bloco.grupos().entrySet()) {
                    LinhaPreco linha = linhaPreco(row, sheet.getSheetName(), bloco.tabela(), g.getKey(), nome, g.getValue(), desconto);
                    if (linha != null) {
                        destino.add(linha);
                        algumPreco = true;
                    }
                }
            }
            if (algumPreco) itens++;
        }
        return itens;
    }

    /**
     * Na linha de blocos, cada "TABELA X" começa um bloco que vai até o próximo "TABELA ...".
     * Só REGULAR e RENOVA são lidos; TABELA ANTERIOR e os demais encerram o bloco anterior e são ignorados.
     */
    private List<Bloco> localizarBlocos(Row linhaBlocos, Cabecalho cab) {
        if (linhaBlocos == null) return List.of();
        List<int[]> marcadores = new ArrayList<>(); // {coluna, tabela.ordinal() ou -1}
        for (Cell cell : linhaBlocos) {
            String t = TextoUtil.normalizar(texto(cell));
            if (!t.startsWith("tabela")) continue;
            int tabela = t.startsWith("tabela regular") ? TabelaPreco.REGULAR.ordinal()
                    : t.startsWith("tabela renova") ? TabelaPreco.RENOVA.ordinal() : -1;
            marcadores.add(new int[]{cell.getColumnIndex(), tabela});
        }
        marcadores.sort(Comparator.comparingInt(m -> m[0]));

        List<Bloco> blocos = new ArrayList<>();
        for (int i = 0; i < marcadores.size(); i++) {
            int tabela = marcadores.get(i)[1];
            if (tabela < 0) continue;
            int inicio = marcadores.get(i)[0];
            int fim = i + 1 < marcadores.size() ? marcadores.get(i + 1)[0] : cab.ultimaColuna() + 1;
            TabelaPreco t = TabelaPreco.values()[tabela];
            Integer colDesconto = t == TabelaPreco.RENOVA ? colunaDesconto(cab, inicio, fim) : null;
            blocos.add(new Bloco(t, gruposNoIntervalo(cab, inicio, fim), colDesconto));
        }
        return blocos;
    }

    /** Coluna DESCONTO dentro do bloco Renova (coluna única, antes dos grupos); null se não houver. */
    private Integer colunaDesconto(Cabecalho cab, int inicio, int fim) {
        for (int c = inicio; c < fim; c++) {
            if (cab.titulo(c).startsWith("desconto")) return c;
        }
        return null;
    }

    /** Desconto do fabricante da linha no bloco Renova: vazio ou não numérico = 0 (preço Renova = Regular). */
    private BigDecimal descontoFabricante(Row row, Bloco bloco) {
        if (bloco.tabela() != TabelaPreco.RENOVA) return null;
        if (bloco.colDesconto() == null) return BigDecimal.ZERO.setScale(2);
        BigDecimal v = numero(row.getCell(bloco.colDesconto()));
        return v == null ? BigDecimal.ZERO.setScale(2) : v;
    }

    /** Dentro de [inicio, fim): coluna do grupo = preço cheio; seguintes até o próximo grupo = PIX e parcelas. */
    private Map<GrupoPlano, ColunasGrupo> gruposNoIntervalo(Cabecalho cab, int inicio, int fim) {
        Map<GrupoPlano, ColunasGrupo> grupos = new LinkedHashMap<>();
        GrupoPlano atual = null;
        int colPreco = -1;
        Integer colPix = null;
        Map<Integer, Integer> parcelas = new TreeMap<>();
        for (int c = inicio; c < fim; c++) {
            String h = cab.titulo(c);
            Optional<GrupoPlano> grupo = GrupoPlano.deRotulo(h).filter(g -> g != GrupoPlano.SEM_PLANO);
            if (grupo.isPresent()) {
                if (atual != null) grupos.putIfAbsent(atual, new ColunasGrupo(colPreco, colPix, parcelas));
                atual = grupo.get();
                colPreco = c;
                colPix = null;
                parcelas = new TreeMap<>();
            } else if (atual != null) {
                if (h.equals(COL_PIX)) {
                    colPix = c;
                } else {
                    Integer n = numeroDaParcela(h);
                    if (n != null && n >= PRIMEIRA_PARCELA_A_PARTE && n <= ULTIMA_PARCELA) parcelas.put(n, c);
                }
            }
        }
        if (atual != null) grupos.putIfAbsent(atual, new ColunasGrupo(colPreco, colPix, parcelas));
        return grupos;
    }

    // ------------------------------------------------------------------ abas sem plano

    private int lerAbaSemPlano(Sheet sheet, List<LinhaPreco> destino) {
        Optional<Cabecalho> cabOpt = cabecalho(sheet, COL_NOME_COMERCIAL);
        if (cabOpt.isEmpty()) {
            log.warn("Aba '{}' sem cabeçalho 'Nome Comercial' — ignorada.", sheet.getSheetName());
            return 0;
        }
        Cabecalho cab = cabOpt.get();
        int colNome = cab.coluna(COL_NOME_COMERCIAL::equals);
        int colPreco = -1;
        for (String alias : COLS_PRECO_SEM_PLANO) {
            colPreco = cab.coluna(alias::equals);
            if (colPreco >= 0) break;
        }
        if (colPreco < 0) {
            log.warn("Aba '{}' sem coluna de preço (PVP Base/PVP/PVD) — ignorada.", sheet.getSheetName());
            return 0;
        }
        Integer colPix = cab.coluna(COL_PIX::equals) >= 0 ? cab.coluna(COL_PIX::equals) : null;
        Map<Integer, Integer> parcelas = new TreeMap<>();
        for (int c = colPreco + 1; c <= cab.ultimaColuna(); c++) {
            Integer n = numeroDaParcela(cab.titulo(c));
            if (n != null && n >= PRIMEIRA_PARCELA_A_PARTE && n <= ULTIMA_PARCELA) parcelas.put(n, c);
        }
        ColunasGrupo colunas = new ColunasGrupo(colPreco, colPix, parcelas);

        int itens = 0;
        for (int r = cab.linha() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String nome = texto(row, colNome);
            if (nome.isBlank()) continue;
            LinhaPreco linha = linhaPreco(row, sheet.getSheetName(), TabelaPreco.REGULAR, GrupoPlano.SEM_PLANO, nome, colunas, null);
            if (linha != null) {
                destino.add(linha);
                itens++;
            }
        }
        return itens;
    }

    // ------------------------------------------------------------------ linha de preço

    /** null quando não há preço cheio numérico (linha pulada). */
    private LinhaPreco linhaPreco(Row row, String aba, TabelaPreco tabela, GrupoPlano grupo, String nome, ColunasGrupo cols,
                                  BigDecimal descontoFabricante) {
        BigDecimal preco = numero(row.getCell(cols.preco()));
        if (preco == null) return null;
        BigDecimal pix = cols.pix() == null ? null : numero(row.getCell(cols.pix()));
        Map<Integer, BigDecimal> parcelas = new TreeMap<>();
        cols.parcelas().forEach((n, c) -> {
            BigDecimal v = numero(row.getCell(c));
            if (v != null) parcelas.put(n, v);
        });
        return new LinhaPreco(aba, tabela, grupo, nome.trim(), preco, pix, parcelas.isEmpty() ? null : parcelas,
                descontoFabricante);
    }

    // ------------------------------------------------------------------ cabeçalho

    /** Linha de cabeçalho: coluna -> título normalizado. */
    private record Cabecalho(int linha, Map<Integer, String> titulos) {
        int coluna(Predicate<String> criterio) {
            return titulos.entrySet().stream().filter(e -> criterio.test(e.getValue()))
                    .mapToInt(Map.Entry::getKey).min().orElse(-1);
        }

        int exigir(Predicate<String> criterio, String aba, String nomeColuna) {
            int c = coluna(criterio);
            if (c < 0) throw new NegocioException("Aba " + aba + " sem a coluna '" + nomeColuna + "'.");
            return c;
        }

        String titulo(int coluna) {
            return titulos.getOrDefault(coluna, "");
        }

        int ultimaColuna() {
            return titulos.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        }
    }

    /** Primeira linha (entre as iniciais) que tem uma célula com o título-âncora informado. */
    private Optional<Cabecalho> cabecalho(Sheet sheet, String ancora) {
        int limite = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + LINHAS_VARRIDAS_NO_CABECALHO);
        for (int r = Math.max(sheet.getFirstRowNum(), 0); r <= limite; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<Integer, String> titulos = new LinkedHashMap<>();
            for (Cell cell : row) {
                String t = TextoUtil.normalizar(texto(cell));
                if (!t.isEmpty()) titulos.put(cell.getColumnIndex(), t);
            }
            if (titulos.containsValue(ancora)) return Optional.of(new Cabecalho(r, titulos));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ utilitários

    /** Aba pelo nome normalizado ("ÍNDICE" = "indice", "ELETRÔNICOS_LP_Não Conectados" = "eletronicos_lp_nao conectados"). */
    private Optional<Sheet> aba(Workbook wb, String nomeNormalizado) {
        for (Sheet s : wb) {
            if (TextoUtil.normalizar(s.getSheetName()).equals(nomeNormalizado)) return Optional.of(s);
        }
        return Optional.empty();
    }

    private Integer numeroDaParcela(String titulo) {
        Matcher m = COL_PARCELA.matcher(titulo);
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    private String texto(Row row, int coluna) {
        return coluna < 0 || row == null ? "" : texto(row.getCell(coluna));
    }

    private String texto(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String vazioComoNull(String texto) {
        return ConversorValores.texto(texto);
    }

    /**
     * Número da célula em BigDecimal(2, HALF_UP). Célula numérica (ou fórmula numérica) usa a representação decimal
     * exata do valor gravado; texto ("3059.1", "R$ 12.909,00") passa por ConversorValores.decimal. Não numérico -> null.
     */
    private BigDecimal numero(Cell cell) {
        if (cell == null) return null;
        CellType tipo = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (tipo == CellType.NUMERIC) {
            return new BigDecimal(Double.toString(cell.getNumericCellValue())).setScale(2, RoundingMode.HALF_UP);
        }
        if (tipo != CellType.STRING) return null;
        try {
            return ConversorValores.decimal(cell.getStringCellValue());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
