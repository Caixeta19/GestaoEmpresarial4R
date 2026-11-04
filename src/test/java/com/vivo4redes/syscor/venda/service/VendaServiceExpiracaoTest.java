package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.exception.CarrinhoNaoEditavelException;
import com.vivo4redes.syscor.venda.dto.request.PagamentoVendaRequestDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.ItemVenda;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Expiração de carrinhos abandonados: VendaService.expirarCarrinho e registro de atividade. */
@ExtendWith(MockitoExtension.class)
class VendaServiceExpiracaoTest {

    private static final Instant LIMITE = Instant.parse("2026-11-02T12:00:00Z");

    @Mock private VendaRepository vendaRepository;
    @Mock private ClienteService clienteService;
    @Mock private FilialService filialService;
    @Mock private UsuarioService usuarioService;
    @Mock private NumeroVendaGenerator numeroVendaGenerator;
    @Mock private EstoqueGeralService estoqueService;
    @Mock private PagamentoVendaRepository pagamentoVendaRepository;
    @Mock private PrecoVigenteService precoVigenteService;
    @Mock private ResolvedorTabela resolvedorTabela;

    @InjectMocks
    private VendaService service;

    private EstoqueGeral unidade;

    @BeforeEach
    void setUp() {
        unidade = EstoqueGeral.builder().id(10L).serial("356789012345678").build();
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Carrinho ABERTA parado antes do limite é cancelado e libera as unidades")
    void expiraCarrinhoParado() {
        Venda venda = venda(StatusVenda.ABERTA, LIMITE.minus(Duration.ofMinutes(1)));
        venda.adicionarItem(itemCom(unidade));

        boolean expirou = service.expirarCarrinho(1L, LIMITE);

        assertThat(expirou).isTrue();
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.CANCELADA);
        verify(estoqueService).liberar(unidade);
    }

    @Test
    @DisplayName("Carrinho que voltou a ter atividade depois do limite não é cancelado")
    void ignoraCarrinhoComAtividade() {
        Venda venda = venda(StatusVenda.ABERTA, LIMITE.plusSeconds(1));
        venda.adicionarItem(itemCom(unidade));

        assertThat(service.expirarCarrinho(1L, LIMITE)).isFalse();
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.ABERTA);
        verifyNoInteractions(estoqueService);
    }

    @ParameterizedTest
    @EnumSource(value = StatusVenda.class, names = {"PENDENTE", "APROVADA", "CONCLUIDA", "CANCELADA"})
    @DisplayName("Venda que já saiu de ABERTA não é expirada")
    void ignoraVendaForaDeAberta(StatusVenda status) {
        Venda venda = venda(status, LIMITE.minus(Duration.ofDays(3)));
        venda.adicionarItem(itemCom(unidade));

        assertThat(service.expirarCarrinho(1L, LIMITE)).isFalse();
        assertThat(venda.getStatus()).isEqualTo(status);
        verifyNoInteractions(estoqueService);
    }

    @Test
    @DisplayName("Venda inexistente é ignorada")
    void ignoraVendaInexistente() {
        when(vendaRepository.buscarPorIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThat(service.expirarCarrinho(99L, LIMITE)).isFalse();
    }

    @Test
    @DisplayName("Adicionar pagamento conta como atividade do carrinho")
    void pagamentoRegistraAtividade() {
        Instant antes = Instant.now().minus(Duration.ofDays(1));
        Venda venda = venda(StatusVenda.ABERTA, antes);
        venda.setValorTotal(new BigDecimal("100.00"));
        when(pagamentoVendaRepository.findByVendaIdOrderByIdAsc(anyLong())).thenReturn(List.of());

        service.adicionarPagamento(1L, new PagamentoVendaRequestDTO(null, new BigDecimal("50.00"), 1, null));

        assertThat(venda.getAtualizadoEm()).isAfter(antes);
        verify(vendaRepository).save(venda);
    }

    @Test
    @DisplayName("Mexer em carrinho cancelado avisa que a venda foi cancelada")
    void mensagemDeCarrinhoCancelado() {
        venda(StatusVenda.CANCELADA, LIMITE);

        assertThatThrownBy(() -> service.removerItem(1L, 100L))
                .isInstanceOf(CarrinhoNaoEditavelException.class)
                .hasMessageContaining("foi cancelada")
                .hasMessageContaining("Abra uma nova venda");
    }

    private Venda venda(StatusVenda status, Instant atualizadoEm) {
        Venda venda = Venda.builder().id(1L).status(status).atualizadoEm(atualizadoEm).build();
        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        return venda;
    }

    private static ItemVenda itemCom(EstoqueGeral estoque) {
        return ItemVenda.builder()
                .id(100L)
                .categoria(CategoriaItemVenda.PRODUTO_VIVO)
                .produtoId(estoque.getId())
                .descricaoProduto("Galaxy A15")
                .quantidade(BigDecimal.ONE)
                .valorUnitario(new BigDecimal("999.90"))
                .estoque(estoque)
                .build();
    }
}
