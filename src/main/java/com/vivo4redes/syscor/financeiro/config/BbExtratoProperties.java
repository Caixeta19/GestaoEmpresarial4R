package com.vivo4redes.syscor.financeiro.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Extrato do Banco do Brasil (API Extratos v2), prefixo {@code syscor.financeiro.bb-extrato}. Tudo desligado por
 * padrão. Os segredos vêm só de variáveis de ambiente ({@code BB_EXTRATO_*}) e esta classe NÃO tem {@code toString}
 * (sem {@code @Data}) para nunca irem parar em log.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "syscor.financeiro.bb-extrato")
public class BbExtratoProperties {

    public enum Modo {
        /** Fala com o banco (mTLS + OAuth2). */
        REAL,
        /** Lê um JSON sintético no formato da v2 (dev e testes). */
        FAKE
    }

    /** Liga a sincronização (agendada e "Sincronizar agora"). Desligado, o cliente nunca é chamado. */
    private boolean enabled = false;

    private Modo modo = Modo.REAL;

    /** Homologação por padrão; em produção: {@code https://extratos.mtls.api.bb.com.br/v2}. */
    private String baseUrl = "https://extratos.mtls.api.hm.bb.com.br/v2";

    /** Homologação por padrão; em produção: {@code https://oauth.bb.com.br/oauth/token}. */
    private String tokenUrl = "https://oauth.hm.bb.com.br/oauth/token";

    /** Só dígitos, sem dígito verificador e sem zeros à esquerda (0297 vira 297). */
    private String agencia;

    /** Só dígitos, sem dígito verificador e sem zeros à esquerda (001213 vira 1213). */
    private String conta;

    /** Intervalo entre rodadas agendadas (15 min). */
    private long sincronizacaoMs = 900_000L;

    /** Cada rodada consulta os últimos N dias (idempotente pelo UNIQUE do id externo). */
    private int janelaDias = 7;

    /** Vencimento a até N dias da data do lançamento para a conta ser candidata. */
    private int janelaMatchingDias = 45;

    /** Pausa entre páginas, para não esbarrar em limite de chamadas que a spec não informa. */
    private long pausaPaginaMs = 250;

    private int timeoutConexaoSegundos = 5;

    private int timeoutRequisicaoSegundos = 30;

    /** Tentativas por chamada (só 5xx, timeout e 429). */
    private int tentativas = 3;

    /** JSON sintético usado no modo FAKE (classpath). */
    private String fakeRecurso = "extrato-fake/extrato-bb-v2-sintetico.json";

    // ---- segredos (somente por variável de ambiente)
    private String clientId;
    private String clientSecret;
    /** developer_application_key: vai na query string {@code gw-dev-app-key}. */
    private String appKey;
    /** Keystore PKCS12 do certificado A1 (ICP-Brasil). */
    private String keystorePath;
    private String keystorePassword;
}
