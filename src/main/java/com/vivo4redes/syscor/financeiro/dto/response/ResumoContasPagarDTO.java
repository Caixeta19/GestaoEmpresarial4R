package com.vivo4redes.syscor.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Cards da tela de contas a pagar. {@code totalEmAberto} soma as contas ABERTO e DIVERGENTE (ainda por pagar);
 * cancelada sai de todos os totais. {@code canalBancario}: DESLIGADO, AGUARDANDO, CONECTADO ou COM_ERRO.
 */
public record ResumoContasPagarDTO(BigDecimal totalEmAberto, long quantidadeEmAberto, long conciliadas,
                                   long divergencias, long vencidas, String canalBancario,
                                   OffsetDateTime ultimaSincronizacao) {
}
