package com.vivo4redes.syscor.estoque;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.service.diagnostico.CausaProvavelIa;
import com.vivo4redes.syscor.estoque.service.diagnostico.ConfiancaIa;
import com.vivo4redes.syscor.estoque.service.diagnostico.DiagnosticoIaClient;
import com.vivo4redes.syscor.estoque.service.diagnostico.ItemParaDiagnostico;
import com.vivo4redes.syscor.estoque.service.diagnostico.RespostaDiagnosticoIa;
import com.vivo4redes.syscor.estoque.service.diagnostico.RespostaIaInvalidaException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Inventário por filial de ponta a ponta (HTTP + JWT + Postgres real): abrir com IQ09 sintética de 2 centros, bipar,
 * fechar, exportar e diagnosticar com um cliente de IA falso (nenhuma chamada real).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InventarioIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FilialRepository filiais;
    @Autowired UsuarioRepository usuarios;
    @Autowired EstoqueGeralRepository estoque;
    @Autowired PasswordEncoder encoder;

    @MockBean DiagnosticoIaClient ia;

    private int n;
    private Filial loja;
    private String centro;
    private String token;
    /** Seriais do teste (únicos por execução da classe). */
    private String conferido1, conferido2, conferido3, soNaIq09, soNoSistema, rlib, vendido, outraLoja, deOutroCentro;

    @BeforeEach
    void preparar() throws Exception {
        n = SEQUENCIA.incrementAndGet();
        centro = "C" + n;
        loja = filiais.save(Filial.builder().codigo("INV" + n).nome("Loja Inventario " + n).centroSap(centro).build());
        Usuario usuario = usuarios.save(Usuario.builder().nome("Estoquista " + n).login("estoque" + n)
                .email("estoque" + n + "@teste.local").senhaHash(encoder.encode(SENHA)).primeiroAcesso(false)
                .filial(loja.getNome()).cargo("VENDEDOR").build());
        token = entrar(usuario);

        String base = String.format("35%03d", n);
        conferido1 = base + "0000000001";
        conferido2 = base + "0000000002";
        conferido3 = base + "0000000003";
        soNaIq09 = base + "0000000004";
        soNoSistema = base + "0000000005";
        rlib = base + "0000000006";
        vendido = base + "0000000007";
        outraLoja = base + "0000000008";
        deOutroCentro = base + "0000000009";

        for (String s : List.of(conferido1, conferido2, conferido3, soNoSistema)) {
            unidade(s, loja.getNome(), StatusEstoque.DISPONIVEL);
        }
        unidade(vendido, loja.getNome(), StatusEstoque.VENDIDO);
        unidade(outraLoja, "Outra Loja " + n, StatusEstoque.DISPONIVEL);
        when(ia.disponivel()).thenReturn(true);
    }

    /** IQ09 sintética com 2 centros: o da loja e um centro sem filial cadastrada. */
    private MockMultipartFile iq09() {
        return Iq09Sintetica.nova()
                .linha(conferido1, centro)
                .linha(conferido2, centro)
                .linha(conferido3, centro)
                .linha(soNaIq09, centro)
                .linha(rlib, centro, "RLIB", "DEPS")
                .linha(vendido, centro)
                .linha(deOutroCentro, "X" + n)
                .linha("", centro)
                .arquivo();
    }

    @Test
    @DisplayName("IQ09 com 2 centros: só o centro da filial entra; o resumo mostra as linhas ignoradas e o centro sem filial")
    void soOCentroDaFilial() throws Exception {
        JsonNode resumo = abrir();

        assertThat(resumo.get("status").asText()).isEqualTo("ABERTO");
        assertThat(resumo.get("totalLinhasSap").asInt()).isEqualTo(8);
        assertThat(resumo.get("linhasIgnoradasOutroCentro").asInt()).isEqualTo(1);
        assertThat(resumo.get("linhasSemSerial").asInt()).isEqualTo(1);
        assertThat(resumo.get("centrosSemFilial").toString()).contains("X" + n);
        // Esperados: 4 da IQ09 (ADEP) + vendido (IQ09) + 1 só do sistema; RLIB fica fora.
        JsonNode totais = resumo.get("totais");
        assertThat(totais.get("pendentes").asInt()).isEqualTo(5);
        assertThat(totais.get("porResultado").get("VENDIDO_EM_ESTOQUE").asInt()).isEqualTo(1);
        assertThat(totais.get("porResultado").get("FORA_ESTOQUE_SAP").asInt()).isEqualTo(1);
        assertThat(resumo.toString()).doesNotContain(deOutroCentro);
    }

    @Test
    @DisplayName("Bipar 3 esperados: CONFERIDO e pendentes caem 3; fora da IQ09 = SOBRA_FISICA; de novo = jaBipado sem contar duas vezes")
    void bipagens() throws Exception {
        long id = abrir().get("execucaoId").asLong();

        for (String s : List.of(conferido1, conferido2, conferido3)) {
            assertThat(bipar(id, s).get("resultado").asText()).isEqualTo("CONFERIDO");
        }
        JsonNode sobra = bipar(id, outraLoja);
        assertThat(sobra.get("resultado").asText()).isEqualTo("SOBRA_FISICA");
        assertThat(sobra.get("mensagem").asText()).contains("Outra Loja " + n);

        JsonNode deNovo = bipar(id, conferido1);
        assertThat(deNovo.get("jaBipado").asBoolean()).isTrue();
        assertThat(deNovo.get("mensagem").asText()).startsWith("Já bipado às").contains("Estoquista " + n);
        assertThat(deNovo.get("totais").get("conferidos").asInt()).isEqualTo(3);
        assertThat(deNovo.get("totais").get("pendentes").asInt()).isEqualTo(2);
        assertThat(deNovo.get("totais").get("bipados").asInt()).isEqualTo(4);

        JsonNode statusSap = bipar(id, rlib);
        assertThat(statusSap.get("resultado").asText()).isEqualTo("STATUS_SAP_DIVERGENTE");
        assertThat(detalhe(id).get("status").asText()).isEqualTo("EM_CONTAGEM");
    }

    @Test
    @DisplayName("Serial com apóstrofo e espaços bate com o esperado")
    void serialComApostrofo() throws Exception {
        long id = abrir().get("execucaoId").asLong();
        String sujo = "'" + conferido1.substring(0, 5) + " " + conferido1.substring(5) + " ";

        assertThat(bipar(id, sujo).get("resultado").asText()).isEqualTo("CONFERIDO");
    }

    @Test
    @DisplayName("Desfazer: esperado volta a PENDENTE; sobra que só existia pela leitura some")
    void desfazer() throws Exception {
        long id = abrir().get("execucaoId").asLong();
        long conferido = bipar(id, conferido1).get("itemId").asLong();
        long sobra = bipar(id, "SERIALSOLTO" + n).get("itemId").asLong();

        JsonNode totais = json(mvc.perform(autenticado(delete("/estoque/inventario/" + id + "/bipagens/" + conferido))).andReturn(), 200);
        assertThat(totais.get("pendentes").asInt()).isEqualTo(5);
        json(mvc.perform(autenticado(delete("/estoque/inventario/" + id + "/bipagens/" + sobra))).andReturn(), 200);

        assertThat(detalhe(id).toString()).doesNotContain("SERIALSOLTO" + n);
    }

    @Test
    @DisplayName("Fechar com pendente sem justificativa: 422. Justificado: FALTA/SO_NO_SISTEMA, diagnóstico gravado e CSV completo")
    void fecharComJustificativa() throws Exception {
        long id = abrir().get("execucaoId").asLong();
        bipar(id, conferido1);
        bipar(id, conferido2);
        bipar(id, conferido3);

        MvcResult recusa = mvc.perform(autenticado(post("/estoque/inventario/" + id + "/fechar"))).andReturn();
        assertThat(recusa.getResponse().getStatus()).isEqualTo(422);
        assertThat(recusa.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2 itens pendentes");

        for (JsonNode item : detalhe(id, "PENDENTE").get("itens").get("conteudo")) {
            mvc.perform(autenticado(patch("/estoque/inventario/" + id + "/itens/" + item.get("id").asLong() + "/justificativa")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Não localizado fisicamente\"}")))
                    .andReturn();
        }
        when(ia.diagnosticar(anyList())).thenAnswer(inv -> {
            List<ItemParaDiagnostico> lote = inv.getArgument(0);
            return new RespostaDiagnosticoIa(lote.stream().map(i -> new RespostaDiagnosticoIa.Item(i.imei(),
                    CausaProvavelIa.POSSIVEL_PERDA_OU_FURTO, ConfiancaIa.BAIXA, "Registrar ocorrência.")).toList(),
                    "Duas faltas sem explicação nos dados.");
        });

        JsonNode fechado = json(mvc.perform(autenticado(post("/estoque/inventario/" + id + "/fechar"))).andReturn(), 200);

        assertThat(fechado.get("status").asText()).isEqualTo("FECHADO");
        assertThat(fechado.get("totais").get("porResultado").get("FALTA").asInt()).isEqualTo(1);
        assertThat(fechado.get("totais").get("porResultado").get("SO_NO_SISTEMA").asInt()).isEqualTo(1);
        assertThat(fechado.get("resumoIa").asText()).isEqualTo("Duas faltas sem explicação nos dados.");
        JsonNode falta = detalhe(id, "FALTA").get("itens").get("conteudo").get(0);
        assertThat(falta.get("serialNormalizado").asText()).isEqualTo(soNaIq09);
        assertThat(falta.get("causaProvavelRotulo").asText()).isEqualTo("Possível perda ou furto");
        assertThat(falta.get("justificativa").asText()).isEqualTo("Não localizado fisicamente");

        MvcResult csv = mvc.perform(autenticado(get("/estoque/inventario/" + id + "/exportacao"))).andReturn();
        String conteudo = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(conteudo).contains("Resultado").contains("CONFERIDO").contains("FALTA").contains("SO_NO_SISTEMA")
                .contains("VENDIDO_EM_ESTOQUE").contains("FORA_ESTOQUE_SAP").contains("Possível perda ou furto");

        int bipagemDepois = mvc.perform(autenticado(post("/estoque/inventario/" + id + "/bipagens")
                .contentType(MediaType.APPLICATION_JSON).content("{\"serial\":\"" + soNaIq09 + "\"}"))).andReturn().getResponse().getStatus();
        assertThat(bipagemDepois).as("fechado não aceita bipagem").isEqualTo(422);
    }

    @Test
    @DisplayName("Refazer diagnóstico com a IA respondendo fora do formato duas vezes: 502")
    void diagnostico502() throws Exception {
        long id = abrir().get("execucaoId").asLong();
        justificarTodosPendentes(id);
        when(ia.diagnosticar(anyList())).thenThrow(new RespostaIaInvalidaException("JSON quebrado"));
        mvc.perform(autenticado(post("/estoque/inventario/" + id + "/fechar"))).andReturn();

        MvcResult refazer = mvc.perform(autenticado(post("/estoque/inventario/" + id + "/diagnostico"))).andReturn();

        assertThat(refazer.getResponse().getStatus()).isEqualTo(502);
        assertThat(detalhe(id).get("resumoIa").asText()).startsWith("Diagnóstico falhou");
    }

    @Test
    @DisplayName("Sem chave da API: fecha normalmente e mostra \"Diagnóstico indisponível\"")
    void semChave() throws Exception {
        when(ia.disponivel()).thenReturn(false);
        long id = abrir().get("execucaoId").asLong();
        justificarTodosPendentes(id);

        JsonNode fechado = json(mvc.perform(autenticado(post("/estoque/inventario/" + id + "/fechar"))).andReturn(), 200);

        assertThat(fechado.get("resumoIa").asText()).isEqualTo("Diagnóstico indisponível: configure a chave da API");
    }

    @Test
    @DisplayName("Segunda execução aberta da mesma filial: 409; filial sem centro SAP: 422")
    void umaAbertaPorFilial() throws Exception {
        long id = abrir().get("execucaoId").asLong();

        MvcResult segunda = enviarIq09(loja.getId(), iq09());
        assertThat(segunda.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(segunda.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("execucaoId").asLong()).isEqualTo(id);

        Filial semCentro = filiais.save(Filial.builder().codigo("SEMC" + n).nome("Sem Centro " + n).build());
        MvcResult recusa = enviarIq09(semCentro.getId(), iq09());
        assertThat(recusa.getResponse().getStatus()).isEqualTo(422);
        assertThat(recusa.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("Cadastre o centro SAP da filial antes de abrir o inventário");

        json(mvc.perform(autenticado(post("/estoque/inventario/" + id + "/cancelar"))).andReturn(), 200);
        assertThat(enviarIq09(loja.getId(), iq09()).getResponse().getStatus()).as("cancelado libera a filial").isEqualTo(201);
    }

    @Test
    @DisplayName("Dois celulares bipando o mesmo serial ao mesmo tempo: só um conta, o outro recebe jaBipado")
    void bipagemConcorrente() throws Exception {
        long id = abrir().get("execucaoId").asLong();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> leituras = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                leituras.add(pool.submit(() -> {
                    largada.await();
                    return bipar(id, conferido2);
                }));
            }
            largada.countDown();
            int contaram = 0;
            for (Future<JsonNode> leitura : leituras) {
                if (!leitura.get(30, TimeUnit.SECONDS).get("jaBipado").asBoolean()) contaram++;
            }
            assertThat(contaram).isEqualTo(1);
            assertThat(detalhe(id).get("totais").get("conferidos").asInt()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Carga dos centros SAP por CSV ADABAS;CENTRO_SAP: atualiza, lista códigos não encontrados e centro repetido")
    void cargaCentrosSap() throws Exception {
        Filial outra = filiais.save(Filial.builder().codigo("CARGA" + n).nome("Carga " + n).build());
        String csv = "ADABAS;CENTRO_SAP\nCARGA" + n + ";Z" + n + "\nNAOEXISTE" + n + ";Y" + n + "\nINV" + n + ";Z" + n + "\n";
        MvcResult r = mvc.perform(autenticado(multipart("/filiais/centros-sap")
                .file(new MockMultipartFile("arquivo", "centros.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))))).andReturn();

        JsonNode carga = json(r, 200);
        assertThat(carga.get("filiaisAtualizadas").asInt()).isEqualTo(1);
        assertThat(carga.get("codigosNaoEncontrados").toString()).contains("NAOEXISTE" + n);
        assertThat(carga.get("erros").toString()).contains("Z" + n);
        assertThat(filiais.findById(outra.getId()).orElseThrow().getCentroSap()).isEqualTo("Z" + n);
    }

    // ---------------------------------------------------------------- apoio

    private void justificarTodosPendentes(long id) throws Exception {
        for (JsonNode item : detalhe(id, "PENDENTE").get("itens").get("conteudo")) {
            mvc.perform(autenticado(patch("/estoque/inventario/" + id + "/itens/" + item.get("id").asLong() + "/justificativa")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Não localizado fisicamente\"}"))).andReturn();
        }
    }

    private JsonNode abrir() throws Exception {
        return json(enviarIq09(loja.getId(), iq09()), 201);
    }

    private MvcResult enviarIq09(Long filialId, MockMultipartFile arquivo) throws Exception {
        return mvc.perform(autenticado(multipart("/estoque/inventario").file(arquivo).param("filialId", String.valueOf(filialId))))
                .andReturn();
    }

    private JsonNode bipar(long id, String serial) throws Exception {
        MvcResult r = mvc.perform(autenticado(post("/estoque/inventario/" + id + "/bipagens")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("serial", serial))))).andReturn();
        return json(r, 200);
    }

    private JsonNode detalhe(long id) throws Exception {
        return json(mvc.perform(autenticado(get("/estoque/inventario/" + id))).andReturn(), 200);
    }

    private JsonNode detalhe(long id, String resultado) throws Exception {
        return json(mvc.perform(autenticado(get("/estoque/inventario/" + id).param("resultado", resultado))).andReturn(), 200);
    }

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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

    private void unidade(String serial, String filial, StatusEstoque status) {
        estoque.save(EstoqueGeral.builder().serial(serial).modeloComercial("APARELHO SINTETICO").filialEstoque(filial)
                .status(status).build());
    }
}
