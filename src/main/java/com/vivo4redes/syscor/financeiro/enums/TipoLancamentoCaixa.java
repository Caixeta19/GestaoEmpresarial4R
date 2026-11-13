package com.vivo4redes.syscor.financeiro.enums;

/** Tipo do lançamento de dinheiro no caixa. Literais = CHECK chk_caixa_lancamento_tipo (V40). */
public enum TipoLancamentoCaixa {
    /** Retirada de dinheiro do caixa (para depósito em conta): sai do esperado do dinheiro. */
    SANGRIA,
    /** Dinheiro adicionado ao caixa (resgatado de uma conta): entra no esperado do dinheiro. */
    SUPRIMENTO,
    /** Contrapartida de um lançamento errado: desfaz o efeito dele, sem apagar a linha. */
    ESTORNO
}
