package com.vivo4redes.syscor.financeiro.webhook;

import java.util.Map;

/** Requisição HTTP já "achatada" (headers em minúsculas) para os adapters não dependerem do Servlet. */
public record RequisicaoWebhook(byte[] body, Map<String, String> headers, Map<String, String> params, String ip) {
    public String header(String nome) { return headers.get(nome.toLowerCase()); }
    public String param(String nome) { return params.get(nome); }
}