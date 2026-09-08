package com.vivo4redes.syscor.dashboard.dto;
import java.math.BigDecimal;

public record EvolucaoMensalDTO(
        String mesRotulo, // "Mar", "Abr", "Mai", "Jun", "Jul", "Ago"
        BigDecimal valor,
        String valorFormatadoK // "48k", "53k", etc.
) {}