package com.vivo4redes.syscor.financeiro.extrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Adaptador da API Extratos v2 do Banco do Brasil (docs/bb/OpenAPI_BB_Extratos_v2.json): mTLS com o certificado A1,
 * OAuth2 {@code client_credentials} (escopo {@code extrato-info}) com token em cache, {@code gw-dev-app-key} na query,
 * paginação por {@code numeroPaginaProximo} até 0 (mesmo tamanho de página em todas), períodos fatiados em blocos de
 * até 31 dias e retry com backoff só para 5xx, timeout e 429 (respeitando {@code Retry-After}).
 *
 * <p>A chave de aplicação vai na URL, então a URL <b>nunca</b> entra em log nem em mensagem de erro, e nenhuma exceção
 * leva a causa original (que poderia carregá-la). O mapeamento dos campos é do {@link BbExtratoMapper}.
 */
public class BancoDoBrasilExtratoClient implements ExtratoBancarioClient {

    private static final Logger log = LoggerFactory.getLogger(BancoDoBrasilExtratoClient.class);

    static final int DIAS_POR_CONSULTA = 31;
    static final int TAMANHO_PAGINA = 120;
    private static final int MAX_PAGINAS = 1000;
    private static final long MAX_ESPERA_MS = 30_000L;
    private static final long BACKOFF_BASE_MS = 500L;
    private static final long FOLGA_TOKEN_SEGUNDOS = 60L;

    /** Pausa injetável: os testes não esperam de verdade. */
    @FunctionalInterface
    interface Pausa {
        void dormir(long milissegundos) throws InterruptedException;
    }

    private final BbExtratoProperties props;
    private final BbExtratoMapper mapper;
    private final Pausa pausa;
    private final Clock relogio;

    private HttpClient http;
    private String token;
    private Instant tokenExpiraEm = Instant.EPOCH;

    public BancoDoBrasilExtratoClient(BbExtratoProperties props, BbExtratoMapper mapper) {
        this(props, mapper, Thread::sleep, Clock.systemUTC());
    }

