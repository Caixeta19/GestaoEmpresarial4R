package com.vivo4redes.syscor.financeiro.dto.caixa;

import java.util.List;

public record PaginaCaixaResponseDTO(
        List<CaixaSessaoResponseDTO> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
