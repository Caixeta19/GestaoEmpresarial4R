package com.vivo4redes.syscor.estoque.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstoqueConsolidadoResponseDTO {

    private Long produtoId;
    private String sku;
    private String nome;
    private String categoria;
    private Integer estoqueMinimo;
    private Integer saldoFisico;
    private String status;
    private BigDecimal precoBase;
    private List<String> seriaisDisponiveis;
}