package com.vivo4redes.syscor.mailing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {
    private String apiBaseUrl = "https://graph.facebook.com/v20.0";
    private String phoneNumberId;
    private String accessToken;
    private String wabaId;
    private long rateLimitMs = 1000;
}