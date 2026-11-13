package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Fechamento recusado por diferença. O detalhe (valor digitado, esperado e diferença por forma) é um JSON em texto e só
 * quem confere o vê; o gerente da loja recebe apenas quais formas divergem.
 */
@Entity
@Table(name = "tb_caixa_tentativa_fechamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaixaTentativaFechamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_sessao_id", nullable = false)
    private CaixaSessao caixaSessao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    @Builder.Default
    private Instant em = Instant.now();

    /** Dia da tentativa em America/Sao_Paulo: o limite é por caixa por dia. */
    @Column(nullable = false)
    private LocalDate dia;

    @Column(name = "detalhe_json", nullable = false, columnDefinition = "text")
    private String detalheJson;
}
