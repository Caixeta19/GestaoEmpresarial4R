package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.exception.AutenticacaoInvalidaException;
import com.vivo4redes.syscor.exception.CarrinhoNaoEditavelException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.TotalVendaAlteradoException;
import com.vivo4redes.syscor.exception.TransicaoStatusInvalidaException;
import com.vivo4redes.syscor.exception.VendaSemItemException;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.request.DadosIniciaisVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.StatusVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.VendaRequestDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Senha só no fim: abrir e editar usam o usuário logado; finalizar e mudar status gravam quem se autenticou. */
@ExtendWith(MockitoExtension.class)
class VendaServiceAutenticacaoTest {

    private static final String PLANO = "CONTROLE_ALTO_VALOR";
    private static final AutenticacaoUsuarioDTO AUTH = new AutenticacaoUsuarioDTO("outro@4r.com", "senha");
    private static final FinalizarVendaRequestDTO FINALIZAR = new FinalizarVendaRequestDTO(AUTH);

    @Mock private VendaRepository vendaRepository;
    @Mock private ClienteService clienteService;
    @Mock private FilialService filialService;
    @Mock private UsuarioService usuarioService;
    @Mock private NumeroVendaGenerator numeroVendaGenerator;
    @Mock private EstoqueGeralService estoqueService;
    @Mock private PagamentoVendaRepository pagamentoVendaRepository;
    @Mock private PrecoVigenteService precoVigenteService;
    @Spy private ResolvedorTabela resolvedorTabela = new ResolvedorTabela();

    @InjectMocks
    private VendaService service;

    private final Usuario provisorio = Usuario.builder().id(4L).nome("Provisório").build();
    private final Usuario autenticado = Usuario.builder().id(5L).nome("Autenticado").build();
    private final List<PagamentoVenda> pagamentos = new ArrayList<>();
    private EstoqueGeral aparelho;
    private Venda venda;

