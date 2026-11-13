package com.vivo4redes.syscor.venda.dto.request;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * {@code caixaSessaoId} só é preciso quando a filial tem mais de um caixa aberto hoje; com um só, a venda vai para ele.
 */
public record FinalizarVendaRequestDTO(

        @Valid
        @NotNull(message = "autenticacaoVendedor é obrigatória")
        AutenticacaoUsuarioDTO autenticacaoVendedor,

        Long caixaSessaoId
) {
    public FinalizarVendaRequestDTO(AutenticacaoUsuarioDTO autenticacaoVendedor) {
        this(autenticacaoVendedor, null);
    }
}
