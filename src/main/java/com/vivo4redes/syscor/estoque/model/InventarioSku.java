package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.OrigemSku;
import com.vivo4redes.syscor.estoque.enums.ResultadoSku;
import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha da conferência por SKU da execução: MB52 (quantidade do SAP) x IQ09 x sistema x contagem.
 * {@code materialNormalizado} nulo = linha agrupada dos itens sem SKU identificado.
 */
@Entity
@Table(name = "tb_inventario_sku")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id", nullable = false)
    private InventarioExecucao execucao;

    @Column(name = "material_normalizado", length = 40)
    private String materialNormalizado;

    /** Material como veio da MB52 (ou da IQ09/estoque, se não houver linha na MB52). */
    @Column(name = "material_sap", length = 40)
    private String materialSap;

    @Column(length = 200)
    private String denominacao;

    @Column(length = 10)
    private String unidade;

    // ---- MB52
    @Column(name = "tem_linha_mb52", nullable = false)
    private boolean temLinhaMb52;

    @Column(name = "qtd_mb52_livre", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal qtdMb52Livre = BigDecimal.ZERO;

    @Column(name = "qtd_mb52_qualidade", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal qtdMb52Qualidade = BigDecimal.ZERO;

    @Column(name = "qtd_mb52_bloqueado", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal qtdMb52Bloqueado = BigDecimal.ZERO;

    @Column(name = "qtd_mb52_transito", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal qtdMb52Transito = BigDecimal.ZERO;

    /** Valor utilização livre ÷ quantidade livre (estimativa). */
    @Column(name = "valor_unitario_sap", precision = 16, scale = 4)
    private BigDecimal valorUnitarioSap;

    // ---- Demais fontes
    @Column(name = "qtd_iq09", nullable = false)
    @Builder.Default
    private Integer qtdIq09 = 0;

    @Column(name = "qtd_sistema", nullable = false)
    @Builder.Default
    private Integer qtdSistema = 0;

    @Column(name = "qtd_bipada", nullable = false)
    @Builder.Default
    private Integer qtdBipada = 0;

    // ---- Contagem manual (acessório com quantidade)
    @Column(name = "qtd_contagem_manual", precision = 15, scale = 3)
    private BigDecimal qtdContagemManual;

    @Column(name = "contagem_justificativa", length = 300)
    private String contagemJustificativa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contado_por_id")
    private Usuario contadoPor;

    @Column(name = "contado_em")
    private Instant contadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_sku", length = 20)
    private OrigemSku origemSku;

    // ---- Resultado
    @Column(name = "dif_sap_sistema", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal difSapSistema = BigDecimal.ZERO;

    @Column(name = "dif_sap_iq09", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal difSapIq09 = BigDecimal.ZERO;

    /** Contagem efetiva − MB52; nulo enquanto não contou. */
    @Column(name = "dif_contagem", precision = 15, scale = 3)
    private BigDecimal difContagem;

    /** Estimativa: valor unitário SAP × (MB52 − sistema). */
    @Column(name = "valor_diferenca", precision = 16, scale = 2)
    private BigDecimal valorDiferenca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ResultadoSku resultado = ResultadoSku.CONFERIDO;

    @Column(length = 300)
    private String observacao;

    /** Itens deste SKU com o SKU estimado pela tabela de preços (só para a observação; não é gravado). */
    @Transient
    private int unidadesEstimadas;

    public boolean naoIdentificado() {
        return materialNormalizado == null;
    }

    /** Contou = contagem manual ou pelo menos uma bipagem. */
    public boolean contou() {
        return qtdContagemManual != null || (qtdBipada != null && qtdBipada > 0);
    }

    /** Contagem manual, se houver; senão o número de bipagens. */
    public BigDecimal contagemEfetiva() {
        if (qtdContagemManual != null) return qtdContagemManual;
        return BigDecimal.valueOf(qtdBipada == null ? 0 : qtdBipada);
    }
}
