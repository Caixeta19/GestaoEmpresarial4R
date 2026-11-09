package com.vivo4redes.syscor.gestao.dto.response;

import com.vivo4redes.syscor.gestao.enums.SituacaoServico;
import com.vivo4redes.syscor.gestao.enums.TipoPlano;
import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
import com.vivo4redes.syscor.gestao.service.RegrasDocumental;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.Venda;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Venda + protocolo documental (campos do protocolo nulos quando ele ainda não existe). {@code dataVenda} é a data de
 * lançamento e {@code statusBko} nunca vem nulo: venda sem avaliação aparece como Nao_avaliado.
 */
public record ProtocoloDocumentalResponseDTO(
        Long protocoloId,
        Long vendaId,
        Long numeroVenda,
        String servico,
        String plano,
        String clienteNome,
        String clienteDocumento,
        String vendedorNome,
        Instant dataVenda,
        String numeroAcesso,
        StatusAvaliacaoProcedencia statusBko,
        String numeroProtocoloGed,
        LocalDate dataDigitalizacao,
        Boolean zerarRemuneracao,
        Boolean gerarPrice,
        String vencimento,
        String liderEquipe,
        String observacoes,
        String observacoesImportacao,
        SituacaoServico situacaoServico,
        Boolean gerarComissao,
        List<String> motivosCancelamento,
        StatusVenda statusVenda,
        Long filialId,
        String filialNome,
        BigDecimal valorTotal,
        TipoPlano tipoPlano
) {

    public static ProtocoloDocumentalResponseDTO from(Venda venda, ProtocoloDocumental protocolo) {
        ItemVenda item = RegrasDocumental.itemPrincipal(venda).orElse(null);
        String servico = item == null ? null
                : item.getTipoServico() != null ? item.getTipoServico() : item.getCategoria().name();
        TipoPlano tipoPlano = item == null ? null : TipoPlano.de(item.getGrupoPlano(), item.getSegmento()).orElse(null);
        boolean temProtocolo = protocolo != null;

        return new ProtocoloDocumentalResponseDTO(
                temProtocolo ? protocolo.getId() : null,
                venda.getId(),
                venda.getNumeroVenda(),
                servico,
                item != null ? item.getPlano() : null,
                venda.getCliente().getNome(),
                venda.getCliente().getCpfCnpj(),
                venda.getUsuario().getNome(),
                RegrasDocumental.dataLancamento(venda),
                item != null ? item.getNumeroAcesso() : null,
                RegrasDocumental.statusBko(venda),
                temProtocolo ? protocolo.getNumeroProtocoloGed() : null,
                temProtocolo ? protocolo.getDataDigitalizacao() : null,
                temProtocolo ? protocolo.getZerarRemuneracao() : null,
                temProtocolo ? protocolo.getGerarPrice() : null,
                temProtocolo ? protocolo.getVencimento() : null,
                temProtocolo ? protocolo.getLiderEquipe() : null,
                temProtocolo ? protocolo.getObservacoes() : null,
                temProtocolo ? protocolo.getObservacoesImportacao() : null,
                temProtocolo ? protocolo.getSituacaoServico() : null,
                temProtocolo ? protocolo.getGerarComissao() : null,
                temProtocolo && protocolo.getMotivosCancelamento() != null
                        ? List.of(protocolo.getMotivosCancelamento().split(";"))
                        : List.of(),
                venda.getStatus(),
                venda.getFilial().getId(),
                venda.getFilial().getNome(),
                venda.getValorTotal(),
                tipoPlano
        );
    }
}
