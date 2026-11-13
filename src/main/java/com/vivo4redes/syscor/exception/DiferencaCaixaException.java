package com.vivo4redes.syscor.exception;

import java.math.BigDecimal;

/**
 * O caixa fecha com diferença e falta a observação (422). Leva o valor da diferença (contado − esperado) no corpo
 * para o front perguntar "Fechar com diferença de R$ X?" sem precisar do esperado antes de o gerente contar.
 */
public class DiferencaCaixaException extends BusinessException {

    private final BigDecimal diferenca;

    public DiferencaCaixaException(BigDecimal diferenca, String mensagem) {
        super(mensagem);
        this.diferenca = diferenca;
    }

    public BigDecimal getDiferenca() {
        return diferenca;
    }
}
