package com.vivo4redes.syscor.gestao.dto.request;

import com.vivo4redes.syscor.gestao.enums.SituacaoServico;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record ProtocoloDocumentalRequestDTO(
        String numeroProtocoloGed,
        LocalDate dataDigitalizacao,
        Boolean zerarRemuneracao,
        Boolean gerarPrice,
        String vencimento,
        String liderEquipe,
        String observacoes,
        String observacoesImportacao,
        SituacaoServico situacaoServico,
        Boolean gerarComissao,
        List<String> motivosCancelamento,
        String statusBko
) {
}