package com.vivo4redes.syscor.venda.dto.request;

import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ItemVendaRequestDTO(

        @NotNull(message = "categoria é obrigatória (PRODUTO_VIVO, SERVICO_VIVO, ACESSORIO ou RECARGA)")
        CategoriaItemVenda categoria,

        /** Obrigatório, exceto em SERVICO_VIVO (validado no service). */
        Long produtoId,

        @NotBlank(message = "descricaoProduto é obrigatória")
        String descricaoProduto,

        @NotNull(message = "quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "quantidade deve ser maior que zero")
        BigDecimal quantidade,

        /** Pode ser 0 em SERVICO_VIVO (plano cobrado na fatura); fora de serviço deve ser maior que zero (validado no service). */
        @NotNull(message = "valorUnitario é obrigatório")
        @PositiveOrZero(message = "valorUnitario não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "valorUnitario deve ter no máximo 2 casas decimais")
        BigDecimal valorUnitario,

        String imeiOuSerial,

        String tabelaPreco,
        Boolean sva,
        Boolean seguro,
        String segmento,
        String tipoServico,
        String ddd,
        String planoAntigo,
        String plano,
        Boolean debitoAutomatico,
        BigDecimal valorAdicional,
        BigDecimal valorAcrescimo,
        BigDecimal desconto,
        Boolean cupom,
        String vencimentoFatura,
        String numeroAcesso,
        String sistemaOrigem,
        String numOrdemNext,
        String numSolicitacaoGed,
        String simcard3g,
        String simcard4g,
        Boolean clientePossuiSimcard,
        Boolean simcardDoado,
        BigDecimal descontoChip,
        BigDecimal valorChip,
        Boolean serialConfirmado
) {
}