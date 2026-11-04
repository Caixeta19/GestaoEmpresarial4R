package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

/** Preço de um item (Nome Comercial) em uma execução, para uma tabela (Regular/Renova) e um grupo de plano. */
@Entity
@Table(name = "tb_preco_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrecoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id", nullable = false)
    private TabelaPrecoExecucao execucao;

    /** Aba de origem (SMARTPHONES, VITRINE...). */
    @Column(name = "aba", nullable = false, length = 60)
    private String aba;

    @Enumerated(EnumType.STRING)
    @Column(name = "tabela", nullable = false, length = 10)
    private TabelaPreco tabela;

    @Enumerated(EnumType.STRING)
    @Column(name = "grupo_plano", nullable = false, length = 30)
    private GrupoPlano grupoPlano;

    @Column(name = "nome_comercial", nullable = false, length = 200)
    private String nomeComercial;

    /** Nome Comercial normalizado (TextoUtil.chave) — chave de busca. */
    @Column(name = "chave_nome", nullable = false, length = 200)
    private String chaveNome;

    /** Preço cheio: à vista no crédito/débito/dinheiro e de 2x a 12x. */
    @Column(name = "preco", nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    /** PIX e Vivo Pay. */
    @Column(name = "preco_pix", precision = 12, scale = 2)
    private BigDecimal precoPix;

    /** Valor total por número de parcelas, de 13x a 21x: {13: 3662.25, ..., 21: 3818.49}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parcelas", columnDefinition = "jsonb")
    private Map<Integer, BigDecimal> parcelas;

    /** Coluna DESCONTO do bloco Renova: desconto do fabricante já aplicado no preço Renova (null em REGULAR). */
    @Column(name = "desconto_fabricante", precision = 12, scale = 2)
    private BigDecimal descontoFabricante;
}
