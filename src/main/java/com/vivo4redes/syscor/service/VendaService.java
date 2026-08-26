package com.vivo4redes.syscor.service;

import com.vivo4redes.syscor.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.dto.ResumoCarrinhoDTO;
import com.vivo4redes.syscor.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.enums.StatusVenda;
import com.vivo4redes.syscor.exception.CarrinhoNaoEditavelException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.exception.TransicaoStatusInvalidaException;
import com.vivo4redes.syscor.exception.VendaSemItemException;
import com.vivo4redes.syscor.model.Cliente;
import com.vivo4redes.syscor.model.ItemVenda;
import com.vivo4redes.syscor.model.Venda;
import com.vivo4redes.syscor.repository.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-302/US-303: ciclo de vida da venda como carrinho.
 * Fluxo esperado pela UI (abas Produto Vivo / Serviço Vivo / Recarga):
 *   1. abrirCarrinho          -> Venda nasce em status ABERTA
 *   2. adicionarItem (N vezes, uma por categoria/produto)
 *   3. removerItem (opcional)
 *   4. obterResumo            -> alimenta os badges "(3)", "(0)", "(0)" das abas
 *   5. finalizar              -> ABERTA -> PENDENTE (exige ao menos 1 item)
 *   6. avancarStatus          -> PENDENTE -> APROVADA -> CONCLUIDA (ou CANCELADA)
 */
@Service
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ClienteService clienteService;

    public VendaService(VendaRepository vendaRepository, ClienteService clienteService) {
        this.vendaRepository = vendaRepository;
        this.clienteService = clienteService;
    }

    @Transactional
    public Venda abrirCarrinho(Long clienteId) {
        Cliente cliente = clienteService.buscarPorId(clienteId);

        Venda venda = Venda.builder()
                .cliente(cliente)
                .status(StatusVenda.ABERTA)
                .build();

        return vendaRepository.save(venda);
    }
    @Transactional
    public Venda adicionarItem(Long vendaId, ItemVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        ItemVenda item = ItemVenda.builder()
                .categoria(dto.categoria())
                .produtoId(dto.produtoId())
                .descricaoProduto(dto.descricaoProduto())
                .quantidade(dto.quantidade())
                .valorUnitario(dto.valorUnitario())
                .build();

        venda.adicionarItem(item);
        venda.recalcularValorTotal();
        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda removerItem(Long vendaId, Long itemId) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        venda.removerItem(itemId);
        venda.recalcularValorTotal();
        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public ResumoCarrinhoDTO obterResumo(Long vendaId) {
        Venda venda = buscarPorId(vendaId);
        return new ResumoCarrinhoDTO(
                venda.getId(),
                venda.contarItensPorCategoria(CategoriaItemVenda.PRODUTO_VIVO),
                venda.contarItensPorCategoria(CategoriaItemVenda.SERVICO_VIVO),
                venda.contarItensPorCategoria(CategoriaItemVenda.RECARGA),
                venda.getValorTotal()
        );
    }

    /** US-302: encerra a etapa de carrinho — a partir daqui os itens não podem mais ser alterados. */
    @Transactional
    public Venda finalizar(Long vendaId) {
        Venda venda = buscarPorId(vendaId);
        if (venda.getItens().isEmpty()) {
            throw new VendaSemItemException();
        }
        transicionar(venda, StatusVenda.PENDENTE);
        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda avancarStatus(Long vendaId, StatusVenda novoStatus) {
        Venda venda = buscarPorId(vendaId);
        transicionar(venda, novoStatus);
        return vendaRepository.save(venda);
    }

    /** US-303: venda improcedente é automaticamente cancelada e sai do cálculo de comissão (US-106). */
    @Transactional
    public Venda avaliarProcedencia(Long vendaId, StatusAvaliacaoProcedencia resultado) {
        Venda venda = buscarPorId(vendaId);
        venda.setAvaliacaoProcedencia(resultado);

        if (resultado == StatusAvaliacaoProcedencia.Improcedente
                && venda.getStatus() != StatusVenda.CANCELADA) {
            transicionar(venda, StatusVenda.CANCELADA);
        }
        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public Venda buscarPorId(Long id) {
        return vendaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Venda", id));
    }

    private void exigirCarrinhoEditavel(Venda venda) {
        if (venda.getStatus() != StatusVenda.ABERTA) {
            throw new CarrinhoNaoEditavelException(venda.getId());
        }
    }

    private void transicionar(Venda venda, StatusVenda novoStatus) {
        if (!venda.getStatus().podeTransicionarPara(novoStatus)) {
            throw new TransicaoStatusInvalidaException(venda.getStatus(), novoStatus);
        }
        venda.setStatus(novoStatus);
    }
}