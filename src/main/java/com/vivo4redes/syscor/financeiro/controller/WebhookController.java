package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.service.WebhookIngestService;
import com.vivo4redes.syscor.financeiro.webhook.BancoRegistry;
import com.vivo4redes.syscor.financeiro.webhook.RequisicaoWebhook;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints PÚBLICOS (sem login), protegidos por assinatura/segredo.
 * Recebe o corpo como byte[] para validar a assinatura sobre os bytes exatos.
 * Respostas deliberadamente "mudas": não revelam o motivo da recusa.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookIngestService ingest;
    private final BancoRegistry bancos;

    @PostMapping("/banco-brasil")
    public ResponseEntity<Void> bancoDoBrasil(HttpServletRequest req, @RequestBody byte[] body) {
        return tratar("BANCO_BRASIL", req, body);
    }

    @PostMapping("/banco-proprio")
    public ResponseEntity<Void> bancoProprio(HttpServletRequest req, @RequestBody byte[] body) {
        return tratar("BANCO_PROPRIO", req, body);
    }

    private ResponseEntity<Void> tratar(String banco, HttpServletRequest req, byte[] body) {
        try {
            var resultado = ingest.receber(bancos.get(banco), montar(req, body));
            return switch (resultado) {
                case ACEITO -> ResponseEntity.accepted().build();                 // 202: processamento é assíncrono
                case DUPLICADO -> ResponseEntity.ok().build();                    // 200: banco para de reenviar
                case NAO_AUTORIZADO -> ResponseEntity.status(401).build();
                case INVALIDO -> ResponseEntity.badRequest().build();
            };
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok().build();                                   // retentativa simultânea já gravada
        }
    }

    private static RequisicaoWebhook montar(HttpServletRequest req, byte[] body) {
        Map<String, String> headers = new HashMap<>();
        for (String nome : Collections.list(req.getHeaderNames())) headers.put(nome.toLowerCase(), req.getHeader(nome));
        Map<String, String> params = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> { if (v.length > 0) params.put(k, v[0]); });
        return new RequisicaoWebhook(body, headers, params, req.getRemoteAddr()); // use server.forward-headers-strategy=native atrás de proxy
    }
}