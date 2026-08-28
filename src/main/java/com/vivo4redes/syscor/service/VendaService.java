package com.vivo4redes.syscor.service;
import com.vivo4redes.syscor.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.dto.ResumoCarrinhoDTO;
import com.vivo4redes.syscor.dto.request.DadosIniciaisVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.StatusVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.VendaRequestDTO;
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
    private final FilialService filialService;
    private final UsuarioService usuarioService;
    private final NumeroVendaGenerator numeroVendaGenerator;

    public VendaService(VendaRepository vendaRepository, ClienteService clienteService,
                        FilialService filialService, UsuarioService usuarioService,
                        NumeroVendaGenerator numeroVendaGenerator) {
        this.vendaRepository = vendaRepository;
        this.clienteService = clienteService;
        this.filialService = filialService;
        this.usuarioService = usuarioService;
        this.numeroVendaGenerator = numeroVendaGenerator;
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
                .vendedor(usuario)
                .estoqueAvancado(Boolean.TRUE.equals(dto.estoqueAvancado()))
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

        transicionar(venda, StatusVenda.PENDENTE);

        if (venda.getItens().isEmpty()) {
            throw new VendaSemItemException();
        }

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
        return vendaRepository.buscarComDetalhesPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Venda", id));
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

    /**
     * US-302: reautenticação obrigatória a cada salvar/alterar a venda (tela Início).
     * Sem @Valid/@NotNull aqui de propósito — método privado, chamado internamente
     * (self-invocation), o Spring não aplica validação via proxy AOP nesse caso.
     * A checagem de nulidade é feita manualmente; a validação real do DTO acontece
     * em vendedorService.autenticar (ou via @Valid no controller/DTO externo).
     */
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