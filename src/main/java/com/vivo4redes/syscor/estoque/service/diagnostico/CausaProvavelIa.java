package com.vivo4redes.syscor.estoque.service.diagnostico;

/** Causas que o diagnóstico pode apontar (o prompt de sistema lista as mesmas, mais OUTRA). */
public enum CausaProvavelIa {
    VENDIDO_SEM_BAIXA_SAP("Vendido no sistema e não baixado no SAP"),
    TRANSFERENCIA_EM_TRANSITO("Transferência em trânsito"),
    APARELHO_EM_OUTRA_LOJA("Aparelho em outra loja"),
    ENTRADA_SAP_SEM_ENTRADA_SISTEMA("Entrada no SAP sem entrada no sistema"),
    POSSIVEL_PERDA_OU_FURTO("Possível perda ou furto"),
    ERRO_DE_BIPAGEM("Erro de bipagem"),
    RESERVADO_RESE("Reservado (RESE)"),
    OUTRA("Outra");

    private final String rotulo;

    CausaProvavelIa(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Rótulo para a tela a partir do valor gravado; valor desconhecido volta como está. */
    public static String rotuloDe(String valor) {
        if (valor == null) return null;
        for (CausaProvavelIa causa : values()) {
            if (causa.name().equals(valor)) return causa.rotulo;
        }
        return valor;
    }
}
