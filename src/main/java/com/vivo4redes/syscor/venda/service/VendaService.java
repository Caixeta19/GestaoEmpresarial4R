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
import com.vivo4redes.syscor.venda.dto.response.PagamentoIncompativelDTO;
import com.vivo4redes.syscor.venda.dto.response.SimulacaoPagamentoDTO;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.enums.TipoPagamento;
import com.vivo4redes.syscor.exception.CarrinhoNaoEditavelException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.PagamentosIncompativeisException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.exception.TotalVendaAlteradoException;
import com.vivo4redes.syscor.exception.TransicaoStatusInvalidaException;
import com.vivo4redes.syscor.exception.VendaSemItemException;
import com.vivo4redes.syscor.exception.VoucherJaUsadoException;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.CalculadoraCondicaoPagamento;
import com.vivo4redes.syscor.estoque.service.CalculadoraCondicaoPagamento.ComposicaoVenda;
import com.vivo4redes.syscor.estoque.service.CalculadoraCondicaoPagamento.TotalVenda;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.util.EstoqueUtil;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Service
public class VendaService {

    private static final java.time.ZoneId ZONA = java.time.ZoneId.of("America/Sao_Paulo");
    private static final int TAMANHO_MAXIMO_VOUCHER = 60;

    private final VendaRepository vendaRepository;
    private final ClienteService clienteService;
    private final FilialService filialService;
    private final UsuarioService usuarioService;
    private final NumeroVendaGenerator numeroVendaGenerator;
    private final EstoqueGeralService estoqueService;
    private final PagamentoVendaRepository pagamentoVendaRepository;
    private final PrecoVigenteService precoVigenteService;
    private final ResolvedorTabela resolvedorTabela;

    public VendaService(VendaRepository vendaRepository,
                        ClienteService clienteService,
                        FilialService filialService,
                        UsuarioService usuarioService,
                        NumeroVendaGenerator numeroVendaGenerator,
                        EstoqueGeralService estoqueService,
                        PagamentoVendaRepository pagamentoVendaRepository,
                        PrecoVigenteService precoVigenteService,
                        ResolvedorTabela resolvedorTabela) {
        this.vendaRepository = vendaRepository;
        this.clienteService = clienteService;
        this.filialService = filialService;
        this.usuarioService = usuarioService;
        this.numeroVendaGenerator = numeroVendaGenerator;
        this.estoqueService = estoqueService;
        this.pagamentoVendaRepository = pagamentoVendaRepository;
        this.precoVigenteService = precoVigenteService;
        this.resolvedorTabela = resolvedorTabela;
    }

