package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.CaixaException;
import com.vivo4redes.syscor.exception.CaixaComDiferencaException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.financeiro.dto.caixa.*;
import com.vivo4redes.syscor.financeiro.enums.AcaoCaixa;
import com.vivo4redes.syscor.financeiro.enums.ConferenciaCaixa;
import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.financeiro.enums.TipoLancamentoCaixa;
import com.vivo4redes.syscor.financeiro.model.CaixaHistorico;
import com.vivo4redes.syscor.financeiro.model.CaixaLancamento;
import com.vivo4redes.syscor.financeiro.model.CaixaSessao;
import com.vivo4redes.syscor.financeiro.model.FormaPagamentoCaixa;
import com.vivo4redes.syscor.financeiro.repository.CaixaHistoricoRepository;
import com.vivo4redes.syscor.financeiro.repository.CaixaLancamentoRepository;
import com.vivo4redes.syscor.financeiro.repository.CaixaSessaoRepository;
import com.vivo4redes.syscor.financeiro.repository.FormaPagamentoCaixaRepository;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import com.vivo4redes.syscor.venda.service.FilialService;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Caixa diário da filial (docs/financeiro_caixa.md). Sem caixa aberto hoje a filial não lança venda (ver
 * {@link CaixaVendaService}); aqui ficam abrir, fechar, conferir, reabrir e as consultas.
 * <p>
 * DINHEIRO fica na gaveta: esperado = abertura + movimento + entradas − saídas. PIX, Vivo Pay, cartões e boleto
 * liquidam na conciliação: as entradas contam como saída no fechamento e o contabilizado é 0.
 * As entradas vêm das vendas CONCLUIDAS lançadas neste caixa ({@code vendas.caixa_sessao_id}), nunca de janela de data.
 */
