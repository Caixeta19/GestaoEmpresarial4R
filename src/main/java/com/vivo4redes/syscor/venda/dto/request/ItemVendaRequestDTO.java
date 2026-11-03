package com.vivo4redes.syscor.venda.dto.request;

import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

        /**
         * Ignorado em PRODUTO_VIVO e ACESSORIO: o preço vem da tabela vigente da Telefônica (plano, tabela e pagamento).
         * Usado só em RECARGA (deve ser maior que zero). Serviço Vivo entra com 0.
         */
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
        Boolean serialConfirmado,

        /** Forma de pagamento usada no preço do item (PIX -> preço PIX; crédito em 13x–21x -> parcela). Opcional: preço cheio. */
        FormaPagamento formaPagamento,

        /** Número de parcelas no crédito (1 a 21). Opcional. */
        @Min(value = 1, message = "parcelas deve ser no mínimo 1")
        @Max(value = 21, message = "parcelas deve ser no máximo 21")
        Integer parcelas
) {
}