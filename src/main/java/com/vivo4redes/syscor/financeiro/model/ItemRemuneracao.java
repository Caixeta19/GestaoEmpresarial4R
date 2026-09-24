package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.StatusRemuneracao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resultado do cruzamento de uma linha do consolidado Vivo contra o protocolo documental da venda. */
@Entity
@Table(name = "tb_item_remuneracao", indexes = {
        @Index(name = "idx_item_remuneracao_execucao", columnList = "execucao_id"),
        @Index(name = "idx_item_remuneracao_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemRemuneracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id", nullable = false)
    private ExecucaoRemuneracao execucao;

    @Column(name = "id_transacao_vivo", length = 50)
    private String idTransacaoVivo;

    @Column(name = "numero_acesso_vivo", length = 20)
    private String numeroAcessoVivo;

    @Column(name = "cliente_vivo", length = 150)
    private String clienteVivo;

    @Column(name = "cpf_vivo", length = 20)
    private String cpfVivo;

    @Column(name = "plano_vivo", length = 100)
    private String planoVivo;

    @Column(name = "valor_comissao", precision = 12, scale = 2)
    private BigDecimal valorComissao;

    @Column(name = "data_venda_vivo")
    private LocalDate dataVendaVivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRemuneracao status;

    @Column(length = 300)
    private String diagnostico;

    /** Referência à venda quando encontrada por numeroAcesso — nulo quando não localizada. */
    @Column(name = "venda_id")
    private Long vendaId;
}