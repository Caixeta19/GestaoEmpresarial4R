package com.vivo4redes.syscor.estoque.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "estoque")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Estoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_produto")
    private String tipoProduto;

    private String categoria;
    private String subcategoria;

    @Column(name = "filial_estoque")
    private String filialEstoque;

    @Column(name = "num_compra")
    private String numCompra;

    @Column(name = "data_compra")
    private String dataCompra;

    @Column(name = "data_emissao_nota")
    private String dataEmissaoNota;

    @Column(name = "filial_compra")
    private String filialCompra;

    @Column(name = "fornecedor_compra")
    private String fornecedorCompra;

    private String ncm;
    private String marca;

    @Column(name = "modelo_comercial")
    private String modeloComercial;

    @Column(name = "modelo_dpgc")
    private String modeloDpgc;

    private String cor;
    private String serial;

    @Column(name = "codigo_acessorio")
    private String codigoAcessorio;

    @Column(name = "numero_acesso")
    private String numeroAcesso;

    @Column(name = "tempo_na_rede")
    private String tempoNaRede;

    @Column(name = "dias_na_rede")
    private String diasNaRede;

    @Column(name = "tempo_no_pdv")
    private String tempoNoPdv;

    @Column(name = "dias_no_pdv")
    private String diasNoPdv;

    private BigDecimal quantidade;

    @Column(name = "valor_venda")
    private BigDecimal valorVenda;

    @Column(name = "valor_custo")
    private BigDecimal valorCusto;

    @Column(name = "custo_ultima_compra_un")
    private BigDecimal custoUltimaCompraUn;

    @Column(name = "custo_medio_un")
    private BigDecimal custoMedioUn;

    @Column(name = "valor_unit_desconto_compra")
    private BigDecimal valorUnitDescontoCompra;

    @Column(name = "valor_unit_despesa_acessoria_compra")
    private BigDecimal valorUnitDespesaAcessoriaCompra;

    @Column(name = "valor_unit_bruto_compra")
    private BigDecimal valorUnitBrutoCompra;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;
}