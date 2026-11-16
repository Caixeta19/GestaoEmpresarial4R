package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.enums.StatusSincronizacao;

import java.time.OffsetDateTime;

/** Resultado de uma rodada de sincronização com o extrato ("Sincronizar agora"). */
public record SincronizacaoExtratoDTO(StatusSincronizacao status, int lancamentosNovos, int conciliados, String erro,
                                      OffsetDateTime inicio, OffsetDateTime fim) {
}
