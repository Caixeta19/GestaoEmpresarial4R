package com.vivo4redes.syscor.estoque.enums;

import com.vivo4redes.syscor.util.TextoUtil;

import java.util.Arrays;
import java.util.Optional;

/**
 * Grupo de preço do plano do cliente (colunas das abas SMARTPHONES / ELETRÔNICOS_LP_Conectados e
 * GRUPAMENTO ATUAL da aba PLANOS). Literais = CHECKs chk_preco_item_grupo_plano / chk_plano_vivo_grupo_plano (V24).
 */
public enum GrupoPlano {
    PRE,
    CONTROLE_BTL,
    CONTROLE_ENTRADA,
    CONTROLE_ALTO_VALOR,
    POS_INDIVIDUAL,
    FAMILIA_2,
    FAMILIA_3,
    FAMILIA_4_5,
    VIVO_V,
    MULTIVIVO,
    VIM_8GB,
    VIM_14GB,
    VIM_20GB,
    VIM_40GB,
    VIM_100GB,
    /** Itens cujo preço não depende de plano (Não Conectados, Vitrine, Demo). */
    SEM_PLANO;

    /** Rótulo da planilha -> grupo: "PRÉ" -> PRE, "FAMILIA 4/5" -> FAMILIA_4_5, "PÓS INDIVIDUAL" -> POS_INDIVIDUAL. */
    public static Optional<GrupoPlano> deRotulo(String rotulo) {
        String id = TextoUtil.identificador(rotulo);
        return Arrays.stream(values()).filter(g -> g.name().equals(id)).findFirst();
    }
}
