package com.vivo4redes.syscor.estoque.service;

import java.util.Locale;

/**
 * Normalização determinística do serial/IMEI no inventário (sem IA, sem aproximação): o mesmo texto da IQ09, do
 * estoque e do leitor de código de barras vira a mesma chave.
 */
public final class SerialInventario {

    private SerialInventario() {
    }

    /**
     * Tira apóstrofo, aspas e espaços; maiúsculas; número com 16+ dígitos perde os zeros à esquerda (o SAP e o Excel
     * completam códigos com zeros, ex.: {@code 0000194252156926} -> {@code 194252156926}). IMEI de 15 dígitos não muda.
     */
    public static String normalizar(String serial) {
        if (serial == null) return "";
        String texto = serial.replace("'", "").replace("\"", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (texto.length() >= 16 && texto.chars().allMatch(Character::isDigit)) {
            String semZeros = texto.replaceFirst("^0+", "");
            texto = semZeros.isEmpty() ? "0" : semZeros;
        }
        return texto;
    }
}
