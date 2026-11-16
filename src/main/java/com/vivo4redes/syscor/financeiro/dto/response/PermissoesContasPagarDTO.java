package com.vivo4redes.syscor.financeiro.dto.response;

/** O que o usuário logado pode fazer em contas a pagar (o backend confere de novo em cada ação). */
public record PermissoesContasPagarDTO(boolean consultar, boolean gerenciar, boolean baixaManual,
                                       boolean sincronizar) {
}
