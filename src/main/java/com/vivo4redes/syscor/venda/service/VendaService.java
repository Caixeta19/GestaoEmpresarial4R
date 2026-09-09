package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.ResumoCarrinhoDTO;
import com.vivo4redes.syscor.venda.dto.request.DadosIniciaisVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.ItemVendaRequestDTO;
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
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public VendaService(VendaRepository vendaRepository,
                        ClienteService clienteService,
                        FilialService filialService,
                        UsuarioService usuarioService,
                        NumeroVendaGenerator numeroVendaGenerator,
                        EstoqueService estoqueService) {
        this.vendaRepository = vendaRepository;
        this.clienteService = clienteService;
        this.filialService = filialService;
        this.usuarioService = usuarioService;
        this.numeroVendaGenerator = numeroVendaGenerator;
        this.estoqueService = estoqueService;
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

        transicionar(venda, StatusVenda.PENDENTE);
        return vendaRepository.save(venda);
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