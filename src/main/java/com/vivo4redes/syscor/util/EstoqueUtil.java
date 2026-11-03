package com.vivo4redes.syscor.util;

import com.vivo4redes.syscor.estoque.model.EstoqueGeral;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

public final class EstoqueUtil {

    /**
     * Valores que planilhas/exportações usam no lugar de célula vazia (o Excel grava '' ou "" quando
     * a célula tem só aspas). Comparados depois de trim; "null" sem diferenciar maiúsculas.
     */
    private static final Set<String> MARCADORES_DE_VAZIO = Set.of("", "''", "\"\"", "'", "\"", "-", "null");

    private EstoqueUtil() {
    }

    /** Texto ausente: null, em branco ou um marcador de vazio ('', "", ', ", -, null). */
    public static boolean textoVazio(String valor) {
        return valor == null || MARCADORES_DE_VAZIO.contains(valor.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * "R$ 4.230,45" -> 4230.45 ; "4230.45" -> 4230.45 ; vazio ou inválido -> null.
     * Use onde o valor ainda chegar como texto (ex.: importação de planilha).
     */
    public static BigDecimal paraBigDecimal(String texto) {
        if (texto == null) return null;
        String s = texto.replaceAll("[^\\d,.-]", "");
        if (s.isBlank()) return null;
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");   // formato brasileiro
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Tira espaços e o apóstrofo que o Excel coloca na frente de números gravados como texto. */
    public static String limparSerial(String serial) {
        if (serial == null) return "";
        return serial.replace("'", "").replaceAll("\\s+", "");
    }

    /** Linha de acessório = tem código de acessório preenchido de verdade (marcadores de vazio não contam). */
    public static boolean ehAcessorio(EstoqueGeral e) {
        return e != null && !textoVazio(e.getCodigoAcessorio());
    }
}