    @BeforeEach
    void setUp() {
        aparelho = EstoqueGeral.builder().id(10L).serial("356789012345678").modeloComercial("Aparelho").build();
        venda = Venda.builder().id(1L).status(StatusVenda.ABERTA)
                .cliente(Cliente.builder().id(2L).nome("Cliente").build())
                .filial(Filial.builder().id(3L).nome("Loja").build())
                .usuario(provisorio)
                .build();
        venda.adicionarItem(ItemVenda.builder().id(100L).categoria(CategoriaItemVenda.PRODUTO_VIVO).produtoId(10L)
                .descricaoProduto("Aparelho").quantidade(BigDecimal.ONE).estoque(aparelho).plano(PLANO)
                .grupoPlano(GrupoPlano.CONTROLE_ALTO_VALOR).tabelaAplicada(TabelaPreco.REGULAR)
                .valorUnitario(new BigDecimal("1000.00")).build());
        venda.recalcularValorTotal();
        precoVigente("1000.00");
        pagamentos.add(PagamentoVenda.builder().id(50L).venda(venda).forma(FormaPagamento.DINHEIRO)
                .valor(new BigDecimal("1000.00")).parcelas(1).build());

        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagamentoVendaRepository.findByVendaIdOrderByIdAsc(1L)).thenAnswer(inv -> List.copyOf(pagamentos));
        lenient().when(usuarioService.autenticar(AUTH)).thenReturn(autenticado);
    }

    @Test
    @DisplayName("Abrir o carrinho usa o usuário logado, sem autenticar por senha")
    void abrirUsaUsuarioLogado() {
        when(usuarioService.usuarioLogado()).thenReturn(provisorio);

        Venda aberta = service.abrirCarrinho(new VendaRequestDTO(2L, 3L, false));

        assertThat(aberta.getUsuario()).isSameAs(provisorio);
        verify(usuarioService, never()).autenticar(any());
    }

    @Test
    @DisplayName("Editar a venda ABERTA não autentica nem troca o vendedor")
    void editarAbertaNaoAutentica() {
        Cliente novo = Cliente.builder().id(9L).nome("Novo").build();
        when(clienteService.buscarPorId(9L)).thenReturn(novo);

        Venda editada = service.atualizarDadosIniciais(1L, new DadosIniciaisVendaRequestDTO(9L, 3L, false, null, null, null));

        assertThat(editada.getCliente()).isSameAs(novo);
        assertThat(editada.getUsuario()).isSameAs(provisorio);
        verify(usuarioService, never()).autenticar(any());
    }

    @ParameterizedTest
    @EnumSource(value = StatusVenda.class, names = "ABERTA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Editar venda que não está ABERTA é recusado, sem alterar nada")
    void editarForaDeAbertaRecusa(StatusVenda status) {
        venda.setStatus(status);
        Cliente original = venda.getCliente();

        assertThatThrownBy(() -> service.atualizarDadosIniciais(1L,
                new DadosIniciaisVendaRequestDTO(9L, 3L, true, null, "1", "2")))
                .isInstanceOf(CarrinhoNaoEditavelException.class);

        assertThat(venda.getCliente()).isSameAs(original);
        assertThat(venda.isEstoqueAvancado()).isFalse();
        assertThat(venda.getNumeroNota()).isNull();
    }

    @Test
    @DisplayName("Finalizar lança a venda (CONCLUIDA): baixa o estoque e o autenticado vira o vendedor, com autor e data")
    void finalizarGravaAutenticado() {
        Venda finalizada = service.finalizar(1L, FINALIZAR);

        assertThat(finalizada.getStatus()).isEqualTo(StatusVenda.CONCLUIDA);
        verify(estoqueService).confirmarVenda(aparelho);
        assertThat(finalizada.getUsuario()).isSameAs(autenticado);
        assertThat(finalizada.getFinalizadoPor()).isSameAs(autenticado);
        assertThat(finalizada.getFinalizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("Ordem: a senha errada vem antes dos itens e nem trava a venda")
    void autenticacaoVemPrimeiro() {
        venda.getItens().clear();
        when(usuarioService.autenticar(AUTH)).thenThrow(new AutenticacaoInvalidaException());

        assertThatThrownBy(() -> service.finalizar(1L, FINALIZAR)).isInstanceOf(AutenticacaoInvalidaException.class);

        verify(vendaRepository, never()).buscarPorIdForUpdate(any());
        verify(estoqueService, never()).confirmarVenda(any());
        assertNaoFinalizada();
    }

    @Test
    @DisplayName("Ordem: sem itens é recusado antes do Renova")
    void itensAntesDoRenova() {
        venda.getItens().clear();
        venda.setRenova(true);

        assertThatThrownBy(() -> service.finalizar(1L, FINALIZAR)).isInstanceOf(VendaSemItemException.class);

        assertNaoFinalizada();
    }

    @Test
    @DisplayName("Ordem: Renova sem número é recusado antes de reprecificar")
    void renovaAntesDaReprecificacao() {
        venda.setRenova(true);
        precoVigente("1100.00");

        assertThatThrownBy(() -> service.finalizar(1L, FINALIZAR))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Venda com Renova precisa do número do voucher");

        assertThat(venda.getValorTotal()).isEqualByComparingTo("1000.00");
        assertNaoFinalizada();
    }

    @Test
    @DisplayName("Ordem: total alterado vem antes dos pagamentos; o total novo fica, mas o vendedor não muda")
    void totalAlteradoNaoTrocaVendedor() {
        precoVigente("1100.00");
        pagamentos.clear();

        assertThatThrownBy(() -> service.finalizar(1L, FINALIZAR)).isInstanceOf(TotalVendaAlteradoException.class);

        assertThat(venda.getValorTotal()).isEqualByComparingTo("1100.00");
        assertNaoFinalizada();
    }

    @Test
    @DisplayName("Pagamentos diferentes do total são recusados sem trocar o vendedor")
    void pagamentosDiferentesNaoTrocamVendedor() {
        pagamentos.clear();

        assertThatThrownBy(() -> service.finalizar(1L, FINALIZAR)).isInstanceOf(NegocioException.class);

        assertNaoFinalizada();
    }

    @Test
    @DisplayName("Venda já lançada: finalizar de novo não troca o vendedor nem baixa o estoque de novo")
    void finalizarDeNovoNaoTrocaVendedor() {
        venda.setStatus(StatusVenda.CONCLUIDA);

        assertThatThrownBy(() -> service.finalizar(1L, FINALIZAR)).isInstanceOf(TransicaoStatusInvalidaException.class);
        verify(estoqueService, never()).confirmarVenda(any());

        assertThat(venda.getUsuario()).isSameAs(provisorio);
        assertThat(venda.getFinalizadoPor()).isNull();
    }

    @Test
    @DisplayName("Cancelar venda lançada grava autor e data, devolve o estoque e não troca o vendedor")
    void statusGravaAutor() {
        venda.setStatus(StatusVenda.CONCLUIDA);

        Venda cancelada = service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH));

        assertThat(cancelada.getStatus()).isEqualTo(StatusVenda.CANCELADA);
        assertThat(cancelada.getStatusAlteradoPor()).isSameAs(autenticado);
        assertThat(cancelada.getStatusAlteradoEm()).isNotNull();
        assertThat(cancelada.getUsuario()).isSameAs(provisorio);
        verify(estoqueService).liberar(aparelho);
    }

    @ParameterizedTest
    @EnumSource(value = StatusVenda.class, names = {"PENDENTE", "APROVADA"})
    @DisplayName("Pedir PENDENTE ou APROVADA é 422: a venda é lançada direto")
    void statusNaoUsado(StatusVenda pedido) {
        assertThatThrownBy(() -> service.avancarStatus(1L, new StatusVendaRequestDTO(pedido, AUTH)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Status não usado: a venda é lançada direto ao finalizar.");

        assertThat(venda.getStatus()).isEqualTo(StatusVenda.ABERTA);
        assertThat(venda.getStatusAlteradoPor()).isNull();
    }

    @Test
    @DisplayName("Pedir CONCLUIDA pelo status é 422: lançar só pelo finalizar, sem baixar o estoque")
    void concluirSoPeloFinalizar() {
        assertThatThrownBy(() -> service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CONCLUIDA, AUTH)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("A venda é lançada pelo finalizar, com a senha do vendedor.");

        assertThat(venda.getStatus()).isEqualTo(StatusVenda.ABERTA);
        verify(estoqueService, never()).confirmarVenda(any());
    }

    @Test
    @DisplayName("Mudar o status com senha errada não altera nada")
    void statusSenhaErrada() {
        venda.setStatus(StatusVenda.CONCLUIDA);
        when(usuarioService.autenticar(AUTH)).thenThrow(new AutenticacaoInvalidaException());

        assertThatThrownBy(() -> service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH)))
                .isInstanceOf(AutenticacaoInvalidaException.class);

        assertThat(venda.getStatus()).isEqualTo(StatusVenda.CONCLUIDA);
        assertThat(venda.getStatusAlteradoPor()).isNull();
        assertThat(venda.getStatusAlteradoEm()).isNull();
        verify(estoqueService, never()).liberar(any());
    }

    private void assertNaoFinalizada() {
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.ABERTA);
        assertThat(venda.getUsuario()).isSameAs(provisorio);
        assertThat(venda.getFinalizadoPor()).isNull();
        assertThat(venda.getFinalizadoEm()).isNull();
    }

    private void precoVigente(String valor) {
        BigDecimal v = new BigDecimal(valor);
        lenient().when(precoVigenteService.calcular(any(), any(), any(), any(), any())).thenReturn(new PrecoCalculado(
                1L, LocalDate.of(2026, 10, 29), "Aparelho", TabelaPreco.REGULAR, GrupoPlano.CONTROLE_ALTO_VALOR,
                v, v, null, null, null, v, null, List.of()));
    }
}
