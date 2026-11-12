package com.vivo4redes.syscor.relatorio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.venda.enums.TipoPessoa;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.ClienteRepository;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Relatórios Venda de Produtos, Venda de Serviços e Vendas (docs/relatorios_produtos_servicos.md), com dados
 * sintéticos, JWT e PostgreSQL reais. Cada teste usa uma filial própria e filtra por ela.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RelatorioAnaliticoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.now(ZONA);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepository clientes;
    @Autowired FilialRepository filiais;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transacao;

    private int n;
    private long clienteId;
    private long filialId;
    private Usuario vendedor;
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        n = SEQUENCIA.incrementAndGet();
        clienteId = clientes.save(Cliente.builder().tipoPessoa(TipoPessoa.FISICA).nome("Cliente Rel " + n)
                .cpfCnpj("8" + String.format("%010d", n)).uf("GO").build()).getId();
        filialId = filiais.save(Filial.builder().codigo("REL" + n).nome("Filial Rel " + n).build()).getId();
        vendedor = usuarios.save(Usuario.builder().nome("Vendedor Rel").login("rel" + n).email("rel" + n + "@teste.local")
                .senhaHash(encoder.encode(SENHA)).primeiroAcesso(false).filial("Filial Rel").cargo("VENDEDOR").build());
        token = entrar();
    }

    // ------------------------------------------------------------------ o que entra em cada relatório

    @Test
    @DisplayName("Venda só com produto entra em Produtos e Vendas, e fica fora de Serviços")
    void soProduto() throws Exception {
        long venda = vendaLancada(HOJE);
        produto(venda, "PRODUTO_VIVO", "iPhone Teste", "100.00", "SERIAL-P" + n, 1, "60.00");

        assertThat(numeros("produtos", Map.of())).containsExactly(numeroDa(venda));
        assertThat(numeros("servicos", Map.of())).isEmpty();
        assertThat(numeros("vendas", Map.of())).containsExactly(numeroDa(venda));
    }

    @Test
    @DisplayName("Venda só com serviço entra em Serviços e Vendas, e fica fora de Produtos")
    void soServico() throws Exception {
        long venda = vendaLancada(HOJE);
        servico(venda, "61 998674575", "Alta", "CONTROLE_BTL", "Controle");

        assertThat(numeros("produtos", Map.of())).isEmpty();
        assertThat(numeros("servicos", Map.of())).containsExactly(numeroDa(venda));
        assertThat(numeros("vendas", Map.of())).containsExactly(numeroDa(venda));
    }

    @Test
    @DisplayName("Venda mista entra nos dois relatórios; carrinho cancelado (nunca lançado) não entra em nenhum")
    void mistaECarrinhoCancelado() throws Exception {
        long mista = vendaLancada(HOJE);
        produto(mista, "ACESSORIO", "Capa Teste", "30.00", "SERIAL-A" + n, 1, null);
        servico(mista, "61 998674576", "Alta", "CONTROLE_BTL", "Controle");
        long carrinho = vendaSemFinalizar("CANCELADA");
        produto(carrinho, "PRODUTO_VIVO", "Aparelho carrinho", "10.00", "SERIAL-C" + n, 1, null);

        assertThat(numeros("produtos", Map.of())).containsExactly(numeroDa(mista));
        assertThat(numeros("servicos", Map.of())).containsExactly(numeroDa(mista));
        assertThat(numeros("vendas", Map.of("statusVenda", "AMBAS"))).containsExactly(numeroDa(mista));
    }

    @Test
    @DisplayName("Venda cancelada depois de lançada sai de Produtos/Serviços e continua em Vendas com status Cancelada")
    void vendaCancelada() throws Exception {
        long cancelada = venda("CANCELADA", null, HOJE);
        produto(cancelada, "PRODUTO_VIVO", "Aparelho cancelado", "50.00", "SERIAL-X" + n, 1, null);
        servico(cancelada, "61 998674577", "Alta", "CONTROLE_BTL", "Controle");

        assertThat(numeros("produtos", Map.of())).isEmpty();
        assertThat(numeros("servicos", Map.of())).isEmpty();
        assertThat(numeros("produtos", Map.of("statusVenda", "CANCELADA"))).containsExactly(numeroDa(cancelada));

        JsonNode vendas = buscar("vendas", Map.of("campos", "statusVenda"));
        assertThat(total(vendas)).isEqualTo(1);
        assertThat(vendas.get("content").get(0).get("statusVenda").asText()).isEqualTo("Cancelada");
        assertThat(numeros("vendas", Map.of("statusVenda", "CONCLUIDA"))).isEmpty();
    }

    // ------------------------------------------------------------------ filtros

    @Test
    @DisplayName("Filtros de filial, período e vendedor")
    void filtrosBasicos() throws Exception {
        long hoje = vendaLancada(HOJE);
        produto(hoje, "PRODUTO_VIVO", "Aparelho hoje", "10.00", "SERIAL-H" + n, 1, null);
        long antiga = vendaLancada(HOJE.minusDays(10));
        produto(antiga, "PRODUTO_VIVO", "Aparelho antigo", "10.00", "SERIAL-O" + n, 1, null);

        Map<String, String> recente = new HashMap<>();
        recente.put("dataInicio", HOJE.minusDays(2).toString());
        recente.put("dataFim", HOJE.toString());
        assertThat(numeros("produtos", recente)).containsExactly(numeroDa(hoje));
        assertThat(numeros("produtos", Map.of())).containsExactlyInAnyOrder(numeroDa(hoje), numeroDa(antiga));
        assertThat(numeros("produtos", Map.of("vendedorId", String.valueOf(vendedor.getId() + 9999)))).isEmpty();
        assertThat(numeros("produtos", Map.of("filialIds", String.valueOf(filialId + 9999)))).isEmpty();
    }

    @Test
    @DisplayName("Considerar Data de Ativação filtra pela ativação do protocolo (Serviços)")
    void considerarAtivacao() throws Exception {
        long ativada = vendaLancada(HOJE.minusDays(20));
        servico(ativada, "61 998674578", "Alta", "CONTROLE_BTL", "Controle");
        protocolo(ativada, HOJE.minusDays(1));
        long semAtivacao = vendaLancada(HOJE.minusDays(1));
        servico(semAtivacao, "61 998674579", "Alta", "CONTROLE_BTL", "Controle");

        Map<String, String> porAtivacao = new HashMap<>();
        porAtivacao.put("considerar", "ATIVACAO");
        porAtivacao.put("dataInicio", HOJE.minusDays(3).toString());
        porAtivacao.put("dataFim", HOJE.toString());
        assertThat(numeros("servicos", porAtivacao)).containsExactly(numeroDa(ativada));

        porAtivacao.put("considerar", "VENDA");
        assertThat(numeros("servicos", porAtivacao)).containsExactly(numeroDa(semAtivacao));
    }

    @Test
    @DisplayName("Segmento Comercial escolhe as linhas de serviço pelo serviço e pelo tipo de plano")
    void segmentos() throws Exception {
        long venda = vendaLancada(HOJE);
        servico(venda, "61 998674580", "Alta", "CONTROLE_BTL", "Controle");
        servico(venda, "61 998674581", "Alta", "PRE", "Pré");
        servico(venda, "61 998674582", "Troca de Simcard", null, null);

        assertThat(total(buscar("servicos", Map.of()))).isEqualTo(3);
        assertThat(total(buscar("servicos", Map.of("segmentos", "ALTAS_CONTROLE")))).isEqualTo(1);
        assertThat(total(buscar("servicos", Map.of("segmentos", "ALTAS_PRE_PAGO,TROCA_DE_SIMCARD")))).isEqualTo(2);
        assertThat(total(buscar("servicos", Map.of("segmentos", "ALTAS_POS_PURO")))).isZero();
    }

    @Test
    @DisplayName("Tipo de Produto: Acessórios, Produto Vivo, Recarga e Sim Card (pelo estoque)")
    void tiposDeProduto() throws Exception {
        long venda = vendaLancada(HOJE);
        produto(venda, "PRODUTO_VIVO", "Aparelho", "100.00", "SERIAL-T1-" + n, 1, "50.00");
        produto(venda, "ACESSORIO", "Capa", "20.00", "SERIAL-T2-" + n, 1, "5.00");
        produto(venda, "RECARGA", "Recarga 20", "20.00", null, 1, null);
        long chip = produto(venda, "PRODUTO_VIVO", "Chip Vivo", "10.00", "SERIAL-T3-" + n, 1, null);
        jdbc.update("UPDATE tb_estoque_geral SET tipo_produto = 'Sim Card' WHERE id = (SELECT estoque_geral_id FROM itens_venda WHERE id = ?)", chip);

        assertThat(total(buscar("produtos", Map.of()))).isEqualTo(4);
        assertThat(total(buscar("produtos", Map.of("tiposProduto", "ACESSORIOS")))).isEqualTo(1);
        assertThat(total(buscar("produtos", Map.of("tiposProduto", "PRODUTO_VIVO")))).isEqualTo(1);
        assertThat(total(buscar("produtos", Map.of("tiposProduto", "RECARGA")))).isEqualTo(1);
        assertThat(total(buscar("produtos", Map.of("tiposProduto", "SIM_CARD")))).isEqualTo(1);
        assertThat(total(buscar("produtos", Map.of("tiposProduto", "RECARGA,SIM_CARD")))).isEqualTo(2);
    }

    // ------------------------------------------------------------------ campos, lucro e agrupamento

    @Test
    @DisplayName("Só saem os campos escolhidos (mais os travados); 'somente excel' não vai para a tela; lucro só com custo")
    void camposEscolhidos() throws Exception {
        long venda = vendaLancada(HOJE);
        produto(venda, "PRODUTO_VIVO", "Aparelho com custo", "100.00", "SERIAL-L1-" + n, 2, "30.00");
        produto(venda, "ACESSORIO", "Capa sem custo", "20.00", "SERIAL-L2-" + n, 1, null);

        JsonNode pagina = buscar("produtos", Map.of("campos", "valorVenda,valorCusto,valorLucro,horaVenda,cor"));
        List<String> chaves = new ArrayList<>();
        pagina.get("colunas").forEach(c -> chaves.add(c.get("chave").asText()));
        // filial e nº da venda travados; horaVenda é "somente excel" e não vem na tela
        assertThat(chaves).containsExactly("filial", "numeroVenda", "valorCusto", "valorVenda", "valorLucro", "cor");

        Map<String, JsonNode> porValor = new HashMap<>();
        pagina.get("content").forEach(l -> porValor.put(l.get("valorVenda").asText(), l));
        // 2 × 100 de venda, 2 × 30 de custo => lucro 140
        assertThat(porValor.get("200.0").get("valorLucro").decimalValue()).isEqualByComparingTo("140.00");
        assertThat(porValor.get("20.0").get("valorLucro").isNull()).as("sem custo, sem lucro").isTrue();
    }

    @Test
    @DisplayName("Subtotal por grupo (Tipo de Produto) com quantidade, venda, custo e lucro; Categoria agrupa por categoria")
    void subtotaisPorGrupo() throws Exception {
        long venda = vendaLancada(HOJE);
        long a = produto(venda, "PRODUTO_VIVO", "Aparelho A", "100.00", "SERIAL-G1-" + n, 1, "60.00");
        long b = produto(venda, "PRODUTO_VIVO", "Aparelho B", "200.00", "SERIAL-G2-" + n, 1, "150.00");
        long c = produto(venda, "ACESSORIO", "Capa", "30.00", "SERIAL-G3-" + n, 1, "10.00");
        estoqueDe(a, "Smartphone", "Celular");
        estoqueDe(b, "Smartphone", "Celular");
        estoqueDe(c, "Capa", "Acessório");

        JsonNode porTipo = buscar("produtos", Map.of("agruparPor", "TIPO_PRODUTO"));
        Map<String, JsonNode> grupos = new HashMap<>();
        porTipo.get("totais").forEach(t -> grupos.put(t.get("grupo").asText(), t));
        assertThat(grupos).containsOnlyKeys("Smartphone", "Capa");
        assertThat(grupos.get("Smartphone").get("valor").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(grupos.get("Smartphone").get("custo").decimalValue()).isEqualByComparingTo("210.00");
        assertThat(grupos.get("Smartphone").get("lucro").decimalValue()).isEqualByComparingTo("90.00");

        JsonNode porCategoria = buscar("produtos", Map.of("agruparPor", "CATEGORIA"));
        List<String> categorias = new ArrayList<>();
        porCategoria.get("totais").forEach(t -> categorias.add(t.get("grupo").asText()));
        assertThat(categorias).containsExactlyInAnyOrder("Celular", "Acessório");
    }

    @Test
    @DisplayName("Resumo Sintético: totais por filial e vendedor (e grupo em Produtos)")
    void sintetico() throws Exception {
        long v1 = vendaLancada(HOJE);
        produto(v1, "PRODUTO_VIVO", "Aparelho", "100.00", "SERIAL-S1-" + n, 1, "40.00");
        long v2 = vendaLancada(HOJE);
        produto(v2, "PRODUTO_VIVO", "Aparelho", "50.00", "SERIAL-S2-" + n, 1, "20.00");

        JsonNode produtos = buscar("produtos", Map.of("modo", "SINTETICO"));
        assertThat(total(produtos)).isEqualTo(1);
        JsonNode linha = produtos.get("content").get(0);
        assertThat(linha.get("registros").asInt()).isEqualTo(2);
        assertThat(linha.get("valor").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(linha.get("lucro").decimalValue()).isEqualByComparingTo("90.00");

        JsonNode vendas = buscar("vendas", Map.of("modo", "SINTETICO"));
        assertThat(vendas.get("content").get(0).get("registros").asInt()).isEqualTo(2);
        assertThat(contagem("vendas", Map.of("modo", "SINTETICO"))).isEqualTo(1);
        assertThat(contagem("vendas", Map.of())).isEqualTo(2);
    }

    // ------------------------------------------------------------------ exportação

    @Test
    @DisplayName("Exportação .xlsx traz só as colunas escolhidas, inclusive as 'somente excel'; exige período de até 93 dias")
    void exportacaoExcel() throws Exception {
        long venda = vendaLancada(HOJE);
        produto(venda, "PRODUTO_VIVO", "Aparelho Excel", "100.00", "SERIAL-E1-" + n, 1, "40.00");

        Map<String, String> filtro = doMes();
        filtro.put("campos", "serial,valorVenda,horaVenda");
        MvcResult r = mvc.perform(comParametros(get("/relatorios/produtos/exportacao"), filtro)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).contains("spreadsheetml");
        assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("venda_de_produtos.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            Sheet aba = wb.getSheetAt(0);
            Row cabecalho = aba.getRow(0);
            List<String> titulos = new ArrayList<>();
            cabecalho.forEach(c -> titulos.add(c.getStringCellValue()));
            assertThat(titulos).containsExactly("Filial", "Nº da Venda", "Serial", "Valor de venda", "Hora da venda (excel)");
            Row dados = aba.getRow(1);
            assertThat(dados.getCell(0).getStringCellValue()).isEqualTo("Filial Rel " + n);
            assertThat(dados.getCell(2).getStringCellValue()).isEqualTo("SERIAL-E1-" + n);
            assertThat(dados.getCell(3).getNumericCellValue()).isEqualTo(100.0);
            // linha de subtotal do grupo, depois dos dados
            assertThat(aba.getRow(2).getCell(0).getStringCellValue()).startsWith("Subtotal:");
        }

        Map<String, String> semPeriodo = daFilial();
        assertThat(mvc.perform(comParametros(get("/relatorios/produtos/exportacao"), semPeriodo))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
        Map<String, String> longo = daFilial();
        longo.put("dataInicio", HOJE.minusDays(93).toString());
        longo.put("dataFim", HOJE.toString());
        assertThat(mvc.perform(comParametros(get("/relatorios/produtos/exportacao"), longo))
                .andReturn().getResponse().getStatus()).as("94 dias").isEqualTo(422);
    }

    @Test
    @DisplayName("Relatório desconhecido dá 422; as opções trazem campos, segmentos e tipos de produto")
    void opcoes() throws Exception {
        assertThat(mvc.perform(comParametros(get("/relatorios/outro/busca"), Map.of())).andReturn()
                .getResponse().getStatus()).isEqualTo(422);

        JsonNode servicos = enviar(get("/relatorios/servicos/opcoes")).corpo;
        assertThat(servicos.get("segmentos")).isNotEmpty();
        assertThat(servicos.get("tiposProduto")).isEmpty();
        JsonNode produtos = enviar(get("/relatorios/produtos/opcoes")).corpo;
        assertThat(produtos.get("tiposProduto")).hasSize(4);
        assertThat(produtos.get("campos").findValuesAsText("chave")).contains("serial", "valorLucro");
    }

    // ------------------------------------------------------------------ apoio

    private long vendaLancada(LocalDate dia) {
        return venda("CONCLUIDA", null, dia);
    }

    /** Venda inserida por SQL; finalizado_em gravado pelo Hibernate, que converte o Instant como na leitura. */
    private long venda(String status, String avaliacao, LocalDate dia) {
        long id = vendaSemFinalizar(status, avaliacao);
        Instant lancamento = dia.atTime(12, 0).atZone(ZONA).toInstant().plusSeconds(id);
        transacao.executeWithoutResult(s -> em.createQuery("update Venda v set v.finalizadoEm = :t where v.id = :id")
                .setParameter("t", lancamento).setParameter("id", id).executeUpdate());
        return id;
    }

    private long vendaSemFinalizar(String status) {
        return vendaSemFinalizar(status, null);
    }

    private long vendaSemFinalizar(String status, String avaliacao) {
        return jdbc.queryForObject("""
                INSERT INTO vendas (cliente_id, filial_id, usuario_id, numero_venda, status, avaliacao_procedencia, valor_total)
                VALUES (?, ?, ?, nextval('seq_numero_venda'), ?, ?, 1000) RETURNING id
                """, Long.class, clienteId, filialId, vendedor.getId(), status, avaliacao);
    }

    /** Item de produto; com serial, cria a linha de estoque com o custo unitário (ou sem custo, se nulo). */
    private long produto(long vendaId, String categoria, String descricao, String valor, String serial, int quantidade,
                         String custo) {
        Long estoque = null;
        if (serial != null) {
            estoque = jdbc.queryForObject("""
                    INSERT INTO tb_estoque_geral (serial, quantidade, status, valor_custo, modelo_comercial, marca)
                    VALUES (?, 1, 'VENDIDO', ?::NUMERIC, ?, 'Marca Teste') RETURNING id
                    """, Long.class, serial, custo, descricao);
        }
        return jdbc.queryForObject("""
                INSERT INTO itens_venda (venda_id, categoria, descricao_produto, quantidade, valor_unitario,
                                         serial_imei, estoque_geral_id)
                VALUES (?, ?, ?, ?, ?::NUMERIC, ?, ?) RETURNING id
                """, Long.class, vendaId, categoria, descricao, quantidade, valor, serial, estoque);
    }

    private void estoqueDe(long itemId, String tipoProduto, String categoria) {
        jdbc.update("UPDATE tb_estoque_geral SET tipo_produto = ?, categoria = ? "
                + "WHERE id = (SELECT estoque_geral_id FROM itens_venda WHERE id = ?)", tipoProduto, categoria, itemId);
    }

    private long servico(long vendaId, String acesso, String tipoServico, String grupo, String segmento) {
        return jdbc.queryForObject("""
                INSERT INTO itens_venda (venda_id, categoria, descricao_produto, quantidade, valor_unitario,
                                         numero_acesso, tipo_servico, grupo_plano, segmento)
                VALUES (?, 'SERVICO_VIVO', 'Serviço', 1, 0, ?, ?, ?, ?) RETURNING id
                """, Long.class, vendaId, acesso, tipoServico, grupo, segmento);
    }

    private void protocolo(long vendaId, LocalDate ativacao) {
        jdbc.update("INSERT INTO tb_protocolo_documental (venda_id, data_ativacao) VALUES (?, ?)", vendaId, ativacao);
    }

    private String numeroDa(long vendaId) {
        return String.valueOf(jdbc.queryForObject("SELECT numero_venda FROM vendas WHERE id = ?", Long.class, vendaId));
    }

    private Map<String, String> daFilial() {
        Map<String, String> filtro = new HashMap<>();
        filtro.put("filialIds", String.valueOf(filialId));
        return filtro;
    }

    private Map<String, String> doMes() {
        Map<String, String> filtro = daFilial();
        filtro.put("dataInicio", HOJE.withDayOfMonth(1).toString());
        filtro.put("dataFim", HOJE.toString());
        return filtro;
    }

    /** Busca na filial do teste (os filtros extras entram por cima). */
    private JsonNode buscar(String relatorio, Map<String, String> extras) throws Exception {
        Map<String, String> filtro = daFilial();
        filtro.putAll(extras);
        filtro.put("size", "100");
        Resposta r = enviar(comParametros(get("/relatorios/" + relatorio + "/busca"), filtro));
        assertThat(r.status).as("busca " + relatorio).isEqualTo(200);
        return r.corpo;
    }

    private long contagem(String relatorio, Map<String, String> extras) throws Exception {
        Map<String, String> filtro = daFilial();
        filtro.putAll(extras);
        Resposta r = enviar(comParametros(get("/relatorios/" + relatorio + "/contagem"), filtro));
        assertThat(r.status).isEqualTo(200);
        return r.corpo.get("total").asLong();
    }

    /** Nº das vendas das linhas devolvidas (campo travado numeroVenda), sem repetir. */
    private List<String> numeros(String relatorio, Map<String, String> extras) throws Exception {
        Map<String, String> filtro = new HashMap<>(extras);
        filtro.putIfAbsent("campos", "numeroVenda"); // em Serviços o nº da venda não é campo travado
        JsonNode pagina = buscar(relatorio, filtro);
        List<String> numeros = new ArrayList<>();
        pagina.get("content").forEach(l -> {
            String v = l.get("numeroVenda").asText();
            if (!numeros.contains(v)) numeros.add(v);
        });
        return numeros;
    }

    private static long total(JsonNode pagina) {
        return pagina.get("totalElements").asLong();
    }

    private MockHttpServletRequestBuilder comParametros(MockHttpServletRequestBuilder pedido, Map<String, String> filtro) {
        filtro.forEach(pedido::param);
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String entrar() throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", vendedor.getLogin(), "senha", SENHA)))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private Resposta enviar(MockHttpServletRequestBuilder pedido) throws Exception {
        pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MvcResult r = mvc.perform(pedido).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Resposta(r.getResponse().getStatus(), texto.isBlank() ? json.createObjectNode() : json.readTree(texto));
    }

    private record Resposta(int status, JsonNode corpo) {
    }
}
