package com.vivo4redes.syscor.financeiro.dto.caixa;

import java.time.LocalDate;
import java.util.List;

/**
 * Situação do caixa de hoje numa filial, para a tela de Venda e para o gerente. {@code caixaId}/{@code caixaPdv} são
 * do primeiro caixa aberto; {@code caixas} lista todos (filial com mais de um caixa).
 */
public record SituacaoCaixaResponseDTO(
        boolean aberto,
        Long caixaId,
        LocalDate data,
        String caixaPdv,
        List<CaixaResumoDTO> caixas,
        /** O usuário logado pode abrir caixa nessa filial (mostra o botão "Abrir caixa"). */
        boolean podeAbrir,
        List<String> avisos
) {
    public record CaixaResumoDTO(Long id, String caixaPdv) {
    }
}
