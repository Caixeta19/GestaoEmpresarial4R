package com.vivo4redes.syscor.util;

/**
 * Validação de dígito verificador de CPF e CNPJ (US-301).
 * Sem dependências externas de propósito — é regra de domínio pura.
 */
public final class ValidadorCpfCnpj {

    private ValidadorCpfCnpj() {
    }

    public static boolean isValido(String documento) {
        if (documento == null) {
            return false;
        }
        String limpo = documento.replaceAll("\\D", "");
        if (limpo.length() == 11) {
            return isCpfValido(limpo);
        }
        if (limpo.length() == 14) {
            return isCnpjValido(limpo);
        }
        return false;
    }

    private static boolean isCpfValido(String cpf) {
        if (cpf.chars().distinct().count() == 1) {
            return false; // 00000000000, 11111111111 etc. passam no cálculo mas são inválidos
        }
        int[] digitos = cpf.chars().map(c -> c - '0').toArray();

        int soma1 = 0;
        for (int i = 0; i < 9; i++) {
            soma1 += digitos[i] * (10 - i);
        }
        int dv1 = calcularDigito(soma1);

        int soma2 = 0;
        for (int i = 0; i < 10; i++) {
            soma2 += digitos[i] * (11 - i);
        }
        int dv2 = calcularDigito(soma2);

        return dv1 == digitos[9] && dv2 == digitos[10];
    }

    private static boolean isCnpjValido(String cnpj) {
        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }
        int[] digitos = cnpj.chars().map(c -> c - '0').toArray();
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int soma1 = 0;
        for (int i = 0; i < 12; i++) {
            soma1 += digitos[i] * pesos1[i];
        }
        int dv1 = calcularDigito(soma1);

        int soma2 = 0;
        for (int i = 0; i < 13; i++) {
            soma2 += digitos[i] * pesos2[i];
        }
        int dv2 = calcularDigito(soma2);

        return dv1 == digitos[12] && dv2 == digitos[13];
    }

    private static int calcularDigito(int somaPonderada) {
        int resto = somaPonderada % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Remove máscara, mantendo só dígitos — usar antes de persistir. */
    public static String normalizar(String documento) {
        return documento == null ? null : documento.replaceAll("\\D", "");
    }
}