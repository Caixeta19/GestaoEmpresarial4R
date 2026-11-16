package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.FinanceiroException;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import com.vivo4redes.syscor.financeiro.dto.response.SincronizacaoExtratoDTO;
import com.vivo4redes.syscor.financeiro.enums.OrigemSincronizacao;
import com.vivo4redes.syscor.financeiro.enums.SituacaoExtrato;
import com.vivo4redes.syscor.financeiro.enums.StatusSincronizacao;
import com.vivo4redes.syscor.financeiro.extrato.ExtratoBancarioClient;
import com.vivo4redes.syscor.financeiro.extrato.ExtratoBancarioException;
import com.vivo4redes.syscor.financeiro.extrato.LancamentoExtrato;
import com.vivo4redes.syscor.financeiro.model.ExtratoLancamento;
import com.vivo4redes.syscor.financeiro.model.ExtratoSincronizacao;
import com.vivo4redes.syscor.financeiro.repository.ExtratoLancamentoRepository;
import com.vivo4redes.syscor.financeiro.repository.ExtratoSincronizacaoRepository;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sincroniza o extrato do banco e concilia. Uma execução por vez entre instâncias ({@code pg_try_advisory_lock} numa
 * conexão dedicada, solta no fim). A falha do banco nunca derruba o sistema nem baixa nada: o erro vai para
 * {@code tb_extrato_sincronizacao} e a próxima rodada tenta de novo. Cada rodada consulta os últimos
 * {@code janela-dias} (idempotente pelo UNIQUE do id externo) e reprocessa a fila ainda sem decisão, porque a conta
 * pode ter sido cadastrada depois do pagamento.
 */
@Service
@RequiredArgsConstructor
public class ExtratoSincronizacaoService {

    private static final Logger log = LoggerFactory.getLogger(ExtratoSincronizacaoService.class);

    /** Chave do advisory lock: só esta tarefa a usa. */
    static final long CHAVE_LOCK = 7_410_042_001L;

    private final ObjectProvider<ExtratoBancarioClient> cliente;
    private final BbExtratoProperties props;
    private final ExtratoLancamentoRepository lancamentos;
    private final ExtratoSincronizacaoRepository sincronizacoes;
    private final ConciliacaoExtratoService conciliacao;
    private final FinanceiroPerfilService perfil;
    private final UsuarioService usuarioService;
    private final DataSource dataSource;

    /** Rodada agendada: não faz nada com o canal desligado. */
    @Scheduled(fixedDelayString = "${syscor.financeiro.bb-extrato.sincronizacao-ms:900000}",
            initialDelayString = "${syscor.financeiro.bb-extrato.sincronizacao-ms:900000}")
    public void sincronizarAgendado() {
        if (!props.isEnabled()) return;
        try {
            executar(OrigemSincronizacao.AGENDADA, null);
        } catch (RuntimeException e) {
            log.warn("Sincronização agendada do extrato falhou: {}", e.getClass().getSimpleName());
        }
    }

