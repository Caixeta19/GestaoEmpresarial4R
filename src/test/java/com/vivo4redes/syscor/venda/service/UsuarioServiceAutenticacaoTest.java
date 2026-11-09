package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.exception.AutenticacaoInvalidaException;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Inativo pela flag, pelo cargo ou pela situação não se autentica na venda, como já não entra no login. */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceAutenticacaoTest {

    private static final AutenticacaoUsuarioDTO AUTH = new AutenticacaoUsuarioDTO("v@4r.com", "senha");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService service;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Autenticar: ativo com a senha certa passa")
    void autenticarAtivo() {
        Usuario usuario = usuario().build();
        comSenhaCerta(usuario);

        assertThat(service.autenticar(AUTH)).isSameAs(usuario);
    }

    @ParameterizedTest
    @MethodSource("inativos")
    @DisplayName("Autenticar: inativo é recusado mesmo com a senha certa")
    void autenticarInativo(Usuario usuario) {
        comSenhaCerta(usuario);

        assertThatThrownBy(() -> service.autenticar(AUTH)).isInstanceOf(AutenticacaoInvalidaException.class);
    }

    @Test
    @DisplayName("Usuário logado: vem do login do contexto de segurança")
    void usuarioLogado() {
        Usuario usuario = usuario().build();
        logar("vendedor");
        when(usuarioRepository.findFirstByLoginIgnoreCase("vendedor")).thenReturn(Optional.of(usuario));

        assertThat(service.usuarioLogado()).isSameAs(usuario);
    }

    @ParameterizedTest
    @MethodSource("inativos")
    @DisplayName("Usuário logado: inativo com token ainda válido é recusado")
    void usuarioLogadoInativo(Usuario usuario) {
        logar("vendedor");
        when(usuarioRepository.findFirstByLoginIgnoreCase("vendedor")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.usuarioLogado()).isInstanceOf(AutenticacaoInvalidaException.class);
    }

    @Test
    @DisplayName("Usuário logado: sem autenticação no contexto é recusado")
    void semUsuarioLogado() {
        assertThatThrownBy(() -> service.usuarioLogado()).isInstanceOf(AutenticacaoInvalidaException.class);
    }

    static Stream<Usuario> inativos() {
        return Stream.of(
                usuario().ativo(false).build(),
                usuario().cargo("inativo").build(),
                usuario().situacao("INATIVO").build());
    }

    private static Usuario.UsuarioBuilder usuario() {
        return Usuario.builder().id(1L).nome("Vendedor").login("vendedor").email("v@4r.com").senhaHash("hash")
                .cargo("VENDEDOR").situacao("ATIVO");
    }

    private void comSenhaCerta(Usuario usuario) {
        when(usuarioRepository.findByEmailIgnoreCase("v@4r.com")).thenReturn(Optional.of(usuario));
        lenient().when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
    }

    private void logar(String login) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }
}
