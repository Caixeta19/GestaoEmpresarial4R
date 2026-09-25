package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.financeiro.dto.request.TituloFinanceiroRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.response.TituloFinanceiroResponseDTO;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** US-101/102/104: contas a pagar/receber com baixa manual. */
@Service
@RequiredArgsConstructor
public class TituloFinanceiroService {

    private final TituloFinanceiroRepository tituloRepository;

    @Transactional
    public TituloFinanceiroResponseDTO criar(TituloFinanceiroRequestDTO dto) {
        TituloFinanceiro titulo = TituloFinanceiro.builder()
                .tipo(dto.tipo())
                .descricao(dto.descricao())
                .quem(dto.quem())
                .vencimento(dto.vencimento())
                .valor(dto.valor())
                .status(StatusTitulo.ABERTO)
                .vendaId(dto.vendaId())
                .build();
        return TituloFinanceiroResponseDTO.from(tituloRepository.save(titulo));
    }

    @Transactional(readOnly = true)
    public List<TituloFinanceiroResponseDTO> listar(TipoTitulo tipo, StatusTitulo status) {
        List<TituloFinanceiro> titulos;
        if (tipo != null && status != null) {
            titulos = tituloRepository.findByTipoAndStatusOrderByVencimentoAsc(tipo, status);
        } else if (tipo != null) {
            titulos = tituloRepository.findByTipoOrderByVencimentoAsc(tipo);
        } else {
            titulos = tituloRepository.findAllByOrderByVencimentoAsc();
        }
        return titulos.stream().map(TituloFinanceiroResponseDTO::from).toList();
    }

    /** US-104: dar baixa — marca o título como pago/recebido na data informada (default hoje). */
    @Transactional
    public TituloFinanceiroResponseDTO darBaixa(Long id, LocalDate dataBaixa) {
        TituloFinanceiro titulo = buscarEntidade(id);
        if (titulo.getStatus() == StatusTitulo.BAIXADO) {
            throw new NegocioException("Esse título já está baixado.");
        }
        titulo.setStatus(StatusTitulo.BAIXADO);
        titulo.setDataBaixa(dataBaixa == null ? LocalDate.now() : dataBaixa);
        return TituloFinanceiroResponseDTO.from(tituloRepository.save(titulo));
    }

    /** Reverte uma baixa feita por engano. */
    @Transactional
    public TituloFinanceiroResponseDTO reabrir(Long id) {
        TituloFinanceiro titulo = buscarEntidade(id);
        titulo.setStatus(StatusTitulo.ABERTO);
        titulo.setDataBaixa(null);
        return TituloFinanceiroResponseDTO.from(tituloRepository.save(titulo));
    }

    private TituloFinanceiro buscarEntidade(Long id) {
        return tituloRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Título financeiro"));
    }
}