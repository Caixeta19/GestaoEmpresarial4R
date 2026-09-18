package com.vivo4redes.syscor.mailing.model;

import com.vivo4redes.syscor.mailing.enums.StatusEnvio;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "tb_destinatario_mailing", indexes = {
        @Index(name = "idx_destinatario_mailing_campanha", columnList = "campanha_id"),
        @Index(name = "idx_destinatario_mailing_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinatarioMailing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private CampanhaMailing campanha;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(nullable = false, length = 20)
    private String telefone;

    @Column(name = "parametros_template", columnDefinition = "TEXT")
    private String parametrosTemplateJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusEnvio status = StatusEnvio.PENDENTE;

    @Column(name = "whatsapp_message_id", length = 100)
    private String whatsappMessageId;

    @Column(length = 500)
    private String erro;

    @Column(name = "enviado_em")
    private Instant enviadoEm;
}