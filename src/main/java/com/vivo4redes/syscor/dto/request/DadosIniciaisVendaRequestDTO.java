package com.vivo4redes.syscor.dto.request;
import com.vivo4redes.syscor.dto.AutenticacaoVendedorDTO;
import com.vivo4redes.syscor.enums.StatusScoreCliente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Edita os campos da tela "Início" de uma venda já aberta (trocar cliente,
 * filial, flag de estoque avançado, score, número de série/nota do
 * comprovante). Exige reautenticação do vendedor, como qualquer
 * salvar/alterar nessa tela.
 */
public record DadosIniciaisVendaRequestDTO(

        @NotNull(message = "clienteId é obrigatório")
        Long clienteId,

        @NotNull(message = "filialId é obrigatório")
        Long filialId,

        @NotNull(message = "estoqueAvancado é obrigatório (true/false)")
        Boolean estoqueAvancado,

        StatusScoreCliente statusScoreCliente,

        String numeroSerieNota,

        String numeroNota,

        @Valid
        @NotNull(message = "autenticacaoVendedor é obrigatória")
        AutenticacaoVendedorDTO autenticacaoVendedor
) {
}
