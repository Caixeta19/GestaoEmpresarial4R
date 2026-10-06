package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.model.WebhookLog;
import java.time.OffsetDateTime;

/** Sem o payload bruto (pode conter dados pessoais). */
public record WebhookLogDTO(Long id, String provider, OffsetDateTime recebidoEm, String status,
                            int tentativas, String erro, OffsetDateTime processadoEm) {
    public static WebhookLogDTO de(WebhookLog l) {
        return new WebhookLogDTO(l.getId(), l.getProvider(), l.getRecebidoEm(), l.getStatus().name(),
                l.getTentativas(), l.getErro(), l.getProcessadoEm());
    }
}