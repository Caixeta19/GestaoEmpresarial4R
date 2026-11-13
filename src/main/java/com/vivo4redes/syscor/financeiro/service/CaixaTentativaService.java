package com.vivo4redes.syscor.financeiro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.financeiro.dto.caixa.TentativaFechamentoDTO;
import com.vivo4redes.syscor.financeiro.enums.AcaoCaixa;
import com.vivo4redes.syscor.financeiro.model.CaixaHistorico;
import com.vivo4redes.syscor.financeiro.model.CaixaTentativaFechamento;
import com.vivo4redes.syscor.financeiro.repository.CaixaHistoricoRepository;
import com.vivo4redes.syscor.financeiro.repository.CaixaSessaoRepository;
import com.vivo4redes.syscor.financeiro.repository.CaixaTentativaFechamentoRepository;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Grava o fechamento recusado por diferença numa transação PRÓPRIA: o 422 desfaz a transação do fechamento, e sem isso a
 * tentativa sumiria junto (e o limite de 5 por dia nunca valeria). Fica num serviço à parte porque a transação nova só
 * vale quando a chamada passa pelo proxy do Spring.
 */
@Service
public class CaixaTentativaService {

    /** Fechamentos recusados por diferença, por caixa por dia, antes de bloquear (só para quem não fecha com divergência). */
    public static final int MAXIMO_POR_DIA = 5;

    private static final TypeReference<List<TentativaFechamentoDTO.Linha>> LINHAS = new TypeReference<>() { };

    private final CaixaTentativaFechamentoRepository tentativaRepository;
    private final CaixaHistoricoRepository historicoRepository;
    private final CaixaSessaoRepository caixaSessaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper json;

    public CaixaTentativaService(CaixaTentativaFechamentoRepository tentativaRepository,
                                 CaixaHistoricoRepository historicoRepository,
                                 CaixaSessaoRepository caixaSessaoRepository,
                                 UsuarioRepository usuarioRepository,
                                 ObjectMapper json) {
        this.tentativaRepository = tentativaRepository;
        this.historicoRepository = historicoRepository;
        this.caixaSessaoRepository = caixaSessaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.json = json;
    }

    /** Grava a tentativa e devolve quantas já foram feitas hoje, contando esta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long registrar(Long caixaId, Long usuarioId, LocalDate dia, List<TentativaFechamentoDTO.Linha> linhas) {
        var sessao = caixaSessaoRepository.getReferenceById(caixaId);
        var usuario = usuarioId == null ? null : usuarioRepository.getReferenceById(usuarioId);
        try {
            tentativaRepository.save(CaixaTentativaFechamento.builder()
                    .caixaSessao(sessao).usuario(usuario).dia(dia).detalheJson(json.writeValueAsString(linhas)).build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível gravar o detalhe da tentativa de fechamento.", e);
        }
        long hoje = tentativaRepository.countByCaixaSessaoIdAndDia(caixaId, dia);
        String divergentes = linhas.stream().filter(l -> l.diferenca().signum() != 0)
                .map(l -> l.forma().name()).collect(Collectors.joining(", "));
        historicoRepository.save(CaixaHistorico.builder().caixaSessao(sessao).acao(AcaoCaixa.TENTATIVA_FECHAMENTO)
                .usuario(usuario).texto("Fechamento recusado por diferença em: " + divergentes
                        + " (tentativa " + hoje + " de " + MAXIMO_POR_DIA + ")").build());
        return hoje;
    }

    @Transactional(readOnly = true)
    public List<TentativaFechamentoDTO> listar(Long caixaId) {
        return tentativaRepository.findByCaixaSessaoIdOrderByEmAscIdAsc(caixaId).stream()
                .map(t -> new TentativaFechamentoDTO(t.getEm(), t.getUsuario() == null ? null : t.getUsuario().getNome(),
                        ler(t.getDetalheJson())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long tentativasHoje(Long caixaId, LocalDate dia) {
        return tentativaRepository.countByCaixaSessaoIdAndDia(caixaId, dia);
    }

    private List<TentativaFechamentoDTO.Linha> ler(String detalhe) {
        try {
            return json.readValue(detalhe, LINHAS);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
