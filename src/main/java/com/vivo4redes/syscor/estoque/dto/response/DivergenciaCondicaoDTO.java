package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;

import java.math.BigDecimal;

/** Valor da planilha que difere da fórmula em mais de R$ 0,01. A planilha prevalece; isto é só um alerta. */
public record DivergenciaCondicaoDTO(
        String produto,
        GrupoPlano grupo,
        TabelaPreco tabela,
        CondicaoPagamento condicao,
        BigDecimal planilha,
        BigDecimal calculado
) {
}
