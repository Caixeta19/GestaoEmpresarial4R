package com.vivo4redes.syscor.financeiro.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WebhookListener {

    private final ConciliacaoProcessor processor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoReceber(WebhookRecebidoEvent evento) {
        processor.processar(evento.logId());
    }
}