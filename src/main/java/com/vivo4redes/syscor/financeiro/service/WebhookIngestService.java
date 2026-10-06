package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.enums.StatusWebhook;
import com.vivo4redes.syscor.financeiro.model.WebhookLog;
import com.vivo4redes.syscor.financeiro.repository.WebhookLogRepository;
import com.vivo4redes.syscor.financeiro.webhook.BancoAdapter;
import com.vivo4redes.syscor.financeiro.webhook.RequisicaoWebhook;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caminho RÁPIDO do webhook: autentica, valida formato, grava o log e responde.
 * O processamento pesado acontece depois (WebhookListener → ConciliacaoProcessor).
 */
@Service
@RequiredArgsConstructor
public class WebhookIngestService {

    public enum Resultado { ACEITO, DUPLICADO, NAO_AUTORIZADO, INVALIDO }

    private final WebhookLogRepository logs;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public Resultado receber(BancoAdapter adapter, RequisicaoWebhook req) {
        String sha = sha256(req.body());

        if (!adapter.autenticar(req)) {
            registrarRejeitado(adapter, req, sha);
            return Resultado.NAO_AUTORIZADO;
        }

        String raw = new String(req.body(), StandardCharsets.UTF_8);
        try {
            adapter.parse(raw);                        // só valida a estrutura; não toca no banco
        } catch (Exception e) {
            return Resultado.INVALIDO;
        }

        if (logs.existsByProviderAndPayloadSha256AndStatusNot(adapter.codigo(), sha, StatusWebhook.REJEITADO)) {
            return Resultado.DUPLICADO;                // o banco reenviou: confirma sem reprocessar
        }

        WebhookLog log = new WebhookLog();
        log.setProvider(adapter.codigo());
        log.setPayloadSha256(sha);
        log.setPayloadRaw(raw);
        log.setIpOrigem(req.ip());
        log.setAssinaturaValida(true);
        log.setStatus(StatusWebhook.RECEBIDO);
        logs.saveAndFlush(log);                        // corrida entre retentativas → violação do índice único (tratada no controller)

        publisher.publishEvent(new WebhookRecebidoEvent(log.getId()));   // dispara só DEPOIS do commit
        return Resultado.ACEITO;
    }

    private void registrarRejeitado(BancoAdapter adapter, RequisicaoWebhook req, String sha) {
        WebhookLog log = new WebhookLog();
        log.setProvider(adapter.codigo());
        log.setPayloadSha256(sha);
        String corpo = new String(req.body(), StandardCharsets.UTF_8);
        log.setPayloadRaw(corpo.substring(0, Math.min(corpo.length(), 2000)));   // trunca: evita encher o banco com lixo
        log.setIpOrigem(req.ip());
        log.setAssinaturaValida(false);
        log.setStatus(StatusWebhook.REJEITADO);
        logs.save(log);
    }

    private static String sha256(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}