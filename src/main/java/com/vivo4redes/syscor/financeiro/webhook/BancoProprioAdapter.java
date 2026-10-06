package com.vivo4redes.syscor.financeiro.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Headers:  X-Timestamp: <epoch segundos>   X-Signature: sha256=<hex(HMAC_SHA256(secret, timestamp + "." + corpo))>
 * Corpo:    {"eventId":"evt_1","tipo":"PAGAMENTO_LIQUIDADO","identificador":"PAG-000123",
 *            "valor":8400.00,"liquidadoEm":"2026-10-06T14:30:00-03:00","comprovanteUrl":"https://..."}
 */
@Component
public class BancoProprioAdapter implements BancoAdapter {

    private final byte[] segredo;
    private final long toleranciaSegundos;
    private final ObjectMapper mapper;

    public BancoProprioAdapter(@Value("${syscor.webhooks.banco-proprio.secret}") String segredo,
                               @Value("${syscor.webhooks.tolerancia-segundos:300}") long tolerancia,
                               ObjectMapper mapper) {
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        this.toleranciaSegundos = tolerancia;
        this.mapper = mapper;
    }

    @Override public String codigo() { return "BANCO_PROPRIO"; }

    @Override
    public boolean autenticar(RequisicaoWebhook req) {
        try {
            String ts = req.header("x-timestamp");
            String sig = req.header("x-signature");
            if (ts == null || sig == null || !sig.startsWith("sha256=")) return false;
            if (Math.abs(Instant.now().getEpochSecond() - Long.parseLong(ts)) > toleranciaSegundos) return false; // replay

            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            msg.write(ts.getBytes(StandardCharsets.UTF_8));
            msg.write('.');
            msg.write(req.body());                                   // bytes EXATOS, antes de qualquer parse

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            byte[] esperado = mac.doFinal(msg.toByteArray());
            byte[] recebido = HexFormat.of().parseHex(sig.substring("sha256=".length()));
            return MessageDigest.isEqual(esperado, recebido);        // comparação em tempo constante
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<EventoPagamento> parse(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        if (!"PAGAMENTO_LIQUIDADO".equals(n.path("tipo").asText())) return List.of();   // ignora outros eventos
        return List.of(new EventoPagamento(
                codigo(),
                exigir(n, "eventId"),
                exigir(n, "identificador"),
                null,
                new BigDecimal(exigir(n, "valor")),
                OffsetDateTime.parse(exigir(n, "liquidadoEm")),
                n.path("comprovanteUrl").asText(null)));
    }

    private static String exigir(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        if (v == null || v.isNull() || v.asText().isBlank()) throw new IllegalArgumentException("campo ausente: " + campo);
        return v.asText();
    }
}