package com.vivo4redes.syscor.gestao.dto.response;

import com.vivo4redes.syscor.gestao.enums.SituacaoServico;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProtocoloDocumentalResponseDTO(
        Long protocoloId,
        Long vendaId,
        Long numeroVenda,
        String servico,
        String plano,
        String clienteNome,
        String clienteDocumento,
        String vendedorNome,
        Instant dataVenda,
        String numeroAcesso,
        StatusAvaliacaoProcedencia statusBko,
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
        List<String> motivosCancelamento
) {
}