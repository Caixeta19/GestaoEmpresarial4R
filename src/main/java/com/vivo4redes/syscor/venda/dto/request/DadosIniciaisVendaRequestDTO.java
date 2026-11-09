package com.vivo4redes.syscor.venda.dto.request;
import com.vivo4redes.syscor.venda.enums.StatusScoreCliente;
import jakarta.validation.constraints.NotNull;

/**
 * Edita os campos da tela "Início" de uma venda ABERTA (trocar cliente,
 * filial, flag de estoque avançado, score, número de série/nota do
 * comprovante). Não pede senha, e uma autenticação enviada pelo front antigo
 * é ignorada.
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

        String numeroNota
) {
}
