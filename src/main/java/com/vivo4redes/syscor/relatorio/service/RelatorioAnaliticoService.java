package com.vivo4redes.syscor.relatorio.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.relatorio.dto.CampoRelatorio;
import com.vivo4redes.syscor.relatorio.dto.CampoRelatorio.Formato;
import com.vivo4redes.syscor.relatorio.dto.FiltroRelatorioDTO;
import com.vivo4redes.syscor.relatorio.dto.PaginaRelatorioDTO;
import com.vivo4redes.syscor.relatorio.dto.PaginaRelatorioDTO.Coluna;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.Modo;
import com.vivo4redes.syscor.relatorio.enums.TipoRelatorio;
import com.vivo4redes.syscor.relatorio.repository.RelatorioAnaliticoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Relatórios "Venda de Produtos", "Venda de Serviços" e "Vendas" (docs/relatorios_produtos_servicos.md): busca
 * paginada para a tela, contagem e exportação .xlsx com as colunas escolhidas.
 */
@Service
@RequiredArgsConstructor
public class RelatorioAnaliticoService {

    public static final int MAXIMO_DIAS = 93;
    public static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int TAMANHO_MAXIMO_PAGINA = 200;

    private final RelatorioAnaliticoRepository repository;

    public record Arquivo(String nome, byte[] conteudo) {
    }

    @Transactional(readOnly = true)
    public long contar(TipoRelatorio tipo, FiltroRelatorioDTO filtro) {
        validarPeriodo(filtro);
        return repository.contar(tipo, filtro, sintetico(filtro));
    }

    @Transactional(readOnly = true)
    public PaginaRelatorioDTO buscar(TipoRelatorio tipo, FiltroRelatorioDTO filtro, int page, int size) {
        validarPeriodo(filtro);
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA);
        int pagina = Math.max(page, 0);
        long deslocamento = (long) pagina * tamanho;
        boolean resumo = sintetico(filtro);
        long total = repository.contar(tipo, filtro, resumo);
        int totalPaginas = (int) ((total + tamanho - 1) / tamanho);

        if (resumo) {
            List<Coluna> colunas = colunasSinteticas(tipo);
            List<Map<String, Object>> linhas = repository.sintetico(tipo, filtro, tamanho, deslocamento);
            return new PaginaRelatorioDTO(colunas, linhas, pagina, tamanho, total, totalPaginas, List.of());
        }

