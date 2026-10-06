package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.StatusWebhook;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "webhook_log")
@Getter @Setter @NoArgsConstructor
public class WebhookLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String provider;
    private String payloadSha256;
    @Column(columnDefinition = "text")
    private String payloadRaw;
    private String ipOrigem;
    private boolean assinaturaValida;
    @Enumerated(EnumType.STRING)
    private StatusWebhook status;
    private int tentativas;
    @Column(columnDefinition = "text")
    private String erro;
    private OffsetDateTime recebidoEm = OffsetDateTime.now();
    private OffsetDateTime processadoEm;

    public void concluir() {
        status = StatusWebhook.PROCESSADO;
        erro = null;
        processadoEm = OffsetDateTime.now();
    }

    public void registrarErro(String mensagem) {
        status = StatusWebhook.ERRO;
        tentativas++;
        erro = mensagem == null ? "erro desconhecido" : mensagem.substring(0, Math.min(mensagem.length(), 1000));
    }
}