package com.vivo4redes.syscor.mailing.dto.response;

import com.vivo4redes.syscor.mailing.enums.StatusEnvio;
import com.vivo4redes.syscor.mailing.model.DestinatarioMailing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinatarioResponseDTO {
    private Long id;
    private Long clienteId;
    private String telefone;
    private StatusEnvio status;
    private String whatsappMessageId;
    private String erro;
    private Instant enviadoEm;

    public static DestinatarioResponseDTO from(DestinatarioMailing d) {
        return DestinatarioResponseDTO.builder()
                .id(d.getId())
                .clienteId(d.getClienteId())
                .telefone(d.getTelefone())
                .status(d.getStatus())
                .whatsappMessageId(d.getWhatsappMessageId())
                .erro(d.getErro())
                .enviadoEm(d.getEnviadoEm())
                .build();
    }
}