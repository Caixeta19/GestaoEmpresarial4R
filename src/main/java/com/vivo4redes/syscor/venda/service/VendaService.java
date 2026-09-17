package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.ResumoCarrinhoDTO;
import com.vivo4redes.syscor.venda.dto.request.DadosIniciaisVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.PagamentoVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.StatusVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.VendaRequestDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.exception.CarrinhoNaoEditavelException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.exception.TransicaoStatusInvalidaException;
import com.vivo4redes.syscor.exception.VendaSemItemException;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.estoque.model.ItemEstoque;
import com.vivo4redes.syscor.estoque.model.Produto;
import com.vivo4redes.syscor.estoque.service.EstoqueService;
import com.vivo4redes.syscor.estoque.enums.StatusSerial;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * US-302/US-303: ciclo de vida da venda como carrinho e integracao com estoque serializado.
 * Fluxo esperado pela UI (abas Produto Vivo / Servico Vivo / Recarga):
 *   1. abrirCarrinho          -> Venda nasce em status ABERTA
 *   2. adicionarItem          -> Valida e baixa o serial atomico no Estoque (ItemEstoque)
 *   3. removerItem            -> Estorna o serial para DISPONIVEL e recalcula
 *   4. obterResumo            -> alimenta os badges "(3)", "(0)", "(0)" das abas
 *   5. finalizar              -> ABERTA -> PENDENTE (exige ao menos 1 item)
 *   6. avancarStatus          -> PENDENTE -> APROVADA -> CONCLUIDA (ou CANCELADA com estorno)
 */
