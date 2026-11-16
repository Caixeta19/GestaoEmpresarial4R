package com.vivo4redes.syscor.financeiro.enums;

import java.util.Set;

/**
 * Status do título financeiro. Literais = CHECK chk_titulo_status (V41). "Vencida" não é status: é derivada
 * (ABERTO com vencimento no passado). As transições ficam aqui para não vazar a máquina de estados para os services.
 */
public enum StatusTitulo {

    /** "Pendente" na tela. */
    ABERTO {
        @Override
        public Set<StatusTitulo> transicoesPermitidas() {
            return Set.of(BAIXADO, DIVERGENTE, CANCELADO);
        }
    },
    BAIXADO {
        @Override
        public Set<StatusTitulo> transicoesPermitidas() {
            return Set.of(ABERTO);          // só reabrindo (com justificativa)
        }
    },
    /** Valor do banco difere do valor da conta: não baixa, vai para análise. */
    DIVERGENTE {
        @Override
        public Set<StatusTitulo> transicoesPermitidas() {
            return Set.of(ABERTO, BAIXADO, CANCELADO);
        }
    },
    CANCELADO {
        @Override
        public Set<StatusTitulo> transicoesPermitidas() {
            return Set.of();
        }
    };

    public abstract Set<StatusTitulo> transicoesPermitidas();

    public boolean podeTransicionarPara(StatusTitulo novo) {
        return transicoesPermitidas().contains(novo);
    }

    /** Só ABERTO e DIVERGENTE aceitam edição e cancelamento. */
    public boolean editavel() {
        return this == ABERTO || this == DIVERGENTE;
    }
}
