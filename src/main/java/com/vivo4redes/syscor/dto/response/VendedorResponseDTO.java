package com.vivo4redes.syscor.dto.response;

import com.vivo4redes.syscor.model.Vendedor;

public record VendedorResponseDTO(Long id, String nome, String email, boolean ativo) {
    public static VendedorResponseDTO from(Vendedor v) {
        return new VendedorResponseDTO(v.getId(), v.getNome(), v.getEmail(), v.isAtivo());
    }
}
