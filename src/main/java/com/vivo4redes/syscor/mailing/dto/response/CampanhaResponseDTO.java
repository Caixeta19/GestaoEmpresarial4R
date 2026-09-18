package com.vivo4redes.syscor.mailing.dto.response;

import com.vivo4redes.syscor.mailing.enums.StatusCampanha;
import com.vivo4redes.syscor.mailing.model.CampanhaMailing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampanhaResponseDTO {
    private Long id;
    private String nome;
    private String templateNome;
    private String templateIdioma;
    private StatusCampanha status;
    private Integer totalDestinatarios;
    private Integer totalEnviados;
    private Integer totalFalhas;
    private Instant criadoEm;
    private List<DestinatarioResponseDTO> destinatarios;

    public static CampanhaResponseDTO resumo(CampanhaMailing c) {
        return base(c).destinatarios(null).build();
    }

    public static CampanhaResponseDTO comDestinatarios(CampanhaMailing c, List<DestinatarioResponseDTO> destinatarios) {
        return base(c).destinatarios(destinatarios).build();
    }

    private static CampanhaResponseDTOBuilder base(CampanhaMailing c) {
        return CampanhaResponseDTO.builder()
                .id(c.getId())
                .nome(c.getNome())
                .templateNome(c.getTemplateNome())
                .templateIdioma(c.getTemplateIdioma())
                .status(c.getStatus())
                .totalDestinatarios(c.getTotalDestinatarios())
                .totalEnviados(c.getTotalEnviados())
                .totalFalhas(c.getTotalFalhas())
                .criadoEm(c.getCriadoEm());
    }
}