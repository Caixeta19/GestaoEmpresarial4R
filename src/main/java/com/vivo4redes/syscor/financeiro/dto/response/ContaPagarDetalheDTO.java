package com.vivo4redes.syscor.financeiro.dto.response;

import java.util.List;

/** Detalhe da conta a pagar com o histórico de auditoria. */
public record ContaPagarDetalheDTO(TituloPagarDTO conta, String justificativaBaixa, String motivoCancelamento,
                                   List<HistoricoTituloDTO> historico) {
}
