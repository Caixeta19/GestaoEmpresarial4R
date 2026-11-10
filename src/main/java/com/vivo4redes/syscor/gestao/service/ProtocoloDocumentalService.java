package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.dto.request.ProtocoloDocumentalRequestDTO;
import com.vivo4redes.syscor.gestao.dto.response.ProtocoloDocumentalResponseDTO;
import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
import com.vivo4redes.syscor.gestao.repository.BuscaDocumentalSpecs;
import com.vivo4redes.syscor.gestao.repository.ProtocoloDocumentalRepository;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import com.vivo4redes.syscor.venda.service.VendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tela "Gestão Documental" — busca as vendas lançadas, cria/atualiza o protocolo de uma venda (1:1) e delega a
 * avaliação de procedência (statusBko) para o VendaService. O status BKO pode mudar quantas vezes for preciso
 * (inclusive sair de Improcedente): ele não cancela a venda nem mexe no estoque.
 */
@Service
@RequiredArgsConstructor
public class ProtocoloDocumentalService {

    private final ProtocoloDocumentalRepository protocoloRepository;
    private final VendaService vendaService;
    private final VendaRepository vendaRepository;
    private final UsuarioService usuarioService;

    /** Só venda lançada (CONCLUIDA) recebe protocolo: carrinho aberto ainda muda, e venda cancelada não tem o que documentar. */
    @Transactional
    public ProtocoloDocumentalResponseDTO salvar(Long vendaId, ProtocoloDocumentalRequestDTO dto) {
        Venda venda = vendaService.buscarPorId(vendaId);
        if (venda.getStatus() != StatusVenda.CONCLUIDA) {
            throw new NegocioException("Só vendas lançadas recebem protocolo documental (venda #"
                    + venda.getNumeroVenda() + " está " + venda.getStatus() + ").");
        }

        ProtocoloDocumental protocolo = protocoloRepository.findByVendaId(vendaId)
                .orElse(null);

        if (dto.statusBko() != null && !dto.statusBko().isBlank()) {
            StatusAvaliacaoProcedencia novo = parseStatusBko(dto.statusBko());
            exigirCorrecaoAoReabrir(venda, novo, protocolo == null ? null : protocolo.getObservacoes(), dto.observacoes());
            venda = vendaService.avaliarProcedencia(vendaId, novo);
        }

        if (protocolo == null) {
            protocolo = ProtocoloDocumental.builder().venda(venda).build();
        }

        protocolo.setNumeroProtocoloGed(dto.numeroProtocoloGed());
        protocolo.setDataDigitalizacao(dto.dataDigitalizacao());
        protocolo.setZerarRemuneracao(dto.zerarRemuneracao());
        protocolo.setGerarPrice(dto.gerarPrice());
        protocolo.setVencimento(dto.vencimento());
        protocolo.setLiderEquipe(dto.liderEquipe());
        protocolo.setObservacoes(dto.observacoes());
        protocolo.setObservacoesImportacao(dto.observacoesImportacao());
        if (dto.situacaoServico() != null) {
            protocolo.setSituacaoServico(dto.situacaoServico());
        }
        if (dto.gerarComissao() != null) {
            protocolo.setGerarComissao(dto.gerarComissao());
        }
        protocolo.setMotivosCancelamento(
                dto.motivosCancelamento() == null || dto.motivosCancelamento().isEmpty()
                        ? null : String.join(";", dto.motivosCancelamento()));
        protocolo.setAtivacaoPrimeiraChamada(dto.ativacaoPrimeiraChamada());
        protocolo.setDataAtivacao(dto.dataAtivacao());
        protocolo.setDataBaixa(dto.dataBaixa());
        protocolo.setNumeroProtocoloGedPortabilidade(dto.numeroProtocoloGedPortabilidade());
        protocolo.setDataInstalacao(dto.dataInstalacao());
        protocolo.setStatusGed(dto.statusGed());
        protocolo.setMotivoReprovacao(dto.motivoReprovacao());
        protocolo.setServicoInstalado(dto.servicoInstalado());
        protocolo.setDataAgendadaInstalacao(dto.dataAgendadaInstalacao());
        protocolo.setArmario(dto.armario());
        if (dto.comissaoAparelho() != null) {
            protocolo.setComissaoAparelho(dto.comissaoAparelho());
        }
        protocolo.setDataUltimaAtivacao(dto.dataUltimaAtivacao());
        protocolo.setAlteradoPor(usuarioService.usuarioLogado());

        protocolo = protocoloRepository.save(protocolo);
        return ProtocoloDocumentalResponseDTO.from(venda, protocolo);
    }

