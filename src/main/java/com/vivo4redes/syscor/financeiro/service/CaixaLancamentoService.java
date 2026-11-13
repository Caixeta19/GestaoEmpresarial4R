package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.CaixaException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.financeiro.dto.caixa.EstornoRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.caixa.LancamentoCaixaResponseDTO;
import com.vivo4redes.syscor.financeiro.dto.caixa.LancamentoRequestDTO;
import com.vivo4redes.syscor.financeiro.enums.AcaoCaixa;
import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.financeiro.enums.TipoLancamentoCaixa;
import com.vivo4redes.syscor.financeiro.model.CaixaHistorico;
import com.vivo4redes.syscor.financeiro.model.CaixaLancamento;
import com.vivo4redes.syscor.financeiro.model.CaixaSessao;
import com.vivo4redes.syscor.financeiro.repository.CaixaHistoricoRepository;
import com.vivo4redes.syscor.financeiro.repository.CaixaLancamentoRepository;
import com.vivo4redes.syscor.financeiro.repository.CaixaSessaoRepository;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sangria e suprimento de dinheiro no caixa (docs/correcao_caixa.md). Valem só para o DINHEIRO e só com o caixa ABERTO, e
 * entram no esperado: suprimentos no movimento, sangrias nas saídas. Nunca se apaga linha: o lançamento errado é estornado
 * por outro lançamento (só financeiro e admin, com justificativa).
 * <p>
 * Cada lançamento trava a linha do caixa (FOR UPDATE) antes de conferir o saldo, para duas sangrias simultâneas não
 * passarem juntas do que há na gaveta.
 */
@Service
public class CaixaLancamentoService {

    private static final int MINIMO_JUSTIFICATIVA = 10;

    /** {@code criado = false}: a chave de idempotência já existia e o lançamento original foi devolvido. */
    public record Resultado(LancamentoCaixaResponseDTO lancamento, boolean criado) {
    }

    private final CaixaLancamentoRepository lancamentoRepository;
    private final CaixaSessaoRepository caixaSessaoRepository;
    private final CaixaHistoricoRepository historicoRepository;
    private final CaixaPerfilService perfil;
    private final CaixaSessaoService caixaSessaoService;
    private final UsuarioService usuarioService;

    public CaixaLancamentoService(CaixaLancamentoRepository lancamentoRepository,
                                  CaixaSessaoRepository caixaSessaoRepository,
                                  CaixaHistoricoRepository historicoRepository,
                                  CaixaPerfilService perfil,
                                  CaixaSessaoService caixaSessaoService,
                                  UsuarioService usuarioService) {
        this.lancamentoRepository = lancamentoRepository;
        this.caixaSessaoRepository = caixaSessaoRepository;
        this.historicoRepository = historicoRepository;
        this.perfil = perfil;
        this.caixaSessaoService = caixaSessaoService;
        this.usuarioService = usuarioService;
    }

    @Transactional
    public Resultado lancar(Long caixaId, LancamentoRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        CaixaSessao sessao = travar(caixaId);
        perfil.exigirLancar(usuario, sessao.getFilial());

        String chave = dto.chaveIdempotencia().trim();
        Optional<CaixaLancamento> existente = lancamentoRepository.findByCaixaSessaoIdAndChaveIdempotencia(caixaId, chave);
        if (existente.isPresent()) {
            return new Resultado(paraDto(existente.get(), Set.of()), false);
        }

        exigirAberto(sessao);
        if (dto.tipo() != TipoLancamentoCaixa.SANGRIA && dto.tipo() != TipoLancamentoCaixa.SUPRIMENTO) {
            throw new NegocioException("O tipo do lançamento é SANGRIA ou SUPRIMENTO; o estorno tem endpoint próprio.");
        }
        BigDecimal valor = dto.valor().setScale(2, RoundingMode.HALF_UP);
        if (valor.signum() <= 0) {
            throw new NegocioException("O valor do lançamento deve ser maior que zero.");
        }
        String conta = textoOuNull(dto.conta());
        String descricao = textoOuNull(dto.descricao());
        if (conta == null && descricao == null) {
            throw new NegocioException(dto.tipo() == TipoLancamentoCaixa.SANGRIA
                    ? "Informe a conta de destino ou a descrição da sangria."
                    : "Informe a conta de origem ou a descrição do suprimento.");
        }
        // A mensagem não cita o saldo: o gerente da loja não pode descobrir o esperado por tentativa.
        if (dto.tipo() == TipoLancamentoCaixa.SANGRIA && valor.compareTo(caixaSessaoService.esperadoDoDinheiro(caixaId)) > 0) {
            throw new NegocioException("A sangria é maior que o dinheiro disponível no caixa.");
        }

        CaixaLancamento lancamento = lancamentoRepository.save(CaixaLancamento.builder()
                .caixaSessao(sessao).tipo(dto.tipo()).valor(valor).descricao(descricao).conta(conta)
                .observacao(textoOuNull(dto.observacao())).criadoPor(usuario).chaveIdempotencia(chave).build());
        registrar(sessao, AcaoCaixa.LANCAMENTO, usuario, (dto.tipo() == TipoLancamentoCaixa.SANGRIA ? "Sangria" : "Suprimento")
                + " de R$ " + valor.toPlainString() + (conta != null ? " · " + conta : "") + (descricao != null ? " · " + descricao : ""));
        return new Resultado(paraDto(lancamento, Set.of()), true);
    }

