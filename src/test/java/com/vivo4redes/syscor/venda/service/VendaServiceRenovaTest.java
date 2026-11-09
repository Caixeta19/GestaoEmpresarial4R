package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.VoucherJaUsadoException;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
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
 * Renova pelo número do voucher: o voucher não é forma de pagamento nem tem valor na venda. O preço Renova já tem o
 * desconto do fabricante; o desconto é só controle (não abate o total de novo).
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
    @DisplayName("Sem número não há Renova: nulo ou em branco é recusado (422) e nada muda")
    void renovaExigeNumero() {
        for (String vazio : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service.aplicarRenova(1L, true, vazio))
                    .isInstanceOf(NegocioException.class).hasMessage("Informe o número do voucher.");
        }
        assertThat(venda.isRenova()).isFalse();
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("10149.00");
    }

    @Test
    @DisplayName("Número: sem espaços nas pontas e até 60 caracteres")
    void tamanhoDoNumero() {
        service.aplicarRenova(1L, true, "  " + "A".repeat(60) + "  ");
        assertThat(venda.getCodigoVoucher()).isEqualTo("A".repeat(60));

        assertThatThrownBy(() -> service.aplicarRenova(1L, true, "A".repeat(61)))
                .isInstanceOf(NegocioException.class).hasMessageContaining("no máximo 60");
    }

    @Test
    @DisplayName("Com número, o Renova reprecifica só o aparelho e não cria linha de pagamento")
    void renovaReprecificaAparelho() {
        service.aplicarRenova(1L, true, " V-123 ");

        assertThat(venda.isRenova()).isTrue();
        assertThat(venda.getCodigoVoucher()).isEqualTo("V-123");
        assertThat(aparelho.getTabelaAplicada()).isEqualTo(TabelaPreco.RENOVA);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("9649.00");
        assertThat(aparelho.getDescontoFabricante()).isEqualByComparingTo("500.00");
        // Acessório (SEM_PLANO) fica na REGULAR, com o mesmo preço.
        assertThat(acessorio.getValorUnitario()).isEqualByComparingTo("199.90");
        assertThat(acessorio.getTabelaAplicada()).isEqualTo(TabelaPreco.REGULAR);
        // O desconto já está no preço Renova: o total é a soma dos itens, sem abater de novo.
        assertThat(venda.getValorTotal()).isEqualByComparingTo("9848.90");
        assertThat(venda.getDescontoFabricante()).isEqualByComparingTo("500.00");
        verify(pagamentoVendaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desligar limpa o número, devolve o aparelho para a Regular e zera o desconto do fabricante")
    void desligarVoltaParaRegular() {
        service.aplicarRenova(1L, true, "V-123");

        service.aplicarRenova(1L, false, null);

        assertThat(venda.isRenova()).isFalse();
        assertThat(venda.getCodigoVoucher()).isNull();
        assertThat(aparelho.getTabelaAplicada()).isEqualTo(TabelaPreco.REGULAR);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("10149.00");
        assertThat(aparelho.getDescontoFabricante()).isNull();
        assertThat(venda.getValorTotal()).isEqualByComparingTo("10348.90");
        assertThat(venda.getDescontoFabricante()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Desligar ignora o número enviado")
    void desligarIgnoraNumero() {
        service.aplicarRenova(1L, true, "V-123");

        service.aplicarRenova(1L, false, "V-999");

        assertThat(venda.getCodigoVoucher()).isNull();
    }

    @Test
    @DisplayName("Aparelho em plano pré-pago recusa o Renova com a mensagem do cálculo de preço")
    void prePagoRecusaRenova() {
        aparelho.setPlano(null);
        aparelho.setGrupoPlano(GrupoPlano.PRE);
        when(precoVigenteService.calcular(iphone, null, TabelaPreco.REGULAR, null, null))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.PRE, "12909.00", null));
        when(precoVigenteService.calcular(iphone, null, TabelaPreco.RENOVA, null, null))
                .thenThrow(new NegocioException("Renova não disponível para plano pré-pago."));

        assertThatThrownBy(() -> service.aplicarRenova(1L, true, "V-123"))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Renova não disponível para plano pré-pago.");
    }

    @Test
    @DisplayName("Sem aparelho com preço por plano não há o que aplicar")
    void semAparelhoElegivel() {
        venda.getItens().remove(aparelho);

        assertThatThrownBy(() -> service.aplicarRenova(1L, true, "V-123"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não tem aparelho com preço por plano");
        assertThat(venda.getCodigoVoucher()).isNull();
    }

    @Test
    @DisplayName("Número em outra venda não cancelada: recusa com o id da venda e nada muda")
    void numeroEmOutraVenda() {
        when(vendaRepository.buscarOutrasVendasComVoucher("V-123", 1L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.aplicarRenova(1L, true, " V-123 "))
                .isInstanceOf(VoucherJaUsadoException.class).hasMessage("Voucher já usado na venda #7");
        assertThat(venda.isRenova()).isFalse();
        assertThat(venda.getCodigoVoucher()).isNull();
    }

    @Test
    @DisplayName("Número em linha de pagamento de venda antiga também conta como usado")
    void numeroEmVendaAntiga() {
        when(pagamentoVendaRepository.buscarOutrasVendasComVoucher("V-123", 1L)).thenReturn(List.of(3L));

        assertThatThrownBy(() -> service.aplicarRenova(1L, true, "V-123"))
                .isInstanceOf(VoucherJaUsadoException.class).hasMessage("Voucher já usado na venda #3");
    }

    @Test
    @DisplayName("Com o Renova ligado, aplicar outro número troca o número")
    void trocarNumero() {
        service.aplicarRenova(1L, true, "V-123");

        service.aplicarRenova(1L, true, "V-456");

        assertThat(venda.getCodigoVoucher()).isEqualTo("V-456");
        assertThat(venda.isRenova()).isTrue();
    }

    @Test
    @DisplayName("POST de pagamento com VOUCHER é recusado (422), em qualquer condição")
    void voucherNaoEFormaDePagamento() {
        assertThatThrownBy(() -> service.adicionarPagamento(1L,
                new PagamentoVendaRequestDTO(FormaPagamento.VOUCHER, new BigDecimal("300.00"), 1)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Voucher não é forma de pagamento: informe o número no bloco Renova.");
        verify(pagamentoVendaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Remover um pagamento não mexe no Renova")
    void removerPagamentoNaoDesligaRenova() {
        service.aplicarRenova(1L, true, "V-123");
        PagamentoVenda dinheiro = pagamento(FormaPagamento.DINHEIRO, "300.00");
        when(pagamentoVendaRepository.findById(5L)).thenReturn(Optional.of(dinheiro));

        service.removerPagamento(1L, 5L);

        assertThat(venda.isRenova()).isTrue();
        assertThat(venda.getCodigoVoucher()).isEqualTo("V-123");
        assertThat(aparelho.getTabelaAplicada()).isEqualTo(TabelaPreco.RENOVA);
    }

    @Test
    @DisplayName("Ligar e desligar o Renova não apaga pagamentos")
    void renovaNaoApagaPagamentos() {
        pagamentos.add(pagamento(FormaPagamento.DINHEIRO, "10348.90"));

        service.aplicarRenova(1L, true, "V-123");
        service.aplicarRenova(1L, false, null);

        verify(pagamentoVendaRepository, never()).delete(any());
        assertThat(service.listarPagamentos(1L)).hasSize(1);
    }

    @Test
    @DisplayName("Com o Renova ligado, aparelho novo já entra pela Renova")
    void aparelhoNovoComRenovaLigado() {
        venda.getItens().clear();
        venda.setRenova(true);
        venda.setCodigoVoucher("V-123");
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
    @DisplayName("Resposta traz renova, número, desconto, saldo e aviso quando os pagamentos passam do total")
    void respostaComAvisoDeSaldo() {
        service.aplicarRenova(1L, true, "V-123");
        PagamentoVenda dinheiro = pagamento(FormaPagamento.DINHEIRO, "10348.90");

        VendaResponseDTO dto = VendaResponseDTO.from(venda, List.of(dinheiro));

        assertThat(dto.renova()).isTrue();
        assertThat(dto.codigoVoucher()).isEqualTo("V-123");
        assertThat(dto.descontoFabricante()).isEqualByComparingTo("500.00");
        assertThat(dto.saldo()).isEqualByComparingTo("-500.00");
        assertThat(dto.aviso()).contains("passam do total");
        assertThat(dto.itens()).filteredOn(i -> i.id().equals(100L)).singleElement()
                .satisfies(i -> {
                    assertThat(i.tabelaPrecoAplicada()).isEqualTo(TabelaPreco.RENOVA);
                    assertThat(i.descontoFabricante()).isEqualByComparingTo("500.00");
                });

        VendaResponseDTO semExcesso = VendaResponseDTO.from(venda, List.of());
        assertThat(semExcesso.saldo()).isEqualByComparingTo("9848.90");
        assertThat(semExcesso.aviso()).isNull();
    }

    @Test
    @DisplayName("Venda antiga continua mostrando o código na linha VOUCHER")
    void linhaAntigaMantemCodigo() {
        PagamentoVenda antiga = pagamento(FormaPagamento.VOUCHER, "300.00");
        antiga.setCodigoVoucher("V-ANTIGO");

        VendaResponseDTO dto = VendaResponseDTO.from(venda, List.of(antiga));

        assertThat(dto.pagamentos().get(0).codigoVoucher()).isEqualTo("V-ANTIGO");
        assertThat(dto.codigoVoucher()).isNull();
    }

    @Test
    @DisplayName("Finalizar venda com Renova sem o número é recusado")
    void finalizarRenovaSemNumero() {
        venda.setRenova(true);

        assertThatThrownBy(() -> service.finalizar(1L, finalizar()))
                .isInstanceOf(NegocioException.class).hasMessage("Venda com Renova precisa do número do voucher");
    }

    @Test
    @DisplayName("Finalizar venda com Renova só exige o número e a soma dos pagamentos, sem linha de voucher")
    void finalizarRenovaComNumero() {
        service.aplicarRenova(1L, true, "V-123");
        pagamentos.add(pagamento(FormaPagamento.DINHEIRO, "9848.90"));

        assertThat(service.finalizar(1L, finalizar()).getStatus()).isEqualTo(StatusVenda.PENDENTE);
    }

    private static FinalizarVendaRequestDTO finalizar() {
        return new FinalizarVendaRequestDTO(new AutenticacaoUsuarioDTO("v@4r.com", "senha"));
    }

    private PagamentoVenda pagamento(FormaPagamento forma, String valor) {
        return PagamentoVenda.builder().id(5L).venda(venda).forma(forma)
                .valor(new BigDecimal(valor)).parcelas(1).build();
    }

    private static PrecoCalculado preco(TabelaPreco tabela, GrupoPlano grupo, String valor, String desconto) {
        BigDecimal v = new BigDecimal(valor);
        return new PrecoCalculado(1L, LocalDate.of(2026, 11, 1), "iPhone 17 Pro Max 512GB", tabela, grupo,
                v, v, null, null, null, v, desconto == null ? null : new BigDecimal(desconto), List.of());
    }
}