        List<CampoRelatorio> campos = CampoRelatorio.escolhidos(tipo, filtro.campos(), true);
        List<Map<String, Object>> linhas = new ArrayList<>();
        repository.analitico(tipo, filtro, campos, tamanho, deslocamento, valores -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i < campos.size(); i++) m.put(campos.get(i).chave(), valores[i]);
            linhas.add(m);
        });
        List<Coluna> colunas = campos.stream().map(c -> new Coluna(c.chave(), c.rotulo(), c.formato())).toList();
        List<Map<String, Object>> totais = tipo == TipoRelatorio.PRODUTOS && agrupa(filtro)
                ? repository.subtotaisPorGrupo(filtro) : List.of();
        return new PaginaRelatorioDTO(colunas, linhas, pagina, tamanho, total, totalPaginas, totais);
    }

    /** Período obrigatório, de no máximo {@value #MAXIMO_DIAS} dias contando as duas pontas (422 fora disso). */
    @Transactional(readOnly = true)
    public Arquivo exportar(TipoRelatorio tipo, FiltroRelatorioDTO filtro) {
        if (filtro.dataInicio() == null || filtro.dataFim() == null) {
            throw new NegocioException("Informe o período (data inicial e final) para exportar.");
        }
        validarPeriodo(filtro);
        if (ChronoUnit.DAYS.between(filtro.dataInicio(), filtro.dataFim()) + 1 > MAXIMO_DIAS) {
            throw new NegocioException("O período da exportação pode ter no máximo " + MAXIMO_DIAS + " dias.");
        }
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            Sheet aba = wb.createSheet(tipo.nomeArquivo());
            if (sintetico(filtro)) {
                escreverSintetico(tipo, filtro, aba, e);
            } else {
                escreverAnalitico(tipo, filtro, aba, e);
            }
            wb.write(saida);
            wb.dispose();
            return new Arquivo(tipo.nomeArquivo() + ".xlsx", saida.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gerar o Excel do relatório.", ex);
        }
    }

    // ------------------------------------------------------------------ Excel

    private void escreverAnalitico(TipoRelatorio tipo, FiltroRelatorioDTO filtro, Sheet aba, Estilos e) {
        List<CampoRelatorio> campos = CampoRelatorio.escolhidos(tipo, filtro.campos(), false);
        cabecalho(aba, e, campos.stream().map(CampoRelatorio::rotulo).toList());
        boolean subtotais = tipo == TipoRelatorio.PRODUTOS && agrupa(filtro);
        int[] linha = {1};
        int n = campos.size();
        String[] atual = {null};
        String[] rotuloAtual = {null};
        BigDecimal[] soma = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        repository.analitico(tipo, filtro, campos, null, 0, valores -> {
            if (subtotais) {
                String chave = valores[0] + "|" + valores[n];
                if (atual[0] != null && !atual[0].equals(chave)) {
                    subtotal(aba, e, linha[0]++, campos, rotuloAtual[0], soma);
                    java.util.Arrays.fill(soma, BigDecimal.ZERO);
                }
                atual[0] = chave;
                rotuloAtual[0] = String.valueOf(valores[n]);
                soma[0] = soma[0].add(numero(valores[n + 1]));
                soma[1] = soma[1].add(numero(valores[n + 2]));
                soma[2] = soma[2].add(numero(valores[n + 3]));
                soma[3] = soma[3].add(numero(valores[n + 4]));
            }
            Row r = aba.createRow(linha[0]++);
            for (int i = 0; i < n; i++) celula(r, i, campos.get(i).formato(), valores[i], e);
        });
        if (subtotais && atual[0] != null) subtotal(aba, e, linha[0], campos, rotuloAtual[0], soma);
    }

    private void subtotal(Sheet aba, Estilos e, int numeroLinha, List<CampoRelatorio> campos, String grupo,
                          BigDecimal[] soma) {
        Row r = aba.createRow(numeroLinha);
        Cell rotulo = r.createCell(0);
        rotulo.setCellValue("Subtotal: " + grupo);
        rotulo.setCellStyle(e.negrito);
        String[] chaves = {"quantidade", "valorVenda", "valorCusto", "valorLucro"};
        for (int k = 0; k < chaves.length; k++) {
            for (int i = 1; i < campos.size(); i++) {
                if (campos.get(i).chave().equals(chaves[k])) {
                    Cell c = r.createCell(i);
                    c.setCellValue(soma[k].doubleValue());
                    c.setCellStyle(k == 0 ? e.negrito : e.decimalNegrito);
                }
            }
        }
    }

    private void escreverSintetico(TipoRelatorio tipo, FiltroRelatorioDTO filtro, Sheet aba, Estilos e) {
        List<Coluna> colunas = colunasSinteticas(tipo);
        cabecalho(aba, e, colunas.stream().map(Coluna::rotulo).toList());
        int linha = 1;
        for (Map<String, Object> m : repository.sintetico(tipo, filtro, null, 0)) {
            Row r = aba.createRow(linha++);
            for (int i = 0; i < colunas.size(); i++) {
                celula(r, i, colunas.get(i).formato(), m.get(colunas.get(i).chave()), e);
            }
        }
    }

    private static void cabecalho(Sheet aba, Estilos e, List<String> rotulos) {
        Row r = aba.createRow(0);
        for (int i = 0; i < rotulos.size(); i++) {
            Cell c = r.createCell(i);
            c.setCellValue(rotulos.get(i));
            c.setCellStyle(e.negrito);
        }
        aba.createFreezePane(0, 1);
    }

    private static void celula(Row r, int coluna, Formato formato, Object valor, Estilos e) {
        if (valor == null) return;
        Cell c = r.createCell(coluna);
        switch (valor) {
            case Boolean b -> c.setCellValue(b ? "Sim" : "Não");
            case LocalDateTime dh -> {
                c.setCellValue(dh);
                c.setCellStyle(e.dataHora);
            }
            case LocalDate d -> {
                c.setCellValue(d);
                c.setCellStyle(e.data);
            }
            case LocalTime h -> {
                c.setCellValue(h.toSecondOfDay() / 86400.0);
                c.setCellStyle(e.hora);
            }
            case BigDecimal n -> {
                c.setCellValue(n.doubleValue());
                if (formato == Formato.DECIMAL) c.setCellStyle(e.decimal);
            }
            case Number n -> c.setCellValue(n.doubleValue());
            default -> c.setCellValue(valor.toString());
        }
    }

    private static final class Estilos {
        final CellStyle negrito;
        final CellStyle decimalNegrito;
        final CellStyle decimal;
        final CellStyle data;
        final CellStyle dataHora;
        final CellStyle hora;

        Estilos(SXSSFWorkbook wb) {
            DataFormat f = wb.createDataFormat();
            Font fonte = wb.createFont();
            fonte.setBold(true);
            negrito = wb.createCellStyle();
            negrito.setFont(fonte);
            decimal = wb.createCellStyle();
            decimal.setDataFormat(f.getFormat("#,##0.00"));
            decimalNegrito = wb.createCellStyle();
            decimalNegrito.setFont(fonte);
            decimalNegrito.setDataFormat(f.getFormat("#,##0.00"));
            data = wb.createCellStyle();
            data.setDataFormat(f.getFormat("dd/mm/yyyy"));
            dataHora = wb.createCellStyle();
            dataHora.setDataFormat(f.getFormat("dd/mm/yyyy hh:mm"));
            hora = wb.createCellStyle();
            hora.setDataFormat(f.getFormat("hh:mm"));
        }
    }

    // ------------------------------------------------------------------ apoio

    private static List<Coluna> colunasSinteticas(TipoRelatorio tipo) {
        List<Coluna> c = new ArrayList<>(List.of(
                new Coluna("filial", "Filial", Formato.TEXTO),
                new Coluna("vendedor", "Vendedor", Formato.TEXTO)));
        switch (tipo) {
            case PRODUTOS -> c.addAll(List.of(
                    new Coluna("grupo", "Grupo", Formato.TEXTO),
                    new Coluna("registros", "Registros", Formato.INTEIRO),
                    new Coluna("quantidade", "Quantidade", Formato.INTEIRO),
                    new Coluna("valor", "Valor de venda", Formato.DECIMAL),
                    new Coluna("custo", "Valor de Custo", Formato.DECIMAL),
                    new Coluna("lucro", "Valor Lucro", Formato.DECIMAL)));
            case SERVICOS -> c.addAll(List.of(
                    new Coluna("registros", "Serviços", Formato.INTEIRO),
                    new Coluna("valor", "Valor Receita", Formato.DECIMAL)));
            case VENDAS -> c.addAll(List.of(
                    new Coluna("registros", "Vendas", Formato.INTEIRO),
                    new Coluna("valor", "Valor Total", Formato.DECIMAL)));
        }
        return c;
    }

    private static boolean sintetico(FiltroRelatorioDTO f) {
        return f.modo() == Modo.SINTETICO;
    }

    /** Produtos sempre agrupa (padrão: Tipo de Produto); o parâmetro só escolhe entre Tipo e Categoria. */
    private static boolean agrupa(FiltroRelatorioDTO f) {
        return true;
    }

    private static BigDecimal numero(Object o) {
        return o instanceof BigDecimal b ? b : (o instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO);
    }

    private static void validarPeriodo(FiltroRelatorioDTO f) {
        if (f.dataInicio() != null && f.dataFim() != null && f.dataFim().isBefore(f.dataInicio())) {
            throw new NegocioException("A data final não pode ser anterior à inicial.");
        }
        Objects.requireNonNull(f);
    }
}
