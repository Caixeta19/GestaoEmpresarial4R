package com.vivo4redes.syscor.estoque.model;

import jakarta.persistence.*;
import lombok.*;

/** Produto da aba PRODUTOS de uma execução: Cod_Sap -> Nome Comercial. */
@Entity
@Table(name = "tb_produto_vivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoVivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id", nullable = false)
    private TabelaPrecoExecucao execucao;

    @Column(name = "cod_sap", nullable = false, length = 60)
    private String codSap;

    @Column(name = "descricao_sap", length = 255)
    private String descricaoSap;

    @Column(name = "modelo_dpgc", length = 200)
    private String modeloDpgc;

    @Column(name = "cor", length = 60)
    private String cor;

    @Column(name = "nome_comercial", nullable = false, length = 200)
    private String nomeComercial;

    /** Nome Comercial normalizado (TextoUtil.chave). */
    @Column(name = "chave_nome", nullable = false, length = 200)
    private String chaveNome;

    @Column(name = "categoria", length = 80)
    private String categoria;
}
