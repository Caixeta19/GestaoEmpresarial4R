package com.vivo4redes.syscor.estoque.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Resultado de cada serial no inventário por filial: IQ09 (SAP) x estoque do sistema x contagem física (bipagem).
 * CORRETO e DIVERGENCIA são só das execuções antigas (conciliação IQ09 x sistema, sem bipagem).
 */
public enum ResultadoConciliacaoInventario {

    /** Esperado (IQ09 da filial ou estoque da filial) e ainda não bipado. Bloqueia o fechamento até ter justificativa. */
    PENDENTE,
    /** Na IQ09 da filial, no estoque da filial e bipado. */
    CONFERIDO,
    /** Estava na IQ09 da filial e não foi achado: pendente justificado no fechamento. */
    FALTA,
    /** Na IQ09 da filial e bipado, mas sem entrada no estoque da filial. */
    SEM_ENTRADA_SISTEMA,
    /** Bipado e fora da IQ09 da filial. */
    SOBRA_FISICA,
    /** Na IQ09 da filial, mas VENDIDO no sistema (bipado ou não). */
    VENDIDO_EM_ESTOQUE,
    /** Só no estoque da filial (fora da IQ09) e não achado: pendente justificado no fechamento. */
    SO_NO_SISTEMA,
    /** Bipado na loja, mas na IQ09 o status usuário não é de estoque da loja (RLIB, AECL, RESE...). */
    STATUS_SAP_DIVERGENTE,
    /** Na IQ09 do centro, mas com status usuário fora do estoque da loja, e não bipado: só informativo. */
    FORA_ESTOQUE_SAP,

    /** Execuções antigas: disponível nos dois lados. */
    CORRETO,
    /** Execuções antigas: estado inconsistente entre IQ09 e sistema. */
    DIVERGENCIA;

    /** Vão para o diagnóstico da IA. */
    public static final Set<ResultadoConciliacaoInventario> DIVERGENTES = EnumSet.of(
            FALTA, SEM_ENTRADA_SISTEMA, SOBRA_FISICA, VENDIDO_EM_ESTOQUE, SO_NO_SISTEMA, STATUS_SAP_DIVERGENTE);
}
