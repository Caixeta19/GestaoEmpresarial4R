package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.Venda;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/** Regras do documental compartilhadas pela busca, pelo detalhe e pela exportação. */
public final class RegrasDocumental {

    private RegrasDocumental() {
    }

    /**
     * Item de serviço Vivo: tem número de acesso e serviço (SERVICO_VIVO, ou aparelho vinculado a uma linha).
     * Venda sem nenhum item assim (só aparelho avulso ou acessório) não entra no documental.
     */
    public static boolean ehItemDeServico(ItemVenda item) {
        return preenchido(item.getNumeroAcesso()) && preenchido(item.getTipoServico());
    }

    /** O item que representa a venda no documental: o primeiro de serviço, com SERVICO_VIVO na frente. */
    public static Optional<ItemVenda> itemPrincipal(Venda venda) {
        Optional<ItemVenda> deServico = venda.getItens().stream()
                .filter(RegrasDocumental::ehItemDeServico)
                .min(Comparator.comparing((ItemVenda i) -> i.getCategoria() != CategoriaItemVenda.SERVICO_VIVO));
        return deServico.isPresent() ? deServico : venda.getItens().stream().findFirst();
    }

    /** Data de lançamento: quando o vendedor finalizou; sem ela (vendas antigas), a criação. */
    public static Instant dataLancamento(Venda venda) {
        return venda.getFinalizadoEm() != null ? venda.getFinalizadoEm() : venda.getCriadoEm();
    }

    /** Venda sem avaliação gravada está na fila do BKO: Não avaliado. */
    public static StatusAvaliacaoProcedencia statusBko(Venda venda) {
        return venda.getAvaliacaoProcedencia() != null ? venda.getAvaliacaoProcedencia() : StatusAvaliacaoProcedencia.Nao_avaliado;
    }

    private static boolean preenchido(String texto) {
        return texto != null && !texto.isBlank();
    }
}
