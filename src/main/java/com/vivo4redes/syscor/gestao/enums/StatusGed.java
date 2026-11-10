package com.vivo4redes.syscor.gestao.enums;

/** Status GED do protocolo. Sem valor gravado, o relatório deduz do protocolo (GED, digitalização, cancelamento). */
public enum StatusGed {
    NAO_DIGITALIZADO,
    AGUARDANDO_DIGITALIZACAO,
    DIGITALIZADO,
    CANCELADO
}
