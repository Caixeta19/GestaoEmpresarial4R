package com.vivo4redes.syscor.gestao.dto.request;

import com.vivo4redes.syscor.gestao.enums.SituacaoServico;
import com.vivo4redes.syscor.gestao.enums.StatusGed;
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
        String statusBko,
        Boolean ativacaoPrimeiraChamada,
        LocalDate dataAtivacao,
        LocalDate dataBaixa,
        String numeroProtocoloGedPortabilidade,
        LocalDate dataInstalacao,
        StatusGed statusGed,
        String motivoReprovacao,
        Boolean servicoInstalado,
        LocalDate dataAgendadaInstalacao,
        String armario,
        Boolean comissaoAparelho,
        LocalDate dataUltimaAtivacao
) {
}