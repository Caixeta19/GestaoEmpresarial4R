package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.TransicaoStatusInvalidaException;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.StatusVendaRequestDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Bug 4: carrinho reserva; VENDIDO só na conclusão; cancelamento/remoção liberam. */
@ExtendWith(MockitoExtension.class)
class VendaServiceReservaTest {

    private static final AutenticacaoUsuarioDTO AUTH = new AutenticacaoUsuarioDTO("vendedor@4r.com", "senha");

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

    private EstoqueGeral unidade;

    @BeforeEach
    void setUp() {
        unidade = EstoqueGeral.builder().id(10L).serial("356789012345678").build();
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Adicionar aparelho ao carrinho reserva a unidade (não baixa como VENDIDO)")
    void adicionarItemReserva() {
        Venda venda = venda(StatusVenda.ABERTA);
        when(estoqueService.buscarPorId(10L)).thenReturn(unidade);
        when(estoqueService.reservarNaVenda("356789012345678")).thenReturn(unidade);
        BigDecimal preco = new BigDecimal("1299.00");
        when(precoVigenteService.calcular(unidade, null, TabelaPreco.REGULAR, null, null)).thenReturn(new PrecoCalculado(
                1L, null, "Galaxy A15", TabelaPreco.REGULAR, GrupoPlano.PRE, preco, preco, null, null, null, preco, null, java.util.List.of()));

        service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.PRODUTO_VIVO)
                .produtoId(10L)
                .descricaoProduto("Galaxy A15")
                .quantidade(BigDecimal.ONE)
                .valorUnitario(new BigDecimal("999.90"))
                .imeiOuSerial("356789012345678")
                .build());

