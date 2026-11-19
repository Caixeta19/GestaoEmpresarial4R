package com.vivo4redes.syscor.estoque.service;

import java.util.Locale;

/**
 * Normalização do material SAP (SKU) na conferência por SKU: MB52, IQ09 e {@code codigo_acessorio} viram a mesma chave.
 */
public final class SkuInventario {

    private SkuInventario() {
    }

    /**
     * Sem apóstrofo, aspas e espaços; maiúsculas; zeros à esquerda removidos só quando o material é todo numérico
     * ({@code 000000000012345} -> {@code 12345}; {@code 0ABC12} não muda). Vazio quando não há material.
     */
    public static String normalizar(String material) {
        if (material == null) return "";
        String texto = material.replace("'", "").replace("\"", "").replaceAll("\s+", "").toUpperCase(Locale.ROOT);
        if (!texto.isEmpty() && texto.chars().allMatch(Character::isDigit)) {
            String semZeros = texto.replaceFirst("^0+", "");
            texto = semZeros.isEmpty() ? "0" : semZeros;
        }
        return texto.length() > 40 ? texto.substring(0, 40) : texto;
    }
}
