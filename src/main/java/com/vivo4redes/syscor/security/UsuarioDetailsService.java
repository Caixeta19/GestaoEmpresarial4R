package com.vivo4redes.syscor.security;

import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;

/** Carrega o usuário do banco para o Spring Security. Aceita e-mail OU login, sem diferenciar maiúsculas. */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository repositorio;

    @Override
    public UserDetails loadUserByUsername(String identificador) throws UsernameNotFoundException {
        String id = identificador == null ? "" : identificador.trim();

        // 1. Tenta buscar por e-mail
        java.util.Optional<Usuario> optUsuario = repositorio.findByEmailIgnoreCase(id);

        // 2. Se não encontrar por e-mail, tenta buscar por login
        if (optUsuario.isEmpty()) {
            optUsuario = repositorio.findFirstByLoginIgnoreCase(id);
        }

        // 3. Obtém o usuário ou lança exceção se nenhum for encontrado
        Usuario u = optUsuario.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // No banco importado, "INATIVO" aparece no campo cargo; confira também a coluna situacao.
        boolean inativo = !u.isAtivo()
                || "INATIVO".equalsIgnoreCase(u.getCargo())
                || "INATIVO".equalsIgnoreCase(u.getSituacao());

        return User.withUsername(u.getEmail())
                .password(u.getSenhaHash())          // precisa ser BCrypt; "PENDENTE_RESET" nunca autentica
                .roles("USER", papel(u.getCargo()))  // ex.: ROLE_USER + ROLE_GERENTE_DE_AREA
                .disabled(inativo)
                .build();
    }

    /** "GERENTE DE ÁREA" -> "GERENTE_DE_AREA" (sem acento, sem espaço), para usar em hasRole(...). */
    static String papel(String cargo) {
        if (cargo == null || cargo.isBlank()) {
            return "SEM_CARGO";
        }
        String semAcento = Normalizer.normalize(cargo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String papel = semAcento.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return papel.isEmpty() ? "SEM_CARGO" : papel;
    }
}