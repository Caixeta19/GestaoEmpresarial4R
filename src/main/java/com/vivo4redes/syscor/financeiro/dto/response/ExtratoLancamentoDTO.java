package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.enums.OrigemConciliacao;
import com.vivo4redes.syscor.financeiro.enums.SituacaoExtrato;
import com.vivo4redes.syscor.financeiro.model.ExtratoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Débito do extrato na fila de conciliação. Não expõe o payload cru do banco. */
public record ExtratoLancamentoDTO(Long id, String idExterno, LocalDate dataLancamento, BigDecimal valor,
                                   Long numeroDocumento, Integer codigoHistorico, String subHistorico,
                                   String infoComplementar, String contraparteDocumento, SituacaoExtrato situacao,
                                   Long tituloId, String motivo, OrigemConciliacao origem,
                                   OffsetDateTime conciliadoEm, List<SugestaoContaDTO> sugestoes) {

    public static ExtratoLancamentoDTO de(ExtratoLancamento l, List<SugestaoContaDTO> sugestoes) {
        return new ExtratoLancamentoDTO(l.getId(), l.getIdExterno(), l.getDataLancamento(), l.getValor(),
                l.getNumeroDocumento(), l.getCodigoHistorico(), l.getSubHistorico(), l.getInfoComplementar(),
                l.getContraparteDocumento(), l.getSituacao(), l.getTituloId(), l.getMotivo(), l.getOrigem(),
                l.getConciliadoEm(), sugestoes);
    }
}
