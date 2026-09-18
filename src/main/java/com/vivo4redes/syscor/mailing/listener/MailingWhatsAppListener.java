package com.vivo4redes.syscor.mailing.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.mailing.client.WhatsAppApiClient;
import com.vivo4redes.syscor.mailing.config.MailingRabbitConfig;
import com.vivo4redes.syscor.mailing.config.WhatsAppProperties;
import com.vivo4redes.syscor.mailing.dto.EnviarMailingMessage;
import com.vivo4redes.syscor.mailing.enums.StatusCampanha;
import com.vivo4redes.syscor.mailing.enums.StatusEnvio;
import com.vivo4redes.syscor.mailing.model.CampanhaMailing;
import com.vivo4redes.syscor.mailing.model.DestinatarioMailing;
import com.vivo4redes.syscor.mailing.repository.CampanhaMailingRepository;
import com.vivo4redes.syscor.mailing.repository.DestinatarioMailingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MailingWhatsAppListener {

    private final DestinatarioMailingRepository destinatarioRepository;
    private final CampanhaMailingRepository campanhaRepository;
    private final WhatsAppApiClient whatsAppApiClient;
    private final WhatsAppProperties whatsAppProperties;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MailingRabbitConfig.QUEUE_ENVIO_WHATSAPP, concurrency = "1")
    public void processar(EnviarMailingMessage mensagem) {
        DestinatarioMailing destinatario = destinatarioRepository.findById(mensagem.destinatarioId()).orElse(null);
        if (destinatario == null) {
            log.warn("Destinatário {} não encontrado — mensagem descartada.", mensagem.destinatarioId());
            return;
        }
        if (destinatario.getStatus() != StatusEnvio.PENDENTE) {
            return;
        }

        enviarParaUmDestinatario(destinatario);
        atualizarContadoresDaCampanha(destinatario.getCampanha().getId());

        aguardarIntervaloMinimo();
    }

    private void enviarParaUmDestinatario(DestinatarioMailing destinatario) {
        try {
            List<String> parametros = desserializarParametros(destinatario.getParametrosTemplateJson());
            String messageId = whatsAppApiClient.enviarTemplate(
                    destinatario.getTelefone(),
                    destinatario.getCampanha().getTemplateNome(),
                    destinatario.getCampanha().getTemplateIdioma(),
                    parametros);

            destinatario.setStatus(StatusEnvio.ENVIADO);
            destinatario.setWhatsappMessageId(messageId);
            destinatario.setEnviadoEm(Instant.now());
            destinatario.setErro(null);
        } catch (Exception e) {
            destinatario.setStatus(StatusEnvio.FALHA);
            destinatario.setErro(resumir(e.getMessage()));
            log.warn("Falha ao enviar mailing para destinatário {}: {}", destinatario.getId(), e.getMessage());
        }
        destinatarioRepository.save(destinatario);
    }

    private void atualizarContadoresDaCampanha(Long campanhaId) {
        CampanhaMailing campanha = campanhaRepository.findById(campanhaId).orElse(null);
        if (campanha == null) return;

        long enviados = destinatarioRepository.countByCampanhaIdAndStatus(campanhaId, StatusEnvio.ENVIADO);
        long falhas = destinatarioRepository.countByCampanhaIdAndStatus(campanhaId, StatusEnvio.FALHA);
        long pendentes = destinatarioRepository.countByCampanhaIdAndStatus(campanhaId, StatusEnvio.PENDENTE);

        campanha.setTotalEnviados((int) enviados);
        campanha.setTotalFalhas((int) falhas);
        if (pendentes == 0) {
            campanha.setStatus(falhas > 0 && enviados == 0 ? StatusCampanha.FALHOU : StatusCampanha.CONCLUIDA);
        }
        campanhaRepository.save(campanha);
    }

    private List<String> desserializarParametros(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Não foi possível desserializar parâmetros do template: {}", e.getMessage());
            return List.of();
        }
    }

    private String resumir(String mensagem) {
        if (mensagem == null) return "Erro desconhecido";
        return mensagem.length() > 490 ? mensagem.substring(0, 490) + "..." : mensagem;
    }

    private void aguardarIntervaloMinimo() {
        try {
            Thread.sleep(whatsAppProperties.getRateLimitMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}