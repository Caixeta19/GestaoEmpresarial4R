package com.vivo4redes.syscor.financeiro.enums;

/** Situação do lançamento do extrato na conciliação. Literais = CHECK chk_extrato_lancamento_situacao (V42). */
public enum SituacaoExtrato {
    /** Recém-gravado, ainda não passou pelo matching. */
    NOVO,
    /** Baixou uma conta (automático ou confirmado pela pessoa). */
    CONCILIADO,
    /** Precisa de confirmação: vários candidatos, um sem reforço, ou valor diferente da conta. */
    AMBIGUO,
    /** Nenhuma conta casa (tarifa, IOF, transferência...). Fica na fila para vincular ou ignorar. */
    SEM_CORRESPONDENCIA,
    /** A pessoa descartou, com motivo. */
    IGNORADO
}
