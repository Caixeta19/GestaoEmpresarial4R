package com.vivo4redes.syscor.estoque.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntradaSeriaisRequestDTO {

    @NotNull(message = "ID do produto é obrigatório")
    private Long produtoId;

    @NotBlank(message = "Depósito SAP é obrigatório")
    private String depositoSap;

    @NotEmpty(message = "Ao menos um serial/IMEI deve ser informado")
    private List<String> seriais;
}