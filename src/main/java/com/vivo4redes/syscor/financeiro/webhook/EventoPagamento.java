package com.vivo4redes.syscor.financeiro.webhook;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Formato normalizado: cada banco é traduzido para isto, e o MatchingService só conhece isto. */
public record EventoPagamento(
        String provider,
        String idExterno,          // identificador único da transação no banco (idempotência)
        String identificador,      // nosso ID de correlação (titulo_financeiro.identificador_pagamento)
        String codigoBarras,       // opcional, fallback
        BigDecimal valor,
        OffsetDateTime liquidadoEm,
        String comprovanteUrl) {}