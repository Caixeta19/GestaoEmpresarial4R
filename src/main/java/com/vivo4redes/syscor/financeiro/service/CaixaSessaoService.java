package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.financeiro.dto.caixa.*;
import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.financeiro.model.CaixaSessao;
import com.vivo4redes.syscor.financeiro.model.FormaPagamentoCaixa;
import com.vivo4redes.syscor.financeiro.repository.CaixaSessaoRepository;
import com.vivo4redes.syscor.financeiro.repository.FormaPagamentoCaixaRepository;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.service.FilialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaixaSessaoService {

    private final CaixaSessaoRepository caixaSessaoRepository;
    private final FormaPagamentoCaixaRepository formaPagamentoCaixaRepository;
    private final PagamentoVendaRepository pagamentoVendaRepository;
    private final FilialService filialService;

    @Transactional
    public CaixaSessaoResponseDTO abrir(AbrirCaixaRequestDTO dto) {
        Filial filial = filialService.buscarPorId(dto.filialId());

        CaixaSessao sessao = CaixaSessao.builder()
                .filial(filial)
                .caixaPdv(dto.caixaPdv())
                .dataAbertura(LocalDate.now())
                .status(StatusCaixaSessao.ABERTO)
                .build();
        sessao = caixaSessaoRepository.save(sessao);

        List<FormaPagamentoCaixa> formas = dto.formasIniciais().stream()
                .map(f -> FormaPagamentoCaixa.builder()
                        .caixaSessao(sessao)
                        .forma(f.forma())
                        .abertura(f.valorAbertura())
                        .build())
                .toList();
        formaPagamentoCaixaRepository.saveAll(formas);

        return montarResponse(sessao);
    }

    @Transactional(readOnly = true)
    public List<CaixaSessaoResponseDTO> listarAbertos(Long filialId) {
        List<CaixaSessao> sessoes = filialId == null
                ? caixaSessaoRepository.findByStatusOrderByAbertoEmDesc(StatusCaixaSessao.ABERTO)
                : caixaSessaoRepository.findByStatusAndFilialIdOrderByAbertoEmDesc(StatusCaixaSessao.ABERTO, filialId);
        return sessoes.stream().map(this::montarResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CaixaSessaoResponseDTO> listarFechados(Long filialId) {
        List<CaixaSessao> sessoes = filialId == null
                ? caixaSessaoRepository.findByStatusOrderByAbertoEmDesc(StatusCaixaSessao.FECHADO)
                : caixaSessaoRepository.findByStatusAndFilialIdOrderByAbertoEmDesc(StatusCaixaSessao.FECHADO, filialId);
        return sessoes.stream().map(this::montarResponse).toList();
    }

    @Transactional(readOnly = true)
    public CaixaSessaoResponseDTO buscarPorId(Long id) {
        return montarResponse(buscarEntidade(id));
    }

    @Transactional
    public CaixaSessaoResponseDTO fechar(Long id, FecharCaixaRequestDTO dto) {
        CaixaSessao sessao = buscarEntidade(id);
        if (sessao.getStatus() == StatusCaixaSessao.FECHADO) {
            throw new NegocioException("Esse caixa ja esta fechado.");
        }

        Map<FormaPagamento, BigDecimal> conferidoPorForma = dto.formas().stream()
                .collect(Collectors.toMap(ConferenciaFormaDTO::forma, ConferenciaFormaDTO::valorConferido));

        List<FormaPagamentoCaixa> formas = formaPagamentoCaixaRepository.findByCaixaSessaoId(id);
        for (FormaPagamentoCaixa f : formas) {
            BigDecimal conferido = conferidoPorForma.get(f.getForma());
            if (conferido == null) {
                throw new NegocioException("Falta o valor conferido da forma " + f.getForma() + " pra fechar esse caixa.");
            }
            BigDecimal entradas = calcularEntradas(sessao, f.getForma());
            BigDecimal esperado = f.getAbertura().add(f.getMovimento()).add(entradas).subtract(f.getSaidas());

            f.setValorConferido(conferido);
            f.setDiferenca(conferido.subtract(esperado).setScale(2, RoundingMode.HALF_UP));
        }
        formaPagamentoCaixaRepository.saveAll(formas);

        sessao.setStatus(StatusCaixaSessao.FECHADO);
        sessao.setFechadoEm(Instant.now());
        sessao = caixaSessaoRepository.save(sessao);

        return montarResponse(sessao);
    }

    @Transactional
    public CaixaSessaoResponseDTO reabrir(Long id) {
        CaixaSessao sessao = buscarEntidade(id);
        if (sessao.getStatus() == StatusCaixaSessao.ABERTO) {
            throw new NegocioException("Esse caixa ja esta aberto.");
        }

        List<FormaPagamentoCaixa> formas = formaPagamentoCaixaRepository.findByCaixaSessaoId(id);
        for (FormaPagamentoCaixa f : formas) {
            f.setValorConferido(null);
            f.setDiferenca(null);
        }
        formaPagamentoCaixaRepository.saveAll(formas);

        sessao.setStatus(StatusCaixaSessao.ABERTO);
        sessao.setFechadoEm(null);
        sessao = caixaSessaoRepository.save(sessao);

        return montarResponse(sessao);
    }

    private BigDecimal calcularEntradas(CaixaSessao sessao, FormaPagamento forma) {
        LocalDate data = sessao.getDataAbertura();
        Instant inicioDoDia = data.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant inicioDoDiaSeguinte = data.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        BigDecimal soma = pagamentoVendaRepository.somarPorFilialFormaEData(
                sessao.getFilial().getId(), forma, inicioDoDia, inicioDoDiaSeguinte);
        return soma == null ? BigDecimal.ZERO : soma;
    }

    private CaixaSessaoResponseDTO montarResponse(CaixaSessao sessao) {
        List<FormaPagamentoCaixa> formas = formaPagamentoCaixaRepository.findByCaixaSessaoId(sessao.getId());

        List<FormaPagamentoCaixaResponseDTO> formasDto = formas.stream().map(f -> {
            BigDecimal entradas = calcularEntradas(sessao, f.getForma());
            BigDecimal esperado = f.getAbertura().add(f.getMovimento()).add(entradas).subtract(f.getSaidas())
                    .setScale(2, RoundingMode.HALF_UP);
            return new FormaPagamentoCaixaResponseDTO(
                    f.getId(), f.getForma(), f.getAbertura(), f.getMovimento(), entradas, f.getSaidas(),
                    esperado, f.getValorConferido(), f.getDiferenca()
            );
        }).toList();

        return new CaixaSessaoResponseDTO(
                sessao.getId(), sessao.getFilial().getId(), sessao.getFilial().getNome(),
                sessao.getCaixaPdv(), sessao.getDataAbertura(), sessao.getStatus(),
                sessao.getAbertoEm(), sessao.getFechadoEm(), formasDto
        );
    }

    private CaixaSessao buscarEntidade(Long id) {
        return caixaSessaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessao de caixa"));
    }
}