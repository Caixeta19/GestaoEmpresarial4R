package com.vivo4redes.syscor.venda.dto.response;
import com.vivo4redes.syscor.venda.enums.TipoPessoa;
import com.vivo4redes.syscor.venda.model.Cliente;

public record ClienteResponseDTO(
        Long id,
        TipoPessoa tipoPessoa,
        String nome,
        String cpfCnpj,
        String email,
        String telefone,
        boolean ativo
) {
    public static ClienteResponseDTO from(Cliente c) {
        return new ClienteResponseDTO(
                c.getId(), c.getTipoPessoa(), c.getNome(), c.getCpfCnpj(),
                c.getEmail(), c.getTelefone(), c.isAtivo()
        );
    }
}