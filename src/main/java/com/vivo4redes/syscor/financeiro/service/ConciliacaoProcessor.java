package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.dto.response.ConciliacaoEventoDTO;
import com.vivo4redes.syscor.financeiro.enums.StatusWebhook;
import com.vivo4redes.syscor.financeiro.model.WebhookLog;
import com.vivo4redes.syscor.financeiro.repository.WebhookLogRepository;
import com.vivo4redes.syscor.financeiro.webhook.BancoRegistry;
import com.vivo4redes.syscor.financeiro.webhook.EventoPagamento;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Processa um webhook_log. A tabela funciona como "outbox": se o app cair entre o commit e o
 * processamento, o job abaixo reprocessa. Tudo de um log é atômico (uma transação).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliacaoProcessor {

    private static final int MAX_TENTATIVAS = 5;

    private final WebhookLogRepository logs;
    private final BancoRegistry bancos;
    private final MatchingService matching;
    private final TransactionTemplate tx;
    private final ConciliacaoStream stream;

    public void processar(Long logId) {
        List<ConciliacaoEventoDTO> notificacoes;
        try {
            notificacoes = tx.execute(status -> executar(logId));
        } catch (Exception ex) {
            log.error("Falha ao processar webhook_log {}", logId, ex);
            tx.executeWithoutResult(s -> logs.findById(logId).ifPresent(l -> l.registrarErro(ex.getMessage())));
            return;
        }
        if (notificacoes != null) notificacoes.forEach(stream::publicar);    // só após o commit
    }

    private List<ConciliacaoEventoDTO> executar(Long logId) {
        WebhookLog wl = logs.findByIdForUpdate(logId).orElseThrow();
        if (wl.getStatus() == StatusWebhook.PROCESSADO || wl.getStatus() == StatusWebhook.REJEITADO) return List.of();

        List<ConciliacaoEventoDTO> resultado = new ArrayList<>();
        try {
            for (EventoPagamento ev : bancos.get(wl.getProvider()).parse(wl.getPayloadRaw())) {
                resultado.add(matching.conciliar(ev, wl));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        wl.concluir();
        return resultado;
    }

    /** Reenfileira manualmente (botão "Reprocessar" na tela de logs). */
    public void reprocessar(Long logId) {
        tx.executeWithoutResult(s -> logs.findById(logId).ifPresent(l -> {
            if (l.getStatus() == StatusWebhook.ERRO) { l.setStatus(StatusWebhook.RECEBIDO); l.setTentativas(0); }
        }));
        processar(logId);
    }

    @Scheduled(fixedDelayString = "${syscor.webhooks.reprocesso-ms:60000}")
    public void reprocessarPendentes() {
        logs.findPendentes(List.of(StatusWebhook.RECEBIDO, StatusWebhook.ERRO), MAX_TENTATIVAS,
                        OffsetDateTime.now().minusMinutes(1), PageRequest.of(0, 50))
                .forEach(l -> processar(l.getId()));
    }
}