package com.vivo4redes.syscor.util;

import com.vivo4redes.syscor.estoque.model.EstoqueGeral;

import java.math.BigDecimal;

public final class EstoqueUtil {

    private EstoqueUtil() {
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

    /** Linha de acessório = tem código de acessório preenchido. */
    public static boolean ehAcessorio(EstoqueGeral e) {
        return e != null && e.getCodigoAcessorio() != null && !e.getCodigoAcessorio().isBlank();
    }
}