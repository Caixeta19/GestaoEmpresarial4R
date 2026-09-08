package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.venda.dto.request.FilialRequestDTO;
import com.vivo4redes.syscor.exception.BusinessException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilialService {

    private final FilialRepository filialRepository;

    public FilialService(FilialRepository filialRepository) {
        this.filialRepository = filialRepository;
    }

    @Transactional
    public Filial cadastrar(FilialRequestDTO dto) {
        if (filialRepository.existsByCodigoIgnoreCase(dto.codigo())) {
            throw new BusinessException("Já existe filial cadastrada com o código: " + dto.codigo());
        }

        Filial filial = Filial.builder()
                .codigo(dto.codigo())
                .nome(dto.nome())
                .ativo(true)
                .build();
        return filialRepository.save(filial);
    }

    @Transactional(readOnly = true)
    public Filial buscarPorId(Long id) {
        return filialRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Filial"));
    }
}