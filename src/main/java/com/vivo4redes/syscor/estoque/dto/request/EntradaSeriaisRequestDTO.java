package com.vivo4redes.syscor.estoque.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Entrada de seriais/IMEIs novos no estoque.
 * {@code produtoId} é o id de uma linha já existente de tb_estoque_geral do mesmo modelo
 * (os dados de catálogo dela são copiados para cada serial novo).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EntradaSeriaisRequestDTO {

    @NotNull(message = "produtoId é obrigatório.")
    private Long produtoId;

    @NotEmpty(message = "Informe ao menos um IMEI/serial.")
    private List<String> seriais;

    /** Depósito/filial de destino; se vazio, usa o da linha de referência. */
    @Size(max = 100, message = "depositoSap deve ter no máximo 100 caracteres.")
    private String depositoSap;
}