package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.venda.dto.VendaFiltroDTO;
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
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class VendaService {

    private static final java.time.ZoneId ZONA = java.time.ZoneId.of("America/Sao_Paulo");

    private final VendaRepository vendaRepository;
    private final ClienteService clienteService;
    private final FilialService filialService;
    private final UsuarioService usuarioService;
    private final NumeroVendaGenerator numeroVendaGenerator;
    private final EstoqueGeralService estoqueService;
    private final PagamentoVendaRepository pagamentoVendaRepository;

    public VendaService(VendaRepository vendaRepository,
                        ClienteService clienteService,
                        FilialService filialService,
                        UsuarioService usuarioService,
                        NumeroVendaGenerator numeroVendaGenerator,
                        EstoqueGeralService estoqueService,
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
                .build();

        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda adicionarItem(Long vendaId, ItemVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        // Serviço (plano) entra com valor 0 e sem produto: a cobrança é na fatura da operadora.
        boolean servico = dto.categoria() == CategoriaItemVenda.SERVICO_VIVO;

        if (!servico) {
            if (dto.produtoId() == null) {
                throw new NegocioException("produtoId é obrigatório para itens que não são serviço.");
            }
            if (dto.valorUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                throw new NegocioException("valorUnitario deve ser maior que zero para itens que não são serviço.");
            }
        }

        // Item com IMEI/Serial é uma unidade física: a quantidade só pode ser 1.
        String serialInformado = dto.imeiOuSerial() == null ? null : dto.imeiOuSerial().trim();
        boolean informouSerial = serialInformado != null && !serialInformado.isBlank() && !serialInformado.equals("-");
        if (informouSerial && dto.quantidade().compareTo(BigDecimal.ONE) != 0) {
            throw new NegocioException("Item com IMEI/Serial deve ter quantidade igual a 1.");
        }

        // produtoId = id da linha de tb_estoque_geral escolhida no PDV; a baixa é sempre por serial.
        EstoqueGeral linhaEstoque = null;
        if (!servico) {
            EstoqueGeral escolhida = estoqueService.buscarPorId(dto.produtoId());
            String serialDaBaixa;
            if (informouSerial) {
                serialDaBaixa = serialInformado;
            } else {
                boolean acessorio = escolhida.getCodigoAcessorio() != null && !escolhida.getCodigoAcessorio().isBlank();
                if (!acessorio) {
                    throw new NegocioException("O item '" + dto.descricaoProduto() + "' exige a leitura de um IMEI/Serial antes de ser adicionado a venda.");
                }
                // A baixa marca a linha inteira como VENDIDA: a quantidade precisa bater com a da linha.
                int emEstoque = escolhida.getQuantidade() == null ? 1 : escolhida.getQuantidade();
                if (dto.quantidade().compareTo(BigDecimal.valueOf(emEstoque)) != 0) {
                    throw new NegocioException("Este item é baixado da linha inteira do estoque (quantidade na linha: "
                            + emEstoque + "). Informe a quantidade " + emEstoque + ".");
                }
                serialDaBaixa = escolhida.getSerial();
            }
            linhaEstoque = estoqueService.baixarNaVenda(serialDaBaixa);
            // O serial precisa ser o da linha escolhida (evita vender o IMEI de um item com a descrição/preço de outro).
            if (!escolhida.getId().equals(linhaEstoque.getId())) {
                throw new NegocioException("O IMEI/Serial '" + serialDaBaixa + "' não corresponde ao item informado.");
            }
        }

        ItemVenda item = ItemVenda.builder()
                .categoria(dto.categoria())
                .produtoId(dto.produtoId())
                .descricaoProduto(dto.descricaoProduto())
                .quantidade(dto.quantidade())
                .valorUnitario(dto.valorUnitario())
                .estoque(linhaEstoque)
                .serialImei(informouSerial && linhaEstoque != null ? linhaEstoque.getSerial() : null)
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

        // Item inexistente agora é erro (antes era ignorado em silêncio).
        ItemVenda itemRemovido = venda.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item da venda"));

        estornarEstoqueDoItem(itemRemovido);

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
                    + ") nao bate com o valor total da venda (" + venda.getValorTotal() + ").");
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
            throw new IllegalArgumentException("O novo status da venda e obrigatorio.");
        }

        if (novoStatus == StatusVenda.CANCELADA) {
            estornarSeriaisDaVenda(venda);
        }

        transicionar(venda, novoStatus);
        return vendaRepository.save(venda);
    }

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

    @Transactional(readOnly = true)
    public List<Venda> listarTodas() {
        return vendaRepository.listarComDetalhes();
    }

    @Transactional(readOnly = true)
    public List<Venda> buscar(VendaFiltroDTO filtro) {
        return listarTodas().stream()
                .filter(v -> filtro.filialId() == null || filtro.filialId().equals(v.getFilial().getId()))
                .filter(v -> filtro.vendedorId() == null || filtro.vendedorId().equals(v.getUsuario().getId()))
                .filter(v -> filtro.numeroVenda() == null || filtro.numeroVenda().equals(v.getNumeroVenda()))
                .filter(v -> filtro.dataInicio() == null || !dataDaVenda(v).isBefore(filtro.dataInicio()))
                .filter(v -> filtro.dataFim() == null || !dataDaVenda(v).isAfter(filtro.dataFim()))
                .filter(v -> vazio(filtro.cliente()) || bateCliente(v, filtro.cliente()))
                .filter(v -> vazio(filtro.numeroAcesso()) || v.getItens().stream().anyMatch(i ->
                        contemDigitos(i.getNumeroAcesso(), filtro.numeroAcesso())
                                || contemDigitos(i.getSerialImei(), filtro.numeroAcesso())))
                .filter(v -> vazio(filtro.serialProdutoVivo()) || v.getItens().stream().anyMatch(i ->
                        i.getCategoria() == CategoriaItemVenda.PRODUTO_VIVO
                                && contemTexto(i.getSerialImei(), filtro.serialProdutoVivo())))
                // O serial do chip fica em simcard3g/simcard4g (não em serialImei).
                .filter(v -> vazio(filtro.serialSimcard()) || v.getItens().stream().anyMatch(i ->
                        i.getCategoria() == CategoriaItemVenda.SERVICO_VIVO
                                && (contemTexto(i.getSimcard4g(), filtro.serialSimcard())
                                || contemTexto(i.getSimcard3g(), filtro.serialSimcard()))))
                .filter(v -> vazio(filtro.modeloAcessorio()) || v.getItens().stream().anyMatch(i ->
                        i.getCategoria() == CategoriaItemVenda.ACESSORIO
                                && (contemTexto(i.getDescricaoProduto(), filtro.modeloAcessorio())
                                || contemTexto(i.getSerialImei(), filtro.modeloAcessorio()))))
                .toList();
    }

    private java.time.LocalDate dataDaVenda(Venda v) {
        return v.getCriadoEm().atZone(ZONA).toLocalDate();
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private boolean contemTexto(String campo, String termo) {
        return campo != null && campo.toLowerCase().contains(termo.trim().toLowerCase());
    }

    private boolean bateCliente(Venda v, String termo) {
        String termoLower = termo.trim().toLowerCase();
        String termoDigitos = termo.replaceAll("\\D", "");

        String nome = v.getCliente().getNome() == null ? "" : v.getCliente().getNome().toLowerCase();
        String documento = v.getCliente().getCpfCnpj() == null ? "" : v.getCliente().getCpfCnpj().replaceAll("\\D", "");

        boolean bateNome = nome.contains(termoLower);
        boolean bateDocumento = termoDigitos.length() >= 2 && documento.contains(termoDigitos);
        return bateNome || bateDocumento;
    }

    private boolean contemDigitos(String campo, String termo) {
        if (campo == null) return false;
        String campoDigitos = campo.replaceAll("\\D", "");
        String termoDigitos = termo.replaceAll("\\D", "");
        return !termoDigitos.isBlank() && campoDigitos.contains(termoDigitos);
    }

    @Transactional
    public Venda adicionarPagamento(Long vendaId, PagamentoVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        exigirCarrinhoEditavel(venda);

        // O pagamento não pode ultrapassar o saldo restante (antes só era validado ao finalizar).
        BigDecimal jaPago = somarPagamentos(vendaId);
        if (jaPago.add(dto.valor()).compareTo(venda.getValorTotal()) > 0) {
            throw new NegocioException("O pagamento excede o saldo restante da venda ("
                    + venda.getValorTotal().subtract(jaPago) + ").");
        }

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
            throw new NegocioException("Esse pagamento nao pertence a essa venda.");
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

    private void exigirCarrinhoEditavel(Venda venda) {
        if (venda.getStatus() != StatusVenda.ABERTA) {
            throw new CarrinhoNaoEditavelException(venda.getId());
        }
    }

    private void estornarSeriaisDaVenda(Venda venda) {
        for (ItemVenda item : venda.getItens()) {
            estornarEstoqueDoItem(item);
        }
    }

    /** Devolve ao estoque a linha que o item baixou (volta a DISPONÍVEL). */
    private void estornarEstoqueDoItem(ItemVenda item) {
        if (item.getEstoque() != null) {
            estoqueService.estornarBaixa(item.getEstoque());
        }
    }

    private void exigirVendedorAutenticado(AutenticacaoUsuarioDTO autenticacao) {
        if (autenticacao == null) {
            throw new IllegalArgumentException("Dados de autenticacao do vendedor sao obrigatorios.");
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