    /** "Sincronizar agora": financeiro e admin. */
    public SincronizacaoExtratoDTO sincronizarAgora() {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);
        if (!props.isEnabled()) {
            throw new FinanceiroException("EXTRATO_DESLIGADO", org.springframework.http.HttpStatus.CONFLICT,
                    "A leitura do extrato do banco está desligada.");
        }
        SincronizacaoExtratoDTO resultado = executar(OrigemSincronizacao.MANUAL, usuario.getId());
        if (resultado == null) {
            throw new FinanceiroException("EXTRATO_SINCRONIZANDO", org.springframework.http.HttpStatus.CONFLICT,
                    "Já existe uma sincronização do extrato em andamento. Tente de novo em instantes.");
        }
        return resultado;
    }

    /** @return o resultado da rodada, ou {@code null} se outra execução já está com o lock */
    SincronizacaoExtratoDTO executar(OrigemSincronizacao origem, Long usuarioId) {
        try (Connection conexao = dataSource.getConnection()) {
            if (!obterLock(conexao)) return null;
            try {
                return rodar(origem, usuarioId);
            } finally {
                soltarLock(conexao);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao obter o lock da sincronização do extrato", e);
        }
    }

    private SincronizacaoExtratoDTO rodar(OrigemSincronizacao origem, Long usuarioId) {
        OffsetDateTime inicio = OffsetDateTime.now();
        int novos = 0;
        int conciliados = 0;
        StatusSincronizacao status = StatusSincronizacao.OK;
        String erro = null;
        try {
            ExtratoBancarioClient banco = cliente.getIfAvailable();
            if (banco == null) throw new ExtratoBancarioException("O cliente do extrato não está configurado.");

            LocalDate hoje = LocalDate.now(ContaPagarService.FUSO);
            // Falha aqui sobe antes de qualquer gravação: o banco com erro nunca baixa nada.
            List<LancamentoExtrato> debitos = banco.listarDebitos(hoje.minusDays(props.getJanelaDias()), hoje);
            novos = gravarNovos(debitos);

            List<Long> ids = lancamentos.idsParaProcessar(
                    EnumSet.of(SituacaoExtrato.NOVO, SituacaoExtrato.AMBIGUO, SituacaoExtrato.SEM_CORRESPONDENCIA),
                    hoje.minusDays(props.getJanelaMatchingDias()));
            for (Long id : ids) {
                try {
                    if (conciliacao.conciliar(id).baixou()) conciliados++;
                } catch (RuntimeException e) {
                    // um lançamento problemático não trava os demais; segue na próxima rodada
                    log.warn("Falha ao conciliar o lançamento {} do extrato: {}", id, e.getClass().getSimpleName());
                }
            }
        } catch (ExtratoBancarioException e) {
            status = StatusSincronizacao.ERRO;
            erro = e.getMessage();
        } catch (RuntimeException e) {
            status = StatusSincronizacao.ERRO;
            erro = "Falha inesperada na sincronização do extrato (" + e.getClass().getSimpleName() + ").";
            log.warn("Sincronização do extrato falhou: {}", e.getClass().getSimpleName());
        }

        ExtratoSincronizacao rodada = sincronizacoes.save(ExtratoSincronizacao.builder()
                .inicio(inicio).fim(OffsetDateTime.now()).status(status).origem(origem).disparadoPorId(usuarioId)
                .lancamentosNovos(novos).conciliados(conciliados).erro(limitar(erro)).build());
        return new SincronizacaoExtratoDTO(rodada.getStatus(), novos, conciliados, rodada.getErro(),
                rodada.getInicio(), rodada.getFim());
    }

    /** Grava só o que ainda não existe (cada save é uma transação; o UNIQUE barra corrida entre instâncias). */
    private int gravarNovos(List<LancamentoExtrato> debitos) {
        Set<String> vistos = new HashSet<>();
        int novos = 0;
        for (LancamentoExtrato d : debitos) {
            if (!vistos.add(d.idExterno())) continue;
            if (lancamentos.existsByProviderAndIdExterno(ExtratoLancamento.BANCO_BRASIL, d.idExterno())) continue;
            try {
                lancamentos.save(ExtratoLancamento.builder()
                        .provider(ExtratoLancamento.BANCO_BRASIL)
                        .idExterno(d.idExterno())
                        .dataLancamento(d.dataLancamento())
                        .valor(d.valor())
                        .numeroDocumento(d.numeroDocumento())
                        .numeroLote(d.numeroLote())
                        .codigoHistorico(d.codigoHistorico())
                        .subHistorico(d.subHistorico())
                        .infoComplementar(d.infoComplementar())
                        .contraparteDocumento(d.contraparteDocumento())
                        .contraparteTipo(d.contraparteTipo())
                        .contraparteBanco(d.contraparteBanco())
                        .payloadRaw(d.payloadRaw())
                        .build());
                novos++;
            } catch (DataIntegrityViolationException e) {
                // outra instância gravou o mesmo lançamento no meio tempo
            }
        }
        return novos;
    }

    private static boolean obterLock(Connection conexao) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, CHAVE_LOCK);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void soltarLock(Connection conexao) {
        try (PreparedStatement ps = conexao.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, CHAVE_LOCK);
            ps.execute();
        } catch (SQLException e) {
            log.warn("Não foi possível soltar o lock da sincronização do extrato: {}", e.getClass().getSimpleName());
        }
    }

    private static String limitar(String texto) {
        return texto == null ? null : (texto.length() <= 500 ? texto : texto.substring(0, 500));
    }
}