    /** Lançamento errado: só o financeiro e o admin estornam, com justificativa, e só com o caixa aberto (senão, reabrir antes). */
    @Transactional
    public Resultado estornar(Long caixaId, Long lancamentoId, EstornoRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        CaixaSessao sessao = travar(caixaId);
        perfil.exigirEstornar(usuario);

        String chave = dto.chaveIdempotencia().trim();
        Optional<CaixaLancamento> existente = lancamentoRepository.findByCaixaSessaoIdAndChaveIdempotencia(caixaId, chave);
        if (existente.isPresent()) {
            return new Resultado(paraDto(existente.get(), Set.of()), false);
        }

        exigirAberto(sessao);
        CaixaLancamento original = lancamentoRepository.findById(lancamentoId)
                .filter(l -> l.getCaixaSessao().getId().equals(caixaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento do caixa"));
        if (original.getTipo() == TipoLancamentoCaixa.ESTORNO) {
            throw new NegocioException("Um estorno não pode ser estornado: faça um novo lançamento.");
        }
        if (lancamentoRepository.existsByEstornadoDeId(lancamentoId)) {
            throw new NegocioException("Esse lançamento já foi estornado.");
        }
        String justificativa = textoOuNull(dto.justificativa());
        if (justificativa == null || justificativa.length() < MINIMO_JUSTIFICATIVA) {
            throw new NegocioException("Informe a justificativa do estorno (mín. " + MINIMO_JUSTIFICATIVA + " caracteres).");
        }
        // Estornar um suprimento tira dinheiro do caixa: não pode deixar o esperado negativo.
        if (original.getTipo() == TipoLancamentoCaixa.SUPRIMENTO
                && original.getValor().compareTo(caixaSessaoService.esperadoDoDinheiro(caixaId)) > 0) {
            throw new NegocioException("O estorno deixaria o dinheiro do caixa negativo.");
        }

        CaixaLancamento estorno = lancamentoRepository.save(CaixaLancamento.builder()
                .caixaSessao(sessao).tipo(TipoLancamentoCaixa.ESTORNO).valor(original.getValor())
                .descricao(original.getDescricao()).conta(original.getConta()).justificativa(justificativa)
                .estornadoDe(original).criadoPor(usuario).chaveIdempotencia(chave).build());
        registrar(sessao, AcaoCaixa.ESTORNO, usuario, "Estorno de " + original.getTipo().name().toLowerCase()
                + " de R$ " + original.getValor().toPlainString() + ": " + justificativa);
        return new Resultado(paraDto(estorno, Set.of()), true);
    }

    @Transactional(readOnly = true)
    public List<LancamentoCaixaResponseDTO> listar(Long caixaId) {
        CaixaSessao sessao = caixaSessaoRepository.findById(caixaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessao de caixa"));
        perfil.exigirConsultar(usuarioService.usuarioLogado(), sessao.getFilial());
        List<CaixaLancamento> todos = lancamentoRepository.findByCaixaSessaoIdOrderByCriadoEmAscIdAsc(caixaId);
        Set<Long> estornados = todos.stream().filter(l -> l.getEstornadoDe() != null)
                .map(l -> l.getEstornadoDe().getId()).collect(Collectors.toSet());
        return todos.stream().map(l -> paraDto(l, estornados)).toList();
    }

    // ------------------------------------------------------------------ apoio
    private CaixaSessao travar(Long caixaId) {
        return caixaSessaoRepository.buscarComTrava(caixaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessao de caixa"));
    }

    private static void exigirAberto(CaixaSessao sessao) {
        if (sessao.getStatus() != StatusCaixaSessao.ABERTO) {
            throw CaixaException.fechado("Sangria, suprimento e estorno só valem com o caixa aberto: peça para reabrir o caixa.");
        }
    }

    private LancamentoCaixaResponseDTO paraDto(CaixaLancamento l, Set<Long> estornados) {
        return new LancamentoCaixaResponseDTO(l.getId(), l.getTipo(), l.getValor(), l.getDescricao(), l.getConta(),
                l.getObservacao(), l.getJustificativa(), l.getEstornadoDe() == null ? null : l.getEstornadoDe().getId(),
                estornados.contains(l.getId()), l.getCriadoPor() == null ? null : l.getCriadoPor().getNome(), l.getCriadoEm());
    }

    private void registrar(CaixaSessao sessao, AcaoCaixa acao, Usuario usuario, String texto) {
        historicoRepository.save(CaixaHistorico.builder().caixaSessao(sessao).acao(acao).usuario(usuario)
                .texto(texto.substring(0, Math.min(texto.length(), 300))).build());
    }

    private static String textoOuNull(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }
}
