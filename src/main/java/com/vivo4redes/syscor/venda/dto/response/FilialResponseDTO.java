package com.vivo4redes.syscor.venda.dto.response;

import com.vivo4redes.syscor.venda.model.Filial;

public record FilialResponseDTO(Long id, String codigo, String nome, boolean ativo) {
    public static FilialResponseDTO from(Filial f) {
        return new FilialResponseDTO(f.getId(), f.getCodigo(), f.getNome(), f.isAtivo());
    }
}