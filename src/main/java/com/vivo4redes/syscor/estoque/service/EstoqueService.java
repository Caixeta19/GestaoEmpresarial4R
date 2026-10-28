package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.request.EntradaSeriaisRequestDTO;
import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.SerialDisponivelResponseDTO;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.util.EstoqueUtil;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estoque centralizado em tb_estoque_geral (uma linha = um serial/IMEI, ou uma
 * linha com quantidade, no caso de acessório). Substitui Produto + ItemEstoque.
 */
@Service
@RequiredArgsConstructor
public class EstoqueService {

    /** Antes vinha de Produto.estoqueMinimo (padrão 2); agora não há cadastro de produto. */
    private static final int ESTOQUE_MINIMO_PADRAO = 2;

    private final EstoqueGeralRepository estoqueGeralRepository;

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public EstoqueGeral buscarPorId(Long id) {
        return estoqueGeralRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item de estoque"));
    }

    /** PDV: aparelhos com serial DISPONÍVEL, por trecho do IMEI/serial ou do modelo. */
    @Transactional(readOnly = true)
    public List<SerialDisponivelResponseDTO> buscarSeriaisDisponiveis(String busca, int limite) {
        String termo = busca == null ? "" : busca.trim();
        int tamanho = Math.min(Math.max(limite, 1), 50);
        return estoqueGeralRepository
                .buscarAparelhosDisponiveis(StatusEstoque.DISPONIVEL, termo, PageRequest.of(0, tamanho))
                .stream()
                .map(SerialDisponivelResponseDTO::from)
                .toList();
    }

