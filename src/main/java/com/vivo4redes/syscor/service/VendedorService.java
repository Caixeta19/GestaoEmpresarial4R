package com.vivo4redes.syscor.service;

import com.vivo4redes.syscor.dto.AutenticacaoVendedorDTO;
import com.vivo4redes.syscor.dto.request.VendedorRequestDTO;
import com.vivo4redes.syscor.exception.AutenticacaoInvalidaException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.exception.VendedorDuplicadoException;
import com.vivo4redes.syscor.model.Vendedor;
import com.vivo4redes.syscor.repository.VendedorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro e autenticação de Vendedor. É uma entidade própria do módulo de
 * Vendas (ver nota em Vendedor.java) — não é o "usuário do sistema" do
 * Épico 0, que continua adiado.
 */
@Service
public class VendedorService {

    private final VendedorRepository vendedorRepository;
    private final PasswordEncoder passwordEncoder;

    public VendedorService(VendedorRepository vendedorRepository, PasswordEncoder passwordEncoder) {
        this.vendedorRepository = vendedorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Vendedor cadastrar(VendedorRequestDTO dto) {
        if (vendedorRepository.existsByEmailIgnoreCase(dto.email())) {
            throw new VendedorDuplicadoException(dto.email());
        }
        Vendedor vendedor = Vendedor.builder()
                .nome(dto.nome())
                .email(dto.email())
                .senhaHash(passwordEncoder.encode(dto.senha()))
                .ativo(true)
                .build();
        return vendedorRepository.save(vendedor);
    }

    @Transactional(readOnly = true)
    public Vendedor buscarPorId(Long id) {
        return vendedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vendedor", id));
    }

    /**
     * US-302: reautenticação exigida para salvar/alterar uma venda.
     * Mensagem de erro deliberadamente genérica (não revela se o e-mail existe).
     */
    @Transactional(readOnly = true)
    public Vendedor autenticar(AutenticacaoVendedorDTO dto) {
        Vendedor vendedor = vendedorRepository.findByEmailIgnoreCaseAndAtivoTrue(dto.email())
                .orElseThrow(AutenticacaoInvalidaException::new);

        if (!passwordEncoder.matches(dto.senha(), vendedor.getSenhaHash())) {
            throw new AutenticacaoInvalidaException();
        }
        return vendedor;
    }
}