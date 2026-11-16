package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.FinanceiroException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import com.vivo4redes.syscor.financeiro.config.ConciliacaoExtratoProperties;
import com.vivo4redes.syscor.financeiro.dto.response.ConciliacaoEventoDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ExtratoLancamentoDTO;
import com.vivo4redes.syscor.financeiro.dto.response.SugestaoContaDTO;
import com.vivo4redes.syscor.financeiro.enums.AcaoTitulo;
import com.vivo4redes.syscor.financeiro.enums.OrigemConciliacao;
import com.vivo4redes.syscor.financeiro.enums.ResultadoConciliacao;
import com.vivo4redes.syscor.financeiro.enums.SituacaoExtrato;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.ExtratoLancamento;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import com.vivo4redes.syscor.financeiro.model.TituloHistorico;
import com.vivo4redes.syscor.financeiro.repository.ExtratoLancamentoRepository;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import com.vivo4redes.syscor.financeiro.repository.TituloHistoricoRepository;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Concilia os débitos do extrato com as contas a pagar. Princípio: é mais seguro errar para "pedir confirmação" do
 * que baixar a conta errada. Uma transação por lançamento, com as contas candidatas travadas
 * ({@code SELECT ... FOR UPDATE}).
 *
 * <ol>
 *   <li>Candidatas: PAGAR em ABERTO, valor igual ({@code compareTo}) e vencimento a até {@code janela-matching-dias}
 *       da data do lançamento (pagar antes ou depois do vencimento é normal).</li>
 *   <li>Reforços: forte = CPF/CNPJ da contrapartida igual ao do fornecedor; médio = nome do fornecedor no
 *       histórico do banco; fraco = {@code codigoHistorico} de pagamento (configurável) perto do vencimento.</li>
 *   <li>Uma candidata com algum reforço: baixa sozinha. Uma sem reforço: AMBIGUO (a pessoa confirma com 1 clique).
 *       Várias: AMBIGUO. Nenhuma, mas o CPF/CNPJ bate com uma conta de outro valor: a conta vira DIVERGENTE (não
 *       baixa). Nenhuma: SEM_CORRESPONDENCIA.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ConciliacaoExtratoService {

    private static final Set<SituacaoExtrato> AGUARDA_DECISAO =
            EnumSet.of(SituacaoExtrato.NOVO, SituacaoExtrato.AMBIGUO, SituacaoExtrato.SEM_CORRESPONDENCIA);
    private static final int LIMITE_LISTA = 500;

    private final ExtratoLancamentoRepository lancamentos;
    private final TituloFinanceiroRepository titulos;
    private final TituloHistoricoRepository historicos;
    private final BbExtratoProperties bbProps;
    private final ConciliacaoExtratoProperties props;
    private final ConciliacaoStream stream;
    private final FinanceiroPerfilService perfil;
    private final UsuarioService usuarioService;

    public record Resultado(SituacaoExtrato situacao, boolean baixou) {
    }

    /** Conta candidata com os reforços que a sustentam. */
    record Pontuacao(TituloFinanceiro titulo, boolean forte, boolean medio, boolean fraco, long distanciaDias) {
        int pontos() {
            return (forte ? 100 : 0) + (medio ? 10 : 0) + (fraco ? 1 : 0);
        }

        boolean temReforco() {
            return pontos() > 0;
        }

        List<String> nomes() {
            List<String> nomes = new ArrayList<>();
            if (forte) nomes.add("DOCUMENTO");
            if (medio) nomes.add("NOME");
            if (fraco) nomes.add("HISTORICO");
            return nomes;
        }
    }

    // ------------------------------------------------------------------ matching automático
    @Transactional
    public Resultado conciliar(Long lancamentoId) {
        ExtratoLancamento l = lancamentos.buscarComTrava(lancamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento do extrato"));
        // Idempotência: conciliado/ignorado é final; desvinculado só a pessoa mexe; conta ligada já tem dono.
        if (!AGUARDA_DECISAO.contains(l.getSituacao()) || l.isDesvinculado() || l.getTituloId() != null) {
            return new Resultado(l.getSituacao(), false);
        }

        int janela = bbProps.getJanelaMatchingDias();
        LocalDate de = l.getDataLancamento().minusDays(janela);
        LocalDate ate = l.getDataLancamento().plusDays(janela);
        List<Pontuacao> candidatas = titulos
                .candidatasPorValor(TipoTitulo.PAGAR, StatusTitulo.ABERTO, l.getValor(), de, ate).stream()
                .map(t -> pontuar(l, t)).sorted(ordemDeSugestao()).toList();

        if (candidatas.isEmpty()) return semCandidata(l, de, ate);

        if (candidatas.size() == 1) {
            Pontuacao unica = candidatas.get(0);
            if (unica.temReforco() || !props.isExigirReforco()) {
                baixarAutomaticamente(l, unica);
                return new Resultado(SituacaoExtrato.CONCILIADO, true);
            }
            marcar(l, SituacaoExtrato.AMBIGUO, "Uma conta casa por valor e vencimento, mas sem nenhum reforço "
                    + "(CPF/CNPJ, nome ou histórico). Confirme se foi ela.");
            return new Resultado(SituacaoExtrato.AMBIGUO, false);
        }
        marcar(l, SituacaoExtrato.AMBIGUO, candidatas.size() + " contas casam por valor e vencimento. "
                + "Confirme qual foi paga.");
        return new Resultado(SituacaoExtrato.AMBIGUO, false);
    }

    private Resultado semCandidata(ExtratoLancamento l, LocalDate de, LocalDate ate) {
        String documento = l.getContraparteDocumento();
        if (documento != null && !documento.isBlank()) {
            List<TituloFinanceiro> doFornecedor = titulos.candidatasPorDocumento(TipoTitulo.PAGAR,
                    StatusTitulo.ABERTO, documento, de, ate);
            if (doFornecedor.size() == 1) {
                TituloFinanceiro conta = doFornecedor.get(0);
                conta.marcarDivergente();                                  // NÃO baixa: vai para análise
                titulos.save(conta);
                l.setTituloId(conta.getId());
                String mensagem = "Valor do banco (R$ " + l.getValor().toPlainString() + ") difere do valor da conta (R$ "
                        + conta.getValor().toPlainString() + ").";
                marcar(l, SituacaoExtrato.AMBIGUO, mensagem);
                publicar(conta, ResultadoConciliacao.DIVERGENTE, l, mensagem);
                return new Resultado(SituacaoExtrato.AMBIGUO, false);
            }
        }
        marcar(l, SituacaoExtrato.SEM_CORRESPONDENCIA,
                "Nenhuma conta pendente com esse valor e vencimento próximo (tarifa, IOF, transferência ou conta ainda não cadastrada).");
        return new Resultado(SituacaoExtrato.SEM_CORRESPONDENCIA, false);
    }

    private void baixarAutomaticamente(ExtratoLancamento l, Pontuacao escolhida) {
        TituloFinanceiro conta = escolhida.titulo();
        conta.baixarPorExtrato(l.getValor(), pagoEm(l), ExtratoLancamento.BANCO_BRASIL, l.getId(), null, null);
        titulos.save(conta);

        String reforcos = escolhida.temReforco() ? String.join(", ", escolhida.nomes()) : "nenhum (exigir-reforco=false)";
        l.setSituacao(SituacaoExtrato.CONCILIADO);
        l.setTituloId(conta.getId());
        l.setOrigem(OrigemConciliacao.AUTO);
        l.setConciliadoEm(OffsetDateTime.now());
        l.setMotivo("Conciliação automática. Reforços: " + reforcos + ".");
        lancamentos.save(l);

        historicos.save(TituloHistorico.builder().titulo(conta).acao(AcaoTitulo.BAIXA_AUTOMATICA)
                .texto(limitar("Baixa automática pelo extrato do Banco do Brasil: débito de R$ "
                        + l.getValor().toPlainString() + " em " + l.getDataLancamento() + ". Reforços: " + reforcos + ".", 500))
                .build());
        publicar(conta, ResultadoConciliacao.CONCILIADA, l, "Conta baixada automaticamente pelo extrato.");
    }

    // ------------------------------------------------------------------ ações da pessoa
    /**
     * Confirma à mão que o lançamento pagou a conta. O valor do banco manda; se difere do valor da conta, exige
     * justificativa. Vale para conta ABERTO ou DIVERGENTE.
     */
    @Transactional
    public ExtratoLancamentoDTO vincular(Long lancamentoId, Long tituloId, String justificativa) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);

        ExtratoLancamento l = lancamentos.buscarComTrava(lancamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento do extrato"));
        if (!AGUARDA_DECISAO.contains(l.getSituacao())) {
            throw FinanceiroException.statusInvalido("Só lançamento pendente de decisão pode ser vinculado.");
        }
        TituloFinanceiro conta = titulos.buscarComTrava(tituloId, TipoTitulo.PAGAR)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar"));
        if (!conta.editavel()) {
            throw FinanceiroException.statusInvalido("Só conta pendente ou divergente pode receber o vínculo.");
        }
        boolean difere = l.getValor().compareTo(conta.getValor()) != 0;
        String motivo = justificativa == null ? "" : justificativa.trim();
        if (difere && motivo.length() < 10) {
            throw new NegocioException("O valor do banco (R$ " + l.getValor().toPlainString() + ") difere do valor da conta (R$ "
                    + conta.getValor().toPlainString() + "): informe a justificativa (mín. 10 caracteres).");
        }

        liberarContaDivergenteAnterior(l, conta.getId());
        conta.baixarPorExtrato(l.getValor(), pagoEm(l), ExtratoLancamento.BANCO_BRASIL, l.getId(), usuario,
                motivo.isEmpty() ? null : limitar(motivo, 300));
        titulos.save(conta);

        l.setSituacao(SituacaoExtrato.CONCILIADO);
        l.setTituloId(conta.getId());
        l.setOrigem(OrigemConciliacao.MANUAL);
        l.setConciliadoEm(OffsetDateTime.now());
        l.setDesvinculado(false);
        l.setMotivo(limitar("Vinculado por " + usuario.getNome() + (motivo.isEmpty() ? "." : ": " + motivo), 300));
        lancamentos.save(l);

        historicos.save(TituloHistorico.builder().titulo(conta).acao(AcaoTitulo.VINCULOU).usuario(usuario)
                .texto(limitar("Vinculada ao débito de R$ " + l.getValor().toPlainString() + " em " + l.getDataLancamento()
                        + " do extrato" + (motivo.isEmpty() ? "." : ". " + motivo), 500)).build());
        publicar(conta, ResultadoConciliacao.CONCILIADA, l, "Conta baixada por vínculo manual com o extrato.");
        return ExtratoLancamentoDTO.de(l, List.of());
    }

    @Transactional
    public ExtratoLancamentoDTO ignorar(Long lancamentoId, String motivo) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);

        ExtratoLancamento l = lancamentos.buscarComTrava(lancamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento do extrato"));
        if (!AGUARDA_DECISAO.contains(l.getSituacao())) {
            throw FinanceiroException.statusInvalido("Só lançamento pendente de decisão pode ser ignorado.");
        }
        liberarContaDivergenteAnterior(l, null);
        l.setSituacao(SituacaoExtrato.IGNORADO);
        l.setOrigem(OrigemConciliacao.MANUAL);
        l.setTituloId(null);
        l.setMotivo(limitar("Ignorado por " + usuario.getNome() + ": " + motivo.trim(), 300));
        lancamentos.save(l);
        return ExtratoLancamentoDTO.de(l, List.of());
    }

    /**
     * Chamado ao reabrir uma conta baixada pelo extrato: o lançamento volta à fila (SEM_CORRESPONDENCIA) e fica
     * marcado como desvinculado, para o reprocesso automático não casá-lo de novo com a mesma conta errada.
     */
    @Transactional
    public void desfazerVinculo(Long lancamentoId, Long tituloId, String justificativa) {
        lancamentos.buscarComTrava(lancamentoId).ifPresent(l -> {
            if (l.getSituacao() == SituacaoExtrato.CONCILIADO && tituloId.equals(l.getTituloId())) {
                l.setSituacao(SituacaoExtrato.SEM_CORRESPONDENCIA);
                l.setTituloId(null);
                l.setConciliadoEm(null);
                l.setOrigem(null);
                l.setDesvinculado(true);
                l.setMotivo(limitar("Baixa da conta " + tituloId + " desfeita ao reabrir: " + justificativa, 300));
                lancamentos.save(l);
            }
        });
    }

    // ------------------------------------------------------------------ consulta
    @Transactional(readOnly = true)
    public List<ExtratoLancamentoDTO> listar(SituacaoExtrato situacao, LocalDate de, LocalDate ate) {
        perfil.exigirConsulta(usuarioService.usuarioLogado());
        return lancamentos.listar(situacao, de, ate, PageRequest.of(0, LIMITE_LISTA)).stream()
                .map(l -> ExtratoLancamentoDTO.de(l, sugestoes(l)))
                .toList();
    }

    private List<SugestaoContaDTO> sugestoes(ExtratoLancamento l) {
        if (l.getSituacao() != SituacaoExtrato.AMBIGUO) return List.of();
        if (l.getTituloId() != null) {   // conta divergente ligada ao lançamento
            return titulos.findById(l.getTituloId()).map(t -> List.of(sugestao(pontuar(l, t), l))).orElse(List.of());
        }
        int janela = bbProps.getJanelaMatchingDias();
        return titulos.sugestoesPorValor(TipoTitulo.PAGAR, StatusTitulo.ABERTO, l.getValor(),
                        l.getDataLancamento().minusDays(janela), l.getDataLancamento().plusDays(janela)).stream()
                .map(t -> pontuar(l, t)).sorted(ordemDeSugestao()).map(p -> sugestao(p, l)).toList();
    }

    private static SugestaoContaDTO sugestao(Pontuacao p, ExtratoLancamento l) {
        TituloFinanceiro t = p.titulo();
        return new SugestaoContaDTO(t.getId(), t.getDescricao(), t.getQuem(), t.getVencimento(), t.getValor(),
                p.nomes(), l.getValor().compareTo(t.getValor()) != 0);
    }

    // ------------------------------------------------------------------ apoio
    Pontuacao pontuar(ExtratoLancamento l, TituloFinanceiro t) {
        boolean forte = l.getContraparteDocumento() != null && t.getDocumentoFornecedor() != null
                && l.getContraparteDocumento().equals(t.getDocumentoFornecedor());

        String nome = TextoUtil.chave(t.getQuem());
        String textoBanco = TextoUtil.chave(nulo(l.getSubHistorico()) + " " + nulo(l.getInfoComplementar()));
        boolean medio = nome.length() >= 4 && textoBanco.contains(nome);

        long distancia = Math.abs(ChronoUnit.DAYS.between(l.getDataLancamento(), t.getVencimento()));
        boolean fraco = l.getCodigoHistorico() != null
                && props.getCodigosHistoricoPagamento().contains(l.getCodigoHistorico())
                && distancia <= props.getProximidadeVencimentoDias();
        return new Pontuacao(t, forte, medio, fraco, distancia);
    }

    private static Comparator<Pontuacao> ordemDeSugestao() {
        return Comparator.comparingInt(Pontuacao::pontos).reversed()
                .thenComparingLong(Pontuacao::distanciaDias)
                .thenComparing(p -> p.titulo().getVencimento())
                .thenComparing(p -> p.titulo().getId());
    }

    /** Se o lançamento tinha marcado outra conta como DIVERGENTE, ela volta a ABERTO (o motivo da divergência acabou). */
    private void liberarContaDivergenteAnterior(ExtratoLancamento l, Long novaContaId) {
        Long anterior = l.getTituloId();
        if (anterior == null || anterior.equals(novaContaId)) return;
        titulos.buscarComTrava(anterior, TipoTitulo.PAGAR).ifPresent(conta -> {
            if (conta.getStatus() == StatusTitulo.DIVERGENTE) {
                conta.setStatus(StatusTitulo.ABERTO);
                titulos.save(conta);
            }
        });
    }

    private void marcar(ExtratoLancamento l, SituacaoExtrato situacao, String motivo) {
        l.setSituacao(situacao);
        l.setMotivo(limitar(motivo, 300));
        lancamentos.save(l);
    }

    /** O extrato não traz hora: o pagamento vale à meia-noite do dia do lançamento (São Paulo). */
    private static OffsetDateTime pagoEm(ExtratoLancamento l) {
        return l.getDataLancamento().atStartOfDay(ContaPagarService.FUSO).toOffsetDateTime();
    }

    /** Avisa a tela só depois do commit: ninguém recebe notícia de uma baixa que foi desfeita. */
    private void publicar(TituloFinanceiro conta, ResultadoConciliacao resultado, ExtratoLancamento l, String mensagem) {
        ConciliacaoEventoDTO evento = new ConciliacaoEventoDTO(conta.getId(), conta.getStatus(), resultado,
                l.getProvider(), l.getValor(), mensagem, OffsetDateTime.now());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stream.publicar(evento);
                }
            });
        } else {
            stream.publicar(evento);
        }
    }

    private static String nulo(String texto) {
        return texto == null ? "" : texto;
    }

    private static String limitar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
