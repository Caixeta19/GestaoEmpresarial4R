package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.gestao.dto.request.ProtocoloDocumentalRequestDTO;
import com.vivo4redes.syscor.gestao.dto.response.ProtocoloDocumentalResponseDTO;
import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
import com.vivo4redes.syscor.gestao.repository.ProtocoloDocumentalRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.service.VendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tela "Gestão Documental" — cria/atualiza o protocolo de uma venda (1:1) e
 * delega a avaliação de procedência (statusBko) para o VendaService, que já
 * cancela/estorna automaticamente quando Improcedente (US-303).
 */
@Service
@RequiredArgsConstructor
public class ProtocoloDocumentalService {

    private final ProtocoloDocumentalRepository protocoloRepository;
    private final VendaService vendaService;

    @Transactional
    public ProtocoloDocumentalResponseDTO salvar(Long vendaId, ProtocoloDocumentalRequestDTO dto) {
        Venda venda = vendaService.buscarPorId(vendaId);

        if (dto.statusBko() != null && !dto.statusBko().isBlank()) {
            venda = vendaService.avaliarProcedencia(vendaId, parseStatusBko(dto.statusBko()));
        }

        ProtocoloDocumental protocolo = protocoloRepository.findByVendaId(vendaId)
                .orElse(null);

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

        protocolo = protocoloRepository.save(protocolo);
        return montarResponse(venda, protocolo);
    }

    @Transactional(readOnly = true)
    public ProtocoloDocumentalResponseDTO buscarPorVenda(Long vendaId) {
        Venda venda = vendaService.buscarPorId(vendaId);
        ProtocoloDocumental protocolo = protocoloRepository.findByVendaId(vendaId).orElse(null);
        return montarResponse(venda, protocolo);
    }

    @Transactional(readOnly = true)
    public List<ProtocoloDocumentalResponseDTO> buscarPorNumeroProtocoloGed(String numeroProtocoloGed) {
        List<ProtocoloDocumental> protocolos = protocoloRepository.findByNumeroProtocoloGed(numeroProtocoloGed);
        if (protocolos.isEmpty()) {
            throw new RecursoNaoEncontradoException("Protocolo documental");
        }
        return protocolos.stream()
                .map(p -> montarResponse(p.getVenda(), p))
                .toList();
    }

    private StatusAvaliacaoProcedencia parseStatusBko(String texto) {
        String normalizado = texto.trim().replace(' ', '_');
        try {
            return StatusAvaliacaoProcedencia.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            throw new NegocioException("statusBko inválido: '" + texto
                    + "'. Use um de: Improcedente, Procedente, Em_avaliacao_pelo_BKO, Nao_avaliado.");
        }
    }

    private ProtocoloDocumentalResponseDTO montarResponse(Venda venda, ProtocoloDocumental protocolo) {
        ItemVenda itemServico = venda.getItens().stream()
                .filter(i -> i.getCategoria() == CategoriaItemVenda.SERVICO_VIVO)
                .findFirst()
                .or(() -> venda.getItens().stream().findFirst())
                .orElse(null);

        String servico = itemServico != null && itemServico.getTipoServico() != null
                ? itemServico.getTipoServico()
                : (itemServico != null ? itemServico.getCategoria().name() : null);
        String plano = itemServico != null ? itemServico.getPlano() : null;
        String numeroAcesso = itemServico != null ? itemServico.getNumeroAcesso() : null;

        return new ProtocoloDocumentalResponseDTO(
                protocolo != null ? protocolo.getId() : null,
                venda.getId(),
                venda.getNumeroVenda(),
                servico,
                plano,
                venda.getCliente().getNome(),
                venda.getCliente().getCpfCnpj(),
                venda.getUsuario().getNome(),
                venda.getCriadoEm(),
                numeroAcesso,
                venda.getAvaliacaoProcedencia(),
                protocolo != null ? protocolo.getNumeroProtocoloGed() : null,
                protocolo != null ? protocolo.getDataDigitalizacao() : null,
                protocolo != null ? protocolo.getZerarRemuneracao() : null,
                protocolo != null ? protocolo.getGerarPrice() : null,
                protocolo != null ? protocolo.getVencimento() : null,
                protocolo != null ? protocolo.getLiderEquipe() : null,
                protocolo != null ? protocolo.getObservacoes() : null,
                protocolo != null ? protocolo.getObservacoesImportacao() : null,
                protocolo != null ? protocolo.getSituacaoServico() : null,
                protocolo != null ? protocolo.getGerarComissao() : null,
                protocolo != null && protocolo.getMotivosCancelamento() != null
                        ? List.of(protocolo.getMotivosCancelamento().split(";"))
                        : List.of()
        );
    }
}