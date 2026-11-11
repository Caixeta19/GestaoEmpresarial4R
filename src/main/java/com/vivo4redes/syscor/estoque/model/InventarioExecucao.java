package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.StatusInventario;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Uma contagem de inventário da filial: IQ09 importada na abertura, bipagens durante a contagem e resultado final no
 * fechamento. Execuções antigas (só conciliação IQ09 x sistema) ficam FECHADO e sem filial.
 */
@Entity
@Table(name = "tb_inventario_execucao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filial_id")
    private Filial filial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private StatusInventario status = StatusInventario.ABERTO;

    @Column(name = "nome_arquivo", length = 255)
    private String nomeArquivo;

    @Column(name = "total_linhas_sap", nullable = false)
    @Builder.Default
    private Integer totalLinhasSap = 0;

    @Column(name = "linhas_ignoradas_outro_centro", nullable = false)
    @Builder.Default
    private Integer linhasIgnoradasOutroCentro = 0;

    @Column(name = "linhas_sem_serial", nullable = false)
    @Builder.Default
    private Integer linhasSemSerial = 0;

    /** Centros do arquivo sem filial cadastrada, separados por vírgula. */
    @Column(name = "centros_sem_filial", length = 2000)
    private String centrosSemFilial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aberto_por_id")
    private Usuario abertoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fechado_por_id")
    private Usuario fechadoPor;

    @Column(name = "fechado_em")
    private Instant fechadoEm;

    /** Execuções antigas: quem processou (texto livre). */
    @Column(name = "executado_por")
    private String executadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();

    // ---- Totais gravados no fechamento (durante a contagem a tela usa a contagem ao vivo)
    @Column(name = "total_esperados", nullable = false) @Builder.Default private Integer totalEsperados = 0;
    @Column(name = "total_bipados", nullable = false) @Builder.Default private Integer totalBipados = 0;
    @Column(name = "total_pendentes", nullable = false) @Builder.Default private Integer totalPendentes = 0;
    @Column(name = "total_conferidos", nullable = false) @Builder.Default private Integer totalConferidos = 0;
    @Column(name = "total_falta", nullable = false) @Builder.Default private Integer totalFalta = 0;
    @Column(name = "total_sem_entrada_sistema", nullable = false) @Builder.Default private Integer totalSemEntradaSistema = 0;
    @Column(name = "total_sobra_fisica", nullable = false) @Builder.Default private Integer totalSobraFisica = 0;
    @Column(name = "total_vendido_em_estoque", nullable = false) @Builder.Default private Integer totalVendidoEmEstoque = 0;
    @Column(name = "total_so_no_sistema", nullable = false) @Builder.Default private Integer totalSoNoSistema = 0;
    @Column(name = "total_status_sap_divergente", nullable = false) @Builder.Default private Integer totalStatusSapDivergente = 0;

    // ---- Execuções antigas (V5)
    @Column(name = "total_corretos", nullable = false) @Builder.Default private Integer totalCorretos = 0;
    @Column(name = "total_divergencias", nullable = false) @Builder.Default private Integer totalDivergencias = 0;

    // ---- Diagnóstico da IA
    @Column(name = "resumo_ia", columnDefinition = "TEXT")
    private String resumoIa;

    @Column(name = "diagnosticado_em")
    private Instant diagnosticadoEm;
}
