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
import com.vivo4redes.syscor.venda.dto.request.PagamentoVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.response.VendaResponseDTO;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renova (decisão de 04/11/2026): o aparelho passa para a TABELA RENOVA com Desconto fabricante + Voucher.
 * O preço Renova já tem o desconto do fabricante; o desconto é só controle (não abate o total de novo).
 */
@ExtendWith(MockitoExtension.class)
class VendaServiceRenovaTest {

    private static final String PLANO = "Vivo V Ilimitado";
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
    private EstoqueGeral iphone;
    private ItemVenda aparelho;
    private ItemVenda acessorio;
    private final List<PagamentoVenda> pagamentos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        iphone = EstoqueGeral.builder().id(10L).serial(IMEI).modeloComercial("iPhone 17 Pro Max 512GB").build();
        aparelho = ItemVenda.builder().id(100L).categoria(CategoriaItemVenda.PRODUTO_VIVO).produtoId(10L)
                .descricaoProduto("iPhone 17 Pro Max 512GB").quantidade(BigDecimal.ONE).estoque(iphone)
                .plano(PLANO).grupoPlano(GrupoPlano.VIVO_V).tabelaAplicada(TabelaPreco.REGULAR)
                .valorUnitario(new BigDecimal("10149.00")).build();
        acessorio = ItemVenda.builder().id(101L).categoria(CategoriaItemVenda.ACESSORIO).produtoId(11L)
                .descricaoProduto("Fone").quantidade(BigDecimal.ONE)
                .estoque(EstoqueGeral.builder().id(11L).serial("FONE-1").build())
                .grupoPlano(GrupoPlano.SEM_PLANO).tabelaAplicada(TabelaPreco.REGULAR)
                .valorUnitario(new BigDecimal("199.90")).build();

