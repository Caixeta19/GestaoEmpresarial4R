package com.vivo4redes.syscor.estoque.service.diagnostico;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.exception.DiagnosticoIaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Diagnóstico pela API do Claude (Messages API), com saída JSON estruturada ({@link RespostaDiagnosticoIa}).
 * A chave vem de {@code syscor.ia.api-key} (variável ANTHROPIC_API_KEY, fora do git).
 */
@Component
public class ClaudeDiagnosticoIaClient implements DiagnosticoIaClient {

    static final String PROMPT_SISTEMA = """
            Você ajuda o setor de estoque de uma revenda Vivo a entender divergências de inventário.
            O inventário de uma loja cruza três fontes pelo IMEI/serial, de forma exata:
            1. IQ09 (SAP): o que o SAP diz que está no depósito da loja. Status usuário ADEP (atribuído ao depósito)
               ou vazio = no estoque da loja; RLIB = disponível/liberado; RESE = reservado; DEPS NREM (status do
               sistema) = atribuído a nota de remessa.
            2. Sistema: o estoque do nosso sistema para a loja (DISPONIVEL, RESERVADO, DEFEITO ou VENDIDO).
            3. Contagem física: os aparelhos bipados na loja.

            O cruzamento já foi feito. Você não decide se um IMEI bate: você recebe cada item já classificado e diz a
            causa mais provável da divergência. Resultados possíveis:
            - FALTA: estava na IQ09 da loja e não foi achado na contagem.
            - SO_NO_SISTEMA: estava só no estoque do sistema e não foi achado.
            - SEM_ENTRADA_SISTEMA: está na IQ09 e foi bipado, mas não tem entrada no estoque do sistema da loja.
            - SOBRA_FISICA: foi bipado, mas não está na IQ09 da loja.
            - VENDIDO_EM_ESTOQUE: está na IQ09 da loja, mas o sistema diz VENDIDO.
            - STATUS_SAP_DIVERGENTE: foi bipado na loja, mas na IQ09 o status usuário não é de estoque da loja.

            Causas que você pode apontar (campo causaProvavel):
            - VENDIDO_SEM_BAIXA_SAP: vendido no sistema e não baixado no SAP.
            - TRANSFERENCIA_EM_TRANSITO: transferência ou remessa ainda em trânsito (ex.: DEPS NREM).
            - APARELHO_EM_OUTRA_LOJA: o IMEI aparece em outro centro da IQ09 ou em outra filial do sistema.
            - ENTRADA_SAP_SEM_ENTRADA_SISTEMA: o SAP recebeu, mas ninguém deu entrada no sistema.
            - POSSIVEL_PERDA_OU_FURTO: esperado, não localizado e sem outra explicação nos dados.
            - ERRO_DE_BIPAGEM: leitura de etiqueta errada, serial de outro produto ou duplicidade.
            - RESERVADO_RESE: reservado no SAP (RESE).
            - OUTRA: nenhuma das anteriores; explique na ação sugerida.

            Regras:
            - Responda um item para cada IMEI recebido, com o IMEI exatamente como veio. Nunca invente IMEIs.
            - Use só os dados recebidos. Confiança ALTA quando os dados apontam claramente a causa; MEDIA quando há
              indício; BAIXA quando é suposição.
            - acaoSugerida: uma frase curta e prática para a loja, em português.
            - resumo: de 3 a 5 frases em português, com as causas mais frequentes do lote e o que fazer primeiro.
            """;

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String modelo;
    private final Duration timeout;
    private final long maxTokens;
    private volatile AnthropicClient client;

    public ClaudeDiagnosticoIaClient(ObjectMapper objectMapper,
                                     @Value("${syscor.ia.api-key:}") String apiKey,
                                     @Value("${syscor.ia.modelo:claude-opus-5-5}") String modelo,
                                     @Value("${syscor.ia.timeout:120s}") Duration timeout,
                                     @Value("${syscor.ia.max-tokens:16000}") long maxTokens) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = modelo;
        this.timeout = timeout;
        this.maxTokens = maxTokens;
    }

    @Override
    public boolean disponivel() {
        return !apiKey.isEmpty();
    }

    @Override
    public RespostaDiagnosticoIa diagnosticar(List<ItemParaDiagnostico> itens) {
        StructuredMessageCreateParams<RespostaDiagnosticoIa> params = montarRequisicao(itens);
        StructuredMessage<RespostaDiagnosticoIa> resposta;
        try {
            resposta = client().messages().create(params);
        } catch (AnthropicServiceException e) {
            throw new DiagnosticoIaException("A API do Claude recusou o pedido de diagnóstico (HTTP " + e.statusCode() + ").", e);
        } catch (AnthropicIoException e) {
            throw new DiagnosticoIaException("Não foi possível falar com a API do Claude: " + e.getMessage(), e);
        }

        StopReason motivo = resposta.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(motivo)) {
            throw new RespostaIaInvalidaException("A IA recusou o diagnóstico deste lote.");
        }
        if (StopReason.MAX_TOKENS.equals(motivo)) {
            throw new RespostaIaInvalidaException("A resposta da IA foi cortada pelo limite de tokens.");
        }
        try {
            return resposta.content().stream()
                    .flatMap(bloco -> bloco.text().stream())
                    .map(texto -> texto.text())
                    .findFirst()
                    .orElseThrow(() -> new RespostaIaInvalidaException("A IA não devolveu o JSON do diagnóstico."));
        } catch (RespostaIaInvalidaException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RespostaIaInvalidaException("A IA devolveu um JSON fora do formato esperado.", e);
        }
    }

    /** Separado para os testes conferirem o pedido sem chamar a API. */
    StructuredMessageCreateParams<RespostaDiagnosticoIa> montarRequisicao(List<ItemParaDiagnostico> itens) {
        String contexto;
        try {
            contexto = objectMapper.writeValueAsString(itens);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o contexto do diagnóstico.", e);
        }
        return MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(maxTokens)
                .system(PROMPT_SISTEMA)
                // Recusa do modelo cai, no servidor, para outro modelo em vez de voltar sem diagnóstico.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .outputConfig(RespostaDiagnosticoIa.class)
                .addUserMessage("Diagnostique as divergências deste inventário. Itens (JSON):\n" + contexto)
                .build();
    }

    private AnthropicClient client() {
        AnthropicClient atual = client;
        if (atual == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(apiKey.isEmpty() ? "sem-chave" : apiKey)
                            .timeout(timeout)
                            .build();
                }
                atual = client;
            }
        }
        return atual;
    }
}
