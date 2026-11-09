package com.vivo4redes.syscor.gestao.enums;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.util.TextoUtil;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tipo de plano do relatório documental, derivado do item da venda: pelo {@link GrupoPlano} e, quando ele não existe
 * (ou é SEM_PLANO), pelo segmento gravado pela tela de Venda ("Pré", "Controle", "Pós").
 */
public enum TipoPlano {

    PRE("Pré", "pre", EnumSet.of(GrupoPlano.PRE)),
    CONTROLE("Controle", "controle",
            EnumSet.of(GrupoPlano.CONTROLE_BTL, GrupoPlano.CONTROLE_ENTRADA, GrupoPlano.CONTROLE_ALTO_VALOR)),
    POS("Pós", "pos", EnumSet.of(GrupoPlano.POS_INDIVIDUAL, GrupoPlano.FAMILIA_2, GrupoPlano.FAMILIA_3,
            GrupoPlano.FAMILIA_4_5, GrupoPlano.VIVO_V, GrupoPlano.MULTIVIVO, GrupoPlano.VIM_8GB, GrupoPlano.VIM_14GB,
            GrupoPlano.VIM_20GB, GrupoPlano.VIM_40GB, GrupoPlano.VIM_100GB));

    private final String rotulo;
    private final String prefixoSegmento;
    private final Set<GrupoPlano> grupos;

    TipoPlano(String rotulo, String prefixoSegmento, Set<GrupoPlano> grupos) {
        this.rotulo = rotulo;
        this.prefixoSegmento = prefixoSegmento;
        this.grupos = grupos;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Segmento normalizado (sem acento, minúsculo) que começa com este prefixo é deste tipo: "Pós" -> "pos". */
    public String prefixoSegmento() {
        return prefixoSegmento;
    }

    public Set<GrupoPlano> grupos() {
        return grupos;
    }

    /** O grupo de preço manda; sem grupo (ou SEM_PLANO), vale o segmento. */
    public static Optional<TipoPlano> de(GrupoPlano grupo, String segmento) {
        if (grupo != null && grupo != GrupoPlano.SEM_PLANO) {
            return Arrays.stream(values()).filter(t -> t.grupos.contains(grupo)).findFirst();
        }
        String seg = TextoUtil.normalizar(segmento);
        if (seg.isEmpty()) return Optional.empty();
        return Arrays.stream(values()).filter(t -> seg.startsWith(t.prefixoSegmento)).findFirst();
    }
}