    @Transactional(readOnly = true)
    public ProtocoloDocumentalResponseDTO buscarPorVenda(Long vendaId) {
        Venda venda = vendaService.buscarPorId(vendaId);
        ProtocoloDocumental protocolo = protocoloRepository.findByVendaId(vendaId).orElse(null);
        return ProtocoloDocumentalResponseDTO.from(venda, protocolo);
    }

    @Transactional(readOnly = true)
    public List<ProtocoloDocumentalResponseDTO> buscarPorNumeroProtocoloGed(String numeroProtocoloGed) {
        List<ProtocoloDocumental> protocolos = protocoloRepository.findByNumeroProtocoloGed(numeroProtocoloGed);
        if (protocolos.isEmpty()) {
            throw new RecursoNaoEncontradoException("Protocolo documental");
        }
        return protocolos.stream()
                .map(p -> ProtocoloDocumentalResponseDTO.from(p.getVenda(), p))
                .toList();
    }

    /** Vendas lançadas (e canceladas, se pedido), com ou sem protocolo. Sem resultado é página vazia, não 404. */
    @Transactional(readOnly = true)
    public Page<ProtocoloDocumentalResponseDTO> buscar(FiltroDocumentalDTO filtro, Pageable pageable) {
        Page<Venda> vendas = vendaRepository.findAll(BuscaDocumentalSpecs.de(filtro), pageable);
        Map<Long, ProtocoloDocumental> protocolos = protocolosDas(vendas.getContent());
        return vendas.map(v -> ProtocoloDocumentalResponseDTO.from(v, protocolos.get(v.getId())));
    }

    private Map<Long, ProtocoloDocumental> protocolosDas(List<Venda> vendas) {
        if (vendas.isEmpty()) return Map.of();
        return protocoloRepository.findByVendaIdIn(vendas.stream().map(Venda::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getVenda().getId(), Function.identity()));
    }

    /** Improcedente -> Em avaliação: quem reabre (gerente ou vendedor) descreve a correção na observação. */
    static void exigirCorrecaoAoReabrir(Venda venda, StatusAvaliacaoProcedencia novo, String observacaoAtual,
                                         String observacaoNova) {
        if (RegrasDocumental.statusBko(venda) != StatusAvaliacaoProcedencia.Improcedente
                || novo != StatusAvaliacaoProcedencia.Em_avaliacao_pelo_BKO) {
            return;
        }
        String nova = observacaoNova == null ? "" : observacaoNova.trim();
        String atual = observacaoAtual == null ? "" : observacaoAtual.trim();
        if (nova.isEmpty() || nova.equals(atual)) {
            throw new NegocioException("Para voltar a venda #" + venda.getNumeroVenda() + " de Improcedente para "
                    + "Em avaliação, descreva a correção feita na observação.");
        }
    }

    static StatusAvaliacaoProcedencia parseStatusBko(String texto) {
        return StatusAvaliacaoProcedencia.deTexto(texto).orElseThrow(() -> new NegocioException("statusBko inválido: '"
                + texto + "'. Use um de: Improcedente, Procedente, Em_avaliacao_pelo_BKO, Nao_avaliado."));
    }
}
