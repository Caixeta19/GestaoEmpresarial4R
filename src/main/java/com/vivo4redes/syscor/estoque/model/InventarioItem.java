package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Um serial da execução: veio da IQ09 da filial, do estoque da filial, ou foi bipado fora da lista. O cruzamento é
 * sempre pelo {@code serialNormalizado} (exato, sem IA).
 */
@Entity
@Table(name = "tb_inventario_item", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventario_item_execucao_serial", columnNames = {"execucao_id", "serial_normalizado"}))
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

    @Column(name = "serial_normalizado", length = 60)
    private String serialNormalizado;

    // ---- IQ09
    @Column(name = "material_sap", length = 40)
    private String materialSap;

    /** Texto breve do material (IQ09) ou, sem IQ09, o modelo comercial do estoque. */
    @Column(name = "denominacao_sap", length = 200)
    private String denominacaoSap;

    /** Serial como veio (IQ09, estoque ou leitura). */
    @Column(name = "serial_sap", length = 50)
    private String serialSap;

    @Column(name = "centro_sap", length = 20)
    private String centroSap;

    @Column(name = "deposito_sap", length = 20)
    private String depositoSap;

    /** Status do sistema SAP (DEPS, DEPS NREM...). */
    @Column(name = "status_sap", length = 30)
    private String statusSap;

    /** Status usuário SAP (ADEP, RLIB, AECL, RESE ou vazio). */
    @Column(name = "status_usuario_sap", length = 30)
    private String statusUsuarioSap;

    @Column(name = "tipo_estoque_sap", length = 10)
    private String tipoEstoqueSap;

    @Column(name = "categoria_equipamento", length = 10)
    private String categoriaEquipamento;

    @Column(name = "modificado_em_sap")
    private LocalDate modificadoEmSap;

    @Column(name = "modificado_por_sap", length = 40)
    private String modificadoPorSap;

    // ---- Esperado
    @Column(name = "esperado_iq09", nullable = false)
    private boolean esperadoIq09;

    @Column(name = "esperado_sistema", nullable = false)
    private boolean esperadoSistema;

    // ---- Estoque do sistema (qualquer filial)
    @Column(name = "item_estoque_id")
    private Long itemEstoqueId;

    @Column(name = "status_sistema", length = 20)
    private String statusSistema;

    @Column(name = "filial_sistema", length = 100)
    private String filialSistema;

    // ---- Contagem física
    @Column(name = "bipado_em")
    private Instant bipadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bipado_por_id")
    private Usuario bipadoPor;

    @Column(length = 300)
    private String justificativa;

    // ---- Resultado
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResultadoConciliacaoInventario resultado;

    @Column(length = 300)
    private String observacao;

    // ---- Diagnóstico da IA
    @Column(name = "causa_provavel", length = 60)
    private String causaProvavel;

    @Column(name = "confianca_ia", length = 5)
    private String confiancaIa;

    @Column(name = "acao_sugerida", length = 500)
    private String acaoSugerida;

    @Column(name = "diagnosticado_em")
    private Instant diagnosticadoEm;

    public boolean bipado() {
        return bipadoEm != null;
    }

    public boolean esperado() {
        return esperadoIq09 || esperadoSistema;
    }
}
