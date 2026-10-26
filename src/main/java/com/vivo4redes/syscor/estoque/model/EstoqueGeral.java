package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Unidade física de estoque (uma linha = um serial/IMEI). É a única fonte da
 * verdade para catálogo, controle unitário e baixa no PDV.
 * <p>
 * Regras (README): nasce {@code DISPONIVEL} no depósito {@code DP01};
 * o serial é único no sistema; {@code @Version} garante que duas baixas
 * concorrentes no mesmo serial não se silenciem.
 * <p>
 * Igualdade por chave de negócio ({@code serial}), não por todos os campos.
 */
@Entity
@Table(name = "tb_estoque_geral", indexes = {
        @Index(name = "idx_estoque_geral_filial_status", columnList = "filial_estoque, status"),
        @Index(name = "idx_estoque_geral_codigo_acessorio", columnList = "codigo_acessorio")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EstoqueGeral {

    public static final String DEPOSITO_PADRAO = "DP01";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ---- Identificação e classificação
    @Column(name = "tipo_produto", length = 60)
    private String tipoProduto;

    @Column(name = "categoria", length = 80)
    private String categoria;

    @Column(name = "subcategoria", length = 80)
    private String subcategoria;

    @Column(name = "marca", length = 80)
    private String marca;

    @Column(name = "modelo_comercial", length = 200)
    private String modeloComercial;

    @Column(name = "modelo_dpgc", length = 200)
    private String modeloDpgc;

    @Column(name = "cor", length = 60)
    private String cor;

    @Column(name = "ncm", length = 20)
    private String ncm;

    // ---- Controle físico / unitário
    @EqualsAndHashCode.Include
    @Column(name = "serial", nullable = false, unique = true, length = 60)
    private String serial;

    @Column(name = "codigo_acessorio", length = 60)
    private String codigoAcessorio;

    @Column(name = "numero_acesso", length = 30)
    private String numeroAcesso;

    // ---- Logística e compras
    @Column(name = "filial_estoque", length = 100)
    private String filialEstoque;

    @Column(name = "num_compra", length = 40)
    private String numCompra;

    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(name = "data_emissao_nota")
    private LocalDate dataEmissaoNota;

    @Column(name = "filial_compra", length = 100)
    private String filialCompra;

    @Column(name = "fornecedor_compra", length = 200)
    private String fornecedorCompra;

    // ---- Métricas temporais
    @Column(name = "tempo_na_rede", length = 50)
    private String tempoNaRede;

    @Column(name = "dias_na_rede")
    private Integer diasNaRede;

    @Column(name = "tempo_no_pdv", length = 50)
    private String tempoNoPdv;

    @Column(name = "dias_no_pdv")
    private Integer diasNoPdv;

    @Column(name = "quantidade", nullable = false)
    @Builder.Default
    private Integer quantidade = 1;

    // ---- Valores e custos
    @Column(name = "valor_venda", precision = 12, scale = 2)
    private BigDecimal valorVenda;

    @Column(name = "valor_custo", precision = 12, scale = 2)
    private BigDecimal valorCusto;

    @Column(name = "custo_ultima_compra_un", precision = 12, scale = 2)
    private BigDecimal custoUltimaCompraUn;

    @Column(name = "custo_medio_un", precision = 12, scale = 2)
    private BigDecimal custoMedioUn;

    // ---- Controle operacional
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatusEstoque status = StatusEstoque.DISPONIVEL;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "data_venda")
    private LocalDateTime dataVenda;

    /** Lock otimista: concorrência entre baixa, estorno e importações. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (status == null) status = StatusEstoque.DISPONIVEL;
        if (filialEstoque == null || filialEstoque.isBlank()) filialEstoque = DEPOSITO_PADRAO;
        if (quantidade == null) quantidade = 1;
    }

    // ---- Comportamento de domínio (nomes sem get/is para não virarem propriedades)

    public boolean disponivelParaVenda() {
        return status == StatusEstoque.DISPONIVEL;
    }

    /** DISPONIVEL -> VENDIDO, carimbando a data da venda. */
    public void baixarPorVenda() {
        this.status = StatusEstoque.VENDIDO;
        this.dataVenda = LocalDateTime.now();
    }

    /** Desfaz a baixa (item removido do carrinho ou venda cancelada). */
    public void estornarBaixa() {
        this.status = StatusEstoque.DISPONIVEL;
        this.dataVenda = null;
    }
}