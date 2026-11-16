package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.FinanceiroException;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import com.vivo4redes.syscor.financeiro.extrato.ExtratoBancarioClient;
import com.vivo4redes.syscor.financeiro.repository.ExtratoLancamentoRepository;
import com.vivo4redes.syscor.financeiro.repository.ExtratoSincronizacaoRepository;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Com o canal desligado (padrão) o cliente do banco nunca é tocado: nem agendado, nem pelo "Sincronizar agora". */
@ExtendWith(MockitoExtension.class)
class ExtratoSincronizacaoServiceTest {

    @Mock private ObjectProvider<ExtratoBancarioClient> cliente;
    @Mock private ExtratoLancamentoRepository lancamentos;
    @Mock private ExtratoSincronizacaoRepository sincronizacoes;
    @Mock private ConciliacaoExtratoService conciliacao;
    @Mock private FinanceiroPerfilService perfil;
    @Mock private UsuarioService usuarioService;
    @Mock private DataSource dataSource;

    private BbExtratoProperties props;
    private ExtratoSincronizacaoService servico;

    @BeforeEach
    void preparar() {
        props = new BbExtratoProperties();
        servico = new ExtratoSincronizacaoService(cliente, props, lancamentos, sincronizacoes, conciliacao, perfil,
                usuarioService, dataSource);
    }

    @Test
    @DisplayName("enabled=false (padrão): a rodada agendada não faz nada, nem pega conexão, nem chama o cliente")
    void agendadoDesligado() {
        assertThat(props.isEnabled()).as("o padrão é desligado").isFalse();

        servico.sincronizarAgendado();

        verifyNoInteractions(cliente, dataSource, lancamentos, sincronizacoes, conciliacao);
    }

    @Test
    @DisplayName("enabled=false: 'Sincronizar agora' responde 409 EXTRATO_DESLIGADO sem chamar o cliente")
    void manualDesligado() {
        when(usuarioService.usuarioLogado()).thenReturn(new Usuario());

        assertThatThrownBy(() -> servico.sincronizarAgora())
                .isInstanceOfSatisfying(FinanceiroException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("EXTRATO_DESLIGADO");
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });

        verifyNoInteractions(cliente, dataSource, lancamentos, sincronizacoes, conciliacao);
    }

    @Test
    @DisplayName("Sem permissão (gerente de loja), 'Sincronizar agora' é 403 antes de qualquer outra coisa")
    void semPermissao() {
        when(usuarioService.usuarioLogado()).thenReturn(new Usuario());
        org.mockito.Mockito.doThrow(FinanceiroException.semPermissao()).when(perfil).exigirGerenciar(org.mockito.ArgumentMatchers.any());
        props.setEnabled(true);

        assertThatThrownBy(() -> servico.sincronizarAgora())
                .isInstanceOfSatisfying(FinanceiroException.class, e -> assertThat(e.getCodigo()).isEqualTo("FINANCEIRO_SEM_PERMISSAO"));

        verifyNoInteractions(cliente, dataSource);
    }
}
