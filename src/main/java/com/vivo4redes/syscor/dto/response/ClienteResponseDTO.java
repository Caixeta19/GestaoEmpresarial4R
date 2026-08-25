package com.vivo4redes.syscor.dto.response;
import com.vivo4redes.syscor.enums.TipoPessoa;
import com.vivo4redes.syscor.model.Cliente;

import java.time.LocalDateTime;

public record ClienteResponseDTO(
        Long id,
        TipoPessoa tipoPessoa,
        String nome,
        String cpfCnpj,
        String email,
        String telefone,
        boolean ativo,
        boolean consentimentoMarketing,
        LocalDateTime consentimentoDataHora
) {
    public static ClienteResponseDTO from(Cliente c) {
        return new ClienteResponseDTO(
                c.getId(), c.getTipoPessoa(), c.getNome(), c.getCpfCnpj(),
                c.getEmail(), c.getTelefone(), c.isAtivo(),
                c.isConsentimentoMarketing(), c.getConsentimentoDataHora()
        );
    }
}

