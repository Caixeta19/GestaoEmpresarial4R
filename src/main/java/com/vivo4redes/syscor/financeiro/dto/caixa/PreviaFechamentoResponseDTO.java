package com.vivo4redes.syscor.financeiro.dto.caixa;

import java.math.BigDecimal;

/**
 * Prévia do fechamento do dinheiro: o esperado só é devolvido junto de um valor contado já informado (o caixa aberto
 * não expõe o esperado ao gerente). {@code diferenca} = contado − esperado.
 */
public record PreviaFechamentoResponseDTO(BigDecimal valorContado, BigDecimal contabilizadoEsperado, BigDecimal diferenca) {
}
