package com.vivo4redes.syscor.relatorio.enums;

/**
 * "Tipo de Produto" do relatório de Produtos. O nome do literal é o valor de {@code tipo_produto_filtro} da
 * vw_relatorio_itens. Produto Open não tem como ser distinguido hoje e fica fora.
 */
public enum TipoProduto {

    ACESSORIOS("Acessórios"),
    PRODUTO_VIVO("Produto Vivo"),
    RECARGA("Recarga"),
    SIM_CARD("Sim Card");

    private final String rotulo;

    TipoProduto(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
