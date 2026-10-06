package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.enums.ResultadoConciliacao;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Enviado ao React via SSE. "contaId" mantém o nome esperado pelo componente (é o id do TituloFinanceiro). */
public record ConciliacaoEventoDTO(Long contaId, StatusTitulo statusTitulo, ResultadoConciliacao resultado,
                                   String provider, BigDecimal valor, String mensagem, OffsetDateTime em) {}