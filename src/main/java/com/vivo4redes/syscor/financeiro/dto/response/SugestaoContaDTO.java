package com.vivo4redes.syscor.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Conta candidata para um lançamento ambíguo. {@code reforcos}: DOCUMENTO, NOME, HISTORICO. Quando o valor do banco
 * difere do da conta ({@code divergenciaValor}), a tela mostra os dois lado a lado.
 */
public record SugestaoContaDTO(Long tituloId, String descricao, String fornecedor, LocalDate vencimento,
                               BigDecimal valor, List<String> reforcos, boolean divergenciaValor) {
}
