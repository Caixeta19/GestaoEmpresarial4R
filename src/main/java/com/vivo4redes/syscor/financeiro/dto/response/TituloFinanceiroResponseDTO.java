package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TituloFinanceiroResponseDTO(
        Long id,
        TipoTitulo tipo,
        String descricao,
        String quem,
        LocalDate vencimento,
        BigDecimal valor,
        StatusTitulo status,
        boolean vencida,
        LocalDate dataBaixa,
        Long vendaId
) {
    public static TituloFinanceiroResponseDTO from(TituloFinanceiro t) {
        boolean vencida = t.getStatus() == StatusTitulo.ABERTO && t.getVencimento().isBefore(LocalDate.now());
        return new TituloFinanceiroResponseDTO(
                t.getId(), t.getTipo(), t.getDescricao(), t.getQuem(), t.getVencimento(),
                t.getValor(), t.getStatus(), vencida, t.getDataBaixa(), t.getVendaId()
        );
    }
}