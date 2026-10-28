package com.vivo4redes.syscor.estoque.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntradaSeriaisRequestDTO {

    @NotNull(message = "O ID do produto de referência é obrigatório.")
    private Long produtoId;

    @NotEmpty(message = "A lista de seriais/IMEIs não pode estar vazia.")
    private List<String> seriais;

    private String depositoSap;
}