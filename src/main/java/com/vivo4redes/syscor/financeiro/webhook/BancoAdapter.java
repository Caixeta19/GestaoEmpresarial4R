package com.vivo4redes.syscor.financeiro.webhook;

import java.util.List;

public interface BancoAdapter {
    /** Valor gravado em webhook_log.provider / transacao_bancaria.provider. */
    String codigo();

    /** Autentica a origem. NUNCA lança exceção: qualquer dúvida = false. */
    boolean autenticar(RequisicaoWebhook req);

    /** Traduz o payload do banco para eventos normalizados. Lança exceção se o formato for inválido. */
    List<EventoPagamento> parse(String payload) throws Exception;
}