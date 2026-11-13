package com.vivo4redes.syscor.financeiro.dto.caixa;

/** Justificativa obrigatória (mín. 10 caracteres), validada no serviço (422). */
public record ReabrirCaixaRequestDTO(String justificativa) {
}
