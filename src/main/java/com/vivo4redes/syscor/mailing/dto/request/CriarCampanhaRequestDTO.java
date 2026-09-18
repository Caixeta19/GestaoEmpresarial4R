package com.vivo4redes.syscor.mailing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriarCampanhaRequestDTO {

    @NotBlank(message = "Nome da campanha é obrigatório")
    private String nome;

    @NotBlank(message = "Nome do template (já aprovado na Meta) é obrigatório")
    private String templateNome;

    private String templateIdioma;

    @NotEmpty(message = "Informe ao menos um destinatário")
    private List<@Valid DestinatarioRequestDTO> destinatarios;
}