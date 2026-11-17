package com.vivo4redes.syscor.financeiro.relatorio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.financeiro.CaixaTestSupport;
import com.vivo4redes.syscor.venda.enums.TipoPessoa;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.ClienteRepository;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Relatório de Formas de Pagamento (docs/formas_pagamento.md) com dados sintéticos, JWT e PostgreSQL reais. Cada teste
 * cria as próprias filiais e filtra por elas, porque o banco é da classe toda.
 */
@SpringBootTest(properties = {
        "syscor.caixa.cargos-auditoria=AUDITORIA DE CAIXA",
        "syscor.caixa.cargos-financeiro=FINANCEIRO",
        "syscor.caixa.cargos-area=GERENTE DE AREA DE VENDAS",
        "syscor.caixa.cargos-gerente=GERENTE DE VENDAS - JUNIOR",
        "syscor.caixa.admins=ADMIN.FORMAS.TESTE",
        "syscor.relatorio.formas-pagamento.maximo-linhas-pdf=6"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class FormasPagamentoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final String BASE = "/financeiro/movimento/formas-pagamento";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.now(ZONA);
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepository clientes;
    @Autowired FilialRepository filiais;
    @Autowired UsuarioRepository usuarios;
    @Autowired PagamentoVendaRepository pagamentos;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transacao;

    private int n;
    private long clienteId;
    private Usuario vendedor;
    private Usuario financeiro;
    private Usuario auditor;
    private Usuario gerenteArea;
    private Usuario gerenteLoja;

    @BeforeEach
    void preparar() {
        n = SEQUENCIA.incrementAndGet();
        clienteId = clientes.save(Cliente.builder().tipoPessoa(TipoPessoa.FISICA).nome("Cliente Formas " + n)
                .cpfCnpj("7" + String.format("%010d", n)).uf("GO").build()).getId();
        vendedor = usuario("vendedor", "VENDEDOR");
        financeiro = usuario("financeiro", "FINANCEIRO");
        auditor = usuario("auditor", "AUDITORIA DE CAIXA");
        gerenteArea = usuario("gerentearea", "GERENTE DE ÁREA DE VENDAS");
        gerenteLoja = usuario("gerenteloja", "GERENTE DE VENDAS - JÚNIOR");
    }

    // ------------------------------------------------------------------ regra de soma

    @Test
    @DisplayName("Soma só vendas CONCLUIDA (ENTRADA e FINANCIADO inclusos), agrupa por filial, caixa e forma, e a soma de um caixa bate com somarPorCaixaEForma")
    void agrupamentos() throws Exception {
        Cenario c = cenario();

        JsonNode porFilial = consultar(c, "FILIAL");
        assertThat(porFilial.get("totalGeral").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(porFilial.get("quantidadeVendas").asInt()).isEqualTo(4);
        assertThat(porFilial.get("quantidadePagamentos").asInt()).isEqualTo(5);
        assertThat(porFilial.get("semDataFinalizacao").asInt()).isZero();
        assertThat(porFilial.get("grupos")).hasSize(2);
        JsonNode a = grupo(porFilial, "Formas A " + n);
        assertThat(a.get("total").decimalValue()).isEqualByComparingTo("330.00");
        assertThat(a.get("quantidadeVendas").asInt()).isEqualTo(3);
        assertThat(a.get("quantidadePagamentos").asInt()).isEqualTo(4);
        assertThat(linha(a, "PIX").get("valor").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(linha(a, "PIX").get("percentual").decimalValue()).isEqualByComparingTo("45.45");
        assertThat(linha(a, "CARTAO_CREDITO").get("valor").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(linha(a, "DINHEIRO").get("formaRotulo").asText()).isEqualTo("Dinheiro");
        assertThat(grupo(porFilial, "Formas B " + n).get("total").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(porFilial.get("totaisPorForma").findValuesAsText("forma"))
                .containsExactly("PIX", "CARTAO_CREDITO", "DINHEIRO");

        JsonNode porCaixa = consultar(c, "CAIXA");
        assertThat(rotulosDosGrupos(porCaixa)).containsExactlyInAnyOrder(
                "Formas A " + n + " · CAIXA X", "Formas A " + n + " · CAIXA Y", "Formas B " + n + " · Sem caixa");
        assertThat(grupo(porCaixa, "Formas A " + n + " · CAIXA X").get("total").decimalValue())
                .isEqualByComparingTo("300.00");

        JsonNode porForma = consultar(c, "FORMA");
        assertThat(grupo(porForma, "PIX").get("total").decimalValue()).isEqualByComparingTo("220.00");
        assertThat(grupo(porForma, "PIX").get("quantidadeVendas").asInt()).isEqualTo(3);
        assertThat(linha(grupo(porForma, "PIX"), String.valueOf(c.filialB)).get("valor").decimalValue())
                .isEqualByComparingTo("70.00");

        // mesma regra do caixa: a soma por forma do caixa X é a do relatório (CAIXA X = PIX 150 + crédito 150)
        Map<String, BigDecimal> doCaixa = new HashMap<>();
        for (Object[] l : pagamentos.somarPorCaixaEForma(c.caixaX)) {
            doCaixa.put(String.valueOf(l[0]), (BigDecimal) l[1]);
        }
        JsonNode x = grupo(porCaixa, "Formas A " + n + " · CAIXA X");
        assertThat(doCaixa).hasSize(2);
        doCaixa.forEach((forma, valor) ->
                assertThat(linha(x, forma).get("valor").decimalValue()).isEqualByComparingTo(valor));

        JsonNode soCredito = consultar(c, "FILIAL", Map.of("formas", "CARTAO_CREDITO"));
        assertThat(soCredito.get("totalGeral").decimalValue()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Dia local de America/Sao_Paulo: 23:59:59 fica no dia, 00:00:00 vai para o seguinte; venda sem finalizadoEm vira aviso")
    void fronteiraDeDatas() throws Exception {
        long filial = filial("Formas Data " + n);
        LocalDate dia = HOJE.minusDays(20);
        venda(filial, "CONCLUIDA", dia.atTime(23, 59, 59).atZone(ZONA).toInstant(), null, p("PIX", "PAGAMENTO", "10.00"));
        venda(filial, "CONCLUIDA", dia.plusDays(1).atStartOfDay(ZONA).toInstant(), null, p("PIX", "PAGAMENTO", "20.00"));
        venda(filial, "CONCLUIDA", null, null, p("PIX", "PAGAMENTO", "500.00"));

        Map<String, String> f = Map.of("filialIds", String.valueOf(filial));
        JsonNode primeiro = enviar(get(BASE), f, dia, dia, financeiro).corpo;
        assertThat(primeiro.get("totalGeral").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(primeiro.get("semDataFinalizacao").asInt()).isEqualTo(1);
        JsonNode segundo = enviar(get(BASE), f, dia.plusDays(1), dia.plusDays(1), financeiro).corpo;
        assertThat(segundo.get("totalGeral").decimalValue()).isEqualByComparingTo("20.00");
        JsonNode ambos = enviar(get(BASE), f, dia, dia.plusDays(1), financeiro).corpo;
        assertThat(ambos.get("totalGeral").decimalValue()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Período inválido (fim antes do início, mais de 366 dias) e formato de exportação inválido dão 422")
    void validacoes() throws Exception {
        Resposta invertido = enviar(get(BASE), Map.of(), HOJE, HOJE.minusDays(1), financeiro);
        assertThat(invertido.status).isEqualTo(422);
        Resposta longo = enviar(get(BASE), Map.of(), HOJE.minusDays(366), HOJE, financeiro);
        assertThat(longo.status).as("367 dias").isEqualTo(422);
        Resposta limite = enviar(get(BASE), Map.of("filialIds", "-1"), HOJE.minusDays(365), HOJE, financeiro);
        assertThat(limite.status).as("366 dias").isEqualTo(200);
        Resposta formato = enviar(get(BASE + "/exportar"), Map.of("formato", "doc"), HOJE, HOJE, financeiro);
        assertThat(formato.status).isEqualTo(422);
    }

    // ------------------------------------------------------------------ perfis

    @Test
    @DisplayName("Financeiro, auditoria, gerente de área e admin consultam; gerente de loja e vendedor levam 403 com código próprio")
    void perfis() throws Exception {
        Cenario c = cenario();
        for (Usuario quem : List.of(financeiro, auditor, gerenteArea)) {
            Resposta r = enviar(get(BASE), Map.of("filialIds", c.filialA + "," + c.filialB), HOJE, HOJE, quem);
            assertThat(r.status).as(quem.getLogin()).isEqualTo(200);
            assertThat(r.corpo.get("totalGeral").decimalValue()).isEqualByComparingTo("400.00");
            assertThat(enviar(get(BASE + "/permissoes"), Map.of(), null, null, quem).corpo.get("consultar").asBoolean())
                    .isTrue();
        }
        for (Usuario quem : List.of(gerenteLoja, vendedor)) {
            Resposta r = enviar(get(BASE), Map.of(), HOJE, HOJE, quem);
            assertThat(r.status).as(quem.getLogin()).isEqualTo(403);
            assertThat(r.corpo.get("codigo").asText()).isEqualTo("RELATORIO_SEM_PERMISSAO");
            assertThat(enviar(get(BASE + "/opcoes"), Map.of(), null, null, quem).status).isEqualTo(403);
            assertThat(enviar(get(BASE + "/exportar"), Map.of(), HOJE, HOJE, quem).status).isEqualTo(403);
            assertThat(enviar(get(BASE + "/permissoes"), Map.of(), null, null, quem).corpo.get("consultar").asBoolean())
                    .isFalse();
        }
        JsonNode opcoes = enviar(get(BASE + "/opcoes"), Map.of(), null, null, gerenteArea).corpo;
        assertThat(opcoes.get("filiais").findValuesAsText("nome")).contains("Formas A " + n, "Formas B " + n);
        // só as formas que as vendas concluídas usaram (esta classe só cria PIX, crédito e dinheiro)
        assertThat(opcoes.get("formas").findValuesAsText("valor")).containsExactly("CARTAO_CREDITO", "PIX", "DINHEIRO");
        assertThat(opcoes.get("formas").findValuesAsText("rotulo"))
                .containsExactly("Cartão de crédito", "PIX", "Dinheiro")
                .doesNotContain("Voucher (histórico)", "Vivo Pay", "Boleto");
    }

    // ------------------------------------------------------------------ exportação

    @Test
    @DisplayName("Exporta XLSX (uma aba, valores numéricos), CSV (UTF-8 com BOM, ';' e vírgula decimal) e PDF, com o nome do arquivo no cabeçalho")
    void exportacao() throws Exception {
        Cenario c = cenario();
        Map<String, String> filtros = Map.of("filialIds", c.filialA + "," + c.filialB, "agruparPor", "CAIXA");

        MvcResult xlsx = baixar(filtros, "xlsx");
        assertThat(xlsx.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("formas-pagamento_" + HOJE + "_" + HOJE + ".xlsx");
        try (XSSFWorkbook wb = workbook(xlsx)) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetName(0)).isEqualTo("Formas de Pagamento");
            XSSFSheet aba = wb.getSheetAt(0);
            int cab = linhaDoTexto(aba, "Nº da venda");
            assertThat(textos(aba)).noneMatch(t -> t.startsWith("Agrupado") || t.contains("Agrupado por"));
            assertThat(aba.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) (cab + 1));
            assertThat(aba.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A" + (cab + 1) + ":H" + (cab + 6));
            assertThat(aba.getRow(cab + 1).getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            double soma = 0;
            for (int i = cab + 1; i <= cab + 5; i++) soma += aba.getRow(i).getCell(7).getNumericCellValue();
            assertThat(soma).isEqualTo(400.0);
            Row geral = aba.getRow(linhaDoTexto(aba, "Total geral"));
            assertThat(geral.getCell(1).getNumericCellValue()).isEqualTo(5.0);
            assertThat(geral.getCell(2).getNumericCellValue()).isEqualTo(400.0);
            assertThat(aba.getRow(linhaDoTexto(aba, "Total de vendas distintas")).getCell(1).getNumericCellValue())
                    .isEqualTo(4.0);
        }

        MvcResult csv = baixar(filtros, "csv");
        byte[] bytes = csv.getResponse().getContentAsByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        String texto = new String(bytes, StandardCharsets.UTF_8);
        assertThat(texto).contains("Nº da venda;Data/hora;Filial/PDV;Caixa;Forma de pagamento;Tipo;Parcelas;Valor")
                .contains("Cartão de crédito").contains("Total geral;5;400,00;100,00")
                .contains("Total de vendas distintas;4").doesNotContain("Agrupado").doesNotContain("Total:");

        MvcResult pdf = baixar(filtros, "pdf");
        byte[] pdfBytes = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.getResponse().getContentType()).startsWith("application/pdf");
    }

    @Test
    @DisplayName("Lista plana: uma linha por pagamento por nº da venda (não pela hora nem pela loja), sem 'Total:' por filial, e os totais por forma no fim batem com a lista")
    void listaPlanaOrdenadaPorVenda() throws Exception {
        long a = filial("Plana A " + n);
        long b = filial("Plana B " + n);
        LocalDate dia = HOJE.minusDays(3);
        // o nº da venda cresce com a inserção; a hora e a loja vão fora de ordem de propósito
        venda(b, "CONCLUIDA", dia.atTime(18, 0).atZone(ZONA).toInstant(), null, p("PIX", "PAGAMENTO", "40.00"));
        venda(a, "CONCLUIDA", dia.atTime(9, 0).atZone(ZONA).toInstant(), null,
                p("DINHEIRO", "ENTRADA", "20.00"), p("CARTAO_CREDITO", "FINANCIADO", "80.00"));
        venda(b, "CONCLUIDA", dia.atTime(8, 0).atZone(ZONA).toInstant(), null, p("PIX", "PAGAMENTO", "15.50"));
        venda(a, "CONCLUIDA", dia.atTime(12, 0).atZone(ZONA).toInstant(), null, p("PIX", "PAGAMENTO", "4.50"));
        Map<String, String> f = new HashMap<>(Map.of("filialIds", a + "," + b, "agruparPor", "FILIAL"));

        try (XSSFWorkbook wb = workbook(baixar(f, "xlsx", dia, dia))) {
            XSSFSheet aba = wb.getSheetAt(0);
            int cab = linhaDoTexto(aba, "Nº da venda");
            List<Long> numeros = new java.util.ArrayList<>();
            List<String> formas = new java.util.ArrayList<>();
            double soma = 0;
            for (int i = cab + 1; i <= cab + 5; i++) {
                Row r = aba.getRow(i);
                numeros.add((long) r.getCell(0).getNumericCellValue());
                formas.add(r.getCell(4).getStringCellValue());
                soma += r.getCell(7).getNumericCellValue();
            }
            assertThat(numeros).isSorted();
            assertThat(numeros.get(1)).as("a venda com 2 formas fica junta").isEqualTo(numeros.get(2));
            assertThat(formas.subList(1, 3)).containsExactly("Dinheiro", "Cartão de crédito");
            assertThat(aba.getRow(cab + 6) == null || aba.getRow(cab + 6).getCell(0) == null).as("linha em branco").isTrue();
            assertThat(textos(aba)).noneMatch(t -> t.startsWith("Total:"));
            int blocoTotais = linhaDoTexto(aba, "Total geral por forma de pagamento");
            assertThat(blocoTotais).isGreaterThan(cab + 5);

            // totais por forma: PIX 60 (3), crédito 80 (1), dinheiro 20 (1), por maior valor
            List<String> ordem = new java.util.ArrayList<>();
            double somaFormas = 0;
            long pagamentosFormas = 0;
            for (int i = blocoTotais + 2; i < linhaDoTexto(aba, "Total geral"); i++) {
                Row r = aba.getRow(i);
                ordem.add(r.getCell(0).getStringCellValue());
                pagamentosFormas += (long) r.getCell(1).getNumericCellValue();
                somaFormas += r.getCell(2).getNumericCellValue();
            }
            assertThat(ordem).containsExactly("Cartão de crédito", "PIX", "Dinheiro");
            Row geral = aba.getRow(linhaDoTexto(aba, "Total geral"));
            assertThat(geral.getCell(2).getNumericCellValue()).isEqualTo(soma).isEqualTo(somaFormas).isEqualTo(160.0);
            assertThat(geral.getCell(1).getNumericCellValue()).isEqualTo(5.0).isEqualTo(pagamentosFormas);
            assertThat(aba.getRow(linhaDoTexto(aba, "Total de vendas distintas")).getCell(1).getNumericCellValue())
                    .as("a venda com 2 formas conta uma vez").isEqualTo(4.0);
        }

        String csv = new String(baixar(f, "csv", dia, dia).getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<String> linhas = csv.lines().toList();
        int cab = linhas.indexOf("Nº da venda;Data/hora;Filial/PDV;Caixa;Forma de pagamento;Tipo;Parcelas;Valor");
        List<Long> numeros = linhas.subList(cab + 1, cab + 6).stream().map(l -> Long.parseLong(l.split(";")[0])).toList();
        assertThat(numeros).isSorted();
        assertThat(linhas.get(cab + 6)).isEmpty();
        assertThat(linhas.get(cab + 7)).isEqualTo("Total geral por forma de pagamento");
        assertThat(linhas.get(cab + 9)).isEqualTo("Cartão de crédito;1;80,00;50,00");
        assertThat(csv).contains("PIX;3;60,00;37,50").contains("Total geral;5;160,00;100,00")
                .contains("Total de vendas distintas;4").doesNotContain("Total:");
    }

    @Test
    @DisplayName("Período sem vendas: os três formatos saem com 'Nenhuma venda no período' e totais zerados")
    void semVendasNoPeriodo() throws Exception {
        long vazia = filial("Formas Vazia " + n);
        Map<String, String> f = Map.of("filialIds", String.valueOf(vazia));
        try (XSSFWorkbook wb = workbook(baixar(f, "xlsx"))) {
            XSSFSheet aba = wb.getSheetAt(0);
            assertThat(textos(aba)).contains("Nenhuma venda no período.");
            Row geral = aba.getRow(linhaDoTexto(aba, "Total geral"));
            assertThat(geral.getCell(1).getNumericCellValue()).isZero();
            assertThat(geral.getCell(2).getNumericCellValue()).isZero();
        }
        String csv = new String(baixar(f, "csv").getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Nenhuma venda no período.").contains("Total geral;0;0,00;0,00")
                .contains("Total de vendas distintas;0");
        assertThat(new String(baixar(f, "pdf").getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("PDF acima do limite de linhas (6 no teste) dá 422 pedindo Excel ou CSV; XLSX e CSV continuam saindo")
    void pdfAcimaDoLimite() throws Exception {
        long filial = filial("Formas Muitas " + n);
        Instant meioDia = HOJE.atTime(12, 0).atZone(ZONA).toInstant();
        for (int i = 0; i < 7; i++) venda(filial, "CONCLUIDA", meioDia, null, p("PIX", "PAGAMENTO", "1.00"));
        Map<String, String> f = new HashMap<>(Map.of("filialIds", String.valueOf(filial), "formato", "pdf"));

        Resposta pdf = enviar(get(BASE + "/exportar"), f, HOJE, HOJE, financeiro);
        assertThat(pdf.status).isEqualTo(422);
        assertThat(pdf.corpo.toString()).contains("Muitas linhas para PDF").contains("use Excel ou CSV");
        f.remove("formato");
        baixar(f, "xlsx");
        baixar(f, "csv");
    }

    // ------------------------------------------------------------------ apoio

    /** Duas filiais; A com os caixas X e Y, B sem caixa; vendas de outros status que não podem entrar. */
    private record Cenario(long filialA, long filialB, long caixaX, long caixaY) {
    }

    private Cenario cenario() {
        long a = filial("Formas A " + n);
        long b = filial("Formas B " + n);
        long x = CaixaTestSupport.abrirCaixa(jdbc, a, "CAIXA X", HOJE);
        long y = CaixaTestSupport.abrirCaixa(jdbc, a, "CAIXA Y", HOJE);
        Instant meioDia = HOJE.atTime(12, 0).atZone(ZONA).toInstant();
        venda(a, "CONCLUIDA", meioDia, x, p("PIX", "PAGAMENTO", "100.00"));
        venda(a, "CONCLUIDA", meioDia, x, p("PIX", "ENTRADA", "50.00"), p("CARTAO_CREDITO", "FINANCIADO", "150.00"));
        venda(a, "CONCLUIDA", meioDia, y, p("DINHEIRO", "PAGAMENTO", "30.00"));
        venda(b, "CONCLUIDA", meioDia, null, p("PIX", "PAGAMENTO", "70.00"));
        // a forma BOLETO só existe numa venda cancelada: não pode aparecer nas opções nem nas somas
        venda(a, "CANCELADA", meioDia, x, p("PIX", "PAGAMENTO", "999.00"), p("BOLETO", "PAGAMENTO", "5.00"));
        venda(a, "ABERTA", null, x, p("PIX", "PAGAMENTO", "888.00"));
        return new Cenario(a, b, x, y);
    }

    private JsonNode consultar(Cenario c, String agruparPor) throws Exception {
        return consultar(c, agruparPor, Map.of());
    }

    private JsonNode consultar(Cenario c, String agruparPor, Map<String, String> extra) throws Exception {
        Map<String, String> p = new HashMap<>(extra);
        p.put("filialIds", c.filialA + "," + c.filialB);
        p.put("agruparPor", agruparPor);
        Resposta r = enviar(get(BASE), p, HOJE, HOJE, financeiro);
        assertThat(r.status).isEqualTo(200);
        return r.corpo;
    }

    /** Só o rótulo de cada grupo (findValuesAsText desceria também nas linhas). */
    private static List<String> rotulosDosGrupos(JsonNode relatorio) {
        List<String> rotulos = new java.util.ArrayList<>();
        relatorio.get("grupos").forEach(g -> rotulos.add(g.get("rotulo").asText()));
        return rotulos;
    }

    private static JsonNode grupo(JsonNode relatorio, String rotulo) {
        for (JsonNode g : relatorio.get("grupos")) {
            if (rotulo.equals(g.get("rotulo").asText())) return g;
        }
        throw new AssertionError("grupo não encontrado: " + rotulo + " em " + rotulosDosGrupos(relatorio));
    }

    /** Linha do grupo pela forma (grupo por filial/caixa) ou pela chave (grupo por forma: id da filial). */
    private static JsonNode linha(JsonNode grupo, String chaveOuForma) {
        for (JsonNode l : grupo.get("linhas")) {
            if (chaveOuForma.equals(l.get("chave").asText()) || chaveOuForma.equals(l.get("forma").asText())) return l;
        }
        throw new AssertionError("linha não encontrada: " + chaveOuForma);
    }

    private record Pag(String forma, String tipo, String valor) {
    }

    private static Pag p(String forma, String tipo, String valor) {
        return new Pag(forma, tipo, valor);
    }

    private long filial(String nome) {
        return filiais.save(Filial.builder().codigo("FP" + n + "-" + SEQUENCIA.incrementAndGet()).nome(nome).build())
                .getId();
    }

    /** Venda inserida por SQL; finalizado_em gravado pelo Hibernate, que converte o Instant como na leitura. */
    private long venda(long filialId, String status, Instant finalizadoEm, Long caixaId, Pag... pags) {
        long id = jdbc.queryForObject("""
                INSERT INTO vendas (cliente_id, filial_id, usuario_id, numero_venda, status, valor_total, caixa_sessao_id)
                VALUES (?, ?, ?, nextval('seq_numero_venda'), ?, 1000, ?) RETURNING id
                """, Long.class, clienteId, filialId, vendedor.getId(), status, caixaId);
        if (finalizadoEm != null) {
            transacao.executeWithoutResult(s -> em.createQuery("update Venda v set v.finalizadoEm = :t where v.id = :id")
                    .setParameter("t", finalizadoEm).setParameter("id", id).executeUpdate());
        }
        for (Pag pag : pags) {
            jdbc.update("INSERT INTO pagamentos_venda (venda_id, forma, tipo, valor, parcelas) VALUES (?, ?, ?, ?, 1)",
                    id, pag.forma, pag.tipo, new BigDecimal(pag.valor));
        }
        return id;
    }

    private Usuario usuario(String prefixo, String cargo) {
        return usuarios.save(Usuario.builder().nome(prefixo + " " + n).login(prefixo + "_" + n)
                .email(prefixo + "_" + n + "@teste.local").senhaHash(encoder.encode(SENHA)).primeiroAcesso(false)
                .filial("Matriz").cargo(cargo).build());
    }

    private String entrar(Usuario usuario) throws Exception {
        String guardado = TOKENS.get(usuario.getLogin());
        if (guardado != null) return guardado;
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", usuario.getLogin(), "senha", SENHA)))).andReturn();
        assertThat(r.getResponse().getStatus()).as("login de " + usuario.getLogin()).isEqualTo(200);
        String token = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
        TOKENS.put(usuario.getLogin(), token);
        return token;
    }

    private MockHttpServletRequestBuilder comParametros(MockHttpServletRequestBuilder pedido, Map<String, String> params,
                                                        LocalDate inicio, LocalDate fim, Usuario quem) throws Exception {
        pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + entrar(quem));
        params.forEach(pedido::param);
        if (inicio != null) pedido.param("dataInicio", inicio.toString());
        if (fim != null) pedido.param("dataFim", fim.toString());
        return pedido;
    }

    private Resposta enviar(MockHttpServletRequestBuilder pedido, Map<String, String> params, LocalDate inicio,
                            LocalDate fim, Usuario quem) throws Exception {
        MvcResult r = mvc.perform(comParametros(pedido, params, inicio, fim, quem)).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Resposta(r.getResponse().getStatus(),
                texto.isBlank() || !texto.trim().startsWith("{") ? json.createObjectNode() : json.readTree(texto));
    }

    private MvcResult baixar(Map<String, String> filtros, String formato) throws Exception {
        return baixar(filtros, formato, HOJE, HOJE);
    }

    private MvcResult baixar(Map<String, String> filtros, String formato, LocalDate inicio, LocalDate fim)
            throws Exception {
        Map<String, String> p = new HashMap<>(filtros);
        p.put("formato", formato);
        MvcResult r = mvc.perform(comParametros(get(BASE + "/exportar"), p, inicio, fim, financeiro)).andReturn();
        assertThat(r.getResponse().getStatus()).as(formato).isEqualTo(200);
        return r;
    }

    private static XSSFWorkbook workbook(MvcResult r) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()));
    }

    /** Textos da primeira coluna da aba (linhas vazias ficam de fora). */
    private static List<String> textos(XSSFSheet aba) {
        List<String> textos = new java.util.ArrayList<>();
        for (Row r : aba) {
            Cell c = r.getCell(0);
            if (c != null && c.getCellType() == CellType.STRING) textos.add(c.getStringCellValue());
        }
        return textos;
    }

    /** Índice da primeira linha cuja primeira célula é exatamente {@code texto}. */
    private static int linhaDoTexto(XSSFSheet aba, String texto) {
        for (Row r : aba) {
            Cell c = r.getCell(0);
            if (c != null && c.getCellType() == CellType.STRING && texto.equals(c.getStringCellValue())) {
                return r.getRowNum();
            }
        }
        throw new AssertionError("linha não encontrada: " + texto);
    }

    private record Resposta(int status, JsonNode corpo) {
    }
}
