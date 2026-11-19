package com.vivo4redes.syscor.estoque.enums;

/**
 * Resultado da conferência por SKU (MB52 x IQ09 x sistema x contagem). Informativo: não interfere no cruzamento por
 * serial nem no fechamento. Literais idênticos ao CHECK de {@code tb_inventario_sku} (V45).
 */
public enum ResultadoSku {
    CONFERIDO,
    MB52_DIFERE_IQ09,
    SAP_ACIMA_SISTEMA,
    SAP_ABAIXO_SISTEMA,
    SO_NO_SAP,
    SO_NO_SISTEMA,
    SKU_NAO_IDENTIFICADO,
    CONTAGEM_DIVERGENTE
}