@Service
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ClienteService clienteService;
    private final FilialService filialService;
    private final UsuarioService usuarioService;
    private final NumeroVendaGenerator numeroVendaGenerator;
    private final EstoqueService estoqueService;
    private final PagamentoVendaRepository pagamentoVendaRepository;

    public VendaService(VendaRepository vendaRepository,
                        ClienteService clienteService,
                        FilialService filialService,
                        UsuarioService usuarioService,
                        NumeroVendaGenerator numeroVendaGenerator,
                        EstoqueService estoqueService,
                        PagamentoVendaRepository pagamentoVendaRepository) {
        this.vendaRepository = vendaRepository;
        this.clienteService = clienteService;
        this.filialService = filialService;
        this.usuarioService = usuarioService;
        this.numeroVendaGenerator = numeroVendaGenerator;
        this.estoqueService = estoqueService;
        this.pagamentoVendaRepository = pagamentoVendaRepository;
    }

    @Transactional
    public Venda abrirCarrinho(VendaRequestDTO dto) {
        Cliente cliente = clienteService.buscarPorId(dto.clienteId());
        var filial = filialService.buscarPorId(dto.filialId());
        var usuario = usuarioService.autenticar(dto.autenticacaoVendedor());

        Venda venda = Venda.builder()
                .numeroVenda(numeroVendaGenerator.gerar())
                .cliente(cliente)
                .filial(filial)
                .usuario(usuario)
                .estoqueAvancado(Boolean.TRUE.equals(dto.estoqueAvancado()))
                .status(StatusVenda.ABERTA)
                // Snapshot de auditoria (US-301): registra o consentimento do cliente
                // no instante da venda, mesmo que ele mude depois — não trava a venda
                .build();

        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda adicionarItem(Long vendaId, ItemVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        // O produto precisa existir no catálogo — todo item de venda (produto,
        // serviço ou recarga) é rastreável a um SKU cadastrado no Estoque.
        Produto produto = estoqueService.buscarProdutoPorId(dto.produtoId());

        ItemEstoque itemEstoqueBaixado = null;
        String serialInformado = dto.imeiOuSerial();
        boolean informouSerial = serialInformado != null && !serialInformado.isBlank() && !serialInformado.equals("—");

        // Regra de integracao com o Estoque: quem decide se a baixa exige
        // IMEI/serial é o cadastro do produto (produto.requerSerial) — não a
        // categoria do item, que é só a aba da UI (Produto Vivo/Serviço/Recarga)
        // e pode não refletir a real necessidade de rastreio serializado.
        if (informouSerial) {
            itemEstoqueBaixado = estoqueService.baixarSerialNaVenda(serialInformado.trim());
        } else if (Boolean.TRUE.equals(produto.getRequerSerial())) {
            throw new NegocioException("O item '" + dto.descricaoProduto() + "' exige a leitura de um IMEI/Serial antes de ser adicionado à venda.");
        }

        ItemVenda item = ItemVenda.builder()
                .categoria(dto.categoria())
                .produtoId(dto.produtoId())
                .descricaoProduto(dto.descricaoProduto())
                .quantidade(dto.quantidade())
                .valorUnitario(dto.valorUnitario())
                .itemEstoque(itemEstoqueBaixado)
                .serialImei(itemEstoqueBaixado != null ? itemEstoqueBaixado.getSerialImei() : serialInformado)
                .tabelaPreco(dto.tabelaPreco())
                .sva(dto.sva())
                .seguro(dto.seguro())
                .segmento(dto.segmento())
                .tipoServico(dto.tipoServico())
                .ddd(dto.ddd())
                .planoAntigo(dto.planoAntigo())
                .plano(dto.plano())
                .debitoAutomatico(dto.debitoAutomatico())
                .valorAdicional(dto.valorAdicional())
                .valorAcrescimo(dto.valorAcrescimo())
                .desconto(dto.desconto())
                .cupom(dto.cupom())
                .vencimentoFatura(dto.vencimentoFatura())
                .numeroAcesso(dto.numeroAcesso())
                .sistemaOrigem(dto.sistemaOrigem())
                .numOrdemNext(dto.numOrdemNext())
                .numSolicitacaoGed(dto.numSolicitacaoGed())
                .simcard3g(dto.simcard3g())
                .simcard4g(dto.simcard4g())
                .clientePossuiSimcard(dto.clientePossuiSimcard())
                .simcardDoado(dto.simcardDoado())
                .descontoChip(dto.descontoChip())
                .valorChip(dto.valorChip())
                .serialConfirmado(dto.serialConfirmado())
                .build();

        venda.adicionarItem(item);
        venda.recalcularValorTotal();
        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda removerItem(Long vendaId, Long itemId) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        // Localiza o item antes da remocao para estornar o serial
        venda.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .ifPresent(itemRemovido -> {
                    if (itemRemovido.getItemEstoque() != null) {
                        ItemEstoque itemEstoque = itemRemovido.getItemEstoque();
                        itemEstoque.setStatus(StatusSerial.DISPONIVEL);
                        itemEstoque.setDataSaida(null);
                    }
                });

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
                venda.contarItensPorCategoria(CategoriaItemVenda.ACESSORIO),
                venda.contarItensPorCategoria(CategoriaItemVenda.RECARGA),
                venda.getValorTotal()
        );
    }

    /** Edita os campos da tela "Início" de uma venda já aberta — exige reautenticação. */
    @Transactional
    public Venda atualizarDadosIniciais(Long vendaId, DadosIniciaisVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirVendedorAutenticado(dto.autenticacaoUsuario());

        Cliente cliente = clienteService.buscarPorId(dto.clienteId());
        var filial = filialService.buscarPorId(dto.filialId());

        venda.setCliente(cliente);
        venda.setFilial(filial);
        venda.setEstoqueAvancado(Boolean.TRUE.equals(dto.estoqueAvancado()));
        if (dto.statusScoreCliente() != null) {
            venda.setStatusScoreCliente(dto.statusScoreCliente());
        }
        venda.setNumeroSerieNota(dto.numeroSerieNota());
        venda.setNumeroNota(dto.numeroNota());

        return vendaRepository.save(venda);
    }

    /** US-302: encerra a etapa de carrinho — a partir daqui os itens não podem mais ser alterados. */
    @Transactional
    public Venda finalizar(Long vendaId, FinalizarVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirVendedorAutenticado(dto.autenticacaoVendedor());

        if (venda.getItens().isEmpty()) {
            throw new VendaSemItemException();
        }

        BigDecimal totalPago = somarPagamentos(vendaId);
        if (totalPago.compareTo(venda.getValorTotal()) != 0) {
            throw new NegocioException("A soma dos pagamentos (" + totalPago
                    + ") não bate com o valor total da venda (" + venda.getValorTotal() + ").");
        }

        transicionar(venda, StatusVenda.PENDENTE);
        return vendaRepository.save(venda);
    }

    /** Adiciona uma forma de pagamento — uma venda pode ter várias (ex.: parte cartão, parte PIX). */
    @Transactional
    public Venda adicionarPagamento(Long vendaId, PagamentoVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        PagamentoVenda pagamento = PagamentoVenda.builder()
                .venda(venda)
                .forma(dto.forma())
                .valor(dto.valor())
                .parcelas(dto.parcelas() == null ? 1 : dto.parcelas())
                .build();
        pagamentoVendaRepository.save(pagamento);

        return buscarPorId(vendaId);
    }

    @Transactional
    public Venda removerPagamento(Long vendaId, Long pagamentoId) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        PagamentoVenda pagamento = pagamentoVendaRepository.findById(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento"));
        if (!pagamento.getVenda().getId().equals(vendaId)) {
            throw new NegocioException("Esse pagamento não pertence a essa venda.");
        }
        pagamentoVendaRepository.delete(pagamento);

        return buscarPorId(vendaId);
    }

    @Transactional(readOnly = true)
    public List<PagamentoVenda> listarPagamentos(Long vendaId) {
        return pagamentoVendaRepository.findByVendaIdOrderByIdAsc(vendaId);
    }

    private BigDecimal somarPagamentos(Long vendaId) {
        return listarPagamentos(vendaId).stream()
                .map(PagamentoVenda::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public Venda avancarStatus(Long vendaId, StatusVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirVendedorAutenticado(dto.autenticacaoUsuario());

        StatusVenda novoStatus = dto.novoStatus();
        if (novoStatus == null) {
            throw new IllegalArgumentException("O novo status da venda é obrigatório.");
        }

        // Se for transicao para CANCELADA, estorna todos os seriais associados
        if (novoStatus == StatusVenda.CANCELADA) {
            estornarSeriaisDaVenda(venda);
        }

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
            estornarSeriaisDaVenda(venda);
            transicionar(venda, StatusVenda.CANCELADA);
        }
        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public Venda buscarPorId(Long id) {
        return vendaRepository.buscarComDetalhesPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Venda"));
    }

    /** Listagem geral — mesma estratégia de fetch eager do buscarPorId, evita N+1/Lazy fora da transação. */
    @Transactional(readOnly = true)
    public java.util.List<Venda> listarTodas() {
        return vendaRepository.listarComDetalhes();
    }

    private void exigirCarrinhoEditavel(Venda venda) {
        if (venda.getStatus() != StatusVenda.ABERTA) {
            throw new CarrinhoNaoEditavelException(venda.getId());
        }
    }

    private void estornarSeriaisDaVenda(Venda venda) {
        for (ItemVenda item : venda.getItens()) {
            if (item.getItemEstoque() != null) {
                ItemEstoque serial = item.getItemEstoque();
                serial.setStatus(StatusSerial.DISPONIVEL);
                serial.setDataSaida(null);
            }
        }
    }

    private void exigirVendedorAutenticado(AutenticacaoUsuarioDTO autenticacao) {
        if (autenticacao == null) {
            throw new IllegalArgumentException("Dados de autenticação do vendedor são obrigatórios.");
        }
        usuarioService.autenticar(autenticacao);
    }

    private void transicionar(Venda venda, StatusVenda novoStatus) {
        if (!venda.getStatus().podeTransicionarPara(novoStatus)) {
            throw new TransicaoStatusInvalidaException(venda.getStatus(), novoStatus);
        }
        venda.setStatus(novoStatus);
    }
}