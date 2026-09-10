package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import jakarta.persistence.*;
import lombok.*;

/** US-206: resultado da conciliação de uma linha (um serial) do relatório SAP. */
@Entity
@Table(name = "tb_inventario_item", indexes = {
        @Index(name = "idx_inventario_item_execucao", columnList = "execucao_id"),
        @Index(name = "idx_inventario_item_resultado", columnList = "resultado")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id", nullable = false)
    private InventarioExecucao execucao;

    @Column(name = "material_sap", length = 40)
    private String materialSap;

    @Column(name = "denominacao_sap", length = 200)
    private String denominacaoSap;

    @Column(name = "serial_sap", length = 50)
    private String serialSap;

    @Column(name = "centro_sap", length = 20)
    private String centroSap;

    @Column(name = "deposito_sap", length = 20)
    private String depositoSap;

    @Column(name = "status_sap", length = 30)
    private String statusSap;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoConciliacaoInventario resultado;

    @Column(length = 300)
    private String observacao;

    /** Referência ao ItemEstoque nosso, quando o serial foi encontrado — nulo se PENDENTE. */
    @Column(name = "item_estoque_id")
    private Long itemEstoqueId;
}