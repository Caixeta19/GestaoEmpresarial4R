package com.vivo4redes.syscor.estoque.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * US-206: cada upload do relatório SAP (IQ09) gera uma execução, com os
 * totalizadores que alimentam os cards da tela ("corretos", "divergência",
 * "pendentes"). Os itens individuais ficam em {@link InventarioItem}.
 */
@Entity
@Table(name = "tb_inventario_execucao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_arquivo", length = 255)
    private String nomeArquivo;

    @Column(name = "total_linhas_sap", nullable = false)
    private Integer totalLinhasSap;

    @Column(name = "total_corretos", nullable = false)
    private Integer totalCorretos;

    @Column(name = "total_divergencias", nullable = false)
    private Integer totalDivergencias;

    @Column(name = "total_pendentes", nullable = false)
    private Integer totalPendentes;

    @Column(name = "executado_por")
    private String executadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();
}