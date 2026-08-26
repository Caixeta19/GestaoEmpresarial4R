package com.vivo4redes.syscor.service;

import com.vivo4redes.syscor.dto.request.ClienteRequestDTO;
import com.vivo4redes.syscor.exception.ClienteDuplicadoException;
import com.vivo4redes.syscor.exception.DocumentoInvalidoException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.model.Cliente;
import com.vivo4redes.syscor.repository.ClienteRepository;
import com.vivo4redes.syscor.util.ValidadorCpfCnpj;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * US-301: cadastro de cliente com LGPD.
 * Nota: a auditoria detalhada (quem acessou o quê) e o RBAC de campos
 * sensíveis fazem parte do Épico 0, adiado — aqui garantimos apenas que o
 * modelo/consentimento já nasce correto para não exigir migração de dado
 * depois que a segurança entrar.
 */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    public Cliente cadastrar(ClienteRequestDTO dto) {
        String documentoNormalizado = ValidadorCpfCnpj.normalizar(dto.cpfCnpj());

        if (!ValidadorCpfCnpj.isValido(documentoNormalizado)) {
            throw new DocumentoInvalidoException(dto.cpfCnpj());
        }
        if (clienteRepository.existsByCpfCnpj(documentoNormalizado)) {
            throw new ClienteDuplicadoException(documentoNormalizado);
        }


        Cliente cliente = Cliente.builder()
                .tipoPessoa(dto.tipoPessoa())
                .nome(dto.nome())
                .cpfCnpj(documentoNormalizado)
                .email(dto.email())
                .telefone(dto.telefone())
                .ativo(true).build();

        return clienteRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
    }
}

    /** US-301: opt-in/opt-out de comunicação — pode ser revogado a qualquer momento. */