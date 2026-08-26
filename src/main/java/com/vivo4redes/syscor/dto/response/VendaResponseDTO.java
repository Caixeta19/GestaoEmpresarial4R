package com.vivo4redes.syscor.dto.response;

import com.vivo4redes.syscor.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.enums.StatusScoreCliente;
import com.vivo4redes.syscor.enums.StatusVenda;
import com.vivo4redes.syscor.model.ItemVenda;
import com.vivo4redes.syscor.model.Venda;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VendaResponseDTO(
        Long id,
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
        BigDecimal valorTotal,
        StatusVenda status,
        StatusAvaliacaoProcedencia avaliacaoProcedencia,
        Instant criadoEm
) {
    public record ItemDTO(Long id, CategoriaItemVenda categoria, Long produtoId, String descricaoProduto,
                          BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotalItem) {
        static ItemDTO from(ItemVenda i) {
            return new ItemDTO(i.getId(), i.getCategoria(), i.getProdutoId(), i.getDescricaoProduto(),
                    i.getQuantidade(), i.getValorUnitario(), i.getValorTotalItem());
        }
    }

    public static VendaResponseDTO from(Venda v) {
        return new VendaResponseDTO(
                v.getId(),
                v.getCliente().getId(),
                v.getCliente().getNome(),
                v.getFilial().getId(),
                v.getFilial().getNome(),
                v.getVendedor().getNome(),
                v.isEstoqueAvancado(),
                v.getStatusScoreCliente(),
                v.getNumeroSerieNota(),
                v.getNumeroNota(),
                v.getItens().stream().map(ItemDTO::from).toList(),
                v.getValorTotal(),
                v.getStatus(),
                v.getAvaliacaoProcedencia(),
                v.getCriadoEm()
        );
    }
}