        venda = Venda.builder().id(1L).status(StatusVenda.ABERTA)
                .cliente(Cliente.builder().id(2L).nome("Cliente").build())
                .filial(Filial.builder().id(3L).nome("Loja").build())
                .usuario(Usuario.builder().id(4L).nome("Vendedor").build())
                .build();
        venda.adicionarItem(aparelho);
        venda.adicionarItem(acessorio);
        venda.recalcularValorTotal();

        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagamentoVendaRepository.findByVendaIdOrderByIdAsc(1L)).thenAnswer(inv -> List.copyOf(pagamentos));
        lenient().when(precoVigenteService.calcular(iphone, PLANO, TabelaPreco.REGULAR, null, null))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.VIVO_V, "10149.00", null));
        lenient().when(precoVigenteService.calcular(iphone, PLANO, TabelaPreco.RENOVA, null, null))
                .thenReturn(preco(TabelaPreco.RENOVA, GrupoPlano.VIVO_V, "9649.00", "500.00"));
        lenient().when(precoVigenteService.calcular(acessorio.getEstoque(), null, TabelaPreco.REGULAR, null, null))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.SEM_PLANO, "199.90", null));
    }

    @Test
    @DisplayName("Aplicar Renova sem voucher lançado é recusado (422) e nada muda")
    void renovaExigeVoucher() {
        assertThatThrownBy(() -> service.aplicarRenova(1L, true))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Lance o voucher na aba Pagamento antes de aplicar o Renova.");
        assertThat(venda.isRenova()).isFalse();
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("10149.00");
    }

    @Test
    @DisplayName("Com voucher, o Renova reprecifica só o aparelho: preço Renova, desconto do fabricante e total")
    void renovaReprecificaAparelho() {
        pagamentos.add(voucher("V-123", "300.00"));

        service.aplicarRenova(1L, true);

        assertThat(venda.isRenova()).isTrue();
        assertThat(aparelho.getTabelaAplicada()).isEqualTo(TabelaPreco.RENOVA);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("9649.00");
        assertThat(aparelho.getDescontoFabricante()).isEqualByComparingTo("500.00");
        // Acessório (SEM_PLANO) fica na REGULAR, com o mesmo preço.
        assertThat(acessorio.getValorUnitario()).isEqualByComparingTo("199.90");
        assertThat(acessorio.getTabelaAplicada()).isEqualTo(TabelaPreco.REGULAR);
        // O desconto já está no preço Renova: o total é a soma dos itens, sem abater de novo.
        assertThat(venda.getValorTotal()).isEqualByComparingTo("9848.90");
        assertThat(venda.getDescontoFabricante()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Desligar o Renova devolve o aparelho para a Regular e zera o desconto do fabricante")
    void desligarVoltaParaRegular() {
        pagamentos.add(voucher("V-123", "300.00"));
        service.aplicarRenova(1L, true);

        service.aplicarRenova(1L, false);

        assertThat(venda.isRenova()).isFalse();
        assertThat(aparelho.getTabelaAplicada()).isEqualTo(TabelaPreco.REGULAR);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("10149.00");
        assertThat(aparelho.getDescontoFabricante()).isNull();
        assertThat(venda.getValorTotal()).isEqualByComparingTo("10348.90");
        assertThat(venda.getDescontoFabricante()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Aparelho em plano pré-pago recusa o Renova com a mensagem do cálculo de preço")
    void prePagoRecusaRenova() {
        aparelho.setPlano(null);
        aparelho.setGrupoPlano(GrupoPlano.PRE);
        pagamentos.add(voucher("V-123", "300.00"));
        when(precoVigenteService.calcular(iphone, null, TabelaPreco.REGULAR, null, null))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.PRE, "12909.00", null));
        when(precoVigenteService.calcular(iphone, null, TabelaPreco.RENOVA, null, null))
                .thenThrow(new NegocioException("Renova não disponível para plano pré-pago."));

        assertThatThrownBy(() -> service.aplicarRenova(1L, true))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Renova não disponível para plano pré-pago.");
    }

    @Test
    @DisplayName("Sem aparelho com preço por plano não há o que aplicar")
    void semAparelhoElegivel() {
        venda.getItens().remove(aparelho);
        pagamentos.add(voucher("V-123", "100.00"));

        assertThatThrownBy(() -> service.aplicarRenova(1L, true))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não tem aparelho com preço por plano");
    }

    @Test
    @DisplayName("Remover o voucher desliga o Renova e o aparelho volta para a Regular")
    void removerVoucherDesligaRenova() {
        PagamentoVenda v = voucher("V-123", "300.00");
        pagamentos.add(v);
        service.aplicarRenova(1L, true);
        when(pagamentoVendaRepository.findById(5L)).thenReturn(Optional.of(v));
        org.mockito.Mockito.doAnswer(inv -> pagamentos.remove(v)).when(pagamentoVendaRepository).delete(v);

        service.removerPagamento(1L, 5L);

        assertThat(venda.isRenova()).isFalse();
        assertThat(aparelho.getTabelaAplicada()).isEqualTo(TabelaPreco.REGULAR);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("10149.00");
    }

    @Test
    @DisplayName("Voucher exige código e é gravado com o código e em 1x")
    void voucherComCodigo() {
        assertThatThrownBy(() -> service.adicionarPagamento(1L,
                new PagamentoVendaRequestDTO(FormaPagamento.VOUCHER, new BigDecimal("300.00"), 1, "  ")))
                .isInstanceOf(NegocioException.class).hasMessage("Informe o código do voucher.");
        verify(pagamentoVendaRepository, never()).save(any());

        service.adicionarPagamento(1L, new PagamentoVendaRequestDTO(FormaPagamento.VOUCHER, new BigDecimal("300.00"), 3, " V-123 "));

        ArgumentCaptor<PagamentoVenda> captor = ArgumentCaptor.forClass(PagamentoVenda.class);
        verify(pagamentoVendaRepository).save(captor.capture());
        assertThat(captor.getValue().getCodigoVoucher()).isEqualTo("V-123");
        assertThat(captor.getValue().getParcelas()).isEqualTo(1);
        assertThat(captor.getValue().getForma()).isEqualTo(FormaPagamento.VOUCHER);
    }

    @Test
    @DisplayName("Com o Renova ligado, aparelho novo já entra pela Renova")
    void aparelhoNovoComRenovaLigado() {
        venda.getItens().clear();
        venda.setRenova(true);
        when(estoqueService.buscarPorId(10L)).thenReturn(iphone);
        when(estoqueService.reservarNaVenda(IMEI)).thenReturn(iphone);

        service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.PRODUTO_VIVO).produtoId(10L).descricaoProduto("iPhone 17 Pro Max 512GB")
                .quantidade(BigDecimal.ONE).imeiOuSerial(IMEI).plano(PLANO).build());

        ItemVenda item = venda.getItens().get(0);
        assertThat(item.getTabelaAplicada()).isEqualTo(TabelaPreco.RENOVA);
        assertThat(item.getValorUnitario()).isEqualByComparingTo("9649.00");
        assertThat(venda.getDescontoFabricante()).isEqualByComparingTo("500.00");
        verify(precoVigenteService, org.mockito.Mockito.atLeastOnce())
                .calcular(eq(iphone), eq(PLANO), eq(TabelaPreco.RENOVA), any(), any());
    }

    @Test
    @DisplayName("Resposta traz renova, desconto, saldo e aviso quando os pagamentos passam do total")
    void respostaComAvisoDeSaldo() {
        pagamentos.add(voucher("V-123", "300.00"));
        service.aplicarRenova(1L, true);
        PagamentoVenda credito = PagamentoVenda.builder().id(6L).venda(venda).forma(FormaPagamento.CARTAO_CREDITO)
                .valor(new BigDecimal("10048.90")).parcelas(1).build();

        VendaResponseDTO dto = VendaResponseDTO.from(venda, List.of(pagamentos.get(0), credito));

        assertThat(dto.renova()).isTrue();
        assertThat(dto.descontoFabricante()).isEqualByComparingTo("500.00");
        assertThat(dto.saldo()).isEqualByComparingTo("-500.00");
        assertThat(dto.aviso()).contains("passam do total");
        assertThat(dto.itens()).filteredOn(i -> i.id().equals(100L)).singleElement()
                .satisfies(i -> {
                    assertThat(i.tabelaPrecoAplicada()).isEqualTo(TabelaPreco.RENOVA);
                    assertThat(i.descontoFabricante()).isEqualByComparingTo("500.00");
                });
        assertThat(dto.pagamentos().get(0).codigoVoucher()).isEqualTo("V-123");

        VendaResponseDTO semExcesso = VendaResponseDTO.from(venda, List.of(pagamentos.get(0)));
        assertThat(semExcesso.saldo()).isEqualByComparingTo("9548.90");
        assertThat(semExcesso.aviso()).isNull();
    }

    @Test
    @DisplayName("Finalizar venda com Renova sem voucher é recusado")
    void finalizarRenovaSemVoucher() {
        venda.setRenova(true);
        assertThatThrownBy(() -> service.finalizar(1L, new com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO(
                new com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO("v@4r.com", "senha"))))
                .isInstanceOf(NegocioException.class).hasMessageContaining("voucher");
    }

    private PagamentoVenda voucher(String codigo, String valor) {
        return PagamentoVenda.builder().id(5L).venda(venda).forma(FormaPagamento.VOUCHER)
                .valor(new BigDecimal(valor)).parcelas(1).codigoVoucher(codigo).build();
    }

    private static PrecoCalculado preco(TabelaPreco tabela, GrupoPlano grupo, String valor, String desconto) {
        BigDecimal v = new BigDecimal(valor);
        return new PrecoCalculado(1L, LocalDate.of(2026, 11, 1), "iPhone 17 Pro Max 512GB", tabela, grupo,
                v, v, null, null, null, v, desconto == null ? null : new BigDecimal(desconto), List.of());
    }
}
