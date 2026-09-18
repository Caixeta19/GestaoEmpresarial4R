package com.vivo4redes.syscor.mailing.client;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.mailing.config.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class WhatsAppApiClient {

    private final WhatsAppProperties props;
    private WebClient webClient;

    private WebClient client() {
        if (webClient == null) {
            webClient = WebClient.builder()
                    .baseUrl(props.getApiBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getAccessToken())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return webClient;
    }

    @SuppressWarnings("unchecked")
    public String enviarTemplate(String telefone, String templateNome, String templateIdioma, List<String> parametros) {
        if (props.getPhoneNumberId() == null || props.getPhoneNumberId().isBlank()
                || props.getAccessToken() == null || props.getAccessToken().isBlank()) {
            throw new NegocioException("Credenciais da API do WhatsApp não configuradas (WHATSAPP_PHONE_NUMBER_ID / WHATSAPP_ACCESS_TOKEN).");
        }

        Map<String, Object> body = montarPayload(telefone, templateNome, templateIdioma, parametros);

        try {
            Map<String, Object> resposta = client().post()
                    .uri("/{phoneNumberId}/messages", props.getPhoneNumberId())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(20));

            if (resposta == null) {
                throw new NegocioException("A API do WhatsApp não retornou resposta.");
            }
            List<Map<String, Object>> mensagens = (List<Map<String, Object>>) resposta.get("messages");
            if (mensagens == null || mensagens.isEmpty()) {
                throw new NegocioException("Resposta da API do WhatsApp sem 'messages': " + resposta);
            }
            return String.valueOf(mensagens.get(0).get("id"));
        } catch (WebClientResponseException e) {
            throw new NegocioException("WhatsApp API retornou erro " + e.getStatusCode() + ": " + resumirCorpoErro(e), e);
        }
    }

    private Map<String, Object> montarPayload(String telefone, String templateNome, String templateIdioma, List<String> parametros) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateNome);
        template.put("language", Map.of("code", templateIdioma == null ? "pt_BR" : templateIdioma));

        if (parametros != null && !parametros.isEmpty()) {
            List<Map<String, String>> parametrosBody = new ArrayList<>();
            for (String valor : parametros) {
                parametrosBody.add(Map.of("type", "text", "text", valor == null ? "" : valor));
            }
            template.put("components", List.of(Map.of(
                    "type", "body",
                    "parameters", parametrosBody
            )));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", telefone);
        body.put("type", "template");
        body.put("template", template);
        return body;
    }

    private String resumirCorpoErro(WebClientResponseException e) {
        String corpo = e.getResponseBodyAsString();
        return corpo.length() > 300 ? corpo.substring(0, 300) + "..." : corpo;
    }
}