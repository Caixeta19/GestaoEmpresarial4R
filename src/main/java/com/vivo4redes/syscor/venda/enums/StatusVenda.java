package com.vivo4redes.syscor.venda.enums;

import java.util.Set;

/**
 * Workflow de status da venda: ABERTA (carrinho) → CONCLUIDA (lançada) num passo só, pelo finalizar com a senha do
 * vendedor; CANCELADA a partir de ABERTA (carrinho expirado ou cancelado) ou de CONCLUIDA (só pelo cancelamento
 * autenticado: a avaliação Improcedente do BKO não cancela). PENDENTE e APROVADA ficam só por compatibilidade com o
 * CHECK do banco: nenhuma venda entra nesses status. Transições permitidas ficam explícitas aqui para não vazar regra de máquina de estados para os
 * services.
 */
public enum StatusVenda {

    /** Carrinho em montagem — itens sendo adicionados/removidos pelas abas da UI. Ainda não é uma venda formal. */
    ABERTA {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            return Set.of(CONCLUIDA, CANCELADA);
        }
    },
    /** Não usado: a venda é lançada direto. Só sai para CANCELADA, caso alguma venda antiga ainda esteja aqui. */
    PENDENTE {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            return Set.of(CANCELADA);
        }
    },
    /** Não usado: a venda é lançada direto. Só sai para CANCELADA, caso alguma venda antiga ainda esteja aqui. */
    APROVADA {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            return Set.of(CANCELADA);
        }
    },
    CONCLUIDA {
        @Override
        public Set<StatusVenda> transicoesPermitidas() {
            // Só pelo cancelamento com autenticação (a avaliação Improcedente não cancela)
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