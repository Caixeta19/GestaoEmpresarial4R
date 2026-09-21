package com.vivo4redes.syscor.venda.dto.response;

import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.enums.StatusScoreCliente;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import com.vivo4redes.syscor.venda.model.Venda;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VendaResponseDTO(
        Long id,
        Long numeroVenda,
        Long clienteId,
        String clienteNome,
        Long filialId,
        String filialNome,
        String vendedorNome,
        boolean estoqueAvancado,
        StatusScoreCliente statusScoreCliente,
        String numeroSerieNota,
        String numeroNota,
        List<ItemDTO> itens,
        List<PagamentoDTO> pagamentos,
        BigDecimal valorTotal,
        StatusVenda status,
        StatusAvaliacaoProcedencia avaliacaoProcedencia,
        Instant criadoEm
) {
    public record ItemDTO(
            Long id, CategoriaItemVenda categoria, Long produtoId, String descricaoProduto,
            BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotalItem, String serialImei,
            String tabelaPreco, Boolean sva, Boolean seguro, String segmento, String tipoServico, String ddd,
            String planoAntigo, String plano, Boolean debitoAutomatico, BigDecimal valorAdicional,
            BigDecimal valorAcrescimo, BigDecimal desconto, Boolean cupom, String vencimentoFatura,
            String numeroAcesso, String sistemaOrigem, String numOrdemNext, String numSolicitacaoGed,
            String simcard3g, String simcard4g, Boolean clientePossuiSimcard, Boolean simcardDoado,
            BigDecimal descontoChip, BigDecimal valorChip, Boolean serialConfirmado
    ) {
        static ItemDTO from(ItemVenda i) {
            return new ItemDTO(
                    i.getId(), i.getCategoria(), i.getProdutoId(), i.getDescricaoProduto(),
                    i.getQuantidade(), i.getValorUnitario(), i.getValorTotalItem(), i.getSerialImei(),
                    i.getTabelaPreco(), i.getSva(), i.getSeguro(), i.getSegmento(), i.getTipoServico(), i.getDdd(),
                    i.getPlanoAntigo(), i.getPlano(), i.getDebitoAutomatico(), i.getValorAdicional(),
                    i.getValorAcrescimo(), i.getDesconto(), i.getCupom(), i.getVencimentoFatura(),
                    i.getNumeroAcesso(), i.getSistemaOrigem(), i.getNumOrdemNext(), i.getNumSolicitacaoGed(),
                    i.getSimcard3g(), i.getSimcard4g(), i.getClientePossuiSimcard(), i.getSimcardDoado(),
                    i.getDescontoChip(), i.getValorChip(), i.getSerialConfirmado()
            );
        }
    }

    public record PagamentoDTO(Long id, FormaPagamento forma, BigDecimal valor, Integer parcelas) {
        static PagamentoDTO from(PagamentoVenda p) {
            return new PagamentoDTO(p.getId(), p.getForma(), p.getValor(), p.getParcelas());
        }
    }

    public static VendaResponseDTO from(Venda v) {
        return from(v, List.of());
    }

    public static VendaResponseDTO from(Venda v, List<PagamentoVenda> pagamentos) {
        return new VendaResponseDTO(
                v.getId(),
                v.getNumeroVenda(),
                v.getCliente().getId(),
                v.getCliente().getNome(),
                v.getFilial().getId(),
                v.getFilial().getNome(),
                v.getUsuario().getNome(),
                v.isEstoqueAvancado(),
                v.getStatusScoreCliente(),
                v.getNumeroSerieNota(),
                v.getNumeroNota(),
                v.getItens().stream().map(ItemDTO::from).toList(),
                pagamentos.stream().map(PagamentoDTO::from).toList(),
                v.getValorTotal(),
                v.getStatus(),
                v.getAvaliacaoProcedencia(),
                v.getCriadoEm()
        );
    }
}