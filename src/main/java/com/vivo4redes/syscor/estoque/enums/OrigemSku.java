package com.vivo4redes.syscor.estoque.enums;

/** De onde veio o SKU de um item do inventário. Literais idênticos aos CHECKs da V45. */
public enum OrigemSku {
    /** {@code codigo_acessorio} do estoque (é o Cod_Sap da tabela da Telefônica). */
    ACESSORIO,
    /** Material da IQ09 desta execução, pelo serial. */
    IQ09,
    /** Estimado pela tabela de preços vigente: modelo comercial + cor com um único Cod_Sap. */
    TABELA_PRECO,
    /** Sem fonte confiável: entra na linha agrupada {@link ResultadoSku#SKU_NAO_IDENTIFICADO}. */
    NAO_IDENTIFICADO
}
