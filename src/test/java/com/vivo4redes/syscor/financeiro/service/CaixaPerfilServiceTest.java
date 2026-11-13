package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.CaixaException;
import com.vivo4redes.syscor.financeiro.config.CaixaProperties;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/** Matriz de perfis do caixa, sem banco: cargos comparados sem acento, gerente só na própria filial. */
@ExtendWith(MockitoExtension.class)
class CaixaPerfilServiceTest {

    @Mock private FilialRepository filialRepository;

    private CaixaPerfilService perfil;
    private final Filial salinas = Filial.builder().id(1L).codigo("S1").nome("MG - SALINAS").build();
    private final Filial outra = Filial.builder().id(2L).codigo("S2").nome("MG - MONTES CLAROS").build();

    @BeforeEach
    void preparar() {
        CaixaProperties props = new CaixaProperties();
        props.setCargosGerente(List.of("GERENTE DE VENDAS - JUNIOR", "GERENTE DE VENDAS - PLENO", "GERENTE DE VENDAS - SENIOR"));
        props.setCargosAuditoria(List.of("AUDITORIA DE CAIXA"));
        props.setCargosFinanceiro(List.of("FINANCEIRO"));
        props.setAdmins(List.of("guilherme.caixeta"));
        perfil = new CaixaPerfilService(props, filialRepository);
        lenient().when(filialRepository.listarIdsPorNome("mg - salinas")).thenReturn(List.of(1L));
    }

    @Test
    @DisplayName("Gerente (cargo com acento no cadastro) abre e fecha só a própria filial, sem diferenciar caixa nem espaços")
    void gerente() {
        Usuario g = usuario("g1", "GERENTE DE VENDAS - JÚNIOR", "  mg - salinas ");

        assertThat(perfil.podeAbrirFechar(g, salinas)).isTrue();
        assertThat(perfil.podeAbrirFechar(g, outra)).isFalse();
        assertThat(perfil.podeConferirReabrir(g)).isFalse();
        assertThat(perfil.podeConsultar(g, salinas)).isTrue();
        assertThat(perfil.podeConsultar(g, outra)).isFalse();
    }

    @Test
    @DisplayName("Auditoria abre e fecha em qualquer filial, confere e reabre")
    void auditoria() {
        Usuario a = usuario("a1", "AUDITORIA DE CAIXA", "Auditoria");

        assertThat(perfil.podeAbrirFechar(a, salinas)).isTrue();
        assertThat(perfil.podeAbrirFechar(a, outra)).isTrue();
        assertThat(perfil.podeConferirReabrir(a)).isTrue();
        assertThat(perfil.filiaisVisiveis(a)).isNull();
    }

    @Test
    @DisplayName("Financeiro confere, reabre e consulta tudo, mas não abre nem fecha")
    void financeiro() {
        Usuario f = usuario("f1", "FINANCEIRO", "Matriz");

        assertThat(perfil.podeAbrirFechar(f, salinas)).isFalse();
        assertThat(perfil.podeConferirReabrir(f)).isTrue();
        assertThat(perfil.podeConsultar(f, outra)).isTrue();
    }

    @Test
    @DisplayName("Admin pelo login (sem diferenciar maiúsculas) pode tudo")
    void admin() {
        Usuario adm = usuario("GUILHERME.CAIXETA", "ASSISTENTE DE DESENVOLVIMENTO DE SISTEMA", "Matriz");

        assertThat(perfil.ehAdmin(adm)).isTrue();
        assertThat(perfil.podeAbrirFechar(adm, outra)).isTrue();
        assertThat(perfil.podeConferirReabrir(adm)).isTrue();
    }

    @Test
    @DisplayName("Vendedor e usuário sem cargo não têm nenhum poder e não veem filial alguma")
    void semPerfil() {
        Usuario v = usuario("v1", "CONSULTOR DE VENDAS 1", "MG - SALINAS");
        Usuario semCargo = usuario("s1", null, "MG - SALINAS");

        for (Usuario u : List.of(v, semCargo)) {
            assertThat(perfil.podeAbrirFechar(u, salinas)).isFalse();
            assertThat(perfil.podeConferirReabrir(u)).isFalse();
            assertThat(perfil.podeConsultar(u, salinas)).isFalse();
            assertThat(perfil.filiaisVisiveis(u)).isEmpty();
        }
    }

    @Test
    @DisplayName("Filiais visíveis do gerente: as que têm o nome do cadastro dele")
    void filiaisVisiveisDoGerente() {
        Usuario g = usuario("g1", "GERENTE DE VENDAS - PLENO", "mg - salinas");

        assertThat(perfil.filiaisVisiveis(g)).containsExactly(1L);
    }

    @Test
    @DisplayName("Filial do usuário: só vale se o nome casa com exatamente uma filial")
    void filialDoUsuario() {
        Usuario g = usuario("g1", "GERENTE DE VENDAS - PLENO", "mg - salinas");
        Usuario semFilial = usuario("g2", "GERENTE DE VENDAS - PLENO", "Filial que não existe");
        lenient().when(filialRepository.listarIdsPorNome("Filial que não existe")).thenReturn(List.of());
        lenient().when(filialRepository.listarIdsPorNome("Nome repetido")).thenReturn(List.of(7L, 8L));

        assertThat(perfil.filialDoUsuario(g)).isEqualTo(1L);
        assertThat(perfil.filialDoUsuario(semFilial)).isNull();
        assertThat(perfil.filialDoUsuario(usuario("g3", "GERENTE DE VENDAS - PLENO", "Nome repetido"))).isNull();
        assertThat(perfil.filialDoUsuario(usuario("g4", "GERENTE DE VENDAS - PLENO", null))).isNull();
    }

    @Test
    @DisplayName("Listas vazias na configuração: ninguém ganha perfil por engano")
    void listasVazias() {
        CaixaPerfilService vazio = new CaixaPerfilService(new CaixaProperties(), filialRepository);
        Usuario a = usuario("a1", "AUDITORIA DE CAIXA", "Auditoria");

        assertThat(vazio.podeAbrirFechar(a, salinas)).isFalse();
        assertThat(vazio.podeConferirReabrir(a)).isFalse();
    }

    @Test
    @DisplayName("exigir* lança CaixaException 403 CAIXA_SEM_PERMISSAO")
    void exigir() {
        Usuario v = usuario("v1", "CONSULTOR DE VENDAS 1", "MG - SALINAS");

        assertThatThrownBy(() -> perfil.exigirAbrirFechar(v, salinas))
                .isInstanceOfSatisfying(CaixaException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("CAIXA_SEM_PERMISSAO");
                    assertThat(e.getStatus().value()).isEqualTo(403);
                });
        assertThatThrownBy(() -> perfil.exigirConferirReabrir(v)).isInstanceOf(CaixaException.class);
    }

    private static Usuario usuario(String login, String cargo, String filial) {
        return Usuario.builder().id((long) login.hashCode()).login(login).nome(login).email(login + "@t.local")
                .senhaHash("x").cargo(cargo).filial(filial).build();
    }
}
