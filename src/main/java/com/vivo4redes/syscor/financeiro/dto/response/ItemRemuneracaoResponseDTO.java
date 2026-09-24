package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.enums.StatusRemuneracao;
import com.vivo4redes.syscor.financeiro.model.ItemRemuneracao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ItemRemuneracaoResponseDTO(
        Long id,
        String idTransacaoVivo,
        String numeroAcessoVivo,
        String clienteVivo,
        String cpfVivo,
        String planoVivo,
        BigDecimal valorComissao,
        LocalDate dataVendaVivo,
        StatusRemuneracao status,
        String diagnostico,
        Long vendaId
) {
    public static ItemRemuneracaoResponseDTO from(ItemRemuneracao i) {
        return new ItemRemuneracaoResponseDTO(
                i.getId(), i.getIdTransacaoVivo(), i.getNumeroAcessoVivo(), i.getClienteVivo(),
                i.getCpfVivo(), i.getPlanoVivo(), i.getValorComissao(), i.getDataVendaVivo(),
                i.getStatus(), i.getDiagnostico(), i.getVendaId()
        );
    }
}