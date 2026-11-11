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

    /**
     * Categorias do relatório do estoque vendidas na aba Acessório (sem acento, maiúsculas). Smartphone, Notebook e
     * Tablets, TV e Box e Modem e FWT ficam na aba Produto Vivo.
     */
    private static final Set<String> CATEGORIAS_DE_ACESSORIO = Set.of(
            "ESSENCIAIS", "ACESSORIOS", "AUDIO", "CASA INTELIGENTE", "BEM ESTAR", "VESTUARIO", "GAMES", "DEMAIS ELETRONICOS");

    /**
     * IMEI: 15 dígitos que não começam por 2. O serial SAP da unidade (código do material + sequência, ex.:
     * 220181370043758) começa por 2; o IMEI começa pelo TAC do fabricante (35..., 86..., 01...).
     */
    private static final java.util.regex.Pattern IMEI = java.util.regex.Pattern.compile("^[013-9]\\d{14}$");

    /**
     * Vendido na aba Acessório (uma unidade por leitura do serial): tem código de acessório, ou é de uma categoria de
     * acessório e não tem IMEI. Relógio LTE e tablet 5G têm IMEI e vão com plano, na aba Produto Vivo.
     * <p>
     * Não substitui {@link #ehAcessorio}: o preço e a reserva por quantidade dependem do código de acessório.
     */
    public static boolean vendidoComoAcessorio(EstoqueGeral e) {
        if (e == null) return false;
        if (ehAcessorio(e)) return true;
        return CATEGORIAS_DE_ACESSORIO.contains(normalizarCategoria(e.getCategoria())) && !pareceImei(e.getSerial());
    }

    static boolean pareceImei(String serial) {
        return serial != null && IMEI.matcher(limparSerial(serial)).matches();
    }

    private static String normalizarCategoria(String categoria) {
        if (textoVazio(categoria)) return "";
        String semAcento = java.text.Normalizer.normalize(categoria, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}