package com.vivo4redes.syscor.util;

import java.text.Normalizer;
import java.util.Locale;

/** Normalizações de texto usadas para casar planilhas da Vivo/Telefônica com o nosso cadastro. */
public final class TextoUtil {

    private TextoUtil() {
    }

    /** Sem acento, minúsculas, espaços e quebras de linha colapsados, trim. Para cabeçalhos e rótulos. */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** Chave de nome comercial/plano: sem acento, MAIÚSCULAS, espaços únicos, trim. */
    public static String chave(String texto) {
        return normalizar(texto).toUpperCase(Locale.ROOT);
    }

    /** Rótulo -> identificador de enum: "FAMÍLIA 4/5" -> "FAMILIA_4_5", "Pré" -> "PRE". */
    public static String identificador(String texto) {
        return chave(texto).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
