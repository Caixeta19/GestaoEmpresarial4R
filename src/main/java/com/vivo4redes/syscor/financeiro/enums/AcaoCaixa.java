package com.vivo4redes.syscor.financeiro.enums;

/** Ação gravada no histórico do caixa. Literais = CHECK chk_caixa_historico_acao (V38). */
public enum AcaoCaixa {
    ABRIU,
    FECHOU,
    CONFEROU,
    REABRIU,
    /** Fechamento recusado por diferença (gravado à parte, mesmo com o 422). */
    TENTATIVA_FECHAMENTO,
    /** Sangria ou suprimento lançado (V40). */
    LANCAMENTO,
    /** Estorno de um lançamento (V40). */
    ESTORNO
}