    @Transactional
    public Venda abrirCarrinho(VendaRequestDTO dto) {
        Cliente cliente = clienteService.buscarPorId(dto.clienteId());
        var filial = filialService.buscarPorId(dto.filialId());
        var usuario = usuarioService.usuarioLogado();

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
        Venda venda = buscarParaEdicao(vendaId);
        exigirCarrinhoEditavel(venda);

        // Serviço (plano) entra com valor 0 e sem produto: a cobrança é na fatura da operadora.
        boolean servico = dto.categoria() == CategoriaItemVenda.SERVICO_VIVO;
        // Aparelho e acessório: o preço vem da tabela vigente da Telefônica; o valorUnitario do front é ignorado.
        boolean precoPelaTabela = dto.categoria() == CategoriaItemVenda.PRODUTO_VIVO
                || dto.categoria() == CategoriaItemVenda.ACESSORIO;

        if (!servico) {
            if (dto.produtoId() == null) {
                throw new NegocioException("produtoId é obrigatório para itens que não são serviço.");
            }
            if (!precoPelaTabela && (dto.valorUnitario() == null || dto.valorUnitario().compareTo(BigDecimal.ZERO) <= 0)) {
                throw new NegocioException("valorUnitario deve ser maior que zero para recarga.");
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
        PrecoCalculado preco = null;
        if (!servico) {
            EstoqueGeral escolhida = estoqueService.buscarPorId(dto.produtoId());
            if (precoPelaTabela) {
                // Antes da reserva: item sem preço na tabela vigente é recusado (422) sem prender a unidade.
                preco = precoNaTabela(venda, venda.getCondicaoPagamento(), dto.categoria(), escolhida, dto.plano());
            }
            String serialDaBaixa;
            if (informouSerial) {
                serialDaBaixa = serialInformado;
            } else {
                boolean acessorio = EstoqueUtil.ehAcessorio(escolhida);
                if (!acessorio) {
                    throw new NegocioException("O item '" + dto.descricaoProduto() + "' exige a leitura de um IMEI/Serial antes de ser adicionado a venda.");
                }
                // A reserva prende a linha inteira: a quantidade precisa bater com a da linha.
                int emEstoque = escolhida.getQuantidade() == null ? 1 : escolhida.getQuantidade();
                if (dto.quantidade().compareTo(BigDecimal.valueOf(emEstoque)) != 0) {
                    throw new NegocioException("Este item é baixado da linha inteira do estoque (quantidade na linha: "
                            + emEstoque + "). Informe a quantidade " + emEstoque + ".");
                }
                serialDaBaixa = escolhida.getSerial();
            }
            // Carrinho só reserva; a unidade vira VENDIDO quando a venda é CONCLUIDA.
            linhaEstoque = estoqueService.reservarNaVenda(serialDaBaixa);
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
                .valorUnitario(valorUnitarioDoItem(dto, preco))
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
                // Chip tem preço fixo de configuração (o PVP do aparelho não o inclui); o valor do front é ignorado.
                .valorChip(dto.valorChip() == null ? null : precoVigenteService.precoChip())
                .serialConfirmado(dto.serialConfirmado())
                .grupoPlano(preco == null ? null : preco.grupoPlano())
                .tabelaAplicada(preco == null ? null : preco.tabela())
                .formaPagamento(preco == null ? null : venda.getCondicaoPagamento().formaPagamento())
                .parcelas(preco == null ? null : venda.getCondicaoPagamento().numeroParcelas())
                .descontoFabricante(preco == null ? null : preco.descontoFabricante())
                .build();

        venda.adicionarItem(item);
        reprecificar(venda);
        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda removerItem(Long vendaId, Long itemId) {
        Venda venda = buscarParaEdicao(vendaId);
        exigirCarrinhoEditavel(venda);

        // Item inexistente agora é erro (antes era ignorado em silêncio).
        ItemVenda itemRemovido = venda.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item da venda"));

        liberarEstoqueDoItem(itemRemovido);

        venda.removerItem(itemId);
        reprecificar(venda);
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
        Venda venda = buscarParaEdicao(vendaId);
        // Sem senha neste endpoint: depois de finalizada, a venda não pode mais ser trocada de cliente ou filial.
        exigirCarrinhoEditavel(venda);

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

    /**
     * Fecha o carrinho pela tabela vigente. Se o total mudou (tabela nova, por exemplo), grava o total novo e recusa
     * com 422: o vendedor confere antes de cobrar. Por isso essa exceção não desfaz a transação, e o vendedor só é
     * gravado depois de todas as validações. Ordem: autenticação → itens → Renova → total vigente → pagamentos.
     */
    @Transactional(noRollbackFor = TotalVendaAlteradoException.class)
    public Venda finalizar(Long vendaId, FinalizarVendaRequestDTO dto) {
        var vendedor = exigirVendedorAutenticado(dto.autenticacaoVendedor());
        Venda venda = buscarParaEdicao(vendaId);

        if (venda.getItens().isEmpty()) {
            throw new VendaSemItemException();
        }

        if (venda.isRenova() && vazio(venda.getCodigoVoucher())) {
            throw new NegocioException("Venda com Renova precisa do número do voucher");
        }
        if (venda.getStatus() == StatusVenda.ABERTA) {
            exigirTotalVigente(venda);
        }
        BigDecimal totalPago = somarPagamentos(vendaId);
        if (totalPago.compareTo(venda.getValorTotal()) != 0) {
            throw new NegocioException("A soma dos pagamentos (" + totalPago
                    + ") nao bate com o valor total da venda (" + venda.getValorTotal() + ").");
        }

        transicionar(venda, StatusVenda.PENDENTE);
        venda.setUsuario(vendedor);
        venda.setFinalizadoPor(vendedor);
        venda.setFinalizadoEm(Instant.now());
        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda avancarStatus(Long vendaId, StatusVendaRequestDTO dto) {
        Venda venda = buscarPorId(vendaId);
        var autenticado = exigirVendedorAutenticado(dto.autenticacaoUsuario());

        StatusVenda novoStatus = dto.novoStatus();
        if (novoStatus == null) {
            throw new IllegalArgumentException("O novo status da venda e obrigatorio.");
        }

        transicionar(venda, novoStatus);
        venda.setStatusAlteradoPor(autenticado);
        venda.setStatusAlteradoEm(Instant.now());
        if (novoStatus == StatusVenda.CONCLUIDA) {
            confirmarEstoqueDaVenda(venda);
        } else if (novoStatus == StatusVenda.CANCELADA) {
            liberarEstoqueDaVenda(venda);
        }
        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda avaliarProcedencia(Long vendaId, StatusAvaliacaoProcedencia resultado) {
        Venda venda = buscarPorId(vendaId);
        venda.setAvaliacaoProcedencia(resultado);

        if (resultado == StatusAvaliacaoProcedencia.Improcedente
                && venda.getStatus() != StatusVenda.CANCELADA) {
            transicionar(venda, StatusVenda.CANCELADA);
            liberarEstoqueDaVenda(venda);
        }
        return vendaRepository.save(venda);
    }

    /**
     * Cancela um carrinho abandonado e devolve as unidades reservadas ao estoque.
     * Uma venda por transação: trava a venda e confere de novo, porque o vendedor pode ter
     * mexido nela entre a busca do job e este ponto.
     *
     * @return true se a venda foi cancelada; false se já não estava ABERTA ou voltou a ter atividade
     */
    @Transactional
    public boolean expirarCarrinho(Long vendaId, Instant limite) {
        Venda travada = vendaRepository.buscarPorIdForUpdate(vendaId).orElse(null);
        if (travada == null || travada.getStatus() != StatusVenda.ABERTA
                || travada.getAtualizadoEm() == null || !travada.getAtualizadoEm().isBefore(limite)) {
            return false;
        }
        Venda venda = buscarPorId(vendaId);
        transicionar(venda, StatusVenda.CANCELADA);
        liberarEstoqueDaVenda(venda);
        vendaRepository.save(venda);
        return true;
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
        Venda venda = buscarParaEdicao(vendaId);
        exigirCarrinhoEditavel(venda);
        if (dto.forma() == FormaPagamento.VOUCHER) {
            throw new NegocioException("Voucher não é forma de pagamento: informe o número no bloco Renova.");
        }

        CondicaoPagamento condicao = venda.getCondicaoPagamento();
        int parcelas = dto.parcelas() == null ? 1 : dto.parcelas();
        exigirFormaAceita(condicao, dto.forma(), parcelas);

        // O pagamento não pode ultrapassar o saldo restante. No parcelado a entrada muda o total: confere depois.
        BigDecimal jaPago = somarPagamentos(vendaId);
        if (!condicao.parcelado() && jaPago.add(dto.valor()).compareTo(venda.getValorTotal()) > 0) {
            throw new NegocioException("O pagamento excede o saldo restante da venda ("
                    + venda.getValorTotal().subtract(jaPago) + ").");
        }

        PagamentoVenda pagamento = PagamentoVenda.builder()
                .venda(venda)
                .forma(dto.forma())
                .valor(dto.valor())
                .parcelas(parcelas)
                .tipo(tipoDaLinha(condicao))
                .build();
        pagamentoVendaRepository.save(pagamento);
        reprecificar(venda);
        BigDecimal entrada = somarEntradas(listarPagamentos(vendaId));
        if (condicao.parcelado() && entrada.compareTo(venda.getValorTotal()) > 0) {
            throw new NegocioException("A entrada (" + entrada + ") passa do total da venda ("
                    + venda.getValorTotal() + ").");
        }
        registrarAtividade(venda);

        return buscarPorId(vendaId);
    }

    @Transactional
    public Venda removerPagamento(Long vendaId, Long pagamentoId) {
        Venda venda = buscarParaEdicao(vendaId);
        exigirCarrinhoEditavel(venda);

        PagamentoVenda pagamento = pagamentoVendaRepository.findById(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento"));
        if (!pagamento.getVenda().getId().equals(vendaId)) {
            throw new NegocioException("Esse pagamento nao pertence a essa venda.");
        }
        pagamentoVendaRepository.delete(pagamento);
        pagamentoVendaRepository.flush();
        reprecificar(venda);
        registrarAtividade(venda);

        return buscarPorId(vendaId);
    }

    /**
     * Liga/desliga a TABELA RENOVA nos aparelhos da venda e reprecifica.
     * Ligar exige o número do voucher, livre em outras vendas, e ao menos um aparelho com preço por plano; aparelho
     * em plano pré-pago recusa o Renova (422). Desligar limpa o número. Os pagamentos não são alterados: se passarem
     * do novo total, a resposta traz o aviso e a venda só finaliza depois do ajuste.
     */
    @Transactional
    public Venda aplicarRenova(Long vendaId, boolean ativo, String codigoVoucher) {
        Venda venda = buscarParaEdicao(vendaId);
        exigirCarrinhoEditavel(venda);

        String codigo = null;
        if (ativo) {
            codigo = numeroDoVoucher(codigoVoucher);
            boolean temAparelho = venda.getItens().stream()
                    .anyMatch(i -> i.getEstoque() != null && resolvedorTabela.elegivelRenova(i.getCategoria(), i.getGrupoPlano()));
            if (!temAparelho) {
                throw new NegocioException("A venda não tem aparelho com preço por plano para aplicar o Renova.");
            }
            exigirVoucherLivre(vendaId, codigo);
        }
        venda.setRenova(ativo);
        venda.setCodigoVoucher(codigo);
        reprecificar(venda);
        return vendaRepository.save(venda);
    }

    private String numeroDoVoucher(String codigoVoucher) {
        String codigo = codigoVoucher == null ? "" : codigoVoucher.trim();
        if (codigo.isEmpty()) {
            throw new NegocioException("Informe o número do voucher.");
        }
        if (codigo.length() > TAMANHO_MAXIMO_VOUCHER) {
            throw new NegocioException("O número do voucher deve ter no máximo " + TAMANHO_MAXIMO_VOUCHER + " caracteres.");
        }
        return codigo;
    }

    /** Confere as vendas com o número na venda e o histórico anterior à V28, com o voucher em linha de pagamento. */
    private void exigirVoucherLivre(Long vendaId, String codigo) {
        Stream.concat(vendaRepository.buscarOutrasVendasComVoucher(codigo, vendaId).stream(),
                        pagamentoVendaRepository.buscarOutrasVendasComVoucher(codigo, vendaId).stream())
                .min(Long::compare)
                .ifPresent(outraVenda -> {
                    throw new VoucherJaUsadoException(outraVenda);
                });
    }

    /**
     * Troca a condição de pagamento e reprecifica os aparelhos e acessórios com preço de tabela. Chip, serviço e
     * recarga não mudam. Linha que a nova condição não aceita recusa a troca (422, com a lista do que remover). As
     * compatíveis trocam de tipo; a FINANCIADO acompanha o novo parcelado e é apagada ao sair dele. Se os pagamentos
     * passarem do novo total, a resposta traz o aviso e a venda só finaliza depois do ajuste.
     */
    @Transactional
    public Venda definirCondicaoPagamento(Long vendaId, CondicaoPagamento condicao) {
        Venda venda = buscarParaEdicao(vendaId);
        exigirCarrinhoEditavel(venda);

        List<PagamentoVenda> digitadas = listarPagamentos(vendaId).stream().filter(digitada()).toList();
        List<PagamentoIncompativelDTO> incompativeis = digitadas.stream()
                .filter(p -> !condicao.aceita(p.getForma(), p.getParcelas()))
                .map(PagamentoIncompativelDTO::from)
                .toList();
        if (!incompativeis.isEmpty()) {
            throw new PagamentosIncompativeisException(condicao, incompativeis);
        }

        venda.setCondicaoPagamento(condicao);
        for (PagamentoVenda linha : digitadas) {
            linha.setTipo(tipoDaLinha(condicao));
        }
        pagamentoVendaRepository.saveAll(digitadas);
        reprecificar(venda);
        return vendaRepository.save(venda);
    }

    /** No parcelado, cria a linha FINANCIADO com o saldo; se ela já existe, só atualiza o valor. */
    @Transactional
    public Venda financiar(Long vendaId) {
        Venda venda = buscarParaEdicao(vendaId);
        exigirCarrinhoEditavel(venda);

        CondicaoPagamento condicao = venda.getCondicaoPagamento();
        if (!condicao.parcelado()) {
            throw new NegocioException("Só há saldo financiado nas condições de 13x a 21x.");
        }
        reprecificar(venda);

        List<PagamentoVenda> linhas = listarPagamentos(vendaId);
        BigDecimal saldo = venda.getValorTotal().subtract(somarEntradas(linhas));
        if (saldo.signum() <= 0) {
            throw new NegocioException("A venda não tem saldo para financiar.");
        }
        if (linhas.stream().noneMatch(digitada().negate())) {
            pagamentoVendaRepository.save(PagamentoVenda.builder()
                    .venda(venda)
                    .forma(FormaPagamento.CARTAO_CREDITO)
                    .valor(saldo)
                    .parcelas(condicao.parcelas())
                    .tipo(TipoPagamento.FINANCIADO)
                    .build());
        }
        registrarAtividade(venda);

        return buscarPorId(vendaId);
    }

    /** Total da venda em cada condição, com as entradas lançadas que a condição aceita. */
    @Transactional(readOnly = true)
    public List<SimulacaoPagamentoDTO> simularPagamento(Long vendaId) {
        Venda venda = buscarPorId(vendaId);
        List<PagamentoVenda> digitadas = listarPagamentos(vendaId).stream().filter(digitada()).toList();

        return Arrays.stream(CondicaoPagamento.values()).map(condicao -> {
            BigDecimal entrada = somarEntradas(digitadas.stream()
                    .filter(p -> condicao.aceita(p.getForma(), p.getParcelas())).toList());
            TotalVenda total = CalculadoraCondicaoPagamento.totalDaVenda(
                    condicao, composicao(venda, condicao, (item, preco) -> { }), entrada);
            return new SimulacaoPagamentoDTO(condicao, total.total(), condicao.parcelas(),
                    total.valorParcela(), total.ajuste());
        }).toList();
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

    /** Soma das linhas lançadas pelo vendedor, que abatem a base dos juros no parcelado. */
    private BigDecimal somarEntradas(List<PagamentoVenda> linhas) {
        return linhas.stream()
                .filter(digitada())
                .map(PagamentoVenda::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Linha lançada pelo vendedor: tudo menos a FINANCIADO, que é do backend. */
    private Predicate<PagamentoVenda> digitada() {
        return p -> p.getTipo() != TipoPagamento.FINANCIADO;
    }

    private TipoPagamento tipoDaLinha(CondicaoPagamento condicao) {
        return condicao.parcelado() ? TipoPagamento.ENTRADA : TipoPagamento.PAGAMENTO;
    }

    private void exigirFormaAceita(CondicaoPagamento condicao, FormaPagamento forma, int parcelas) {
        if (condicao.aceita(forma, parcelas)) return;
        if (condicao.parcelado() && forma == FormaPagamento.CARTAO_CREDITO) {
            throw new NegocioException("Em " + condicao + " o crédito é o saldo financiado, criado pelo sistema: "
                    + "lance só as entradas e use o financiamento.");
        }
        throw new NegocioException("A condição " + condicao + " não aceita " + forma + " em " + parcelas + "x.");
    }

    /**
     * Preço do item na tabela vigente, pela condição de pagamento: primeiro na REGULAR; se a venda está com Renova e
     * o item é elegível (aparelho com preço por plano), na RENOVA.
     */
    private PrecoCalculado precoNaTabela(Venda venda, CondicaoPagamento condicao, CategoriaItemVenda categoria,
                                         EstoqueGeral unidade, String plano) {
        FormaPagamento forma = condicao.formaPagamento();
        Integer parcelas = condicao.numeroParcelas();
        PrecoCalculado regular = precoVigenteService.calcular(unidade, plano, TabelaPreco.REGULAR, forma, parcelas);
        TabelaPreco tabela = resolvedorTabela.resolver(venda.isRenova(), categoria, regular.grupoPlano());
        return tabela == TabelaPreco.REGULAR ? regular
                : precoVigenteService.calcular(unidade, plano, tabela, forma, parcelas);
    }

    /** Aparelho ou acessório precificado pela tabela da Telefônica (grupo do plano gravado ao adicionar). */
    private boolean temPrecoDeTabela(ItemVenda item) {
        return (item.getCategoria() == CategoriaItemVenda.PRODUTO_VIVO || item.getCategoria() == CategoriaItemVenda.ACESSORIO)
                && item.getEstoque() != null && item.getGrupoPlano() != null;
    }

    /**
     * Toda mudança em itens, Renova, condição ou entrada passa por aqui: preço dos itens de tabela, composição do
     * total da venda e valor da linha FINANCIADO. O item fica no preço da condição sem entrada; a redução dos juros
     * pela entrada aparece só no total da venda.
     */
    private void reprecificar(Venda venda) {
        CondicaoPagamento condicao = venda.getCondicaoPagamento();
        ComposicaoVenda composicao = composicao(venda, condicao, (item, preco) -> {
            item.setValorUnitario(preco.valorAplicado());
            item.setTabelaAplicada(preco.tabela());
            item.setGrupoPlano(preco.grupoPlano());
            item.setDescontoFabricante(preco.descontoFabricante());
            item.setFormaPagamento(condicao.formaPagamento());
            item.setParcelas(condicao.numeroParcelas());
        });
        venda.recalcularValorTotal();

        List<PagamentoVenda> linhas = listarPagamentos(venda.getId());
        TotalVenda total = CalculadoraCondicaoPagamento.totalDaVenda(condicao, composicao, somarEntradas(linhas));
        venda.setValorItens(composicao.valorItens());
        venda.setValorTotal(total.total());
        venda.setAjusteCondicao(total.ajuste());
        venda.setValorParcela(total.valorParcela());
        atualizarFinanciado(linhas, condicao, total.financiado());
    }

    private void exigirTotalVigente(Venda venda) {
        BigDecimal anterior = venda.getValorTotal();
        reprecificar(venda);
        if (anterior.compareTo(venda.getValorTotal()) != 0) {
            throw new TotalVendaAlteradoException(anterior, venda.getValorTotal());
        }
    }

    /** O que compõe o total da venda numa condição; {@code aoPrecificar} recebe cada item de tabela. */
    private ComposicaoVenda composicao(Venda venda, CondicaoPagamento condicao,
                                       BiConsumer<ItemVenda, PrecoCalculado> aoPrecificar) {
        BigDecimal cheio = BigDecimal.ZERO;
        BigDecimal naCondicao = BigDecimal.ZERO;
        BigDecimal desconto = BigDecimal.ZERO;
        BigDecimal semTabela = BigDecimal.ZERO;
        for (ItemVenda item : venda.getItens()) {
            semTabela = semTabela.add(item.getValorChipCobrado());
            if (!temPrecoDeTabela(item)) {
                semTabela = semTabela.add(item.getValorTotalItem());
                continue;
            }
            PrecoCalculado preco = precoNaTabela(venda, condicao, item.getCategoria(), item.getEstoque(), item.getPlano());
            aoPrecificar.accept(item, preco);
            cheio = cheio.add(vezesQuantidade(preco.preco(), item));
            naCondicao = naCondicao.add(vezesQuantidade(preco.valorAplicado(), item));
            if (preco.descontoFabricante() != null) {
                desconto = desconto.add(vezesQuantidade(preco.descontoFabricante(), item));
            }
        }
        return new ComposicaoVenda(cheio, naCondicao, desconto, semTabela);
    }

    private BigDecimal vezesQuantidade(BigDecimal valor, ItemVenda item) {
        return valor.multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Acompanha o saldo e o número de parcelas; sem saldo ou fora do parcelado, a linha é apagada. */
    private void atualizarFinanciado(List<PagamentoVenda> linhas, CondicaoPagamento condicao, BigDecimal financiado) {
        linhas.stream().filter(digitada().negate()).findFirst().ifPresent(linha -> {
            if (financiado.signum() > 0) {
                linha.setValor(financiado);
                linha.setParcelas(condicao.parcelas());
                pagamentoVendaRepository.save(linha);
            } else {
                pagamentoVendaRepository.delete(linha);
            }
        });
    }

    /** Aparelho/acessório: preço da tabela vigente. Serviço: sempre 0, o valor enviado é ignorado. Recarga: valor informado. */
    private BigDecimal valorUnitarioDoItem(ItemVendaRequestDTO dto, PrecoCalculado preco) {
        if (preco != null) return preco.valorAplicado();
        return dto.categoria() == CategoriaItemVenda.SERVICO_VIVO ? BigDecimal.ZERO : dto.valorUnitario();
    }

    /** Trava a venda (FOR UPDATE) antes de mexer no carrinho, para não concorrer com a expiração. */
    private Venda buscarParaEdicao(Long vendaId) {
        vendaRepository.buscarPorIdForUpdate(vendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Venda"));
        return buscarPorId(vendaId);
    }

    /** Pagamento fica em outra tabela: marca a venda como alterada para contar como atividade do carrinho. */
    private void registrarAtividade(Venda venda) {
        venda.setAtualizadoEm(Instant.now());
        vendaRepository.save(venda);
    }

    private void exigirCarrinhoEditavel(Venda venda) {
        if (venda.getStatus() != StatusVenda.ABERTA) {
            throw new CarrinhoNaoEditavelException(venda.getId(), venda.getStatus());
        }
    }

    /** Venda CONCLUIDA: cada unidade reservada pelos itens vira VENDIDO. */
    private void confirmarEstoqueDaVenda(Venda venda) {
        for (ItemVenda item : venda.getItens()) {
            if (item.getEstoque() != null) {
                estoqueService.confirmarVenda(item.getEstoque());
            }
        }
    }

    /** Venda cancelada: devolve ao estoque todas as unidades dos itens. */
    private void liberarEstoqueDaVenda(Venda venda) {
        for (ItemVenda item : venda.getItens()) {
            liberarEstoqueDoItem(item);
        }
    }

    /** Devolve ao estoque a linha que o item reservou ou vendeu (volta a DISPONIVEL). */
    private void liberarEstoqueDoItem(ItemVenda item) {
        if (item.getEstoque() != null) {
            estoqueService.liberar(item.getEstoque());
        }
    }

    private Usuario exigirVendedorAutenticado(AutenticacaoUsuarioDTO autenticacao) {
        if (autenticacao == null) {
            throw new IllegalArgumentException("Dados de autenticacao do vendedor sao obrigatorios.");
        }
        return usuarioService.autenticar(autenticacao);
    }

    private void transicionar(Venda venda, StatusVenda novoStatus) {
        if (!venda.getStatus().podeTransicionarPara(novoStatus)) {
            throw new TransicaoStatusInvalidaException(venda.getStatus(), novoStatus);
        }
        venda.setStatus(novoStatus);
    }
}