package com.vivo4redes.syscor.venda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.PrecoItem;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.PrecoItemRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.enums.TipoPessoa;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.ClienteRepository;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Autenticação do vendedor só no fim (docs/autenticacao.md), com filtros, login e JWT reais: o vendedor provisório
 * vem do token, e o vendedor da venda, de quem se autentica ao finalizar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class VendaAutenticacaoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String MODELO = "APARELHO SINTETICO AUTH";
    private static final String PLANO = "CONTROLE_ALTO_VALOR";
    private static final String SENHA = "senha-de-teste";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepository clientes;
    @Autowired FilialRepository filiais;
    @Autowired UsuarioRepository usuarios;
    @Autowired EstoqueGeralRepository estoque;
    @Autowired TabelaPrecoExecucaoRepository execucoes;
    @Autowired PrecoItemRepository precos;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private int n;
    private Long clienteId;
    private Long outroClienteId;
    private Long filialId;
    private Usuario logado;
    private Usuario outroVendedor;
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        n = SEQUENCIA.incrementAndGet();
        clienteId = cliente("A");
        outroClienteId = cliente("B");
        filialId = filiais.save(Filial.builder().codigo("AUTH" + n).nome("Filial Auth").build()).getId();
        logado = usuario("Logado", "logado");
        outroVendedor = usuario("Outro Vendedor", "outro");
        token = entrar(logado);
        tabelaVigente("1000.00");
    }

    @Test
    @DisplayName("Abrir sem senha: 201, com o usuário do token como vendedor provisório")
    void abrirSemSenha() throws Exception {
        Resposta r = enviar(post("/vendas"), corpoAbertura());

        assertThat(r.status).isEqualTo(201);
        assertThat(r.corpo.get("vendedorNome").asText()).isEqualTo("Logado");
        assertThat(r.corpo.get("status").asText()).isEqualTo("ABERTA");
        assertThat(r.corpo.get("finalizadoPorNome").isNull()).isTrue();
        assertThat(r.corpo.get("finalizadoEm").isNull()).isTrue();
    }

    @Test
    @DisplayName("Editar a venda ABERTA sem senha: 200")
    void editarSemSenha() throws Exception {
        long id = abrir();

        Resposta r = enviar(put("/vendas/{id}", id), corpoEdicao());

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("clienteId").asLong()).isEqualTo(outroClienteId);
    }

    @Test
    @DisplayName("Front antigo: autenticação no corpo é ignorada, mesmo em branco ou de outro usuário")
    void frontAntigo() throws Exception {
        Map<String, Object> abertura = corpoAbertura();
        abertura.put("autenticacaoVendedor", Map.of("email", outroVendedor.getEmail(), "senha", "senha-errada"));
        Resposta aberta = enviar(post("/vendas"), abertura);

        assertThat(aberta.status).isEqualTo(201);
        assertThat(aberta.corpo.get("vendedorNome").asText()).isEqualTo("Logado");

        Map<String, Object> edicao = corpoEdicao();
        edicao.put("autenticacaoUsuario", Map.of("email", "", "senha", ""));
        assertThat(enviar(put("/vendas/{id}", aberta.corpo.get("id").asLong()), edicao).status).isEqualTo(200);
    }

    @Test
    @DisplayName("Editar venda já finalizada: 422, sem alterar nada")
    void editarFinalizada() throws Exception {
        long id = vendaProntaParaFinalizar();
        assertThat(finalizar(id, logado, SENHA).status).isEqualTo(200);

        Resposta r = enviar(put("/vendas/{id}", id), corpoEdicao());

        assertThat(r.status).isEqualTo(422);
        assertThat(buscar(id).get("clienteId").asLong()).isEqualTo(clienteId);
    }

    @Test
    @DisplayName("Finalizar sem autenticação: 400, com a venda ainda ABERTA")
    void finalizarSemAutenticacao() throws Exception {
        long id = vendaProntaParaFinalizar();

        Resposta r = enviar(patch("/vendas/{id}/finalizar", id), Map.of());

        assertThat(r.status).isEqualTo(400);
        assertThat(buscar(id).get("status").asText()).isEqualTo("ABERTA");
    }

    @Test
    @DisplayName("Finalizar com senha errada: 401, com a venda ainda ABERTA e o mesmo vendedor")
    void finalizarSenhaErrada() throws Exception {
        long id = vendaProntaParaFinalizar();

        Resposta r = finalizar(id, outroVendedor, "senha-errada");

        assertThat(r.status).isEqualTo(401);
        JsonNode venda = buscar(id);
        assertThat(venda.get("status").asText()).isEqualTo("ABERTA");
        assertThat(venda.get("vendedorNome").asText()).isEqualTo("Logado");
        assertThat(venda.get("finalizadoPorNome").isNull()).isTrue();
    }

    @Test
    @DisplayName("Finalizar com outro vendedor: ele passa a ser o vendedor, inclusive na listagem e no filtro")
    void finalizarComOutroVendedor() throws Exception {
        long id = vendaProntaParaFinalizar();

        Resposta r = finalizar(id, outroVendedor, SENHA);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("status").asText()).isEqualTo("PENDENTE");
        assertThat(r.corpo.get("vendedorNome").asText()).isEqualTo("Outro Vendedor");
        assertThat(r.corpo.get("finalizadoPorNome").asText()).isEqualTo("Outro Vendedor");
        assertThat(r.corpo.get("finalizadoEm").isNull()).isFalse();

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT usuario_id, finalizado_por_id, finalizado_em FROM vendas WHERE id = ?", id);
        assertThat(linha.get("usuario_id")).isEqualTo(outroVendedor.getId());
        assertThat(linha.get("finalizado_por_id")).isEqualTo(outroVendedor.getId());
        assertThat(linha.get("finalizado_em")).isNotNull();

        assertThat(buscar(id).get("finalizadoPorNome").asText()).isEqualTo("Outro Vendedor");
        JsonNode filtradas = enviar(get("/vendas").param("vendedorId", outroVendedor.getId().toString()), null).corpo;
        assertThat(filtradas).hasSize(1);
        assertThat(filtradas.get(0).get("id").asLong()).isEqualTo(id);
        assertThat(filtradas.get(0).get("finalizadoPorNome").asText()).isEqualTo("Outro Vendedor");
    }

    @Test
    @DisplayName("Total alterado ao finalizar: 422 grava o total novo, mas não troca o vendedor nem marca a finalização")
    void totalAlteradoNaoTrocaVendedor() throws Exception {
        long id = vendaProntaParaFinalizar();
        tabelaVigente("1100.00");

        Resposta r = finalizar(id, outroVendedor, SENHA);

        assertThat(r.status).isEqualTo(422);
        JsonNode venda = buscar(id);
        assertThat(new BigDecimal(venda.get("valorTotal").asText())).isEqualByComparingTo("1100.00");
        assertThat(venda.get("status").asText()).isEqualTo("ABERTA");
        assertThat(venda.get("vendedorNome").asText()).isEqualTo("Logado");
        assertThat(venda.get("finalizadoPorNome").isNull()).isTrue();
    }

    @Test
    @DisplayName("Mudar o status sem senha: 400, sem alterar nada")
    void statusSemSenha() throws Exception {
        long id = abrir();

        Resposta r = enviar(patch("/vendas/{id}/status", id), Map.of("novoStatus", "CANCELADA"));

        assertThat(r.status).isEqualTo(400);
        assertThat(buscar(id).get("status").asText()).isEqualTo("ABERTA");
    }

    @Test
    @DisplayName("Mudar o status com senha errada: 401, sem alterar nada")
    void statusSenhaErrada() throws Exception {
        long id = abrir();

        Resposta r = mudarStatus(id, "CANCELADA", outroVendedor, "senha-errada");

        assertThat(r.status).isEqualTo(401);
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT status, status_alterado_por_id FROM vendas WHERE id = ?", id);
        assertThat(linha.get("status")).isEqualTo("ABERTA");
        assertThat(linha.get("status_alterado_por_id")).isNull();
    }

    @Test
    @DisplayName("Mudar o status grava quem alterou e quando, sem trocar o vendedor da venda")
    void statusGravaAutor() throws Exception {
        long id = vendaProntaParaFinalizar();
        assertThat(finalizar(id, logado, SENHA).status).isEqualTo(200);

        Resposta r = mudarStatus(id, "APROVADA", outroVendedor, SENHA);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("vendedorNome").asText()).isEqualTo("Logado");
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT usuario_id, status_alterado_por_id, status_alterado_em FROM vendas WHERE id = ?", id);
        assertThat(linha.get("usuario_id")).isEqualTo(logado.getId());
        assertThat(linha.get("status_alterado_por_id")).isEqualTo(outroVendedor.getId());
        assertThat(linha.get("status_alterado_em")).isNotNull();
    }

    @Test
    @DisplayName("Usuário com situação INATIVO não finaliza: 401, mesmo com a senha certa")
    void situacaoInativaNaoFinaliza() throws Exception {
        long id = vendaProntaParaFinalizar();
        outroVendedor.setSituacao("INATIVO");
        usuarios.save(outroVendedor);

        assertThat(finalizar(id, outroVendedor, SENHA).status).isEqualTo(401);
        assertThat(buscar(id).get("status").asText()).isEqualTo("ABERTA");
    }

    @Test
    @DisplayName("Usuário inativado com o token ainda válido não abre carrinho: 401")
    void tokenDeInativoNaoAbre() throws Exception {
        logado.setCargo("INATIVO");
        usuarios.save(logado);

        assertThat(enviar(post("/vendas"), corpoAbertura()).status).isEqualTo(401);
    }

    @Test
    @DisplayName("GET /usuarios não lista quem tem situação INATIVO")
    void listaSemInativos() throws Exception {
        outroVendedor.setSituacao("INATIVO");
        usuarios.save(outroVendedor);

        JsonNode lista = enviar(get("/usuarios"), null).corpo;

        assertThat(lista.findValuesAsText("login")).contains(logado.getLogin()).doesNotContain(outroVendedor.getLogin());
    }

    @Test
    @DisplayName("Sem token ou com token inválido a venda não abre: 401")
    void semTokenNaoAbre() throws Exception {
        String abertura = json.writeValueAsString(corpoAbertura());

        int semToken = mvc.perform(post("/vendas").contentType(MediaType.APPLICATION_JSON).content(abertura))
                .andReturn().getResponse().getStatus();
        int tokenInvalido = mvc.perform(post("/vendas").contentType(MediaType.APPLICATION_JSON).content(abertura)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")).andReturn().getResponse().getStatus();

        assertThat(semToken).as("sem token").isEqualTo(401);
        assertThat(tokenInvalido).as("token inválido").isEqualTo(401);
    }

    // ------------------------------------------------------------------ apoio

    private Long cliente(String sufixo) {
        return clientes.save(Cliente.builder().tipoPessoa(TipoPessoa.FISICA).nome("Cliente " + sufixo)
                .cpfCnpj("8" + sufixo.charAt(0) % 10 + "00000" + n).build()).getId();
    }

    private Usuario usuario(String nome, String prefixo) {
        return usuarios.save(Usuario.builder().nome(nome).login(prefixo + n).email(prefixo + n + "@teste.local")
                .senhaHash(encoder.encode(SENHA)).primeiroAcesso(false).filial("Filial Auth").cargo("VENDEDOR").build());
    }

    private String entrar(Usuario usuario) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", usuario.getLogin(), "senha", SENHA)))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private long abrir() throws Exception {
        Resposta r = enviar(post("/vendas"), corpoAbertura());
        assertThat(r.status).isEqualTo(201);
        return r.corpo.get("id").asLong();
    }

    /** Aparelho de 1.000,00 à vista, com o pagamento fechando o total. */
    private long vendaProntaParaFinalizar() throws Exception {
        long id = abrir();
        EstoqueGeral aparelho = estoque.save(EstoqueGeral.builder().serial("AUTH-" + n + "-" + id)
                .modeloComercial(MODELO).build());
        Map<String, Object> item = new HashMap<>();
        item.put("categoria", "PRODUTO_VIVO");
        item.put("produtoId", aparelho.getId());
        item.put("descricaoProduto", MODELO);
        item.put("quantidade", 1);
        item.put("imeiOuSerial", aparelho.getSerial());
        item.put("plano", PLANO);
        assertThat(enviar(post("/vendas/{id}/itens", id), item).status).isEqualTo(201);
        Resposta pago = enviar(post("/vendas/{id}/pagamentos", id),
                Map.of("forma", "DINHEIRO", "valor", "1000.00", "parcelas", 1));
        assertThat(pago.status).isEqualTo(201);
        return id;
    }

    private Resposta finalizar(long id, Usuario usuario, String senha) throws Exception {
        return enviar(patch("/vendas/{id}/finalizar", id),
                Map.of("autenticacaoVendedor", Map.of("email", usuario.getEmail(), "senha", senha)));
    }

    private Resposta mudarStatus(long id, String novoStatus, Usuario usuario, String senha) throws Exception {
        return enviar(patch("/vendas/{id}/status", id), Map.of("novoStatus", novoStatus,
                "autenticacaoUsuario", Map.of("email", usuario.getEmail(), "senha", senha)));
    }

    private JsonNode buscar(long id) throws Exception {
        return enviar(get("/vendas/{id}", id), null).corpo;
    }

    private Map<String, Object> corpoAbertura() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("clienteId", clienteId);
        corpo.put("filialId", filialId);
        corpo.put("estoqueAvancado", false);
        return corpo;
    }

    private Map<String, Object> corpoEdicao() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("clienteId", outroClienteId);
        corpo.put("filialId", filialId);
        corpo.put("estoqueAvancado", false);
        return corpo;
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

    /** A execução mais nova é a vigente. */
    private void tabelaVigente(String preco) {
        TabelaPrecoExecucao execucao = execucoes.save(TabelaPrecoExecucao.builder().nomeArquivo("sintetica.xlsx")
                .totalLinhasProcessadas(1).totalProdutosAtualizados(0).totalNaoEncontrados(0).build());
        precos.saveAll(List.of(PrecoItem.builder().execucao(execucao).aba("SMARTPHONES").tabela(TabelaPreco.REGULAR)
                .grupoPlano(GrupoPlano.CONTROLE_ALTO_VALOR).nomeComercial(MODELO).chaveNome(TextoUtil.chave(MODELO))
                .preco(new BigDecimal(preco)).precoPix(new BigDecimal(preco)).parcelas(Map.of()).build()));
    }

    private record Resposta(int status, JsonNode corpo) {
    }
}
