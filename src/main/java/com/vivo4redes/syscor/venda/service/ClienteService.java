package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.exception.ClienteDuplicadoException;
import com.vivo4redes.syscor.exception.ConsentimentoInvalidoException;
import com.vivo4redes.syscor.exception.DocumentoInvalidoException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.util.ValidadorCpfCnpj;
import com.vivo4redes.syscor.venda.dto.request.ClienteRequestDTO;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.repository.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * US-301: cadastro de cliente com LGPD.
 * A auditoria detalhada (quem acessou o quê) e o RBAC de campos sensíveis
 * fazem parte do Épico 0, adiado.
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

        boolean optIn = Boolean.TRUE.equals(dto.consentimentoMarketing());
        if (optIn && (dto.versaoTermoConsentimento() == null
                || dto.versaoTermoConsentimento().isBlank())) {
            throw new ConsentimentoInvalidoException();
        }

        Cliente cliente = Cliente.builder()
                .tipoPessoa(dto.tipoPessoa())
                .nome(dto.nome())
                .cpfCnpj(documentoNormalizado)
                .email(dto.email())
                .telefone(dto.telefone())
                .consentimentoMarketing(optIn)
                .versaoTermoConsentimento(optIn ? dto.versaoTermoConsentimento().trim() : null)
                .dataConsentimento(optIn ? Instant.now() : null)
                .ativo(true)
                .build();

        return clienteRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente"));
    }

    /**
     * Busca paginada. Sem termo: lista todos. Termo só com dígitos e pontuação
     * de CPF/CNPJ (ex.: 123.456.789-09): busca por documento. Qualquer outro
     * termo: busca por nome (contém, sem diferenciar maiúsculas).
     */
    @Transactional(readOnly = true)
    public Page<Cliente> buscar(String busca, Pageable pageable) {
        if (busca == null || busca.isBlank()) {
            return clienteRepository.findAll(pageable);
        }
        String termo = busca.trim();
        if (termo.matches("[\\d.\\-/\\s]+")) {
            String documento = termo.replaceAll("\\D", "");
            return clienteRepository.findByCpfCnpjContaining(documento, pageable);
        }
        return clienteRepository.findByNomeContainingIgnoreCase(termo, pageable);
    }
}