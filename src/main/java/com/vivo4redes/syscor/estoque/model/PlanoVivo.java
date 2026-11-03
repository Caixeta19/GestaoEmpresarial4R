package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import jakarta.persistence.*;
import lombok.*;

/** Plano da aba PLANOS de uma execução: plano do cliente -> grupo de preço (GRUPAMENTO ATUAL). */
@Entity
@Table(name = "tb_plano_vivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanoVivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id", nullable = false)
    private TabelaPrecoExecucao execucao;

    @Column(name = "nome_plano", nullable = false, length = 200)
    private String nomePlano;

    /** Nome do plano normalizado (TextoUtil.chave). */
    @Column(name = "chave_plano", nullable = false, length = 200)
    private String chavePlano;

    @Column(name = "sistema", length = 60)
    private String sistema;

    @Column(name = "cod_sistema", length = 60)
    private String codSistema;

    @Column(name = "cod_sap", length = 60)
    private String codSap;

    @Enumerated(EnumType.STRING)
    @Column(name = "grupo_plano", nullable = false, length = 30)
    private GrupoPlano grupoPlano;

    /** Disponível para alta/migração (coluna TIPO DE PLANO). */
    @Column(name = "disponivel_alta", nullable = false)
    private boolean disponivelAlta;
}
