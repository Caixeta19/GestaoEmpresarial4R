package com.vivo4redes.syscor.venda.dto.request;

import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import jakarta.validation.constraints.NotNull;

public record AvaliacaoProcedenciaRequestDTO(

        @NotNull(message = "resultado é obrgatório(PROCEDENTE, IMPROCEDENTE OU EM AVALIAÇÃO PELO BKO)")
        StatusAvaliacaoProcedencia resultado


) {
}
