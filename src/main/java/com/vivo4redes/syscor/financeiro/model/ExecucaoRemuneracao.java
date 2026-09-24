package com.vivo4redes.syscor.financeiro.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Cabeçalho de cada importação do consolidado de comissão da Vivo, com os totais para os cards da tela. */
@Entity
@Table(name = "tb_execucao_remuneracao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecucaoRemuneracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_arquivo", length = 255)
    private String nomeArquivo;

    @Column(name = "total_linhas", nullable = false)
    private Integer totalLinhas;

    @Column(name = "total_liberados", nullable = false)
    private Integer totalLiberados;

    @Column(name = "total_glosados", nullable = false)
    private Integer totalGlosados;

    @Column(name = "total_pendentes_vivo", nullable = false)
    private Integer totalPendentesVivo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();
}