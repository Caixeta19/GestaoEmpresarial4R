package com.vivo4redes.syscor.estoque.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Leitura do leitor de código de barras, da câmera ou digitada. */
public record BipagemRequestDTO(
        @NotBlank(message = "Informe o serial/IMEI lido.")
        @Size(max = 60, message = "Serial com no máximo 60 caracteres.")
        String serial
) {
}
