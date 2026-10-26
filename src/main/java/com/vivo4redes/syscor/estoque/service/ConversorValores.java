package com.vivo4redes.syscor.estoque.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** Conversões tolerantes (pt-BR) de texto de planilha/CSV para os tipos da entidade. */
final class ConversorValores {

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu"));

    private static final List<DateTimeFormatter> FORMATOS_DATA_HORA = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"));

    private ConversorValores() {
    }

    /** Texto limpo (espaços colapsados); vazio, "-" e "null" viram null. */
    static String texto(String valor) {
        if (valor == null) return null;
        String s = valor.trim().replaceAll("\\s+", " ");
        return (s.isEmpty() || s.equals("-") || s.equalsIgnoreCase("null")) ? null : s;
    }

    /** Serial/IMEI: sem espaços e em maiúsculas (mesma regra do inventário SAP). */
    static String serial(String valor) {
        if (valor == null) return "";
        return valor.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** Chave de comparação de modelo (preço x estoque). */
    static String chaveModelo(String valor) {
        String t = texto(valor);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    /** "R$ 1.234,56", "1234.56", "1.234,56", "1,234.56" -> BigDecimal(2). */
    static BigDecimal decimal(String valor) {
        String s = texto(valor);
        if (s == null) return null;
        s = s.replace("R$", "").replace(" ", "");
        int ultimaVirgula = s.lastIndexOf(',');
        int ultimoPonto = s.lastIndexOf('.');
        if (ultimaVirgula >= 0 && ultimoPonto >= 0) {
            boolean virgulaDecimal = ultimaVirgula > ultimoPonto;
            s = virgulaDecimal ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (ultimaVirgula >= 0) {
            s = s.replace(".", "").replace(',', '.');
        } else if (ultimoPonto >= 0) {
            // "1.234" (milhar) x "12.5"/"12.50" (decimal)
            boolean umPonto = s.indexOf('.') == ultimoPonto;
            boolean tresDigitosDepois = s.length() - ultimoPonto - 1 == 3;
            if (!umPonto || tresDigitosDepois) s = s.replace(".", "");
        }
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("valor numérico inválido: '" + valor + "'");
        }
    }

    static Integer inteiro(String valor) {
        BigDecimal d = decimal(valor);
        return d == null ? null : d.intValue();
    }

    static LocalDate data(String valor) {
        String s = texto(valor);
        if (s == null) return null;
        for (DateTimeFormatter f : FORMATOS_DATA) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        for (DateTimeFormatter f : FORMATOS_DATA_HORA) {
            try {
                return LocalDateTime.parse(s, f).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        throw new IllegalArgumentException("data inválida: '" + valor + "' (use dd/MM/aaaa ou aaaa-MM-dd)");
    }
}