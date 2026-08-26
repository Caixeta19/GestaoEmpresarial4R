package com.vivo4redes.syscor.dto.request;

import com.vivo4redes.syscor.enums.StatusAvaliacaoProcedencia;
import jakarta.validation.constraints.NotNull;

public record AvaliacaoProcedenciaRequestDTO(

        @NotNull(message = "resultado é obrgatório(PROCEDENTE, IMPROCEDENTE OU EM AVALIAÇÃO PELO BKO)")
        StatusAvaliacaoProcedencia resultado


) {
}