        verify(estoqueService).reservarNaVenda("356789012345678");
        verify(estoqueService, never()).confirmarVenda(any());
        assertThat(venda.getItens()).singleElement().extracting(ItemVenda::getEstoque).isEqualTo(unidade);
    }

    @Test
    @DisplayName("Serviço Vivo não reserva nada no estoque")
    void servicoNaoMexeNoEstoque() {
        venda(StatusVenda.ABERTA);

        service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.SERVICO_VIVO)
                .descricaoProduto("Plano Controle")
                .quantidade(BigDecimal.ONE)
                .valorUnitario(BigDecimal.ZERO)
                .build());

        verifyNoInteractions(estoqueService);
    }

    @Test
    @DisplayName("Remover item do carrinho libera a unidade")
    void removerItemLibera() {
        Venda venda = venda(StatusVenda.ABERTA);
        venda.adicionarItem(itemCom(unidade));

        service.removerItem(1L, 100L);

        verify(estoqueService).liberar(unidade);
        assertThat(venda.getItens()).isEmpty();
    }

    @Test
    @DisplayName("Pedir CONCLUIDA pelo status é recusado e não mexe no estoque: lançar é só pelo finalizar")
    void concluirPeloStatusNaoMexe() {
        Venda venda = venda(StatusVenda.ABERTA);
        venda.adicionarItem(itemCom(unidade));

        assertThatThrownBy(() -> service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CONCLUIDA, AUTH)))
                .isInstanceOf(NegocioException.class);

        verifyNoInteractions(estoqueService);
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.ABERTA);
    }

    @Test
    @DisplayName("Pedir PENDENTE/APROVADA é recusado e não mexe no estoque")
    void statusNaoUsadoNaoMexe() {
        Venda venda = venda(StatusVenda.ABERTA);
        venda.adicionarItem(itemCom(unidade));

        assertThatThrownBy(() -> service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.APROVADA, AUTH)))
                .isInstanceOf(NegocioException.class);

        verifyNoInteractions(estoqueService);
    }

    @ParameterizedTest
    @EnumSource(value = StatusVenda.class, names = {"ABERTA", "PENDENTE", "APROVADA", "CONCLUIDA"})
    @DisplayName("Cancelar a venda libera as unidades, em qualquer status não terminal")
    void cancelarLibera(StatusVenda statusAtual) {
        Venda venda = venda(statusAtual);
        venda.adicionarItem(itemCom(unidade));

        service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH));

        verify(estoqueService).liberar(unidade);
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.CANCELADA);
    }

    @Test
    @DisplayName("Transição inválida não mexe no estoque")
    void transicaoInvalidaNaoMexe() {
        Venda venda = venda(StatusVenda.CANCELADA);
        venda.adicionarItem(itemCom(unidade));

        assertThatThrownBy(() -> service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH)))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
        verifyNoInteractions(estoqueService);
    }

    @Test
    @DisplayName("Improcedente só marca o status BKO: a venda continua lançada, o estoque não muda e dá para reanalisar")
    void improcedenteNaoCancela() {
        Venda venda = venda(StatusVenda.CONCLUIDA);
        venda.adicionarItem(itemCom(unidade));

        service.avaliarProcedencia(1L, StatusAvaliacaoProcedencia.Improcedente);

        assertThat(venda.getStatus()).isEqualTo(StatusVenda.CONCLUIDA);
        assertThat(venda.getAvaliacaoProcedencia()).isEqualTo(StatusAvaliacaoProcedencia.Improcedente);

        service.avaliarProcedencia(1L, StatusAvaliacaoProcedencia.Em_avaliacao_pelo_BKO);

        assertThat(venda.getStatus()).isEqualTo(StatusVenda.CONCLUIDA);
        assertThat(venda.getAvaliacaoProcedencia()).isEqualTo(StatusAvaliacaoProcedencia.Em_avaliacao_pelo_BKO);
        verifyNoInteractions(estoqueService);
    }

    @Test
    @DisplayName("Procedente é final: não volta para Em avaliação; repetir Procedente pode")
    void procedenteEhFinal() {
        Venda venda = venda(StatusVenda.CONCLUIDA);
        venda.setAvaliacaoProcedencia(StatusAvaliacaoProcedencia.Procedente);

        assertThatThrownBy(() -> service.avaliarProcedencia(1L, StatusAvaliacaoProcedencia.Em_avaliacao_pelo_BKO))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Procedente");
        assertThat(venda.getAvaliacaoProcedencia()).isEqualTo(StatusAvaliacaoProcedencia.Procedente);

        service.avaliarProcedencia(1L, StatusAvaliacaoProcedencia.Procedente);
        assertThat(venda.getAvaliacaoProcedencia()).isEqualTo(StatusAvaliacaoProcedencia.Procedente);
    }

    @Test
    @DisplayName("Venda lançada em dia anterior não pode ser cancelada e o estoque não muda")
    void cancelarRetroativoRecusa() {
        Venda venda = venda(StatusVenda.CONCLUIDA);
        venda.setFinalizadoEm(java.time.Instant.now().minus(java.time.Duration.ofDays(1)).minusSeconds(60));
        venda.adicionarItem(itemCom(unidade));

        assertThatThrownBy(() -> service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH)))
                .isInstanceOf(NegocioException.class).hasMessageContaining("só é possível cancelar a venda no dia do lançamento");

        verifyNoInteractions(estoqueService);
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.CONCLUIDA);
    }

    @Test
    @DisplayName("Venda lançada hoje pode ser cancelada")
    void cancelarNoDiaPode() {
        Venda hoje = venda(StatusVenda.CONCLUIDA);
        hoje.setFinalizadoEm(java.time.Instant.now());

        service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH));

        assertThat(hoje.getStatus()).isEqualTo(StatusVenda.CANCELADA);
    }

    @Test
    @DisplayName("Carrinho aberto de dia anterior continua cancelável")
    void cancelarCarrinhoAntigoPode() {
        Venda carrinho = venda(StatusVenda.ABERTA);
        carrinho.setCriadoEm(java.time.Instant.now().minus(java.time.Duration.ofDays(3)));

        service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH));

        assertThat(carrinho.getStatus()).isEqualTo(StatusVenda.CANCELADA);
    }

    private Venda venda(StatusVenda status) {
        Venda venda = Venda.builder().id(1L).status(status).build();
        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
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

    private static ItemVenda itemSemEstoque() {
        return ItemVenda.builder()
                .id(101L)
                .categoria(CategoriaItemVenda.SERVICO_VIVO)
                .descricaoProduto("Plano Controle")
                .quantidade(BigDecimal.ONE)
                .valorUnitario(BigDecimal.ZERO)
                .build();
    }
}
