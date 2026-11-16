package com.vivo4redes.syscor.financeiro.extrato;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cliente do extrato do BB contra um servidor HTTP falso do JDK (nada de rede real, nem certificado): token em cache,
 * paginação, fatiamento em 31 dias, retry/backoff, Retry-After e a garantia de que a chave de aplicação não vaza.
 */
class BancoDoBrasilExtratoClientTest {

    private static final String CHAVE = "CHAVE-DE-APLICACAO-SECRETA-123";
    private static final String CLIENT_ID = "cliente-sintetico";
    private static final String CLIENT_SECRET = "segredo-sintetico";

    /** Resposta roteirizada do servidor falso. */
    private record Resposta(int status, String corpo, Map<String, String> cabecalhos) {
        static Resposta ok(String corpo) {
            return new Resposta(200, corpo, Map.of());
        }

        static Resposta status(int status, String corpo) {
            return new Resposta(status, corpo, Map.of());
        }
    }

    /** Relógio que anda quando o teste manda. */
    private static final class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-11-16T12:00:00Z");

        void avancarSegundos(long segundos) {
            agora = agora.plusSeconds(segundos);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private HttpServer servidor;
    private final AtomicInteger chamadasToken = new AtomicInteger();
    private final List<String> consultas = Collections.synchronizedList(new ArrayList<>());
    private final List<String> autorizacoesToken = Collections.synchronizedList(new ArrayList<>());
    private final List<String> corposToken = Collections.synchronizedList(new ArrayList<>());
    private final List<String> autorizacoesExtrato = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> pausas = new ArrayList<>();
    private volatile Function<Map<String, String>, Resposta> extrato = q -> Resposta.ok(pagina(0));
    private volatile int validadeToken = 600;
    private final RelogioManual relogio = new RelogioManual();
    private final BbExtratoMapper mapper = new BbExtratoMapper();
    private BbExtratoProperties props;
    private BancoDoBrasilExtratoClient cliente;
    private ListAppender<ILoggingEvent> logs;
    private Level nivelAnterior;

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/oauth/token", troca -> {
            chamadasToken.incrementAndGet();
            autorizacoesToken.add(troca.getRequestHeaders().getFirst("Authorization"));
            corposToken.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(troca, Resposta.ok("{\"access_token\":\"token-" + chamadasToken.get()
                    + "\",\"token_type\":\"Bearer\",\"expires_in\":" + validadeToken + "}"));
        });
        servidor.createContext("/v2/conta-corrente/agencia/297/conta/1213", troca -> {
            String consulta = troca.getRequestURI().getRawQuery();
            consultas.add(consulta);
            autorizacoesExtrato.add(troca.getRequestHeaders().getFirst("Authorization"));
            responder(troca, extrato.apply(parametros(consulta)));
        });
        servidor.start();

        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        props = new BbExtratoProperties();
        props.setEnabled(true);
        props.setBaseUrl(base + "/v2");
        props.setTokenUrl(base + "/oauth/token");
        props.setAgencia("0297");
        props.setConta("001213");
        props.setClientId(CLIENT_ID);
        props.setClientSecret(CLIENT_SECRET);
        props.setAppKey(CHAVE);
        props.setPausaPaginaMs(10);
        props.setTentativas(3);
        cliente = new BancoDoBrasilExtratoClient(props, mapper, pausas::add, relogio);

        logs = new ListAppender<>();
        logs.start();
        ch.qos.logback.classic.Logger raiz = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        nivelAnterior = raiz.getLevel();
        raiz.setLevel(Level.DEBUG);
        raiz.addAppender(logs);
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
        ch.qos.logback.classic.Logger raiz = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        raiz.detachAppender(logs);
        raiz.setLevel(nivelAnterior);
    }

    // ------------------------------------------------------------------ OAuth2
    @Test
    @DisplayName("Token: client_credentials com escopo extrato-info, Basic com id:secret, em cache até perto de expirar")
    void tokenEmCache() {
        LocalDate dia = LocalDate.of(2026, 11, 10);

        cliente.listarDebitos(dia, dia);
        cliente.listarDebitos(dia, dia);

        assertThat(chamadasToken.get()).as("segundo uso reaproveita o token").isEqualTo(1);
        assertThat(corposToken.get(0)).isEqualTo("grant_type=client_credentials&scope=extrato-info");
        assertThat(autorizacoesToken.get(0)).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8)));
        assertThat(autorizacoesExtrato).containsOnly("Bearer token-1");

        relogio.avancarSegundos(600 - 59);   // ainda dentro da folga de 60 s: renova
        cliente.listarDebitos(dia, dia);
        assertThat(chamadasToken.get()).as("perto de expirar, pega outro").isEqualTo(2);
        assertThat(autorizacoesExtrato.get(autorizacoesExtrato.size() - 1)).isEqualTo("Bearer token-2");
    }

    @Test
    @DisplayName("401 no extrato (token revogado): descarta o token, pega outro e tenta de novo uma vez")
    void tokenRevogado() {
        AtomicInteger vez = new AtomicInteger();
        extrato = q -> vez.incrementAndGet() == 1 ? Resposta.status(401, "{\"code\":\"401\",\"message\":\"token\"}")
                : Resposta.ok(pagina(0, debito("X1", 5112026, "10.00")));

        List<LancamentoExtrato> lista = cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5));

        assertThat(lista).hasSize(1);
        assertThat(chamadasToken.get()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ consulta
    @Test
    @DisplayName("Consulta: agência/conta sem zeros à esquerda, datas como inteiro DDMMAAAA, pagesize 120 e gw-dev-app-key na query")
    void parametros() {
        cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12));

        Map<String, String> q = parametros(consultas.get(0));
        assertThat(q).containsEntry("dataInicioSolicitacao", "5112026")
                .containsEntry("dataFimSolicitacao", "12112026")
                .containsEntry("numeroPaginaSolicitacao", "1")
                .containsEntry("quantidadeRegistroPaginaSolicitacao", "120")
                .containsEntry("gw-dev-app-key", CHAVE);
    }

    @Test
    @DisplayName("Paginação: segue numeroPaginaProximo até 0, com o mesmo tamanho de página, e junta os débitos")
    void paginacao() {
        extrato = q -> switch (q.get("numeroPaginaSolicitacao")) {
            case "1" -> Resposta.ok(pagina(2, debito("P1", 5112026, "10.00"), credito()));
            case "2" -> Resposta.ok(pagina(3, debito("P2", 5112026, "20.00")));
            default -> Resposta.ok(pagina(0, debito("P3", 6112026, "30.00")));
        };

        List<LancamentoExtrato> lista = cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6));

        assertThat(lista).extracting(LancamentoExtrato::idExterno).containsExactly("P1", "P2", "P3");
        assertThat(consultas).hasSize(3);
        assertThat(consultas).allSatisfy(c -> assertThat(parametros(c)).containsEntry("quantidadeRegistroPaginaSolicitacao", "120"));
        assertThat(pausas).as("pausa curta entre páginas").containsExactly(10L, 10L);
    }

    @Test
    @DisplayName("Paginação que não avança falha em vez de entrar em laço")
    void paginacaoInconsistente() {
        extrato = q -> Resposta.ok(pagina(1, debito("L", 5112026, "10.00")));

        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ExtratoBancarioException.class).hasMessageContaining("paginação");
    }

    @Test
    @DisplayName("Fatiamento: período de 71 dias vira 3 consultas de no máximo 31 dias, sem buraco nem sobreposição")
    void fatiamento() {
        LocalDate de = LocalDate.of(2026, 9, 1);
        LocalDate ate = LocalDate.of(2026, 11, 10);

        cliente.listarDebitos(de, ate);

        assertThat(consultas).hasSize(3);
        List<String[]> faixas = consultas.stream().map(c -> parametros(c))
                .map(q -> new String[]{q.get("dataInicioSolicitacao"), q.get("dataFimSolicitacao")}).toList();
        assertThat(faixas.get(0)).containsExactly("1092026", "1102026");     // 01/09 a 01/10 (31 dias)
        assertThat(faixas.get(1)).containsExactly("2102026", "1112026");     // 02/10 a 01/11 (31 dias)
        assertThat(faixas.get(2)).containsExactly("2112026", "10112026");    // 02/11 a 10/11
        assertThat(pausas).as("pausa entre as fatias").hasSize(2);
    }

    @Test
    @DisplayName("Período maior que 5 anos, ou agência/conta ausentes, falham sem chamar o banco")
    void entradasInvalidas() {
        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.now().minusYears(6), LocalDate.now()))
                .isInstanceOf(ExtratoBancarioException.class).hasMessageContaining("5 anos");
        props.setAgencia(" ");
        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.now().minusDays(1), LocalDate.now()))
                .isInstanceOf(ExtratoBancarioException.class).hasMessageContaining("Agência e conta");
        assertThat(consultas).isEmpty();
        assertThat(chamadasToken.get()).isZero();
    }

    // ------------------------------------------------------------------ retry
    @Test
    @DisplayName("Retry: 5xx duas vezes e depois OK; espera 500 ms e 1 s (backoff)")
    void retryEm5xx() {
        AtomicInteger vez = new AtomicInteger();
        extrato = q -> vez.incrementAndGet() <= 2 ? Resposta.status(503, "") : Resposta.ok(pagina(0, debito("R1", 5112026, "10.00")));

        List<LancamentoExtrato> lista = cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5));

        assertThat(lista).hasSize(1);
        assertThat(consultas).hasSize(3);
        assertThat(pausas).containsExactly(500L, 1000L);
    }

    @Test
    @DisplayName("429 respeita o Retry-After (em segundos) antes de tentar de novo")
    void retryAfter() {
        AtomicInteger vez = new AtomicInteger();
        extrato = q -> vez.incrementAndGet() == 1
                ? new Resposta(429, "", Map.of("Retry-After", "2"))
                : Resposta.ok(pagina(0, debito("R2", 5112026, "10.00")));

        List<LancamentoExtrato> lista = cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5));

        assertThat(lista).hasSize(1);
        assertThat(pausas).containsExactly(2000L);
    }

    @Test
    @DisplayName("5xx que não passa esgota as tentativas e falha sem vazar URL, chave, token, agência ou conta")
    void falhaPersistente() {
        extrato = q -> Resposta.status(500, "{\"code\":\"500\",\"message\":\"erro interno\"}");

        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ExtratoBancarioException.class)
                .hasMessageContaining("HTTP 500").hasMessageContaining("3 tentativas")
                .satisfies(e -> semSegredos(e.getMessage()))
                .hasNoCause();
        assertThat(consultas).hasSize(3);
    }

    @Test
    @DisplayName("422 negocial não tenta de novo e devolve código e mensagem do banco, sem segredos")
    void erroNegocial() {
        extrato = q -> Resposta.status(422, "{\"code\":\"BB-422\",\"message\":\"Periodo invalido para " + CHAVE + "\"}");

        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ExtratoBancarioException.class)
                .hasMessageContaining("HTTP 422").hasMessageContaining("BB-422")
                .satisfies(e -> semSegredos(e.getMessage()));
        assertThat(consultas).hasSize(1);
    }

    @Test
    @DisplayName("Credencial OAuth recusada: falha com o status do banco, sem segredos, e sem chamar o extrato")
    void oauthRecusado() {
        servidor.removeContext("/oauth/token");
        servidor.createContext("/oauth/token", troca -> responder(troca,
                Resposta.status(401, "{\"code\":\"invalid_client\",\"message\":\"cliente invalido\"}")));

        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ExtratoBancarioException.class)
                .hasMessageContaining("autenticação OAuth").hasMessageContaining("HTTP 401")
                .satisfies(e -> semSegredos(e.getMessage()));
        assertThat(consultas).isEmpty();
    }

    @Test
    @DisplayName("Banco fora do ar (conexão recusada): falha sem a URL na mensagem e sem causa encadeada")
    void bancoForaDoAr() {
        servidor.stop(0);

        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ExtratoBancarioException.class)
                .satisfies(e -> semSegredos(e.getMessage()))
                .hasNoCause();
        assertThat(pausas).as("tentou 3 vezes, esperando entre elas").containsExactly(500L, 1000L);
    }

    @Test
    @DisplayName("mTLS: com a URL https e sem keystore do A1 falha antes de qualquer chamada, com mensagem clara")
    void semCertificado() {
        props.setBaseUrl("https://extratos.invalido.example/v2");

        assertThatThrownBy(() -> cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ExtratoBancarioException.class).hasMessageContaining("Certificado A1");
        assertThat(consultas).isEmpty();

        props.setKeystorePath("caminho/que/nao/existe.p12");
        props.setKeystorePassword("senha-sintetica");
        BancoDoBrasilExtratoClient outro = new BancoDoBrasilExtratoClient(props, mapper, pausas::add, relogio);
        assertThatThrownBy(() -> outro.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ExtratoBancarioException.class).hasMessageContaining("certificado A1")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("senha-sintetica"));
    }

    // ------------------------------------------------------------------ segredos
    @Test
    @DisplayName("Nenhum log, em sucesso ou em falha, contém a chave de aplicação, o token, o client secret ou a conta")
    void semVazamentoEmLog() {
        cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12));
        extrato = q -> Resposta.status(500, "");
        try {
            cliente.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12));
        } catch (ExtratoBancarioException esperado) {
            // só interessa o que foi para o log
        }

        // Só o que o código do SYSCOR registra: o servidor HTTP falso do JDK também loga a URL que recebe (quando outro
        // teste instala a ponte JUL-SLF4J), mas isso é o lado do "banco", não do nosso cliente.
        List<String> mensagens = logs.list.stream()
                .filter(e -> e.getLoggerName().startsWith("com.vivo4redes.syscor"))
                .map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(mensagens).isNotEmpty();
        assertThat(String.join("\n", mensagens)).doesNotContain(CHAVE).doesNotContain("token-1")
                .doesNotContain(CLIENT_SECRET).doesNotContain("gw-dev-app-key").doesNotContain("001213")
                .doesNotContain("/conta/");
    }

    // ------------------------------------------------------------------ apoio
    private static void semSegredos(String mensagem) {
        assertThat(mensagem).doesNotContain(CHAVE).doesNotContain(CLIENT_SECRET).doesNotContain("token-")
                .doesNotContain("gw-dev-app-key").doesNotContain("127.0.0.1").doesNotContain("localhost")
                .doesNotContain("1213").doesNotContain("http://").doesNotContain("https://");
    }

    private static String credito() {
        return "{\"indicadorTipoLancamento\":\"1\",\"indicadorSinalLancamento\":\"C\",\"dataLancamento\":5112026,"
                + "\"valorLancamento\":999.99,\"numeroDocumento\":1}";
    }

    private static String debito(String idUnico, int data, String valor) {
        return "{\"indicadorTipoLancamento\":\"1\",\"indicadorSinalLancamento\":\"D\",\"dataLancamento\":" + data
                + ",\"valorLancamento\":" + valor + ",\"numeroDocumento\":1,\"textoIdentificadorUnicoTransacao\":\""
                + idUnico + "\"}";
    }

    private static String pagina(int proxima, String... itens) {
        return "{\"numeroPaginaProximo\":" + proxima + ",\"listaLancamento\":[" + String.join(",", itens) + "]}";
    }

    private static Map<String, String> parametros(String consulta) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (consulta == null) return mapa;
        for (String par : consulta.split("&")) {
            String[] kv = par.split("=", 2);
            mapa.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return mapa;
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, Resposta resposta) throws IOException {
        byte[] bytes = resposta.corpo().getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        resposta.cabecalhos().forEach((k, v) -> troca.getResponseHeaders().add(k, v));
        troca.sendResponseHeaders(resposta.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        }
        troca.close();
    }
}
