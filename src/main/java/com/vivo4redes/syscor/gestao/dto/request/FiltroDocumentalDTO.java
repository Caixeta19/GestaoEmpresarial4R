package com.vivo4redes.syscor.gestao.dto.request;

import com.vivo4redes.syscor.gestao.enums.TipoPlano;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;

import java.time.LocalDate;
import java.util.List;

/**
 * Filtros da busca e da exportação documental. Período = data de lançamento da venda (finalizado_em, ou criado_em
 * se vazio), com as duas pontas inclusivas. Campos nulos ou listas vazias não filtram.
 */
public record FiltroDocumentalDTO(
        LocalDate dataInicio,
        LocalDate dataFim,
        List<Long> filialIds,
        Long vendedorId,
        Long numeroVenda,
        String numeroAcesso,
        String numeroProtocoloGed,
        String liderEquipe,
        StatusAvaliacaoProcedencia statusBko,
        List<TipoPlano> tiposPlano,
        List<String> servicos,
        boolean incluirCanceladas
) {
}
