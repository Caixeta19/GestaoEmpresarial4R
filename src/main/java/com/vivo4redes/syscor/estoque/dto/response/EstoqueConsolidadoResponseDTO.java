package com.vivo4redes.syscor.estoque.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstoqueConsolidadoResponseDTO {
    private Long produtoId;
    private String sku;
    private String nome;
    private String categoria;
    private Integer estoqueMinimo;
    private Integer saldoFisico;
    private String status; // Disponível, Crítico, Ruptura
    private BigDecimal precoBase;
    private List<String> seriaisDisponiveis;
}