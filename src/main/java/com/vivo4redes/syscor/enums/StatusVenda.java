package com.vivo4redes.syscor.enums;

import java.util.Set;

/**
 * Workflow de status da venda (US-302): ABERTA (carrinho) → PENDENTE →
 * APROVADA → CONCLUIDA, com CANCELADA acessível a partir de qualquer
 * estado não-terminal. Transições permitidas ficam explícitas aqui para
 * não vazar regra de máquina de estados para os services.
 */
public enum StatusVenda {

    /** Carrinho em montagem — itens sendo adicionados/removidos pelas abas da UI. Ainda não é uma venda formal. */
    ABERTA {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            return Set.of(PENDENTE, CANCELADA);
        }
    },
    PENDENTE {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            return Set.of(APROVADA, CANCELADA);
        }
    },
    APROVADA {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            return Set.of(CONCLUIDA, CANCELADA);
        }
    },
    CONCLUIDA {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            // Só sai de CONCLUIDA via avaliação de procedência (improcedente -> CANCELADA)
            return Set.of(CANCELADA);
        }
    },
    CANCELADA {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            return Set.of();
        }
    };

    public abstract Set<StatusVenda> transicoesPermitidas();

    public boolean podeTransicionarPara(StatusVenda novoStatus) {
        return transicoesPermitidas().contains(novoStatus);
    }
}