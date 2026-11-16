package com.vivo4redes.syscor.financeiro.dto.request;

import com.vivo4redes.syscor.financeiro.enums.FormaPagamentoTitulo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cadastro e edição de conta a pagar. Vencimento no passado é aceito. */
public record ContaPagarRequestDTO(
        @NotBlank(message = "descricao é obrigatória")
        @Size(max = 200, message = "descricao aceita até 200 caracteres")
        String descricao,

        @NotBlank(message = "fornecedor é obrigatório")
        @Size(max = 150, message = "fornecedor aceita até 150 caracteres")
        String fornecedor,

        @NotNull(message = "vencimento é obrigatório")
        LocalDate vencimento,

        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0.01", message = "valor deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "valor deve ter no máximo 2 casas decimais")
        BigDecimal valor,

        FormaPagamentoTitulo formaPagamento,

        /** Linha digitável (47/48) ou código de barras (44); grava só os dígitos. */
        String codigoBarras,

        /** CPF (11) ou CNPJ (14); aceita pontuação, grava só os dígitos. */
        String documentoFornecedor,

        Long filialId,

        @Size(max = 60, message = "categoria aceita até 60 caracteres")
        String categoria,

        @Size(max = 500, message = "observacao aceita até 500 caracteres")
        String observacao,

        /** Libera o cadastro quando já existe conta com o mesmo fornecedor + valor + vencimento. */
        Boolean confirmarDuplicada
) {
}