    /** Catálogo para /produtos: filtra por categoria (aparelho ou acessório) e trecho do nome/código. */
    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listarCatalogo(String busca, CategoriaItemVenda categoria) {
        String termo = busca == null ? "" : busca.trim();
        var pagina = PageRequest.of(0, 50);
        List<EstoqueGeral> linhas = new ArrayList<>();

        if (categoria == null || categoria == CategoriaItemVenda.ACESSORIO) {
            linhas.addAll(estoqueGeralRepository.buscarAcessoriosDisponiveis(StatusEstoque.DISPONIVEL, termo, pagina));
        }
        if (categoria == null || categoria == CategoriaItemVenda.PRODUTO_VIVO) {
            linhas.addAll(estoqueGeralRepository.buscarAparelhosDisponiveis(StatusEstoque.DISPONIVEL, termo, pagina));
        }
        return linhas.stream().map(ProdutoResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EstoqueConsolidadoResponseDTO> listarEstoqueConsolidado() {
        // Agrupa por código de acessório (acessório) ou modelo+cor (aparelho).
        Map<String, List<EstoqueGeral>> grupos = new LinkedHashMap<>();
        for (EstoqueGeral e : estoqueGeralRepository.findAll()) {
            grupos.computeIfAbsent(chaveDoGrupo(e), k -> new ArrayList<>()).add(e);
        }
        return grupos.values().stream().map(this::montarConsolidado).toList();
    }

    @Transactional(readOnly = true)
    public List<EstoqueConsolidadoResponseDTO> listarAlertasEstoqueBaixo() {
        return listarEstoqueConsolidado().stream()
                .filter(item -> item.getSaldoFisico() <= item.getEstoqueMinimo())
                .toList();
    }

    private String chaveDoGrupo(EstoqueGeral e) {
        if (EstoqueUtil.ehAcessorio(e)) {
            return "A|" + e.getCodigoAcessorio().trim().toUpperCase();
        }
        return "P|" + String.valueOf(e.getModeloComercial()).trim().toUpperCase()
                + "|" + String.valueOf(e.getCor()).trim().toUpperCase();
    }

    private EstoqueConsolidadoResponseDTO montarConsolidado(List<EstoqueGeral> linhas) {
        EstoqueGeral ref = linhas.get(0);
        List<EstoqueGeral> disponiveis = linhas.stream().filter(EstoqueGeral::disponivelParaVenda).toList();
        int saldo = disponiveis.stream()
                .mapToInt(l -> l.getQuantidade() == null ? 1 : l.getQuantidade())
                .sum();

        String statusCalculado;
        if (saldo <= 0) {
            statusCalculado = "Ruptura";
        } else if (saldo <= ESTOQUE_MINIMO_PADRAO) {
            statusCalculado = "Crítico";
        } else {
            statusCalculado = "Disponível";
        }

        BigDecimal preco = linhas.stream()
                .map(EstoqueGeral::getValorVenda)
                .filter(v -> v != null && v.signum() > 0)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        return EstoqueConsolidadoResponseDTO.builder()
                .produtoId(ref.getId())
                .sku(EstoqueUtil.ehAcessorio(ref) ? ref.getCodigoAcessorio() : ref.getModeloDpgc())
                .nome(ref.getModeloComercial())
                .categoria(EstoqueUtil.ehAcessorio(ref) ? "ACESSORIO" : "PRODUTO_VIVO")
                .estoqueMinimo(ESTOQUE_MINIMO_PADRAO)
                .saldoFisico(saldo)
                .status(statusCalculado)
                .precoBase(preco)
                .seriaisDisponiveis(disponiveis.stream().map(EstoqueGeral::getSerial).toList())
                .build();
    }

    /**
     * Entrada de seriais novos. {@code produtoId} agora é o id de uma linha de
     * tb_estoque_geral já existente do mesmo modelo: os dados de catálogo dela
     * (modelo, cor, preço...) são copiados para cada serial novo.
     */
    @Transactional
    public void registrarEntradaSeriais(@Valid EntradaSeriaisRequestDTO request) {
        EstoqueGeral modelo = estoqueGeralRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item de estoque de referência"));

        List<EstoqueGeral> novos = new ArrayList<>();
        for (String s : request.getSeriais()) {
            String serialLimpo = EstoqueUtil.limparSerial(s);
            if (serialLimpo.isBlank()) {
                continue;
            }
            if (estoqueGeralRepository.existsBySerial(serialLimpo)) {
                throw new NegocioException("O IMEI/Serial " + serialLimpo + " já está cadastrado no estoque.");
            }
            novos.add(EstoqueGeral.builder()
                    .tipoProduto(modelo.getTipoProduto())
                    .categoria(modelo.getCategoria())
                    .subcategoria(modelo.getSubcategoria())
                    .marca(modelo.getMarca())
                    .modeloComercial(modelo.getModeloComercial())
                    .modeloDpgc(modelo.getModeloDpgc())
                    .cor(modelo.getCor())
                    .ncm(modelo.getNcm())
                    .codigoAcessorio(modelo.getCodigoAcessorio())
                    .serial(serialLimpo)
                    .filialEstoque(request.getDepositoSap() != null ? request.getDepositoSap() : modelo.getFilialEstoque())
                    .valorVenda(modelo.getValorVenda())
                    .valorCusto(modelo.getValorCusto())
                    .quantidade(1)
                    .status(StatusEstoque.DISPONIVEL)
                    .build());
        }
        estoqueGeralRepository.saveAll(novos);
    }

    // ------------------------------------------------------------------ baixa / estorno (PDV)

    /** Venda de um aparelho (ou acessório com serial): DISPONÍVEL -> VENDIDO. */
    @Transactional
    public EstoqueGeral baixarSerialNaVenda(String serial) {
        String limpo = EstoqueUtil.limparSerial(serial);
        EstoqueGeral item = estoqueGeralRepository
                .findBySerialAndStatusForUpdate(limpo, StatusEstoque.DISPONIVEL)
                .orElseThrow(() -> new NegocioException("O IMEI/Serial '" + serial + "' não está disponível para venda."));
        item.baixarPorVenda();
        return estoqueGeralRepository.save(item);
    }

    /** Venda de acessório sem serial: desconta a quantidade; zerou = VENDIDO. */
    @Transactional
    public EstoqueGeral baixarQuantidadeNaVenda(Long estoqueId, int quantidade) {
        if (quantidade <= 0) {
            throw new NegocioException("A quantidade deve ser maior que zero.");
        }
        EstoqueGeral item = estoqueGeralRepository.findByIdForUpdate(estoqueId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item de estoque"));
        int saldo = item.getQuantidade() == null ? 0 : item.getQuantidade();
        if (!item.disponivelParaVenda() || saldo < quantidade) {
            throw new NegocioException("Estoque insuficiente para '" + item.getModeloComercial()
                    + "' (disponível: " + (item.disponivelParaVenda() ? saldo : 0) + ").");
        }
        item.setQuantidade(saldo - quantidade);
        if (item.getQuantidade() == 0) {
            item.setStatus(StatusEstoque.VENDIDO);
            item.setDataVenda(LocalDateTime.now());
        }
        return estoqueGeralRepository.save(item);
    }

    /** Desfaz a baixa (item removido do carrinho ou venda cancelada). */
    @Transactional
    public void estornarBaixa(EstoqueGeral item, boolean vendidoPorSerial, int quantidade) {
        if (item == null) {
            return;
        }
        if (vendidoPorSerial) {
            item.estornarBaixa();
        } else {
            int saldo = item.getQuantidade() == null ? 0 : item.getQuantidade();
            item.setQuantidade(saldo + Math.max(quantidade, 1));
            item.setStatus(StatusEstoque.DISPONIVEL);
            item.setDataVenda(null);
        }
        estoqueGeralRepository.save(item);
    }
}