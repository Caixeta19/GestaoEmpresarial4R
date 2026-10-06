package com.vivo4redes.syscor.financeiro.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ATENÇÃO: formato do payload e autenticação seguem o webhook Pix padrão (BACEN):
 *   {"pix":[{"endToEndId":"E...","txid":"...","valor":"8400.00","horario":"2026-10-06T17:30:00.000Z"}]}
 * CONFIRME no portal do BB qual API você usa (Pix, Pagamentos em Lote, Boletos) e ajuste parse().
 *
 * Camadas: (1) mTLS no gateway/Nginx, (2) allowlist de IP, (3) segredo na URL (…/banco-brasil?hmac=SEGREDO).
 */
@Component
public class BancoDoBrasilAdapter implements BancoAdapter {

    private final byte[] segredo;
    private final Set<String> ipsPermitidos;
    private final ObjectMapper mapper;

    public BancoDoBrasilAdapter(@Value("${syscor.webhooks.banco-brasil.secret}") String segredo,
                                @Value("${syscor.webhooks.banco-brasil.ips-permitidos:}") String ips,
                                ObjectMapper mapper) {
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        this.ipsPermitidos = Arrays.stream(ips.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.mapper = mapper;
    }

    @Override public String codigo() { return "BANCO_BRASIL"; }

    @Override
    public boolean autenticar(RequisicaoWebhook req) {
        if (!ipsPermitidos.isEmpty() && !ipsPermitidos.contains(req.ip())) return false;
        String recebido = req.param("hmac");
        return recebido != null && MessageDigest.isEqual(segredo, recebido.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<EventoPagamento> parse(String payload) throws Exception {
        JsonNode pix = mapper.readTree(payload).path("pix");
        if (!pix.isArray()) throw new IllegalArgumentException("payload sem 'pix'");
        List<EventoPagamento> eventos = new ArrayList<>();
        for (JsonNode p : pix) {
            eventos.add(new EventoPagamento(
                    codigo(),
                    p.path("endToEndId").asText(),
                    p.path("txid").asText(null),
                    null,
                    new BigDecimal(p.path("valor").asText()),
                    OffsetDateTime.parse(p.path("horario").asText()),
                    null));
        }
        return eventos;
    }
}