    BancoDoBrasilExtratoClient(BbExtratoProperties props, BbExtratoMapper mapper, Pausa pausa, Clock relogio) {
        this.props = props;
        this.mapper = mapper;
        this.pausa = pausa;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ porta
    @Override
    public List<LancamentoExtrato> listarDebitos(LocalDate de, LocalDate ate) {
        String agencia = soDigitos(props.getAgencia());
        String conta = soDigitos(props.getConta());
        if (agencia.isEmpty() || conta.isEmpty()) {
            throw new ExtratoBancarioException("Agência e conta do extrato não estão configuradas.");
        }
        if (de.isBefore(LocalDate.now(relogio).minusYears(5))) {
            throw new ExtratoBancarioException("O extrato do BB só permite consultar os últimos 5 anos.");
        }

        List<LancamentoExtrato> debitos = new ArrayList<>();
        LocalDate inicio = de;
        while (!inicio.isAfter(ate)) {
            LocalDate fim = inicio.plusDays(DIAS_POR_CONSULTA - 1L);
            if (fim.isAfter(ate)) fim = ate;
            List<JsonNode> itens = consultarPeriodo(agencia, conta, inicio, fim);
            debitos.addAll(mapper.mapear(itens, agencia, conta));
            inicio = fim.plusDays(1);
            if (!inicio.isAfter(ate)) esperar(props.getPausaPaginaMs());
        }
        return debitos;
    }

    /** Todas as páginas de um período de até 31 dias. */
    private List<JsonNode> consultarPeriodo(String agencia, String conta, LocalDate inicio, LocalDate fim) {
        List<JsonNode> itens = new ArrayList<>();
        int pagina = 1;
        for (int guarda = 0; guarda < MAX_PAGINAS; guarda++) {
            HttpResponse<String> resposta = chamarExtrato(agencia, conta, inicio, fim, pagina);
            JsonNode corpo = mapper.lerCorpo(resposta.body());
            itens.addAll(mapper.itens(corpo));
            int proxima = mapper.proximaPagina(corpo);
            log.debug("Extrato BB: página {} lida ({} itens até agora)", pagina, itens.size());
            if (proxima <= 0) return itens;
            if (proxima <= pagina) {
                throw new ExtratoBancarioException("O banco devolveu uma paginação inconsistente no extrato.");
            }
            pagina = proxima;
            esperar(props.getPausaPaginaMs());
        }
        throw new ExtratoBancarioException("O extrato excedeu o limite de páginas por consulta.");
    }

    // ------------------------------------------------------------------ chamadas
    private HttpResponse<String> chamarExtrato(String agencia, String conta, LocalDate inicio, LocalDate fim, int pagina) {
        boolean tokenRenovado = false;
        while (true) {
            HttpRequest pedido = HttpRequest.newBuilder(urlExtrato(agencia, conta, inicio, fim, pagina))
                    .timeout(Duration.ofSeconds(props.getTimeoutRequisicaoSegundos()))
                    .header("Authorization", "Bearer " + token())
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> resposta = enviarComRetry(pedido, "consulta do extrato");
            int status = resposta.statusCode();
            if (status == 200) return resposta;
            if (status == 401 && !tokenRenovado) {   // token revogado antes de expirar: pega outro e tenta uma vez
                invalidarToken();
                tokenRenovado = true;
                continue;
            }
            throw new ExtratoBancarioException(mensagemDeErro("O banco recusou a consulta do extrato", resposta));
        }
    }

    private URI urlExtrato(String agencia, String conta, LocalDate inicio, LocalDate fim, int pagina) {
        String base = props.getBaseUrl() == null ? "" : props.getBaseUrl().replaceAll("/+$", "");
        String url = base + "/conta-corrente/agencia/" + agencia + "/conta/" + conta
                + "?dataInicioSolicitacao=" + BbExtratoMapper.dataParaQuery(inicio)
                + "&dataFimSolicitacao=" + BbExtratoMapper.dataParaQuery(fim)
                + "&numeroPaginaSolicitacao=" + pagina
                + "&quantidadeRegistroPaginaSolicitacao=" + TAMANHO_PAGINA
                + "&gw-dev-app-key=" + URLEncoder.encode(nuloParaVazio(props.getAppKey()), StandardCharsets.UTF_8);
        return URI.create(url);
    }

    // ------------------------------------------------------------------ OAuth2
    private synchronized String token() {
        if (token != null && relogio.instant().isBefore(tokenExpiraEm)) return token;

        String credenciais = nuloParaVazio(props.getClientId()) + ":" + nuloParaVazio(props.getClientSecret());
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(props.getTokenUrl()))
                .timeout(Duration.ofSeconds(props.getTimeoutRequisicaoSegundos()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(credenciais.getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=extrato-info"))
                .build();
        HttpResponse<String> resposta = enviarComRetry(pedido, "autenticação OAuth");
        if (resposta.statusCode() != 200) {
            throw new ExtratoBancarioException(mensagemDeErro("O banco recusou a autenticação OAuth", resposta));
        }
        JsonNode corpo = mapper.lerCorpo(resposta.body());
        String novo = corpo.path("access_token").asText("");
        if (novo.isBlank()) throw new ExtratoBancarioException("O banco não devolveu o token de acesso.");
        long validade = Math.max(0, corpo.path("expires_in").asLong(300) - FOLGA_TOKEN_SEGUNDOS);
        token = novo;
        tokenExpiraEm = relogio.instant().plusSeconds(validade);
        return token;
    }

    private synchronized void invalidarToken() {
        token = null;
        tokenExpiraEm = Instant.EPOCH;
    }

    // ------------------------------------------------------------------ HTTP, retry e mTLS
    private HttpResponse<String> enviarComRetry(HttpRequest pedido, String operacao) {
        int tentativas = Math.max(1, props.getTentativas());
        for (int tentativa = 1; ; tentativa++) {
            HttpResponse<String> resposta;
            try {
                resposta = cliente().send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtratoBancarioException("A " + operacao + " foi interrompida.");
            } catch (IOException e) {
                // Nem a mensagem nem a causa vão adiante: podem carregar a URL com a chave da aplicação.
                if (tentativa >= tentativas) {
                    throw new ExtratoBancarioException("Sem resposta do banco na " + operacao + " ("
                            + e.getClass().getSimpleName() + ").");
                }
                esperar(backoff(tentativa));
                continue;
            }
            int status = resposta.statusCode();
            boolean tentarDeNovo = status == 429 || status >= 500;
            if (!tentarDeNovo) return resposta;
            if (tentativa >= tentativas) {
                throw new ExtratoBancarioException("O banco respondeu HTTP " + status + " na " + operacao
                        + " depois de " + tentativas + " tentativas.");
            }
            esperar(status == 429 ? retryAfter(resposta, tentativa) : backoff(tentativa));
        }
    }

    private synchronized HttpClient cliente() {
        if (http != null) return http;
        HttpClient.Builder construtor = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getTimeoutConexaoSegundos()))
                .followRedirects(HttpClient.Redirect.NEVER);
        boolean https = props.getBaseUrl() != null && props.getBaseUrl().toLowerCase().startsWith("https");
        if (https || !nuloParaVazio(props.getKeystorePath()).isBlank()) {
            construtor.sslContext(contextoMtls());
        }
        http = construtor.build();
        return http;
    }

    /** SSLContext com o certificado A1 (PKCS12) como identidade do cliente; confia nas CAs padrão da JVM. */
    private SSLContext contextoMtls() {
        String caminho = nuloParaVazio(props.getKeystorePath());
        if (caminho.isBlank()) {
            throw new ExtratoBancarioException(
                    "Certificado A1 não configurado (BB_EXTRATO_KEYSTORE_PATH): o extrato do BB exige mTLS.");
        }
        char[] senha = nuloParaVazio(props.getKeystorePassword()).toCharArray();
        try (FileInputStream arquivo = new FileInputStream(caminho)) {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(arquivo, senha);
            KeyManagerFactory fabrica = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            fabrica.init(keystore, senha);
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(fabrica.getKeyManagers(), null, null);
            return contexto;
        } catch (Exception e) {
            throw new ExtratoBancarioException(
                    "Não foi possível carregar o certificado A1 (confira BB_EXTRATO_KEYSTORE_PATH e a senha).");
        }
    }

    // ------------------------------------------------------------------ apoio
    private void esperar(long milissegundos) {
        if (milissegundos <= 0) return;
        try {
            pausa.dormir(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtratoBancarioException("A consulta do extrato foi interrompida.");
        }
    }

    private static long backoff(int tentativa) {
        return Math.min(MAX_ESPERA_MS, BACKOFF_BASE_MS * (1L << (tentativa - 1)));
    }

    /** {@code Retry-After} em segundos (limitado a 30 s); sem ele ou ilegível, cai no backoff. */
    private static long retryAfter(HttpResponse<String> resposta, int tentativa) {
        return resposta.headers().firstValue("Retry-After").map(String::trim).map(valor -> {
            try {
                return Math.min(MAX_ESPERA_MS, Math.max(0L, Long.parseLong(valor)) * 1000L);
            } catch (NumberFormatException e) {
                return backoff(tentativa);
            }
        }).orElse(backoff(tentativa));
    }

    /** Erro do banco ({@code {code, message}}) sem a chave da aplicação, sem quebras de linha e curto. */
    private String mensagemDeErro(String prefixo, HttpResponse<String> resposta) {
        String detalhe = "";
        try {
            JsonNode corpo = mapper.lerCorpo(resposta.body());
            String codigo = corpo.path("code").asText("");
            String mensagem = corpo.path("message").asText("");
            detalhe = (codigo + " " + mensagem).trim();
        } catch (RuntimeException e) {
            // corpo que não é JSON: fica só o status
        }
        detalhe = detalhe.replaceAll("[\\r\\n\\t]+", " ");
        String chave = nuloParaVazio(props.getAppKey());
        if (!chave.isBlank()) detalhe = detalhe.replace(chave, "***");
        if (detalhe.length() > 200) detalhe = detalhe.substring(0, 200);
        return prefixo + " (HTTP " + resposta.statusCode() + (detalhe.isEmpty() ? "" : ": " + detalhe) + ").";
    }

    private static String soDigitos(String valor) {
        return nuloParaVazio(valor).replaceAll("\\D", "").replaceFirst("^0+(?=\\d)", "");
    }

    private static String nuloParaVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
