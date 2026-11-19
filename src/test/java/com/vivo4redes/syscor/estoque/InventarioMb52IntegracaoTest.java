package com.vivo4redes.syscor.estoque;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.service.diagnostico.DiagnosticoIaClient;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Conferência por SKU com a MB52 (RN.82) de ponta a ponta: HTTP + JWT + Postgres real, planilhas sintéticas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InventarioMb52IntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";
    private static final String MATERIAL_APARELHO = "22018137";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FilialRepository filiais;
    @Autowired UsuarioRepository usuarios;
    @Autowired EstoqueGeralRepository estoque;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean DiagnosticoIaClient ia;

    private int n;
    private Filial loja;
    private String centro;
    private String token;
    private String base;
    /** SKUs do cenário: um por resultado. */
    private String skuCabo, skuSoSap, skuSoSistema, skuAbaixo, skuDifereIq09, skuConferido;

    @BeforeEach
    void preparar() throws Exception {
        n = SEQUENCIA.incrementAndGet();
        centro = "M" + n;
        loja = filiais.save(Filial.builder().codigo("MB" + n).nome("Loja MB52 " + n).centroSap(centro).build());
        Usuario usuario = usuarios.save(Usuario.builder().nome("Conferente " + n).login("mb52_" + n)
                .email("mb52_" + n + "@teste.local").senhaHash(encoder.encode(SENHA)).primeiroAcesso(false)
                .filial(loja.getNome()).cargo("VENDEDOR").build());
        token = entrar(usuario);
        when(ia.disponivel()).thenReturn(false);

        base = String.format("36%03d", n);
        skuCabo = "5" + n + "01";
        skuSoSap = "5" + n + "02";
        skuSoSistema = "5" + n + "03";
        skuAbaixo = "5" + n + "04";
        skuDifereIq09 = "5" + n + "05";
        skuConferido = "5" + n + "06";

        // Aparelhos do material 22018137: 2 no sistema (3 na IQ09) -> MB52 3 = SAP_ACIMA_SISTEMA.
        aparelho(serial(1));
        aparelho(serial(2));
        // MB52_DIFERE_IQ09: 2 seriais na IQ09, 1 no sistema, MB52 1.
        aparelho(serial(4));
        // CONFERIDO: 1 na IQ09, 1 no sistema, MB52 1.
        aparelho(serial(6));
        // Aparelho só no sistema, sem material e sem tabela de preços: SKU não identificado.
        estoque.save(EstoqueGeral.builder().serial(serial(7)).modeloComercial("APARELHO SEM TABELA " + n)
                .filialEstoque(loja.getNome()).status(StatusEstoque.DISPONIVEL).build());
        // Acessórios com quantidade (RN.62).
        acessorio("CABO" + n, "0000" + skuCabo, 5);
        acessorio("CAPA" + n, skuSoSistema, 2);
        acessorio("FONE" + n, skuAbaixo, 2);
    }

    private String serial(int i) {
        return base + String.format("%010d", i);
    }

    private MockMultipartFile iq09() {
        return Iq09Sintetica.nova()
                .linhaMaterial(MATERIAL_APARELHO, serial(1), centro)
                .linhaMaterial(MATERIAL_APARELHO, serial(2), centro)
                .linhaMaterial(MATERIAL_APARELHO, serial(3), centro)
                .linhaMaterial(skuDifereIq09, serial(4), centro)
                .linhaMaterial(skuDifereIq09, serial(5), centro)
                .linhaMaterial(skuConferido, serial(6), centro)
                .arquivo();
    }

    /** MB52 da loja + linha de outro centro + total; o aparelho vem em duas linhas (lotes) e com zeros à esquerda. */
    private Mb52Sintetica mb52(int livreAparelho) {
        return Mb52Sintetica.nova()
                .linha("0000000" + MATERIAL_APARELHO, centro, "RPAR", livreAparelho - 1, (livreAparelho - 1) * 1000)
                .linha(MATERIAL_APARELHO, centro, "RPAR", 1, 1000)
                .linha(skuCabo, centro, "RPAR", 5, 50)
                .linha(skuSoSap, centro, "RPAR", 2, 30)
                .linha(skuAbaixo, centro, "RPAR", 1, 20)
                .linha(skuDifereIq09, centro, "RPAR", 1, 900)
                .linha(skuConferido, centro, "RPAR", 1, 800)
                .linha(skuSoSap, "OUTRO" + n, "RPAR", 99, 0)
                .bruta("Total", "", "", "", "", 999);
    }

    @Test
    @DisplayName("Inventário sem MB52 fica igual ao de hoje: mb52.importada=false e nenhuma linha por SKU")
    void semMb52() throws Exception {
        JsonNode resumo = json(enviar(multipart("/estoque/inventario").file(iq09())
                .param("filialId", String.valueOf(loja.getId()))), 201);

        assertThat(resumo.get("mb52").get("importada").asBoolean()).isFalse();
        assertThat(resumo.get("totais").get("pendentes").asInt()).isEqualTo(10);
        long id = resumo.get("execucaoId").asLong();
        JsonNode skus = skus(id, "");
        assertThat(skus.get("totalItens").asLong()).isZero();
        assertThat(bipar(id, serial(1)).get("resultado").asText()).isEqualTo("CONFERIDO");
        assertThat(skus(id, "").get("totalItens").asLong()).isZero();
    }

    @Test
    @DisplayName("MB52 na abertura: filtra o centro, soma duplicados, ignora total e classifica um SKU de cada resultado")
    void umDeCada() throws Exception {
        JsonNode resumo = json(enviar(multipart("/estoque/inventario").file(iq09()).file(mb52(3).arquivo())
                .param("filialId", String.valueOf(loja.getId()))), 201);
        long id = resumo.get("execucaoId").asLong();

        JsonNode mb52 = resumo.get("mb52");
        assertThat(mb52.get("importada").asBoolean()).isTrue();
        assertThat(mb52.get("linhasDaFilial").asInt()).isEqualTo(7);
        assertThat(mb52.get("linhasOutroCentro").asInt()).isEqualTo(1);
        assertThat(mb52.get("linhasIgnoradas").asInt()).isEqualTo(1);
        assertThat(mb52.get("por").asText()).isEqualTo("Conferente " + n);

        Map<String, JsonNode> porSku = porSku(id);
        assertThat(resultado(porSku, MATERIAL_APARELHO)).isEqualTo("SAP_ACIMA_SISTEMA");
        assertThat(porSku.get(MATERIAL_APARELHO).get("qtdMb52Livre").decimalValue()).isEqualByComparingTo("3");
        assertThat(porSku.get(MATERIAL_APARELHO).get("qtdIq09").asInt()).isEqualTo(3);
        assertThat(porSku.get(MATERIAL_APARELHO).get("qtdSistema").asInt()).isEqualTo(2);
        assertThat(porSku.get(MATERIAL_APARELHO).get("valorDiferencaEstimado").decimalValue()).isEqualByComparingTo("1000");
        assertThat(resultado(porSku, skuCabo)).isEqualTo("CONFERIDO");
        assertThat(porSku.get(skuCabo).get("qtdSistema").asInt()).as("acessório soma a quantidade").isEqualTo(5);
        assertThat(resultado(porSku, skuSoSap)).isEqualTo("SO_NO_SAP");
        assertThat(porSku.get(skuSoSap).get("qtdMb52Livre").decimalValue()).as("linha de outro centro fora").isEqualByComparingTo("2");
        assertThat(resultado(porSku, skuSoSistema)).isEqualTo("SO_NO_SISTEMA");
        assertThat(resultado(porSku, skuAbaixo)).isEqualTo("SAP_ABAIXO_SISTEMA");
        assertThat(resultado(porSku, skuDifereIq09)).isEqualTo("MB52_DIFERE_IQ09");
        assertThat(resultado(porSku, skuConferido)).isEqualTo("CONFERIDO");
        assertThat(resultado(porSku, "NAO_IDENTIFICADO")).isEqualTo("SKU_NAO_IDENTIFICADO");
        assertThat(porSku.get("NAO_IDENTIFICADO").get("qtdSistema").asInt()).isEqualTo(1);

        JsonNode totais = detalhe(id).get("mb52").get("totaisPorResultado");
        assertThat(totais.get("CONFERIDO").asInt()).isEqualTo(2);
        assertThat(totais.get("SKU_NAO_IDENTIFICADO").asInt()).isEqualTo(1);

        JsonNode soDiferencas = json(enviar(get("/estoque/inventario/" + id + "/skus")
                .param("somenteDiferenca", "true").param("ordenacao", "valor")), 200);
        assertThat(soDiferencas.get("totalItens").asInt()).isEqualTo(6);
        assertThat(soDiferencas.get("conteudo").get(0).get("sku").asText()).as("maior valor primeiro").isEqualTo(MATERIAL_APARELHO);

        JsonNode busca = json(enviar(get("/estoque/inventario/" + id + "/skus").param("busca", skuCabo)), 200);
        assertThat(busca.get("totalItens").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Bipagem depois da MB52 sobe a contagem do SKU; acessório com 5 bipado 1 vez diverge até a contagem manual")
    void bipagemEContagemManual() throws Exception {
        long id = abrirComIq09();
        importarMb52(id, mb52(3));

        bipar(id, serial(1));
        bipar(id, "CABO" + n);
        Map<String, JsonNode> porSku = porSku(id);
        assertThat(porSku.get(MATERIAL_APARELHO).get("qtdBipada").asInt()).isEqualTo(1);
        assertThat(resultado(porSku, MATERIAL_APARELHO)).isEqualTo("CONTAGEM_DIVERGENTE");
        assertThat(resultado(porSku, skuCabo)).isEqualTo("CONTAGEM_DIVERGENTE");

        long caboId = porSku.get(skuCabo).get("id").asLong();
        MvcResult semJustificativa = enviar(patch("/estoque/inventario/" + id + "/skus/" + caboId + "/contagem")
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":5}"));
        assertThat(semJustificativa.getResponse().getStatus()).isEqualTo(422);

        JsonNode contado = contar(id, caboId, "5");
        assertThat(contado.get("resultado").asText()).isEqualTo("CONFERIDO");
        assertThat(contado.get("contadoPor").asText()).isEqualTo("Conferente " + n);
        assertThat(contar(id, caboId, "4").get("resultado").asText()).isEqualTo("CONTAGEM_DIVERGENTE");

        // Sobra bipada fora da lista: entra no SKU não identificado; desfazer tira de novo.
        long sobra = bipar(id, "SOLTO" + n).get("itemId").asLong();
        assertThat(porSku(id).get("NAO_IDENTIFICADO").get("qtdBipada").asInt()).isEqualTo(1);
        json(enviar(delete("/estoque/inventario/" + id + "/bipagens/" + sobra)), 200);
        assertThat(porSku(id).get("NAO_IDENTIFICADO").get("qtdBipada").asInt()).isZero();

        // Desfazer a bipagem de um esperado também atualiza o SKU.
        long item = bipar(id, serial(2)).get("itemId").asLong();
        assertThat(porSku(id).get(MATERIAL_APARELHO).get("qtdBipada").asInt()).isEqualTo(2);
        json(enviar(delete("/estoque/inventario/" + id + "/bipagens/" + item)), 200);
        assertThat(porSku(id).get(MATERIAL_APARELHO).get("qtdBipada").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reimportar a MB52 substitui a anterior, guarda o histórico e preserva a contagem manual")
    void reimportar() throws Exception {
        long id = abrirComIq09();
        importarMb52(id, mb52(3));
        long caboId = porSku(id).get(skuCabo).get("id").asLong();
        contar(id, caboId, "4");

        JsonNode segunda = importarMb52(id, mb52(2));

        assertThat(segunda.get("daFilial").asInt()).isEqualTo(7);
        Map<String, JsonNode> porSku = porSku(id);
        assertThat(porSku.get(MATERIAL_APARELHO).get("qtdMb52Livre").decimalValue()).isEqualByComparingTo("2");
        assertThat(resultado(porSku, MATERIAL_APARELHO)).as("MB52 2 = sistema 2, IQ09 3").isEqualTo("MB52_DIFERE_IQ09");
        assertThat(porSku.get(skuCabo).get("qtdContagemManual").decimalValue()).isEqualByComparingTo("4");
        Integer historico = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tb_inventario_mb52_importacao WHERE execucao_id = ?", Integer.class, id);
        assertThat(historico).isEqualTo(2);
    }

    @Test
    @DisplayName("Exportação CSV por SKU; fechar grava os totais; MB52 em execução fechada: 422")
    void exportarEFechar() throws Exception {
        long id = abrirComIq09();
        importarMb52(id, mb52(3));

        MvcResult csv = enviar(get("/estoque/inventario/" + id + "/exportacao-sku"));
        String conteudo = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(conteudo).startsWith("﻿SKU;").contains("SAP_ACIMA_SISTEMA").contains("NÃO IDENTIFICADO")
                .contains("Valor da diferença (estimativa)").contains(skuSoSap + ";");

        for (JsonNode item : json(enviar(get("/estoque/inventario/" + id).param("resultado", "PENDENTE").param("tamanho", "100")), 200)
                .get("itens").get("conteudo")) {
            enviar(patch("/estoque/inventario/" + id + "/itens/" + item.get("id").asLong() + "/justificativa")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Não localizado\"}"));
        }
        JsonNode fechado = json(enviar(post("/estoque/inventario/" + id + "/fechar")), 200);
        assertThat(fechado.get("status").asText()).isEqualTo("FECHADO");
        Integer conferidos = jdbcTemplate.queryForObject(
                "SELECT total_sku_conferido FROM tb_inventario_execucao WHERE id = ?", Integer.class, id);
        assertThat(conferidos).isEqualTo(2);

        MvcResult depois = enviar(multipart("/estoque/inventario/" + id + "/mb52").file(mb52(3).arquivo("arquivo")));
        assertThat(depois.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("Planilha sem as colunas da MB52: 422 com as colunas esperadas")
    void arquivoErrado() throws Exception {
        long id = abrirComIq09();
        MvcResult r = enviar(multipart("/estoque/inventario/" + id + "/mb52")
                .file(Mb52Sintetica.nova().cabecalho("Material", "Centro").bruta("A", centro).arquivo("arquivo")));

        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Utilização livre");
    }

    // ---------------------------------------------------------------- apoio

    private long abrirComIq09() throws Exception {
        return json(enviar(multipart("/estoque/inventario").file(iq09()).param("filialId", String.valueOf(loja.getId()))), 201)
                .get("execucaoId").asLong();
    }

    private JsonNode importarMb52(long id, Mb52Sintetica planilha) throws Exception {
        return json(enviar(multipart("/estoque/inventario/" + id + "/mb52").file(planilha.arquivo("arquivo"))), 200);
    }

    private JsonNode contar(long id, long skuId, String quantidade) throws Exception {
        return json(enviar(patch("/estoque/inventario/" + id + "/skus/" + skuId + "/contagem")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":" + quantidade + ",\"justificativa\":\"Contado na prateleira\"}")), 200);
    }

    private JsonNode skus(long id, String busca) throws Exception {
        return json(enviar(get("/estoque/inventario/" + id + "/skus").param("busca", busca).param("tamanho", "100")), 200);
    }

    /** SKU -> linha; a linha agrupada sem SKU fica em "NAO_IDENTIFICADO". */
    private Map<String, JsonNode> porSku(long id) throws Exception {
        Map<String, JsonNode> mapa = new HashMap<>();
        for (JsonNode linha : skus(id, "").get("conteudo")) {
            mapa.put(linha.get("sku").isNull() ? "NAO_IDENTIFICADO" : linha.get("sku").asText(), linha);
        }
        return mapa;
    }

    private static String resultado(Map<String, JsonNode> porSku, String sku) {
        assertThat(porSku).as("SKU " + sku).containsKey(sku);
        return porSku.get(sku).get("resultado").asText();
    }

    private JsonNode bipar(long id, String serial) throws Exception {
        return json(enviar(post("/estoque/inventario/" + id + "/bipagens").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("serial", serial)))), 200);
    }

    private JsonNode detalhe(long id) throws Exception {
        return json(enviar(get("/estoque/inventario/" + id)), 200);
    }

    private MvcResult enviar(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult r, int status) throws Exception {
        String corpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getResponse().getStatus()).as(corpo).isEqualTo(status);
        return json.readTree(corpo);
    }

    private String entrar(Usuario usuario) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", usuario.getLogin(), "senha", SENHA)))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private void aparelho(String serial) {
        estoque.save(EstoqueGeral.builder().serial(serial).modeloComercial("APARELHO SINTETICO").filialEstoque(loja.getNome())
                .status(StatusEstoque.DISPONIVEL).build());
    }

    private void acessorio(String serial, String codigo, int quantidade) {
        estoque.save(EstoqueGeral.builder().serial(serial).codigoAcessorio(codigo).modeloComercial("ACESSORIO " + codigo)
                .filialEstoque(loja.getNome()).quantidade(quantidade).status(StatusEstoque.DISPONIVEL).build());
    }
}
