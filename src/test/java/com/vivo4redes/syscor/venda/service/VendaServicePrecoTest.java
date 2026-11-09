package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.venda.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.PagamentoVendaRepository;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regra 8: preço de aparelho/acessório calculado no backend; o valorUnitario do front é ignorado. */
@ExtendWith(MockitoExtension.class)
class VendaServicePrecoTest {

    private static final String IMEI = "356789012345678";

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

    private Venda venda;
    private EstoqueGeral aparelho;

    @BeforeEach
    void setUp() {
        venda = Venda.builder().id(1L).status(StatusVenda.ABERTA).build();
        aparelho = EstoqueGeral.builder().id(10L).serial(IMEI).modeloComercial("iPhone 17 Pro Max 512GB").build();
        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(estoqueService.buscarPorId(10L)).thenReturn(aparelho);
        lenient().when(estoqueService.reservarNaVenda(IMEI)).thenReturn(aparelho);
    }

    @Test
    @DisplayName("valorUnitario do front é ignorado: vale o preço REGULAR da tabela vigente, mesmo em troca de aparelho")
    void valorDoFrontIgnorado() {
        venda.setCondicaoPagamento(CondicaoPagamento.PIX);
        when(precoVigenteService.calcular(aparelho, "Vivo V Ilimitado", TabelaPreco.REGULAR, FormaPagamento.PIX, null))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.VIVO_V, "9134.10"));

        service.adicionarItem(1L, aparelho("1.00").plano("Vivo V Ilimitado").tipoServico("Troca de aparelho").build());

        ItemVenda item = venda.getItens().get(0);
        assertThat(item.getValorUnitario()).isEqualByComparingTo("9134.10");
        assertThat(item.getGrupoPlano()).isEqualTo(GrupoPlano.VIVO_V);
        assertThat(item.getTabelaAplicada()).isEqualTo(TabelaPreco.REGULAR);
        assertThat(item.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("9134.10");
        verify(precoVigenteService, never()).calcular(any(), any(), org.mockito.ArgumentMatchers.eq(TabelaPreco.RENOVA), any(), any());
    }

    @Test
    @DisplayName("Aparelho sem valorUnitario no request também é aceito (o preço vem da tabela)")
    void semValorDoFront() {
        when(precoVigenteService.calcular(aparelho, null, TabelaPreco.REGULAR, null, null))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.PRE, "12909.00"));

        service.adicionarItem(1L, aparelho(null).build());

        assertThat(venda.getItens().get(0).getValorUnitario()).isEqualByComparingTo("12909.00");
    }

    @Test
    @DisplayName("Item sem preço na tabela vigente é recusado (422) antes de reservar a unidade")
    void semPrecoNaoReserva() {
        when(precoVigenteService.calcular(aparelho, null, TabelaPreco.REGULAR, null, null))
                .thenThrow(new NegocioException("Item sem preço na tabela vigente (iPhone 17 Pro Max 512GB)."));

        assertThatThrownBy(() -> service.adicionarItem(1L, aparelho("999.00").build()))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Item sem preço na tabela vigente (iPhone 17 Pro Max 512GB).");
        verify(estoqueService, never()).reservarNaVenda(any());
        assertThat(venda.getItens()).isEmpty();
    }

    @Test
    @DisplayName("Chip usa o preço fixo de configuração, não o valor do front")
    void chipFixo() {
        when(precoVigenteService.calcular(aparelho, null, TabelaPreco.REGULAR, null, null))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.PRE, "12909.00"));
        when(precoVigenteService.precoChip()).thenReturn(new BigDecimal("13.00"));

        service.adicionarItem(1L, aparelho(null).valorChip(new BigDecimal("1.00")).build());

        assertThat(venda.getItens().get(0).getValorChip()).isEqualByComparingTo("13.00");
    }

    @Test
    @DisplayName("Serviço Vivo continua com valor 0 e não consulta a tabela de preços")
    void servicoComValorZero() {
        service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.SERVICO_VIVO).descricaoProduto("Plano Controle")
                .quantidade(BigDecimal.ONE).valorUnitario(new BigDecimal("50.00")).build());

        assertThat(venda.getItens().get(venda.getItens().size() - 1).getValorUnitario()).isEqualByComparingTo("0");
        verifyNoInteractions(precoVigenteService);
        verify(estoqueService, never()).reservarNaVenda(any());
    }

    @Test
    @DisplayName("Recarga continua usando o valor informado (e exige valor maior que zero)")
    void recargaUsaValorInformado() {
        service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.RECARGA).produtoId(10L).descricaoProduto("Recarga")
                .quantidade(BigDecimal.ONE).valorUnitario(new BigDecimal("30.00")).imeiOuSerial(IMEI).build());

        assertThat(venda.getItens().get(0).getValorUnitario()).isEqualByComparingTo("30.00");
        verifyNoInteractions(precoVigenteService);
        assertThatThrownBy(() -> service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.RECARGA).produtoId(10L).descricaoProduto("Recarga")
                .quantidade(BigDecimal.ONE).imeiOuSerial(IMEI).build()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("recarga");
    }

    private ItemVendaRequestDTO.ItemVendaRequestDTOBuilder aparelho(String valorDoFront) {
        return ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.PRODUTO_VIVO)
                .produtoId(10L)
                .descricaoProduto("iPhone 17 Pro Max 512GB")
                .quantidade(BigDecimal.ONE)
                .valorUnitario(valorDoFront == null ? null : new BigDecimal(valorDoFront))
                .imeiOuSerial(IMEI);
    }

    private static PrecoCalculado preco(TabelaPreco tabela, GrupoPlano grupo, String valor) {
        BigDecimal v = new BigDecimal(valor);
        return new PrecoCalculado(1L, LocalDate.of(2026, 11, 1), "iPhone 17 Pro Max 512GB", tabela, grupo,
                v, v, null, null, null, v, null, List.of());
    }
}