@Service
public class CaixaSessaoService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");
    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    private static final int MINIMO_JUSTIFICATIVA = 10;

    /** Formas que liquidam na conciliação (não ficam na gaveta). VOUCHER é só histórico e não abre linha no caixa. */
    private static final Set<FormaPagamento> LIQUIDAM = EnumSet.of(
            FormaPagamento.PIX, FormaPagamento.VIVO_PAY, FormaPagamento.CARTAO_CREDITO,
            FormaPagamento.CARTAO_DEBITO, FormaPagamento.BOLETO);
    private static final List<FormaPagamento> FORMAS_DO_CAIXA = List.of(
            FormaPagamento.DINHEIRO, FormaPagamento.PIX, FormaPagamento.VIVO_PAY,
            FormaPagamento.CARTAO_CREDITO, FormaPagamento.CARTAO_DEBITO, FormaPagamento.BOLETO);

    private final CaixaSessaoRepository caixaSessaoRepository;
    private final FormaPagamentoCaixaRepository formaPagamentoCaixaRepository;
    private final CaixaHistoricoRepository historicoRepository;
    private final PagamentoVendaRepository pagamentoVendaRepository;
    private final VendaRepository vendaRepository;
    private final FilialService filialService;
    private final UsuarioService usuarioService;
    private final CaixaPerfilService perfil;
    private final CaixaTentativaService tentativaService;
    private final CaixaLancamentoRepository lancamentoRepository;

    public CaixaSessaoService(CaixaSessaoRepository caixaSessaoRepository,
                              FormaPagamentoCaixaRepository formaPagamentoCaixaRepository,
                              CaixaHistoricoRepository historicoRepository,
                              PagamentoVendaRepository pagamentoVendaRepository,
                              VendaRepository vendaRepository,
                              FilialService filialService,
                              UsuarioService usuarioService,
                              CaixaPerfilService perfil,
                              CaixaTentativaService tentativaService,
                              CaixaLancamentoRepository lancamentoRepository) {
        this.caixaSessaoRepository = caixaSessaoRepository;
        this.formaPagamentoCaixaRepository = formaPagamentoCaixaRepository;
        this.historicoRepository = historicoRepository;
        this.pagamentoVendaRepository = pagamentoVendaRepository;
        this.vendaRepository = vendaRepository;
        this.filialService = filialService;
        this.usuarioService = usuarioService;
        this.perfil = perfil;
        this.tentativaService = tentativaService;
        this.lancamentoRepository = lancamentoRepository;
    }

    // ------------------------------------------------------------------ abrir
    @Transactional
    public CaixaSessaoResponseDTO abrir(AbrirCaixaRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        Filial filial = filialService.buscarPorId(dto.filialId());
        perfil.exigirAbrirFechar(usuario, filial);

        String caixaPdv = nomeDoCaixa(dto.caixaPdv());
        LocalDate hoje = LocalDate.now(ZONA);
        if (caixaSessaoRepository.existsByFilialIdAndCaixaPdvAndDataAbertura(filial.getId(), caixaPdv, hoje)) {
            throw CaixaException.jaAberto(caixaPdv);
        }

        Map<FormaPagamento, BigDecimal> aberturas = aberturasInformadas(dto.formasIniciais());
        BigDecimal dinheiro = aberturas.get(FormaPagamento.DINHEIRO);
        String justificativa = textoOuNull(dto.justificativaAbertura());
        BigDecimal sugerido = sugestaoDinheiro(filial.getId(), caixaPdv).map(CaixaSessaoService::valorDoDinheiro).orElse(null);
        if (sugerido != null && dinheiro.compareTo(sugerido) != 0) {
            exigirJustificativa(justificativa, "A abertura do dinheiro difere do valor sugerido (" + sugerido
                    + ", fechamento do último caixa): informe a justificativa da abertura (mín. "
                    + MINIMO_JUSTIFICATIVA + " caracteres).");
        }

        CaixaSessao sessao = CaixaSessao.builder()
                .filial(filial)
                .caixaPdv(caixaPdv)
                .dataAbertura(hoje)
                .status(StatusCaixaSessao.ABERTO)
                .abertoPor(usuario)
                .justificativaAbertura(justificativa)
                .build();
        try {
            sessao = caixaSessaoRepository.saveAndFlush(sessao);
        } catch (DataIntegrityViolationException e) {
            throw CaixaException.jaAberto(caixaPdv); // duas aberturas simultâneas: o índice único barra a segunda
        }

        final CaixaSessao salva = sessao;
        formaPagamentoCaixaRepository.saveAll(FORMAS_DO_CAIXA.stream()
                .map(f -> FormaPagamentoCaixa.builder()
                        .caixaSessao(salva)
                        .forma(f)
                        .abertura(aberturas.getOrDefault(f, BigDecimal.ZERO))
                        .build())
                .toList());
        registrar(salva, AcaoCaixa.ABRIU, usuario, justificativa != null ? justificativa : "Abertura do caixa");
        return montarResponse(salva, false, usuario);
    }

    /** Abertura sugerida: o contabilizado em dinheiro do último caixa fechado desse caixa da filial. */
    @Transactional(readOnly = true)
    public SugestaoAberturaResponseDTO sugestaoAbertura(Long filialId, String caixaPdv) {
        Usuario usuario = usuarioService.usuarioLogado();
        Filial filial = filialService.buscarPorId(filialId);
        if (!perfil.podeAbrirFechar(usuario, filial)) {
            throw CaixaException.semPermissao("Seu perfil não pode abrir caixa nessa filial.");
        }
        return sugestaoDinheiro(filial.getId(), nomeDoCaixa(caixaPdv))
                .map(c -> new SugestaoAberturaResponseDTO(valorDoDinheiro(c), c.getDataAbertura()))
                .orElse(new SugestaoAberturaResponseDTO(null, null));
    }

    // ------------------------------------------------------------------ consultas
    /** Situação do caixa de hoje na filial (qualquer usuário autenticado: a tela de Venda mostra a faixa de aviso). */
    @Transactional(readOnly = true)
    public SituacaoCaixaResponseDTO situacao(Long filialId) {
        Usuario usuario = usuarioService.usuarioLogado();
        Filial filial = filialService.buscarPorId(filialId);
        LocalDate hoje = LocalDate.now(ZONA);

        List<CaixaSessao> abertos = caixaSessaoRepository.findByFilialIdAndDataAberturaAndStatusOrderByIdAsc(
                filial.getId(), hoje, StatusCaixaSessao.ABERTO);
        List<String> avisos = new ArrayList<>();
        List<CaixaSessao> anteriores = caixaSessaoRepository.findByFilialIdAndStatusAndDataAberturaBeforeOrderByDataAberturaAsc(
                filial.getId(), StatusCaixaSessao.ABERTO, hoje);
        if (!anteriores.isEmpty()) {
            CaixaSessao maisAntigo = anteriores.get(0);
            avisos.add("Há " + anteriores.size() + " caixa(s) de dia anterior ainda aberto(s); o mais antigo é de "
                    + maisAntigo.getDataAbertura().format(DD_MM) + " (" + maisAntigo.getCaixaPdv() + ").");
        }
        CaixaSessao primeiro = abertos.isEmpty() ? null : abertos.get(0);
        return new SituacaoCaixaResponseDTO(
                primeiro != null,
                primeiro == null ? null : primeiro.getId(),
                primeiro == null ? null : primeiro.getDataAbertura(),
                primeiro == null ? null : primeiro.getCaixaPdv(),
                abertos.stream().map(c -> new SituacaoCaixaResponseDTO.CaixaResumoDTO(c.getId(), c.getCaixaPdv())).toList(),
                perfil.podeAbrirFechar(usuario, filial),
                perfil.podeFechar(usuario, filial),
                avisos);
    }

    /** Permissões gerais do usuário logado no caixa (o que o front mostra ou esconde). */
    @Transactional(readOnly = true)
    public PermissoesCaixaResponseDTO permissoes() {
        Usuario usuario = usuarioService.usuarioLogado();
        return new PermissoesCaixaResponseDTO(perfil.podeConferirReabrir(usuario), perfil.veTodasAsFiliais(usuario),
                perfil.filialDoUsuario(usuario), perfil.veEsperadoDoAberto(usuario), perfil.podeFecharComDivergencia(usuario),
                perfil.podeEstornar(usuario));
    }

    @Transactional(readOnly = true)
    public PaginaCaixaResponseDTO listarAbertos(Long filialId, LocalDate dataInicio, LocalDate dataFim,
                                                ConferenciaCaixa conferencia, String busca,
                                                String ordenar, String direcao, int page, int size) {
        return listar(StatusCaixaSessao.ABERTO, filialId, dataInicio, dataFim, conferencia, busca, ordenar, direcao, page, size);
    }

    @Transactional(readOnly = true)
    public PaginaCaixaResponseDTO listarFechados(Long filialId, LocalDate dataInicio, LocalDate dataFim,
                                                 ConferenciaCaixa conferencia, String busca,
                                                 String ordenar, String direcao, int page, int size) {
        return listar(StatusCaixaSessao.FECHADO, filialId, dataInicio, dataFim, conferencia, busca, ordenar, direcao, page, size);
    }

    @Transactional(readOnly = true)
    public CaixaSessaoResponseDTO buscarPorId(Long id) {
        CaixaSessao sessao = buscarEntidade(id);
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirConsultar(usuario, sessao.getFilial());
        return montarResponse(sessao, true, usuario);
    }

    /** Uma linha por pagamento lançado neste caixa, em qualquer status de venda. */
    @Transactional(readOnly = true)
    public List<MovimentoCaixaResponseDTO> movimentoDetalhado(Long id) {
        CaixaSessao sessao = buscarEntidade(id);
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirConsultar(usuario, sessao.getFilial());
        // Cada pagamento mostra o valor: somados, entregariam o esperado. Com o caixa aberto, só quem vê o esperado consulta.
        if (sessao.getStatus() == StatusCaixaSessao.ABERTO && !perfil.veEsperadoDoAberto(usuario)) {
            throw CaixaException.semPermissao("O movimento detalhado do caixa aberto só aparece depois do fechamento.");
        }
        List<MovimentoCaixaResponseDTO> linhas = new ArrayList<>(
                pagamentoVendaRepository.listarDoCaixa(id).stream().map(CaixaSessaoService::paraMovimento).toList());
        lancamentoRepository.findByCaixaSessaoIdOrderByCriadoEmAscIdAsc(id).forEach(l -> linhas.add(paraMovimento(l)));
        linhas.sort(Comparator.comparing(MovimentoCaixaResponseDTO::hora));
        return linhas;
    }

    // ------------------------------------------------------------------ fechar
    /**
     * Conferência por forma: o valor digitado de cada forma exigida é comparado ao esperado, e o caixa só fecha com
     * diferença zero em todas. Com diferença: o gerente da loja é recusado (422, só as formas que divergem, sem os
     * valores) e a tentativa fica gravada, no máximo 5 por caixa por dia; quem pode fechar com divergência informa a
     * justificativa e o caixa fecha AGUARDANDO conferência, marcado como divergente.
     */
    @Transactional
    public CaixaSessaoResponseDTO fechar(Long id, FecharCaixaRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        CaixaSessao sessao = buscarEntidade(id);
        perfil.exigirFechar(usuario, sessao.getFilial());
        if (sessao.getStatus() == StatusCaixaSessao.FECHADO) {
            throw new NegocioException("Esse caixa já está fechado.");
        }
        exigirSemCarrinhoAberto(sessao);

        Map<FormaPagamento, BigDecimal> digitado = valoresDigitados(dto.formas());
        List<FormaPagamentoCaixa> formas = formaPagamentoCaixaRepository.findByCaixaSessaoId(id);
        Set<FormaPagamento> doCaixa = formas.stream().map(FormaPagamentoCaixa::getForma).collect(Collectors.toSet());
        for (FormaPagamento informada : digitado.keySet()) {
            if (!doCaixa.contains(informada)) {
                throw new NegocioException("A forma " + informada + " não faz parte desse caixa.");
            }
        }

        Calculo calculo = calcular(id);
        for (FormaPagamentoCaixa f : formas) {
            if (calculo.exigida(f) && !digitado.containsKey(f.getForma())) {
                throw new NegocioException("Falta o valor conferido da forma " + f.getForma() + " pra fechar esse caixa.");
            }
        }

        boolean podeDivergir = perfil.podeFecharComDivergencia(usuario);
        LocalDate hoje = LocalDate.now(ZONA);
        if (!podeDivergir && tentativaService.tentativasHoje(id, hoje) >= CaixaTentativaService.MAXIMO_POR_DIA) {
            throw new CaixaException("CAIXA_FECHAMENTO_BLOQUEADO", HttpStatus.LOCKED,
                    "Fechamento bloqueado: chame o financeiro.");
        }

        // Nada é alterado antes de decidir: a forma que não é exigida e não veio fecha sem diferença (esperado = 0).
        List<TentativaFechamentoDTO.Linha> linhas = new ArrayList<>();
        for (FormaPagamentoCaixa f : formas) {
            BigDecimal esperado = calculo.esperado(f);
            BigDecimal conferido = digitado.getOrDefault(f.getForma(), esperado).setScale(2, RoundingMode.HALF_UP);
            linhas.add(new TentativaFechamentoDTO.Linha(f.getForma(), conferido, esperado,
                    conferido.subtract(esperado).setScale(2, RoundingMode.HALF_UP)));
        }
        List<TentativaFechamentoDTO.Linha> divergentes = linhas.stream().filter(l -> l.diferenca().signum() != 0).toList();
        String observacao = textoOuNull(dto.observacao());
        String justificativa = textoOuNull(dto.justificativa());

        if (!divergentes.isEmpty()) {
            if (!podeDivergir) {
                long usadas = tentativaService.registrar(id, usuario.getId(), hoje, linhas);
                int restantes = (int) Math.max(0, CaixaTentativaService.MAXIMO_POR_DIA - usadas);
                throw new CaixaComDiferencaException(
                        "O caixa só fecha com diferença zero em todas as formas. Confira: "
                                + divergentes.stream().map(l -> l.forma().name()).collect(Collectors.joining(", ")) + ". "
                                + (restantes > 0
                                ? "Restam " + restantes + " tentativa" + (restantes == 1 ? "" : "s") + "."
                                : "Próximo fechamento bloqueado: chame o financeiro."),
                        divergentes.stream().map(l -> new CaixaComDiferencaException.Forma(l.forma(), null)).toList(),
                        restantes);
            }
            if (justificativa == null || justificativa.length() < MINIMO_JUSTIFICATIVA) {
                throw new CaixaComDiferencaException(
                        "O caixa fecha com diferença: informe a justificativa (mín. " + MINIMO_JUSTIFICATIVA + " caracteres).",
                        divergentes.stream().map(l -> new CaixaComDiferencaException.Forma(l.forma(), l.diferenca())).toList(),
                        null);
            }
        }

        Map<FormaPagamento, TentativaFechamentoDTO.Linha> porForma = new EnumMap<>(FormaPagamento.class);
        linhas.forEach(l -> porForma.put(l.forma(), l));
        for (FormaPagamentoCaixa f : formas) {
            f.setValorConferido(porForma.get(f.getForma()).digitado());
            f.setDiferenca(porForma.get(f.getForma()).diferenca());
        }
        formaPagamentoCaixaRepository.saveAll(formas);

        boolean comDivergencia = !divergentes.isEmpty();
        sessao.setStatus(StatusCaixaSessao.FECHADO);
        sessao.setFechadoEm(Instant.now());
        sessao.setFechadoPor(usuario);
        sessao.setConferencia(ConferenciaCaixa.AGUARDANDO);
        sessao.setConferidoPor(null);
        sessao.setConferidoEm(null);
        sessao.setObservacao(observacao);
        sessao.setFechadoComDivergencia(comDivergencia);
        sessao.setJustificativaDivergencia(comDivergencia ? justificativa : null);
        caixaSessaoRepository.save(sessao);
        registrar(sessao, AcaoCaixa.FECHOU, usuario, comDivergencia
                ? "Fechado com divergência: " + justificativa
                : observacao != null ? observacao : "Fechamento sem diferença");
        return montarResponse(sessao, false, usuario);
    }

    // ------------------------------------------------------------------ conferir e reabrir
    @Transactional
    public CaixaSessaoResponseDTO conferir(Long id) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirConferirReabrir(usuario);
        CaixaSessao sessao = buscarEntidade(id);
        if (sessao.getStatus() != StatusCaixaSessao.FECHADO) {
            throw new NegocioException("Só se confere um caixa fechado.");
        }
        if (sessao.getConferencia() == ConferenciaCaixa.CONFERIDO) {
            throw new NegocioException("Esse caixa já foi conferido.");
        }
        // Separação de funções: quem fechou não confere o mesmo caixa (o admin fica de fora, para não travar o suporte).
        if (!perfil.ehAdmin(usuario) && sessao.getFechadoPor() != null
                && Objects.equals(sessao.getFechadoPor().getId(), usuario.getId())) {
            throw CaixaException.semPermissao(
                    "Quem fechou o caixa não pode conferi-lo: peça a outra pessoa da auditoria ou do financeiro.");
        }
        sessao.setConferencia(ConferenciaCaixa.CONFERIDO);
        sessao.setConferidoPor(usuario);
        sessao.setConferidoEm(Instant.now());
        caixaSessaoRepository.save(sessao);
        registrar(sessao, AcaoCaixa.CONFEROU, usuario, "Caixa conferido");
        return montarResponse(sessao, false, usuario);
    }

    @Transactional
    public CaixaSessaoResponseDTO reabrir(Long id, ReabrirCaixaRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirConferirReabrir(usuario);
        CaixaSessao sessao = buscarEntidade(id);
        String justificativa = textoOuNull(dto == null ? null : dto.justificativa());
        exigirJustificativa(justificativa, "Informe a justificativa da reabertura (mín. " + MINIMO_JUSTIFICATIVA + " caracteres).");
        if (sessao.getStatus() == StatusCaixaSessao.ABERTO) {
            throw new NegocioException("Esse caixa já está aberto.");
        }

        List<FormaPagamentoCaixa> formas = formaPagamentoCaixaRepository.findByCaixaSessaoId(id);
        for (FormaPagamentoCaixa f : formas) {
            f.setValorConferido(null);
            f.setDiferenca(null);
        }
        formaPagamentoCaixaRepository.saveAll(formas);

        sessao.setStatus(StatusCaixaSessao.ABERTO);
        sessao.setFechadoEm(null);
        sessao.setFechadoPor(null);
        sessao.setConferencia(ConferenciaCaixa.AGUARDANDO);
        sessao.setConferidoPor(null);
        sessao.setConferidoEm(null);
        sessao.setObservacao(null);
        sessao.setFechadoComDivergencia(false);
        sessao.setJustificativaDivergencia(null);
        sessao.setReabertoPor(usuario);
        sessao.setReabertoEm(Instant.now());
        sessao.setJustificativaReabertura(justificativa);
        caixaSessaoRepository.save(sessao);
        registrar(sessao, AcaoCaixa.REABRIU, usuario, justificativa);
        return montarResponse(sessao, false, usuario);
    }

    // ------------------------------------------------------------------ apoio
    private PaginaCaixaResponseDTO listar(StatusCaixaSessao status, Long filialId, LocalDate dataInicio, LocalDate dataFim,
                                          ConferenciaCaixa conferencia, String busca, String ordenar, String direcao,
                                          int page, int size) {
        Usuario usuario = usuarioService.usuarioLogado();
        Set<Long> visiveis = perfil.filiaisVisiveis(usuario);
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA);
        int pagina = Math.max(page, 0);
        if (visiveis != null && visiveis.isEmpty()) {
            return new PaginaCaixaResponseDTO(List.of(), pagina, tamanho, 0, 0);
        }

        Sort.Direction sentido = "asc".equalsIgnoreCase(direcao) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort ordem = "numero".equalsIgnoreCase(ordenar)
                ? Sort.by(sentido, "id")
                : Sort.by(sentido, "dataAbertura").and(Sort.by(sentido, "id"));

        Specification<CaixaSessao> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), status));
            if (visiveis != null) p.add(root.get("filial").get("id").in(visiveis));
            if (filialId != null) p.add(cb.equal(root.get("filial").get("id"), filialId));
            if (dataInicio != null) p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("dataAbertura"), dataInicio));
            if (dataFim != null) p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("dataAbertura"), dataFim));
            if (conferencia != null) p.add(cb.equal(root.get("conferencia"), conferencia));
            String termo = textoOuNull(busca);
            if (termo != null) {
                Join<?, ?> filial = root.join("filial");
                String like = "%" + termo.toLowerCase(Locale.ROOT) + "%";
                List<Predicate> ou = new ArrayList<>();
                ou.add(cb.like(cb.lower(root.<String>get("caixaPdv")), like));
                ou.add(cb.like(cb.lower(filial.<String>get("nome")), like));
                if (termo.matches("\\d{1,18}")) ou.add(cb.equal(root.get("id"), Long.parseLong(termo)));
                p.add(cb.or(ou.toArray(Predicate[]::new)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };

        Page<CaixaSessao> resultado = caixaSessaoRepository.findAll(spec, PageRequest.of(pagina, tamanho, ordem));
        return new PaginaCaixaResponseDTO(
                resultado.getContent().stream().map(s -> montarResponse(s, false, usuario)).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    /** Fecha o último caixa aberto do dia só sem carrinho pendente: com outro caixa aberto, o carrinho ainda tem para onde ir. */
    private void exigirSemCarrinhoAberto(CaixaSessao sessao) {
        Long filialId = sessao.getFilial().getId();
        LocalDate dia = sessao.getDataAbertura();
        boolean outroAberto = caixaSessaoRepository
                .findByFilialIdAndDataAberturaAndStatusOrderByIdAsc(filialId, dia, StatusCaixaSessao.ABERTO).stream()
                .anyMatch(c -> !c.getId().equals(sessao.getId()));
        if (outroAberto) return;
        Instant inicio = dia.atStartOfDay(ZONA).toInstant();
        Instant fim = dia.plusDays(1).atStartOfDay(ZONA).toInstant();
        List<Long> carrinhos = vendaRepository.numerosDeCarrinhosAbertos(filialId, inicio, fim);
        if (!carrinhos.isEmpty()) {
            throw new CaixaException("CAIXA_COM_CARRINHO_ABERTO", HttpStatus.CONFLICT,
                    "Há carrinho aberto na filial: conclua ou cancele as vendas "
                            + carrinhos.stream().map(n -> "#" + n).collect(Collectors.joining(", ")) + " antes de fechar o caixa.");
        }
    }

    private Optional<CaixaSessao> sugestaoDinheiro(Long filialId, String caixaPdv) {
        return caixaSessaoRepository.findFirstByFilialIdAndCaixaPdvAndStatusOrderByDataAberturaDescIdDesc(
                filialId, caixaPdv, StatusCaixaSessao.FECHADO);
    }

    /** Contabilizado em dinheiro de um caixa fechado. */
    private static BigDecimal valorDoDinheiro(CaixaSessao sessao) {
        return sessao.getFormasPagamento().stream()
                .filter(f -> f.getForma() == FormaPagamento.DINHEIRO && f.getValorConferido() != null)
                .map(FormaPagamentoCaixa::getValorConferido)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private static Map<FormaPagamento, BigDecimal> aberturasInformadas(List<AberturaFormaDTO> informadas) {
        Map<FormaPagamento, BigDecimal> aberturas = new EnumMap<>(FormaPagamento.class);
        if (informadas != null) {
            for (AberturaFormaDTO a : informadas) {
                if (!FORMAS_DO_CAIXA.contains(a.forma())) {
                    throw new NegocioException("A forma " + a.forma() + " não abre linha no caixa.");
                }
                if (a.valorAbertura().signum() < 0) {
                    throw new NegocioException("O valor de abertura de " + a.forma() + " não pode ser negativo.");
                }
                if (aberturas.put(a.forma(), a.valorAbertura().setScale(2, RoundingMode.HALF_UP)) != null) {
                    throw new NegocioException("A forma " + a.forma() + " aparece mais de uma vez na abertura.");
                }
            }
        }
        if (!aberturas.containsKey(FormaPagamento.DINHEIRO)) {
            throw new NegocioException("Informe o valor em dinheiro da abertura do caixa (se a gaveta abre vazia, informe 0).");
        }
        return aberturas;
    }

    private Map<FormaPagamento, BigDecimal> entradasPorForma(Long caixaId) {
        Map<FormaPagamento, BigDecimal> mapa = new EnumMap<>(FormaPagamento.class);
        for (Object[] linha : pagamentoVendaRepository.somarPorCaixaEForma(caixaId)) {
            mapa.put((FormaPagamento) linha[0], (BigDecimal) linha[1]);
        }
        return mapa;
    }

    /**
     * Valores de um caixa para conferir: entradas por forma (vendas lançadas no caixa) e, só no dinheiro, os suprimentos
     * (movimento) e as sangrias (saídas) efetivos, já descontados os estornos.
     * Esperado de qualquer forma = abertura + movimento + entradas − saídas, e é contra ele que o valor digitado é
     * conferido; nas formas que liquidam, o digitado é o total recebido, conferido contra o extrato ou a maquininha, e
     * elas não ficam na gaveta (ver {@link #contabilizadoQueFica}).
     */
    private record Calculo(Map<FormaPagamento, BigDecimal> entradas, BigDecimal suprimentos, BigDecimal sangrias) {

        BigDecimal entradas(FormaPagamentoCaixa f) {
            return entradas.getOrDefault(f.getForma(), BigDecimal.ZERO);
        }

        BigDecimal movimento(FormaPagamentoCaixa f) {
            return safe(f.getMovimento()).add(f.getForma() == FormaPagamento.DINHEIRO ? suprimentos : BigDecimal.ZERO);
        }

        BigDecimal saidas(FormaPagamentoCaixa f) {
            return safe(f.getSaidas()).add(f.getForma() == FormaPagamento.DINHEIRO ? sangrias : BigDecimal.ZERO);
        }

        BigDecimal esperado(FormaPagamentoCaixa f) {
            return safe(f.getAbertura()).add(movimento(f)).add(entradas(f)).subtract(saidas(f)).setScale(2, RoundingMode.HALF_UP);
        }

        /** Forma que precisa ser informada para fechar: o dinheiro sempre, e as demais se tiverem abertura ou movimento. */
        boolean exigida(FormaPagamentoCaixa f) {
            return f.getForma() == FormaPagamento.DINHEIRO
                    || safe(f.getAbertura()).signum() != 0 || movimento(f).signum() != 0
                    || saidas(f).signum() != 0 || entradas(f).signum() != 0;
        }
    }

    private Calculo calcular(Long caixaId) {
        BigDecimal suprimentos = BigDecimal.ZERO;
        BigDecimal sangrias = BigDecimal.ZERO;
        for (CaixaLancamento l : lancamentoRepository.findByCaixaSessaoIdOrderByCriadoEmAscIdAsc(caixaId)) {
            // Estorno desfaz o efeito do lançamento original: estornar sangria devolve o dinheiro, estornar suprimento o tira.
            TipoLancamentoCaixa efeito = l.getTipo() == TipoLancamentoCaixa.ESTORNO ? l.getEstornadoDe().getTipo() : l.getTipo();
            BigDecimal sinal = l.getTipo() == TipoLancamentoCaixa.ESTORNO ? l.getValor().negate() : l.getValor();
            if (efeito == TipoLancamentoCaixa.SUPRIMENTO) suprimentos = suprimentos.add(sinal);
            else sangrias = sangrias.add(sinal);
        }
        return new Calculo(entradasPorForma(caixaId), suprimentos, sangrias);
    }

    /** O dinheiro que há (ou deve haver) no caixa agora: usado para limitar a sangria e o estorno de suprimento. */
    public BigDecimal esperadoDoDinheiro(Long caixaId) {
        FormaPagamentoCaixa dinheiro = formaPagamentoCaixaRepository.findByCaixaSessaoId(caixaId).stream()
                .filter(f -> f.getForma() == FormaPagamento.DINHEIRO).findFirst()
                .orElseThrow(() -> new NegocioException("Esse caixa não tem linha de dinheiro."));
        return calcular(caixaId).esperado(dinheiro);
    }

    /** O que fica no caixa: o esperado do dinheiro; 0,00 nas formas que liquidam na conciliação. */
    private static BigDecimal contabilizadoQueFica(FormaPagamentoCaixa f, BigDecimal esperado) {
        return LIQUIDAM.contains(f.getForma()) ? BigDecimal.ZERO.setScale(2) : esperado;
    }

    private static Map<FormaPagamento, BigDecimal> valoresDigitados(List<ConferenciaFormaDTO> informadas) {
        Map<FormaPagamento, BigDecimal> digitado = new EnumMap<>(FormaPagamento.class);
        for (ConferenciaFormaDTO c : informadas == null ? List.<ConferenciaFormaDTO>of() : informadas) {
            if (c.valorConferido().signum() < 0) {
                throw new NegocioException("O valor conferido de " + c.forma() + " não pode ser negativo.");
            }
            if (digitado.put(c.forma(), c.valorConferido()) != null) {
                throw new NegocioException("A forma " + c.forma() + " aparece mais de uma vez no fechamento.");
            }
        }
        return digitado;
    }

    /**
     * Fechamento cego: com o caixa aberto, só quem vê o esperado (financeiro, gerente de área, auditoria e admin) o
     * recebe. O gerente da loja recebe {@code null} em movimento, entradas, saídas e esperado de TODAS as formas
     * (somados, dariam o esperado); a abertura ele mesmo informou. Depois de fechado todos veem.
     */
    private CaixaSessaoResponseDTO montarResponse(CaixaSessao sessao, boolean comHistorico, Usuario leitor) {
        boolean fechado = sessao.getStatus() == StatusCaixaSessao.FECHADO;
        boolean veEsperado = fechado || perfil.veEsperadoDoAberto(leitor);
        List<FormaPagamentoCaixa> formas = formaPagamentoCaixaRepository.findByCaixaSessaoId(sessao.getId());
        Calculo calculo = calcular(sessao.getId());

        List<FormaPagamentoCaixaResponseDTO> formasDto = formas.stream()
                .sorted(Comparator.comparingInt(f -> FORMAS_DO_CAIXA.indexOf(f.getForma())))
                .map(f -> {
                    boolean liquida = LIQUIDAM.contains(f.getForma());
                    boolean exigida = calculo.exigida(f);
                    if (!veEsperado) {
                        return new FormaPagamentoCaixaResponseDTO(
                                f.getId(), f.getForma(), safe(f.getAbertura()), null, null, null, null, null,
                                f.getValorConferido(), f.getDiferenca(), liquida, exigida);
                    }
                    BigDecimal esperado = calculo.esperado(f);
                    return new FormaPagamentoCaixaResponseDTO(
                            f.getId(), f.getForma(), safe(f.getAbertura()), calculo.movimento(f), calculo.entradas(f),
                            calculo.saidas(f), contabilizadoQueFica(f, esperado), esperado,
                            f.getValorConferido(), f.getDiferenca(), liquida, exigida);
                }).toList();

        BigDecimal totalAbertura = formasDto.stream().map(FormaPagamentoCaixaResponseDTO::abertura)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // O que fica no caixa é o dinheiro contado: as formas que liquidam contam 0 no total, mesmo digitadas.
        BigDecimal totalContabilizado = fechado ? formasDto.stream().filter(d -> !d.liquidaNaConciliacao())
                .map(FormaPagamentoCaixaResponseDTO::valorConferido).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal totalDiferenca = fechado ? formasDto.stream().map(FormaPagamentoCaixaResponseDTO::diferenca)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;

        List<HistoricoCaixaDTO> historico = comHistorico
                ? historicoRepository.findByCaixaSessaoIdOrderByEmAscIdAsc(sessao.getId()).stream()
                        .map(h -> new HistoricoCaixaDTO(h.getAcao(), nome(h.getUsuario()), h.getEm(), h.getTexto())).toList()
                : null;
        // Os valores digitados em cada recusa só aparecem para quem confere ou pode fechar com divergência.
        List<TentativaFechamentoDTO> tentativas = comHistorico
                && (perfil.podeConferirReabrir(leitor) || perfil.podeFecharComDivergencia(leitor))
                ? tentativaService.listar(sessao.getId()) : null;
        Integer restantes = fechado ? null : (int) Math.max(0,
                CaixaTentativaService.MAXIMO_POR_DIA - tentativaService.tentativasHoje(sessao.getId(), LocalDate.now(ZONA)));

        return new CaixaSessaoResponseDTO(
                sessao.getId(), sessao.getFilial().getId(), sessao.getFilial().getNome(),
                sessao.getCaixaPdv(), sessao.getDataAbertura(), sessao.getStatus(), sessao.getConferencia(),
                sessao.getAbertoEm(), nome(sessao.getAbertoPor()),
                sessao.getFechadoEm(), nome(sessao.getFechadoPor()),
                sessao.getConferidoEm(), nome(sessao.getConferidoPor()),
                sessao.getReabertoEm(), nome(sessao.getReabertoPor()),
                sessao.getObservacao(), sessao.getJustificativaAbertura(), sessao.getJustificativaReabertura(),
                sessao.isFechadoComDivergencia(), sessao.getJustificativaDivergencia(), restantes,
                totalAbertura, totalContabilizado, totalDiferenca, formasDto, historico, tentativas);
    }

    private static MovimentoCaixaResponseDTO paraMovimento(CaixaLancamento l) {
        return new MovimentoCaixaResponseDTO(l.getTipo().name(), null, null, l.getCriadoEm(),
                l.getCriadoPor() == null ? null : l.getCriadoPor().getNome(), FormaPagamento.DINHEIRO, l.getValor(), null,
                l.getConta(), l.getDescricao());
    }

    private static MovimentoCaixaResponseDTO paraMovimento(PagamentoVenda p) {
        var venda = p.getVenda();
        return new MovimentoCaixaResponseDTO(
                "PAGAMENTO", venda.getId(), venda.getNumeroVenda(),
                venda.getFinalizadoEm() != null ? venda.getFinalizadoEm() : p.getCriadoEm(),
                venda.getUsuario().getNome(), p.getForma(), p.getValor(), venda.getStatus(), null, null);
    }

    private void registrar(CaixaSessao sessao, AcaoCaixa acao, Usuario usuario, String texto) {
        historicoRepository.save(CaixaHistorico.builder().caixaSessao(sessao).acao(acao).usuario(usuario)
                .texto(texto == null ? null : texto.substring(0, Math.min(texto.length(), 300))).build());
    }

    private static void exigirJustificativa(String texto, String mensagem) {
        if (texto == null || texto.length() < MINIMO_JUSTIFICATIVA) {
            throw new NegocioException(mensagem);
        }
    }

    private static String nomeDoCaixa(String caixaPdv) {
        String nome = caixaPdv == null ? "" : caixaPdv.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (nome.isEmpty()) throw new NegocioException("Informe o nome do caixa.");
        return nome.length() > 100 ? nome.substring(0, 100) : nome;
    }

    private static String textoOuNull(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nome(Usuario u) {
        return u == null ? null : u.getNome();
    }

    private static BigDecimal safe(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private CaixaSessao buscarEntidade(Long id) {
        return caixaSessaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessao de caixa"));
    }
}
