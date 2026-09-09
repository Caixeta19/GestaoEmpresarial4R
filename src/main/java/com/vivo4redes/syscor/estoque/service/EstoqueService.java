package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.request.EntradaSeriaisRequestDTO;
import com.vivo4redes.syscor.estoque.dto.request.ProdutoRequestDTO;
import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoResponseDTO;
import com.vivo4redes.syscor.estoque.enums.StatusSerial;
import com.vivo4redes.syscor.estoque.model.ItemEstoque;
import com.vivo4redes.syscor.estoque.model.Produto;
import com.vivo4redes.syscor.estoque.repository.ItemEstoqueRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.ProdutoDuplicadoException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final ItemEstoqueRepository itemEstoqueRepository;

    /** US-201: cadastro de produto com SKU único, validado antes de salvar. */
    @Transactional
    public Produto cadastrarProduto(ProdutoRequestDTO dto) {
        if (produtoRepository.existsBySkuIgnoreCase(dto.getSku())) {
            throw new ProdutoDuplicadoException(dto.getSku());
        }

        Produto produto = Produto.builder()
                .sku(dto.getSku().trim())
                .descricao(dto.getDescricao())
                .categoria(dto.getCategoria())
                .estoqueMinimo(dto.getEstoqueMinimo())
                .precoBase(dto.getPrecoBase())
                .requerSerial(dto.getRequerSerial())
                .build();

        return produtoRepository.save(produto);
    }

    @Transactional(readOnly = true)
    public Produto buscarProdutoPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto"));
    }

    @Transactional(readOnly = true)
    public List<Produto> listarProdutos() {
        return produtoRepository.findAllByOrderByDescricaoAsc();
    }

    @Transactional(readOnly = true)
    public List<EstoqueConsolidadoResponseDTO> listarEstoqueConsolidado() {
        List<Produto> produtos = produtoRepository.findAll();
        return produtos.stream().map(this::montarConsolidado).toList();
    }
    /**
     * US-204 (versão de consulta síncrona): itens em risco de ruptura —
     * saldo físico já no nível mínimo ou abaixo dele. O job periódico
     * assíncrono de notificação previsto no critério de aceite original
     * fica para quando o Épico 5 (mensageria) for retomado; esta consulta
     * já é suficiente para o painel da tela de Estoque.
     */
    @Transactional(readOnly = true)
    public List<EstoqueConsolidadoResponseDTO> listarAlertasEstoqueBaixo() {
        return listarEstoqueConsolidado().stream()
                .filter(item -> item.getSaldoFisico() <= item.getEstoqueMinimo())
                .toList();
    }

    private EstoqueConsolidadoResponseDTO montarConsolidado(Produto prod) {
        List<ItemEstoque> disponiveis = itemEstoqueRepository.findByProdutoIdAndStatus(
                prod.getId(),
                StatusSerial.DISPONIVEL
        );
        int saldo = disponiveis.size();

        String statusCalculado;
        if (saldo <= 0) {
            statusCalculado = "Ruptura";
        } else if (saldo <= prod.getEstoqueMinimo()) {
            statusCalculado = "Crítico";
        } else {
            statusCalculado = "Disponível";
        }
        return EstoqueConsolidadoResponseDTO.builder()
                .produtoId(prod.getId())
                .sku(prod.getSku())
                .nome(prod.getDescricao())
                .categoria(prod.getCategoria().name())
                .estoqueMinimo(prod.getEstoqueMinimo())
                .saldoFisico(saldo)
                .status(statusCalculado)
                .precoBase(prod.getPrecoBase())
                .seriaisDisponiveis(disponiveis.stream().map(ItemEstoque::getSerialImei).toList())
                .build();
    }
    @Transactional
    public void registrarEntradaSeriais(com.vivo4redes.syscor.estoque.dto.request.@Valid EntradaSeriaisRequestDTO request) {
        Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto"));

        List<ItemEstoque> novosItens = new ArrayList<>();
        for (String s : request.getSeriais()) {
            String serialLimpo = s.trim();
            if (itemEstoqueRepository.existsBySerialImei(serialLimpo)) {
                throw new NegocioException("O IMEI/Serial " + serialLimpo + " já está cadastrado no estoque.");
            }
            novosItens.add(ItemEstoque.builder()
                    .produto(produto)
                    .serialImei(serialLimpo)
                    .status(StatusSerial.DISPONIVEL)
                    .depositoSap(request.getDepositoSap() != null ? request.getDepositoSap() : "DP01")
                    .build());
        }
        itemEstoqueRepository.saveAll(novosItens);
    }

    @Transactional
    public ItemEstoque baixarSerialNaVenda(String serial) {
        ItemEstoque item = itemEstoqueRepository
                .findBySerialImeiAndStatusForUpdate(serial.trim(), StatusSerial.DISPONIVEL)
                .orElseThrow(() -> new NegocioException("O IMEI/Serial '" + serial + "' não está disponível para venda."));

        item.setStatus(StatusSerial.VENDIDO);
        item.setDataSaida(LocalDateTime.now());
        return itemEstoqueRepository.save(item);
    }
}