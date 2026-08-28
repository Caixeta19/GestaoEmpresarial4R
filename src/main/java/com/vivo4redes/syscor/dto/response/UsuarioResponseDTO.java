package com.vivo4redes.syscor.dto.response;

import com.vivo4redes.syscor.model.Usuario;

public record UsuarioResponseDTO(Long id, String nome,String login, String email, boolean ativo,String filial, String cargo) {
    public static UsuarioResponseDTO from(Usuario usuario) {
        return new UsuarioResponseDTO(usuario.getId(), usuario.getNome(),usuario.getLogin(), usuario.getEmail(), usuario.isAtivo(), usuario.getFilial(), usuario.getCargo());
    }
}
