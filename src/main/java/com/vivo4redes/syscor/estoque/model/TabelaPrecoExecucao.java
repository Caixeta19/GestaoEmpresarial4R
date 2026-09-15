package com.vivo4redes.syscor.estoque.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Cabeçalho de uma importação da tabela de preço da Telefônica (planilha
 * multi-aba, transação manual diária). Guarda os totais para relatório/auditoria.
 */
@Entity
@Table(name = "tb_tabela_preco_execucao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TabelaPrecoExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_arquivo", length = 255)
    private String nomeArquivo;

    /** Texto de "Vigência a partir de: DD/MM/AAAA" extraído da planilha, quando presente. */
    @Column(name = "vigencia_em", length = 20)
    private String vigenciaEm;

    @Column(name = "total_linhas_processadas", nullable = false)
    private Integer totalLinhasProcessadas;

    @Column(name = "total_produtos_atualizados", nullable = false)
    private Integer totalProdutosAtualizados;

    @Column(name = "total_nao_encontrados", nullable = false)
    private Integer totalNaoEncontrados;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();
}