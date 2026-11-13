package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.financeiro.enums.TipoLancamentoCaixa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Sangria ("para depósito em conta") ou suprimento ("resgatado de uma conta") de dinheiro. Informe a conta ou a descrição.
 * {@code chaveIdempotencia} é gerada pelo front a cada formulário: repetir a mesma chave devolve o lançamento já feito em
 * vez de criar outro (duplo clique).
 */
public record LancamentoRequestDTO(
        @NotNull(message = "tipo é obrigatório (SANGRIA ou SUPRIMENTO)")
        TipoLancamentoCaixa tipo,

        @NotNull(message = "valor é obrigatório")
        BigDecimal valor,

        @Size(max = 200, message = "descricao aceita até 200 caracteres")
        String descricao,

        @Size(max = 100, message = "conta aceita até 100 caracteres")
        String conta,

        @Size(max = 300, message = "observacao aceita até 300 caracteres")
        String observacao,

        @NotBlank(message = "chaveIdempotencia é obrigatória")
        @Size(max = 80, message = "chaveIdempotencia aceita até 80 caracteres")
        String chaveIdempotencia
) {
}
