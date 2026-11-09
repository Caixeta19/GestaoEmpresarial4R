package com.vivo4redes.syscor.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Respostas de autenticação com os filtros, o login e o JWT reais. Servidor real para cobrir também o despacho de erro. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AutenticacaoHttpIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";

    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Value("${syscor.jwt.secret}") String segredo;

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        int n = SEQUENCIA.incrementAndGet();
        usuario = usuarios.save(Usuario.builder().nome("Usuário Teste").login("http" + n).email("http" + n + "@teste.local")
                .senhaHash(encoder.encode(SENHA)).primeiroAcesso(false).filial("Loja").cargo("VENDEDOR").situacao("ATIVO").build());
    }

    // ------------------------------------------------------------------ sem token válido

    @Test
    @DisplayName("Com token válido a rota responde 200")
    void tokenValido() throws Exception {
        assertThat(comToken(entrar())).isEqualTo(200);
    }

    @Test
    @DisplayName("Sem token: 401")
    void semToken() throws Exception {
        assertThat(mvc.perform(get("/usuarios")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Token inválido: 401")
    void tokenInvalido() throws Exception {
        assertThat(comToken("token-invalido")).isEqualTo(401);
    }

    @Test
    @DisplayName("Token expirado: 401")
    void tokenExpirado() throws Exception {
        String expirado = Jwts.builder()
                .subject(usuario.getLogin())
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(segredo)))
                .compact();

        assertThat(comToken(expirado)).isEqualTo(401);
    }

    @Test
    @DisplayName("Rota inexistente com token válido continua 404")
    void rotaInexistente() throws Exception {
        MvcResult r = mvc.perform(get("/rota-que-nao-existe").header(HttpHeaders.AUTHORIZATION, "Bearer " + entrar())).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Erro que passa pelo /error mantém o status real com token válido, e não vira 401")
    void erroPeloDespachoDeErro() throws Exception {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(entrar());
        cabecalhos.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));

        ResponseEntity<String> r = http.exchange("/rota-que-nao-existe", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("Sem token, o despacho de erro não abre a rota: continua 401")
    void semTokenNoServidorReal() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));

        ResponseEntity<String> r = http.exchange("/usuarios", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Webhook não passa a exigir o token do usuário")
    void webhookSemToken() throws Exception {
        MvcResult r = mvc.perform(post("/webhooks/inexistente").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        assertThat(r.getResponse().getStatus()).isNotIn(401, 403);
    }

    // ------------------------------------------------------------------ usuário inativado com token válido

    @Test
    @DisplayName("Inativado pela flag ativo: o token deixa de valer")
    void inativoPelaFlag() throws Exception {
        assertTokenRecusadoDepoisDe(u -> u.setAtivo(false));
    }

    @Test
    @DisplayName("Inativado pelo cargo: o token deixa de valer")
    void inativoPeloCargo() throws Exception {
        assertTokenRecusadoDepoisDe(u -> u.setCargo("INATIVO"));
    }

    @Test
    @DisplayName("Inativado pela situação: o token deixa de valer")
    void inativoPelaSituacao() throws Exception {
        assertTokenRecusadoDepoisDe(u -> u.setSituacao("INATIVO"));
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("Login com a senha certa: 200 com o token")
    void loginCerto() throws Exception {
        Resposta r = login(usuario.getLogin(), SENHA);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.path("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Login com senha errada: 401 com a mensagem padronizada")
    void loginSenhaErrada() throws Exception {
        assertLoginRecusado(login(usuario.getLogin(), "senha-errada"));
    }

    @Test
    @DisplayName("Login de usuário inexistente: 401, sem revelar que ele não existe")
    void loginInexistente() throws Exception {
        assertLoginRecusado(login("nao-existe", SENHA));
    }

    @Test
    @DisplayName("Login de usuário inativo: 401, com a mesma mensagem")
    void loginInativo() throws Exception {
        usuario.setSituacao("INATIVO");
        usuarios.save(usuario);

        assertLoginRecusado(login(usuario.getLogin(), SENHA));
    }

    // ------------------------------------------------------------------ apoio

    private void assertTokenRecusadoDepoisDe(Consumer<Usuario> inativar) throws Exception {
        String token = entrar();
        assertThat(comToken(token)).as("antes de inativar").isEqualTo(200);

        inativar.accept(usuario);
        usuarios.save(usuario);

        assertThat(comToken(token)).as("depois de inativar").isEqualTo(401);
    }

    private void assertLoginRecusado(Resposta r) {
        assertThat(r.status).isEqualTo(401);
        assertThat(r.corpo.path("mensagem").asText()).isEqualTo("Usuário ou senha inválidos.");
        assertThat(r.corpo.path("token").isMissingNode()).isTrue();
    }

    private int comToken(String token) throws Exception {
        return mvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private String entrar() throws Exception {
        Resposta r = login(usuario.getLogin(), SENHA);
        assertThat(r.status).as("login").isEqualTo(200);
        return r.corpo.get("token").asText();
    }

    private Resposta login(String login, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", login, "senha", senha)))).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Resposta(r.getResponse().getStatus(), texto.isBlank() ? json.createObjectNode() : json.readTree(texto));
    }

    private record Resposta(int status, JsonNode corpo) {
    }
}
