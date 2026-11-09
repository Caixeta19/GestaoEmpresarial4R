package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import org.springframework.stereotype.Component;

/**
 * Regra 3: qual tabela da Telefônica vale para o item da venda. Único lugar dessa decisão — mude aqui.
 * <p>
 * Todo item entra pela REGULAR. O aparelho passa para a RENOVA quando a venda está com o Renova ligado (número do
 * voucher no bloco Renova da aba Pagamento). Só vale para aparelho (PRODUTO_VIVO) com preço por plano: acessório,
 * chip, serviço e itens das abas sem plano (SEM_PLANO) continuam na REGULAR.
 */
@Component
public class ResolvedorTabela {

    /**
     * @param renovaAtivo    a venda está com o Renova ligado
     * @param categoria      categoria do item
     * @param grupoNaRegular grupo do plano em que o item foi encontrado na REGULAR (SEM_PLANO = aba sem plano)
     */
    public TabelaPreco resolver(boolean renovaAtivo, CategoriaItemVenda categoria, GrupoPlano grupoNaRegular) {
        return renovaAtivo && elegivelRenova(categoria, grupoNaRegular) ? TabelaPreco.RENOVA : TabelaPreco.REGULAR;
    }

    /** Aparelho com preço por plano (não vale para acessório, serviço, recarga nem itens SEM_PLANO). */
    public boolean elegivelRenova(CategoriaItemVenda categoria, GrupoPlano grupoNaRegular) {
        return categoria == CategoriaItemVenda.PRODUTO_VIVO
                && grupoNaRegular != null && grupoNaRegular != GrupoPlano.SEM_PLANO;
    }
}
