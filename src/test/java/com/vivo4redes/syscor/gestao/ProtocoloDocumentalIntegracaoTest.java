package com.vivo4redes.syscor.gestao;

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
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Gestão documental ligada à API (docs/gestao.md), com filtros, JWT e PostgreSQL reais. Cada teste usa uma filial
 * própria e filtra por ela, para não enxergar as vendas dos outros testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProtocoloDocumentalIntegracaoTest {

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
        clienteId = clientes.save(Cliente.builder().tipoPessoa(TipoPessoa.FISICA).nome("Cliente Doc " + n)
                .cpfCnpj("7" + String.format("%010d", n)).build()).getId();
        filialId = filiais.save(Filial.builder().codigo("DOC" + n).nome("Filial Doc " + n).build()).getId();
        vendedor = usuarios.save(Usuario.builder().nome("Vendedor Doc").login("doc" + n).email("doc" + n + "@teste.local")
                .senhaHash(encoder.encode(SENHA)).primeiroAcesso(false).filial("Filial Doc").cargo("VENDEDOR").build());
        token = entrar();
    }

    // ------------------------------------------------------------------ busca

    @Test
    @DisplayName("Busca pelo número da venda acha a venda lançada, com status BKO Não avaliado e tipo de plano")
    void buscaPorNumeroVenda() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Troca de Aparelho", "POS_INDIVIDUAL", "Pós", "LINHA ADICIONAL VIVO FAMÍLIA");

        JsonNode pagina = buscar(Map.of("numeroVenda", numeroDa(id)));

        assertThat(total(pagina)).isEqualTo(1);
        JsonNode v = pagina.get("content").get(0);
        assertThat(v.get("vendaId").asLong()).isEqualTo(id);
        assertThat(v.get("numeroVenda").asText()).isEqualTo(numeroDa(id));
        assertThat(v.get("statusBko").asText()).as("venda sem avaliação").isEqualTo("Nao_avaliado");
        assertThat(v.get("statusVenda").asText()).isEqualTo("CONCLUIDA");
        assertThat(v.get("tipoPlano").asText()).isEqualTo("POS");
        assertThat(v.get("servico").asText()).isEqualTo("Troca de Aparelho");
        assertThat(v.get("numeroAcesso").asText()).isEqualTo("61 998674575");
        assertThat(v.get("filialNome").asText()).isEqualTo("Filial Doc " + n);
        assertThat(v.get("vendedorNome").asText()).isEqualTo("Vendedor Doc");
        assertThat(v.get("protocoloId").isNull()).as("ainda sem protocolo").isTrue();
    }

    @Test
    @DisplayName("Busca sem resultado: 200 com a lista vazia, e não 404")
    void buscaSemResultado() throws Exception {
        JsonNode pagina = buscar(Map.of("numeroVenda", "999999999"));

        assertThat(total(pagina)).isZero();
        assertThat(pagina.get("content")).isEmpty();
    }

    @Test
    @DisplayName("Só vendas lançadas com item de serviço; canceladas só com incluirCanceladas")
    void soLancadasDeServico() throws Exception {
        long lancada = vendaLancada(null, HOJE);
        servico(lancada, "11911110001", "Alta", "CONTROLE_ALTO_VALOR", "Controle", "CONTROLE");
        long cancelada = venda("CANCELADA", null, HOJE.minusDays(1));
        servico(cancelada, "11911110002", "Alta", "PRE", "Pré", "PRE");
        long aberta = venda("ABERTA", null, HOJE);
        servico(aberta, "11911110003", "Alta", "PRE", "Pré", "PRE");
        long soAcessorio = vendaLancada(null, HOJE);
        jdbc.update("""
                INSERT INTO itens_venda (venda_id, categoria, produto_id, descricao_produto, quantidade, valor_unitario)
                VALUES (?, 'ACESSORIO', 1, 'Capa', 1, 50)""", soAcessorio);

        assertThat(ids(buscar(daFilial()))).containsExactly(lancada);
        Map<String, String> comCanceladas = daFilial();
        comCanceladas.put("incluirCanceladas", "true");
        assertThat(ids(buscar(comCanceladas))).containsExactly(lancada, cancelada);
    }

    @Test
    @DisplayName("Filtro de status BKO: Nao_avaliado inclui a venda sem avaliação; Procedente só a procedente")
    void filtroStatusBko() throws Exception {
        long semAvaliacao = vendaLancada(null, HOJE);
        long naoAvaliado = vendaLancada("Nao_avaliado", HOJE.minusDays(1));
        long procedente = vendaLancada("Procedente", HOJE.minusDays(2));
        for (long id : List.of(semAvaliacao, naoAvaliado, procedente)) {
            servico(id, "1191111" + id, "Alta", "PRE", "Pré", "PRE");
        }

        Map<String, String> fila = daFilial();
        fila.put("statusBko", "Nao_avaliado");
        assertThat(ids(buscar(fila))).containsExactly(semAvaliacao, naoAvaliado);
        Map<String, String> procedentes = daFilial();
        procedentes.put("statusBko", "Procedente");
        assertThat(ids(buscar(procedentes))).containsExactly(procedente);
    }

    @Test
    @DisplayName("Filtros de item: tipo de plano (grupo ou segmento), serviço sem acento, nº de acesso e período")
    void filtrosDeItem() throws Exception {
        long pos = vendaLancada(null, HOJE);
        servico(pos, "61 998674575", "Troca de numero", "VIM_20GB", null, "VIM 20GB");
        long pre = vendaLancada(null, HOJE);
        servico(pre, "61 988887777", "Alta", null, "Pré", "PRE");
        long antiga = vendaLancada(null, HOJE.minusDays(40));
        servico(antiga, "61 977776666", "Alta", "PRE", "Pré", "PRE");

        Map<String, String> soPre = daFilial();
        soPre.put("tiposPlano", "PRE");
        assertThat(ids(buscar(soPre))).containsExactlyInAnyOrder(pre, antiga);

        Map<String, String> controleEPos = daFilial();
        controleEPos.put("tiposPlano", "CONTROLE,POS");
        assertThat(ids(buscar(controleEPos))).as("VIM conta como Pós").containsExactly(pos);

        Map<String, String> servico = daFilial();
        servico.put("servicos", "Troca de número");
        assertThat(ids(buscar(servico))).as("'Troca de número' = 'Troca de numero'").containsExactly(pos);

        Map<String, String> acesso = daFilial();
        acesso.put("numeroAcesso", "(61) 99867-4575");
        assertThat(ids(buscar(acesso))).containsExactly(pos);

        Map<String, String> periodo = daFilial();
        periodo.put("dataInicio", HOJE.minusDays(30).toString());
        periodo.put("dataFim", HOJE.toString());
        assertThat(ids(buscar(periodo))).containsExactlyInAnyOrder(pos, pre);
    }

    // ------------------------------------------------------------------ salvar

    @Test
    @DisplayName("Salvar protocolo de venda ABERTA ou CANCELADA: 422 com a mensagem")
    void salvarForaDeLancadaRecusa() throws Exception {
        long aberta = venda("ABERTA", null, HOJE);
        long cancelada = venda("CANCELADA", null, HOJE);

        Resposta r = enviar(put("/documental/protocolos/{id}", aberta), Map.of("numeroProtocoloGed", "GED-1"));
        assertThat(r.status).isEqualTo(422);
        assertThat(r.corpo.get("mensagem").asText())
                .isEqualTo("Só vendas lançadas recebem protocolo documental (venda #" + numeroDa(aberta) + " está ABERTA).");
        assertThat(enviar(put("/documental/protocolos/{id}", cancelada), Map.of("numeroProtocoloGed", "GED-1")).status).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_protocolo_documental WHERE venda_id IN (?, ?)",
                Integer.class, aberta, cancelada)).isZero();
    }

    @Test
    @DisplayName("Salvar protocolo de venda lançada: grava e a busca acha por GED e por líder de equipe")
    void salvarEBuscarPorProtocolo() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "11911112222", "Alta", "PRE", "Pré", "PRE");
        String ged = "GED-" + n;

        Resposta r = enviar(put("/documental/protocolos/{id}", id), Map.of("numeroProtocoloGed", ged,
                "liderEquipe", "José Líder", "statusBko", "Em_avaliacao_pelo_BKO", "dataDigitalizacao", HOJE.toString()));

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("statusBko").asText()).isEqualTo("Em_avaliacao_pelo_BKO");
        JsonNode detalhe = enviar(get("/documental/protocolos/{id}", id), null).corpo;
        assertThat(detalhe.get("numeroProtocoloGed").asText()).isEqualTo(ged);
        assertThat(detalhe.get("dataDigitalizacao").asText()).isEqualTo(HOJE.toString());
        assertThat(ids(buscar(Map.of("numeroProtocoloGed", ged.toLowerCase())))).containsExactly(id);
        Map<String, String> lider = daFilial();
        lider.put("liderEquipe", "jose lider");
        assertThat(ids(buscar(lider))).containsExactly(id);
    }

    // ------------------------------------------------------------------ importação

    @Test
    @DisplayName("Importação com uma linha boa e uma ruim: grava a boa e lista o erro da outra")
    void importacaoBoaERuim() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Alta", "PRE", "Pré", "PRE");
        String csv = "﻿NUMERO_VENDA;PROTOCOLO_GED;STATUS_BKO;NUMERO_ACESSO;DATA_DIGITALIZACAO\n"
                + numeroDa(id) + ";GED-IMP-" + n + ";Procedente;(61) 99867-4575;01/11/2026\n"
                + "999999999;GED-X;Procedente;;01/11/2026\n";

        JsonNode r = importar(csv);

        assertThat(r.get("total").asInt()).isEqualTo(2);
        assertThat(r.get("sucessos").asInt()).isEqualTo(1);
        assertThat(r.get("erros")).hasSize(1);
        assertThat(r.get("erros").get(0).get("linha").asInt()).isEqualTo(3);
        assertThat(r.get("erros").get(0).get("numeroVenda").asText()).isEqualTo("999999999");
        assertThat(r.get("erros").get(0).get("motivo").asText()).isEqualTo("Venda 999999999 não encontrada.");

        JsonNode detalhe = enviar(get("/documental/protocolos/{id}", id), null).corpo;
        assertThat(detalhe.get("numeroProtocoloGed").asText()).isEqualTo("GED-IMP-" + n);
        assertThat(detalhe.get("statusBko").asText()).isEqualTo("Procedente");
        assertThat(detalhe.get("dataDigitalizacao").asText()).isEqualTo("2026-11-01");
    }

    @Test
    @DisplayName("Importação com vírgula: acesso de outra venda, data e status inválidos viram erro; o resto do protocolo fica")
    void importacaoValidaCadaLinha() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Alta", "PRE", "Pré", "PRE");
        assertThat(enviar(put("/documental/protocolos/{id}", id),
                Map.of("numeroProtocoloGed", "GED-ANTES", "liderEquipe", "Maria")).status).isEqualTo(200);
        String numero = numeroDa(id);
        String csv = "NUMERO_VENDA,PROTOCOLO_GED,STATUS_BKO,NUMERO_ACESSO,DATA_DIGITALIZACAO\n"
                + numero + ",GED-1,,11 900000000,\n"
                + numero + ",GED-2,,,31/02/2026\n"
                + numero + ",GED-3,Talvez,,\n"
                + "abc,GED-4,,,\n"
                + numero + ",,Procedente,,\n";

        JsonNode r = importar(csv);

        assertThat(r.get("total").asInt()).isEqualTo(5);
        assertThat(r.get("sucessos").asInt()).isEqualTo(1);
        assertThat(r.get("erros").findValuesAsText("linha")).containsExactly("2", "3", "4", "5");
        JsonNode detalhe = enviar(get("/documental/protocolos/{id}", id), null).corpo;
        assertThat(detalhe.get("numeroProtocoloGed").asText()).as("GED vazio na planilha não apaga").isEqualTo("GED-ANTES");
        assertThat(detalhe.get("liderEquipe").asText()).as("campo fora da planilha fica").isEqualTo("Maria");
        assertThat(detalhe.get("statusBko").asText()).isEqualTo("Procedente");
    }

    // ------------------------------------------------------------------ exportação

    @Test
    @DisplayName("Exportação: o período com a venda e status Nao_avaliado traz a venda; com Procedente, só o cabeçalho")
    void exportacaoPorStatusBko() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Troca de Aparelho", "POS_INDIVIDUAL", "Pós", "PLANO POS");
        Map<String, String> filtro = daFilial();
        filtro.put("dataInicio", HOJE.withDayOfMonth(1).toString());
        filtro.put("dataFim", HOJE.toString());
        filtro.put("statusBko", "Nao_avaliado");

        MvcResult r = mvc.perform(comParametros(get("/documental/protocolos/exportacao"), filtro)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).startsWith("text/csv");
        assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains(
                "Relatorio_Gestao_Documental_" + HOJE.withDayOfMonth(1) + "_a_" + HOJE + ".csv");
        String csv = new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Data/Hora;Nº Venda;Filial;Cliente;CPF/CNPJ;Nº de acesso;Serviço;Tipo de plano;"
                + "Plano;Protocolo GED;Vendedor;Status BKO;Valor total\r\n");
        String[] linhas = csv.split("\r\n");
        assertThat(linhas).hasSize(2);
        assertThat(linhas[1]).contains(";" + numeroDa(id) + ";Filial Doc " + n + ";", ";Troca de Aparelho;Pós;PLANO POS;",
                ";Vendedor Doc;Não avaliado;1000,00");

        filtro.put("statusBko", "Procedente");
        String vazio = mvc.perform(comParametros(get("/documental/protocolos/exportacao"), filtro)).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(vazio.split("\r\n")).hasSize(1);
    }

    @Test
    @DisplayName("Exportação sem período ou com mais de 93 dias: 422")
    void exportacaoPeriodo() throws Exception {
        assertThat(mvc.perform(comParametros(get("/documental/protocolos/exportacao"), daFilial()))
                .andReturn().getResponse().getStatus()).isEqualTo(422);

        Map<String, String> longo = daFilial();
        longo.put("dataInicio", HOJE.minusDays(93).toString());
        longo.put("dataFim", HOJE.toString());
        assertThat(mvc.perform(comParametros(get("/documental/protocolos/exportacao"), longo))
                .andReturn().getResponse().getStatus()).as("94 dias").isEqualTo(422);

        longo.put("dataInicio", HOJE.minusDays(92).toString());
        assertThat(mvc.perform(comParametros(get("/documental/protocolos/exportacao"), longo))
                .andReturn().getResponse().getStatus()).as("93 dias").isEqualTo(200);
    }

    // ------------------------------------------------------------------ filiais do relatório

    @Test
    @DisplayName("GET /filiais traz só as ativas; com incluirInativas=true, também as inativas")
    void filiaisInativasNoRelatorio() throws Exception {
        Filial inativa = filiais.save(Filial.builder().codigo("INA" + n).nome("Filial Inativa " + n).ativo(false).build());

        JsonNode ativas = enviar(get("/filiais"), null).corpo;
        JsonNode todas = enviar(get("/filiais").param("incluirInativas", "true"), null).corpo;

        assertThat(ativas.findValuesAsText("id")).contains(String.valueOf(filialId)).doesNotContain(String.valueOf(inativa.getId()));
        assertThat(todas.findValuesAsText("id")).contains(String.valueOf(filialId), String.valueOf(inativa.getId()));
    }

    // ------------------------------------------------------------------ apoio

    private long vendaLancada(String avaliacao, LocalDate dia) {
        return venda("CONCLUIDA", avaliacao, dia);
    }

    /** Venda inserida por SQL; finalizado_em gravado pelo Hibernate, que converte o Instant como na leitura. */
    private long venda(String status, String avaliacao, LocalDate dia) {
        long id = jdbc.queryForObject("""
                INSERT INTO vendas (cliente_id, filial_id, usuario_id, numero_venda, status, avaliacao_procedencia, valor_total)
                VALUES (?, ?, ?, nextval('seq_numero_venda'), ?, ?, 1000) RETURNING id
                """, Long.class, clienteId, filialId, vendedor.getId(), status, avaliacao);
        Instant lancamento = dia.atTime(12, 0).atZone(ZONA).toInstant().plusSeconds(id);
        transacao.executeWithoutResult(s -> em.createQuery("update Venda v set v.finalizadoEm = :t where v.id = :id")
                .setParameter("t", lancamento).setParameter("id", id).executeUpdate());
        return id;
    }

    private void servico(long vendaId, String acesso, String tipoServico, String grupo, String segmento, String plano) {
        jdbc.update("""
                INSERT INTO itens_venda (venda_id, categoria, descricao_produto, quantidade, valor_unitario,
                                         numero_acesso, tipo_servico, grupo_plano, segmento, plano)
                VALUES (?, 'SERVICO_VIVO', 'Serviço', 1, 0, ?, ?, ?, ?, ?)
                """, vendaId, acesso, tipoServico, grupo, segmento, plano);
    }

    private String numeroDa(long vendaId) {
        return String.valueOf(jdbc.queryForObject("SELECT numero_venda FROM vendas WHERE id = ?", Long.class, vendaId));
    }

    private Map<String, String> daFilial() {
        Map<String, String> filtro = new HashMap<>();
        filtro.put("filialIds", String.valueOf(filialId));
        return filtro;
    }

    private JsonNode buscar(Map<String, String> filtro) throws Exception {
        Resposta r = enviar(comParametros(get("/documental/protocolos/busca"), filtro), null);
        assertThat(r.status).as("busca").isEqualTo(200);
        return r.corpo;
    }

    private static long total(JsonNode pagina) {
        return pagina.get("page").get("totalElements").asLong();
    }

    private static List<Long> ids(JsonNode pagina) {
        List<Long> ids = new ArrayList<>();
        pagina.get("content").forEach(v -> ids.add(v.get("vendaId").asLong()));
        return ids;
    }

    private JsonNode importar(String csv) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "protocolos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        Resposta r = enviar(multipart("/documental/protocolos/importacao").file(arquivo), null);
        assertThat(r.status).as("importação").isEqualTo(200);
        return r.corpo;
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

    private Resposta enviar(MockHttpServletRequestBuilder pedido, Object corpo) throws Exception {
        pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (corpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        }
        MvcResult r = mvc.perform(pedido).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Resposta(r.getResponse().getStatus(), texto.isBlank() ? json.createObjectNode() : json.readTree(texto));
    }

    private record Resposta(int status, JsonNode corpo) {
    }
}
