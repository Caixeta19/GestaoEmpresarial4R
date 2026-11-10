package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.exception.NegocioException;
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

    /** Unidades desta linha presas em carrinhos abertos (acessório vendido por unidade). */
    @Column(name = "quantidade_reservada", nullable = false)
    @Builder.Default
    private Integer quantidadeReservada = 0;

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
        if (quantidadeReservada == null) quantidadeReservada = 0;
    }

    // ---- Comportamento de domínio (nomes sem get/is para não virarem propriedades)

    public boolean disponivelParaVenda() {
        return status == StatusEstoque.DISPONIVEL;
    }

    /** Item entrou no carrinho: DISPONIVEL -> RESERVADO (a linha inteira). */
    public void reservar() {
        if (status != StatusEstoque.DISPONIVEL || reservadas() > 0) {
            throw new NegocioException("O IMEI/Serial '" + serial + "' não está disponível para venda (status atual: "
                    + status + ").");
        }
        this.status = StatusEstoque.RESERVADO;
    }

    /** Venda concluída: RESERVADO -> VENDIDO, carimbando a data da venda. */
    public void confirmarVenda() {
        if (status != StatusEstoque.RESERVADO) {
            throw new NegocioException("O IMEI/Serial '" + serial + "' não está reservado para esta venda (status atual: "
                    + status + ").");
        }
        this.status = StatusEstoque.VENDIDO;
        this.dataVenda = LocalDateTime.now();
    }

    /**
     * Item removido do carrinho ou venda cancelada: RESERVADO/VENDIDO -> DISPONIVEL.
     * Já DISPONIVEL não muda nada; DEFEITO nunca volta à venda por este caminho.
     */
    public void liberar() {
        if (status == StatusEstoque.DISPONIVEL) return;
        if (status == StatusEstoque.DEFEITO) {
            throw new NegocioException("O IMEI/Serial '" + serial + "' está com DEFEITO e não pode voltar a DISPONIVEL.");
        }
        this.status = StatusEstoque.DISPONIVEL;
        this.dataVenda = null;
    }

    // ---- Venda por unidade (acessório: um serial por SKU, com quantidade N)

    /** Unidades que ainda podem entrar num carrinho. */
    public int quantidadeLivre() {
        if (status != StatusEstoque.DISPONIVEL) return 0;
        return Math.max(qtd() - reservadas(), 0);
    }

    /** Uma leitura do serial no carrinho: prende 1 unidade; a linha só vira RESERVADO quando não sobra nenhuma livre. */
    public void reservarUnidade() {
        if (quantidadeLivre() <= 0) {
            throw new NegocioException("O serial '" + serial + "' não tem unidade disponível para venda (status atual: "
                    + status + ", em estoque: " + qtd() + ", reservadas: " + reservadas() + ").");
        }
        quantidadeReservada = reservadas() + 1;
        if (qtd() - reservadas() == 0) status = StatusEstoque.RESERVADO;
    }

    /** Venda concluída: a unidade reservada sai do estoque; a linha só vira VENDIDO quando a quantidade zera. */
    public void confirmarVendaUnidade() {
        if (reservadas() <= 0 && status == StatusEstoque.RESERVADO) {
            confirmarVenda(); // reserva antiga da linha inteira (antes da venda por unidade)
            return;
        }
        if (reservadas() <= 0) {
            throw new NegocioException("O serial '" + serial + "' não tem unidade reservada para esta venda.");
        }
        quantidadeReservada = reservadas() - 1;
        quantidade = Math.max(qtd() - 1, 0);
        dataVenda = LocalDateTime.now();
        recalcularStatusPelaQuantidade();
    }

    /** Item removido do carrinho ou carrinho cancelado: a unidade reservada volta a ficar livre. */
    public void liberarUnidadeReservada() {
        if (reservadas() <= 0) {
            if (status == StatusEstoque.RESERVADO) liberar(); // reserva antiga da linha inteira
            return;
        }
        quantidadeReservada = reservadas() - 1;
        recalcularStatusPelaQuantidade();
    }

    /** Venda concluída cancelada: a unidade vendida volta para a linha. */
    public void devolverUnidadeVendida() {
        if (status == StatusEstoque.DEFEITO) {
            throw new NegocioException("O IMEI/Serial '" + serial + "' está com DEFEITO e não pode voltar a DISPONIVEL.");
        }
        quantidade = qtd() + 1;
        recalcularStatusPelaQuantidade();
    }

    /**
     * Status da linha vendida por unidade a partir das quantidades: sem unidade = VENDIDO; todas presas em carrinho =
     * RESERVADO; sobra alguma = DISPONIVEL. DEFEITO não muda.
     */
    public void recalcularStatusPelaQuantidade() {
        if (status == StatusEstoque.DEFEITO) return;
        if (qtd() == 0) {
            status = reservadas() > 0 ? StatusEstoque.RESERVADO : StatusEstoque.VENDIDO;
        } else if (qtd() - reservadas() <= 0) {
            status = StatusEstoque.RESERVADO;
        } else {
            status = StatusEstoque.DISPONIVEL;
        }
    }

    private int qtd() {
        return quantidade == null ? 1 : quantidade;
    }

    private int reservadas() {
        return quantidadeReservada == null ? 0 : quantidadeReservada;
    }
}
