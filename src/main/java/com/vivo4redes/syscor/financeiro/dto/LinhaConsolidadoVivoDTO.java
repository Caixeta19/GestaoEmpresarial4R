package com.vivo4redes.syscor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LinhaConsolidadoVivoDTO(
        String idTransacao,
        String acesso,
        String cliente,
        String cpf,
        String plano,
        BigDecimal valorComissao,
        LocalDate dataVenda
) {
}