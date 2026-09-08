package com.vivo4redes.syscor.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AtividadeRecenteDTO(
        String numeroPedido,
        String status,
        String clienteNome,
        String vendedorNome,
        BigDecimal valorTotal,
        Instant dataHora
) {}