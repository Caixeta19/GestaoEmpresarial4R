package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.request.UsuarioRequestDTO;
import com.vivo4redes.syscor.exception.AutenticacaoInvalidaException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.exception.VendedorDuplicadoException;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Cadastro e autenticação de Vendedor. É uma entidade própria do módulo de
 * Vendas — não é o "usuário do sistema" do Épico 0, que continua adiado.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario cadastrar(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByEmailIgnoreCase(dto.email())) {
            throw new VendedorDuplicadoException(dto.email());
        }
        Usuario usuario = Usuario.builder()
                .nome(dto.nome())
                .login(dto.login())
                .email(dto.email())
                .senhaHash(passwordEncoder.encode(dto.senha()))
                .filial(dto.filial())
                .cargo(dto.cargo())
                .ativo(true)
                .build();
        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vendedor"));
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarAtivos() {
        return usuarioRepository.findByAtivoTrueOrderByNomeAsc();
    }

    /**
     * US-302: reautenticação exigida para salvar/alterar uma venda.
     * Mensagem de erro deliberadamente genérica (não revela se o e-mail/login existe).
     */
    @Transactional(readOnly = true)
    public Usuario autenticar(AutenticacaoUsuarioDTO dto) {
        String identificador = dto.email(); // Pode conter o e-mail ou o login informado no DTO

        // Tenta buscar por e-mail ou por login de forma case-insensitive
        Optional<Usuario> optUsuario = usuarioRepository.findByEmailIgnoreCase(identificador);
        if (optUsuario.isEmpty()) {
            optUsuario = usuarioRepository.findFirstByLoginIgnoreCase(identificador);
        }

        Usuario usuario = optUsuario.orElseThrow(AutenticacaoInvalidaException::new);

        // Verifica se o usuário está ativo e se a senha confere
        if (!usuario.isAtivo() || !passwordEncoder.matches(dto.senha(), usuario.getSenhaHash())) {
            throw new AutenticacaoInvalidaException();
        }

        return usuario;
    }
}