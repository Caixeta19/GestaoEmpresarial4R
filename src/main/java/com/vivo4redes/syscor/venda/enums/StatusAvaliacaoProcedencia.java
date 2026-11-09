package com.vivo4redes.syscor.venda.enums;

import com.vivo4redes.syscor.util.TextoUtil;

import java.util.Arrays;
import java.util.Optional;

/** Avaliação do BKO. Literais = CHECK chk_vendas_avaliacao_procedencia (V13). Venda lançada nasce Nao_avaliado. */
public enum StatusAvaliacaoProcedencia {
    Improcedente("Improcedente"),
    Procedente("Procedente"),
    Em_avaliacao_pelo_BKO("Em avaliação pelo BKO"),
    Nao_avaliado("Não avaliado");

    private final String rotulo;

    StatusAvaliacaoProcedencia(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Aceita o literal do enum ou o rótulo, sem diferenciar acento, maiúsculas nem espaço/underscore. */
    public static Optional<StatusAvaliacaoProcedencia> deTexto(String texto) {
        String id = TextoUtil.identificador(texto);
        if (id.isEmpty()) return Optional.empty();
        return Arrays.stream(values())
                .filter(s -> TextoUtil.identificador(s.name()).equals(id) || TextoUtil.identificador(s.rotulo).equals(id))
                .findFirst();
    }
}
