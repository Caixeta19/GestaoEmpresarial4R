package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CaixaSessaoResponseDTO(
        Long id,
        Long filialId,
        String filialNome,
        String caixaPdv,
        LocalDate dataAbertura,
        StatusCaixaSessao status,
        Instant abertoEm,
        Instant fechadoEm,
        List<FormaPagamentoCaixaResponseDTO> formas
) {
}