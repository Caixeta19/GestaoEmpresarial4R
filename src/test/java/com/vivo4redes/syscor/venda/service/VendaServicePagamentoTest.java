package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.TotalVendaAlteradoException;
import com.vivo4redes.syscor.exception.TransicaoStatusInvalidaException;
import com.vivo4redes.syscor.exception.PagamentosIncompativeisException;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.PagamentoVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.response.SimulacaoPagamentoDTO;
import com.vivo4redes.syscor.venda.dto.response.VendaResponseDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.enums.TipoPagamento;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

/** Linhas de pagamento por condição, com o exemplo iPhone 17 Pro Max 512GB, Controle Alto Valor. */
@ExtendWith(MockitoExtension.class)
class VendaServicePagamentoTest {

    private static final String PLANO = "Controle Alto Valor";

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
    private final List<PagamentoVenda> pagamentos = new ArrayList<>();
    private long proximoId = 50L;

    @BeforeEach
    void setUp() {
        iphone = EstoqueGeral.builder().id(10L).serial("356789012345678").modeloComercial("iPhone 17 Pro Max 512GB").build();
        venda = Venda.builder().id(1L).status(StatusVenda.ABERTA)
                .cliente(Cliente.builder().id(2L).nome("Cliente").build())
                .filial(Filial.builder().id(3L).nome("Loja").build())
                .usuario(Usuario.builder().id(4L).nome("Vendedor").build())
                .build();
        venda.adicionarItem(ItemVenda.builder().id(100L).categoria(CategoriaItemVenda.PRODUTO_VIVO).produtoId(10L)
                .descricaoProduto("iPhone").quantidade(BigDecimal.ONE).estoque(iphone).plano(PLANO)
                .grupoPlano(GrupoPlano.CONTROLE_ALTO_VALOR).tabelaAplicada(TabelaPreco.REGULAR)
                .valorUnitario(new BigDecimal("11199.00")).build());
        venda.recalcularValorTotal();

        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagamentoVendaRepository.findByVendaIdOrderByIdAsc(1L)).thenAnswer(inv -> List.copyOf(pagamentos));
        lenient().when(pagamentoVendaRepository.save(any(PagamentoVenda.class))).thenAnswer(inv -> {
            PagamentoVenda p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(proximoId++);
                pagamentos.add(p);
            }
            return p;
        });
        lenient().when(pagamentoVendaRepository.findById(any())).thenAnswer(inv -> pagamentos.stream()
                .filter(p -> p.getId().equals(inv.getArgument(0))).findFirst());
        lenient().doAnswer(inv -> pagamentos.remove(inv.<PagamentoVenda>getArgument(0)))
                .when(pagamentoVendaRepository).delete(any(PagamentoVenda.class));

        precos(TabelaPreco.REGULAR, "11199.00", null, "10079.10", "12581.12");
        precos(TabelaPreco.RENOVA, "10699.00", "500.00", "9579.10", "12081.12");
        for (CondicaoPagamento c : CondicaoPagamento.values()) {
            if (c.parcelado() && c != CondicaoPagamento.PARCELADO_21) {
                BigDecimal total = new BigDecimal("11199.00").multiply(c.fator()).setScale(2, java.math.RoundingMode.HALF_UP);
                lenient().when(precoVigenteService.calcular(iphone, PLANO, TabelaPreco.REGULAR, FormaPagamento.CARTAO_CREDITO, c.parcelas()))
                        .thenReturn(preco(TabelaPreco.REGULAR, "11199.00", total.toPlainString(), null));
            }
        }
    }

    @Test
    @DisplayName("Regular: PIX R$ 10.079,10, à vista R$ 11.199,00 e 21x R$ 12.581,12 (21 × R$ 599,10)")
    void valoresRegular() {
        assertThat(service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX).getValorTotal()).isEqualByComparingTo("10079.10");
        assertThat(venda.getAjusteCondicao()).isEqualByComparingTo("-1119.90");

        assertThat(service.definirCondicaoPagamento(1L, CondicaoPagamento.A_VISTA).getValorTotal()).isEqualByComparingTo("11199.00");
        assertThat(venda.getAjusteCondicao()).isEqualByComparingTo("0.00");

        assertThat(service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21).getValorTotal()).isEqualByComparingTo("12581.12");
        assertThat(venda.getValorParcela()).isEqualByComparingTo("599.10");
        assertThat(venda.getValorItens()).isEqualByComparingTo("11199.00");
        assertThat(venda.getAjusteCondicao()).isEqualByComparingTo("1382.12");
    }

    @Test
    @DisplayName("Renova: PIX R$ 9.579,10 e à vista R$ 10.699,00; valorItens no preço cheio Renova")
    void valoresRenova() {
        pagar(FormaPagamento.VOUCHER, "300.00", "V-1");
        service.aplicarRenova(1L, true);

        assertThat(venda.getValorTotal()).isEqualByComparingTo("10699.00");
        assertThat(venda.getValorItens()).isEqualByComparingTo("10699.00");
        assertThat(venda.getAjusteCondicao()).isEqualByComparingTo("0.00");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("9579.10");
        assertThat(venda.getAjusteCondicao()).isEqualByComparingTo("-1119.90");
    }

    @Test
    @DisplayName("Renova 21x com voucher de R$ 500: o voucher é entrada e o total fica em R$ 12.019,41")
    void renovaParceladoComVoucher() {
        pagar(FormaPagamento.VOUCHER, "500.00", "V-1");
        service.aplicarRenova(1L, true);
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);

        service.financiar(1L);

        assertThat(venda.getValorTotal()).isEqualByComparingTo("12019.41");
        assertThat(financiado().getValor()).isEqualByComparingTo("11519.41");
        assertThat(pagamentos.get(0).getTipo()).isEqualTo(TipoPagamento.ENTRADA);
    }

    @Test
    @DisplayName("Entrada de R$ 2.000 em dinheiro + 21x: financiado R$ 10.334,29, total R$ 12.334,29, parcela R$ 492,11")
    void entradaEmDinheiro() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        pagar(FormaPagamento.DINHEIRO, "2000.00", null);

        service.financiar(1L);

        assertThat(financiado().getValor()).isEqualByComparingTo("10334.29");
        assertThat(financiado().getForma()).isEqualTo(FormaPagamento.CARTAO_CREDITO);
        assertThat(financiado().getParcelas()).isEqualTo(21);
        assertThat(pagamentos.get(0).getTipo()).isEqualTo(TipoPagamento.ENTRADA);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("12334.29");
        assertThat(venda.getValorParcela()).isEqualByComparingTo("492.11");
        assertThat(service.finalizar(1L, finalizar()).getStatus()).isEqualTo(StatusVenda.PENDENTE);
    }

    @Test
    @DisplayName("Lançar e remover entrada recalcula a linha financiada")
    void entradaRecalculaFinanciado() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        service.financiar(1L);
        assertThat(financiado().getValor()).isEqualByComparingTo("12581.12");

        pagar(FormaPagamento.DINHEIRO, "2000.00", null);
        assertThat(financiado().getValor()).isEqualByComparingTo("10334.29");

        service.removerPagamento(1L, pagamentos.stream()
                .filter(p -> p.getTipo() == TipoPagamento.ENTRADA).findFirst().orElseThrow().getId());

        assertThat(financiado().getValor()).isEqualByComparingTo("12581.12");
        assertThat(venda.getValorTotal()).isEqualByComparingTo("12581.12");
    }

    @Test
    @DisplayName("Item novo e item removido recalculam a linha financiada")
    void itemRecalculaFinanciado() {
        ItemVenda recarga = recarga();
        venda.adicionarItem(recarga);
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        service.financiar(1L);
        assertThat(financiado().getValor()).isEqualByComparingTo("12601.12");

        service.removerItem(1L, recarga.getId());

        assertThat(financiado().getValor()).isEqualByComparingTo("12581.12");
    }

    @Test
    @DisplayName("Chip soma no total pelo valor cheio e não muda com a condição")
    void chipSomaNoTotal() {
        venda.adicionarItem(ItemVenda.builder().id(102L).categoria(CategoriaItemVenda.SERVICO_VIVO)
                .descricaoProduto("Plano").quantidade(BigDecimal.ONE).valorUnitario(BigDecimal.ZERO)
                .valorChip(new BigDecimal("13.00")).build());

        assertThat(service.definirCondicaoPagamento(1L, CondicaoPagamento.A_VISTA).getValorTotal()).isEqualByComparingTo("11212.00");
        assertThat(service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX).getValorTotal()).isEqualByComparingTo("10092.10");
        assertThat(service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21).getValorTotal()).isEqualByComparingTo("12594.12");
        assertThat(venda.getValorItens()).isEqualByComparingTo("11212.00");
    }

    @Test
    @DisplayName("Chip em item de tabela não entra na base dos juros")
    void chipEmItemDeTabela() {
        venda.getItens().get(0).setValorChip(new BigDecimal("13.00"));

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        pagar(FormaPagamento.DINHEIRO, "2000.00", null);

        assertThat(venda.getValorTotal()).isEqualByComparingTo("12347.29");
    }

    @Test
    @DisplayName("Entrada acima da base de tabela: o excesso abate os itens sem tabela, sem juros")
    void entradaAcimaDaBase() {
        venda.adicionarItem(recarga());
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        pagar(FormaPagamento.DINHEIRO, "11209.00", null);

        service.financiar(1L);

        assertThat(venda.getValorTotal()).isEqualByComparingTo("11219.00");
        assertThat(financiado().getValor()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Entrada acima do total é recusada")
    void entradaAcimaDoTotal() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);

        assertThatThrownBy(() -> pagar(FormaPagamento.DINHEIRO, "11199.01", null))
                .isInstanceOf(NegocioException.class).hasMessageContaining("passa do total");
    }

    @Test
    @DisplayName("Entrada que quita a venda apaga a linha financiada")
    void entradaQuitaApagaFinanciado() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        service.financiar(1L);

        pagar(FormaPagamento.DINHEIRO, "11199.00", null);

        assertThat(pagamentos).noneMatch(p -> p.getTipo() == TipoPagamento.FINANCIADO);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("11199.00");
        assertThat(venda.getValorParcela()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> service.financiar(1L))
                .isInstanceOf(NegocioException.class).hasMessageContaining("saldo");
    }

    @Test
    @DisplayName("Condição PIX recusa o cartão; em A_VISTA, PIX + cartão pagam o preço cheio")
    void pixMaisCartao() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);
        assertThatThrownBy(() -> pagar(FormaPagamento.CARTAO_CREDITO, "100.00", null))
                .isInstanceOf(NegocioException.class).hasMessageContaining("não aceita CARTAO_CREDITO");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.A_VISTA);
        pagar(FormaPagamento.PIX, "5000.00", null);
        pagar(FormaPagamento.CARTAO_CREDITO, "6199.00", null);

        assertThat(venda.getValorTotal()).isEqualByComparingTo("11199.00");
        assertThat(pagamentos).allMatch(p -> p.getTipo() == TipoPagamento.PAGAMENTO);
        assertThat(service.finalizar(1L, finalizar()).getStatus()).isEqualTo(StatusVenda.PENDENTE);
    }

    @Test
    @DisplayName("Vivo Pay vale como PIX; A_VISTA recusa crédito acima de 12x")
    void vivoPayECreditoDe13x() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);
        pagar(FormaPagamento.VIVO_PAY, "10079.10", null);
        assertThat(service.finalizar(1L, finalizar()).getStatus()).isEqualTo(StatusVenda.PENDENTE);

        venda.setStatus(StatusVenda.ABERTA);
        pagamentos.clear();
        service.definirCondicaoPagamento(1L, CondicaoPagamento.A_VISTA);
        assertThatThrownBy(() -> service.adicionarPagamento(1L,
                new PagamentoVendaRequestDTO(FormaPagamento.CARTAO_CREDITO, new BigDecimal("100.00"), 13, null)))
                .isInstanceOf(NegocioException.class).hasMessageContaining("13x");
    }

    @Test
    @DisplayName("PARCELADO_N recusa crédito digitado, porque o financiado é criado pelo backend")
    void parceladoRecusaCredito() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);

        assertThatThrownBy(() -> pagar(FormaPagamento.CARTAO_CREDITO, "100.00", null))
                .isInstanceOf(NegocioException.class).hasMessageContaining("saldo financiado");
        assertThat(pagamentos).isEmpty();
    }

    @Test
    @DisplayName("Trocar a condição com linha incompatível dá 422 com a lista e não muda nada")
    void trocaComLinhaIncompativel() {
        pagar(FormaPagamento.CARTAO_CREDITO, "1000.00", null);
        pagar(FormaPagamento.DINHEIRO, "500.00", null);

        assertThatThrownBy(() -> service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX))
                .isInstanceOfSatisfying(PagamentosIncompativeisException.class, e -> {
                    assertThat(e.getPagamentos()).hasSize(2);
                    assertThat(e.getPagamentos().get(0).forma()).isEqualTo(FormaPagamento.CARTAO_CREDITO);
                });
        assertThat(venda.getCondicaoPagamento()).isEqualTo(CondicaoPagamento.A_VISTA);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("11199.00");
    }

    @Test
    @DisplayName("Linhas compatíveis trocam de tipo junto com a condição")
    void trocaDeTipo() {
        pagar(FormaPagamento.DINHEIRO, "2000.00", null);

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        assertThat(pagamentos.get(0).getTipo()).isEqualTo(TipoPagamento.ENTRADA);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("12334.29");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.A_VISTA);
        assertThat(pagamentos.get(0).getTipo()).isEqualTo(TipoPagamento.PAGAMENTO);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("11199.00");
    }

    @Test
    @DisplayName("FINANCIADO acompanha a troca de parcelado e é apagado ao sair dele")
    void financiadoNaTrocaDeCondicao() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        service.financiar(1L);

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_13);
        assertThat(financiado().getParcelas()).isEqualTo(13);
        assertThat(financiado().getValor()).isEqualByComparingTo("12066.36");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);
        assertThat(pagamentos).isEmpty();
        assertThat(venda.getValorTotal()).isEqualByComparingTo("10079.10");
    }

    @Test
    @DisplayName("Financiar fora do parcelado é recusado; repetir não duplica a linha")
    void financiarRegras() {
        assertThatThrownBy(() -> service.financiar(1L))
                .isInstanceOf(NegocioException.class).hasMessageContaining("13x a 21x");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        service.financiar(1L);
        service.financiar(1L);

        assertThat(pagamentos).hasSize(1);
    }

    @Test
    @DisplayName("Simulação traz todas as condições, considerando as entradas lançadas")
    void simulacao() {
        pagar(FormaPagamento.DINHEIRO, "2000.00", null);

        List<SimulacaoPagamentoDTO> simulacao = service.simularPagamento(1L);

        assertThat(simulacao).hasSize(CondicaoPagamento.values().length);
        SimulacaoPagamentoDTO pix = opcao(simulacao, CondicaoPagamento.PIX);
        assertThat(pix.total()).isEqualByComparingTo("10079.10");
        assertThat(pix.economiaOuJuros()).isEqualByComparingTo("-1119.90");
        SimulacaoPagamentoDTO aVista = opcao(simulacao, CondicaoPagamento.A_VISTA);
        assertThat(aVista.total()).isEqualByComparingTo("11199.00");
        assertThat(aVista.parcelas()).isEqualTo(12);
        assertThat(aVista.valorParcela()).isEqualByComparingTo("933.25");
        SimulacaoPagamentoDTO parcelado = opcao(simulacao, CondicaoPagamento.PARCELADO_21);
        assertThat(parcelado.total()).isEqualByComparingTo("12334.29");
        assertThat(parcelado.valorParcela()).isEqualByComparingTo("492.11");
        assertThat(parcelado.economiaOuJuros()).isEqualByComparingTo("1135.29");
        // A simulação só consulta: a venda continua como estava.
        assertThat(venda.getCondicaoPagamento()).isEqualTo(CondicaoPagamento.A_VISTA);
        assertThat(venda.getItens().get(0).getValorUnitario()).isEqualByComparingTo("11199.00");
    }

    @Test
    @DisplayName("Simulação ignora na condição a linha que ela não aceita")
    void simulacaoIgnoraLinhaIncompativel() {
        pagar(FormaPagamento.CARTAO_CREDITO, "2000.00", null);

        assertThat(opcao(service.simularPagamento(1L), CondicaoPagamento.PARCELADO_21).total())
                .isEqualByComparingTo("12581.12");
    }

    @Test
    @DisplayName("Resposta traz valorItens, ajusteCondicao, saldo e o tipo dos pagamentos")
    void resposta() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        pagar(FormaPagamento.DINHEIRO, "2000.00", null);
        service.financiar(1L);

        VendaResponseDTO dto = VendaResponseDTO.from(venda, pagamentos);

        assertThat(dto.valorItens()).isEqualByComparingTo("11199.00");
        assertThat(dto.ajusteCondicao()).isEqualByComparingTo("1135.29");
        assertThat(dto.valorTotal()).isEqualByComparingTo("12334.29");
        assertThat(dto.valorParcela()).isEqualByComparingTo("492.11");
        assertThat(dto.saldo()).isEqualByComparingTo("0.00");
        assertThat(dto.pagamentos()).extracting(VendaResponseDTO.PagamentoDTO::tipo)
                .containsExactly(TipoPagamento.ENTRADA, TipoPagamento.FINANCIADO);
    }

    @Test
    @DisplayName("Tabela nova com o carrinho aberto: finalizar grava o novo total e recusa; depois do ajuste, finaliza")
    void finalizarComTabelaNova() {
        pagar(FormaPagamento.DINHEIRO, "11199.00", null);
        precos(TabelaPreco.REGULAR, "11999.00", null, "10799.10", "13480.00");

        assertThatThrownBy(() -> service.finalizar(1L, finalizar()))
                .isInstanceOf(TotalVendaAlteradoException.class)
                .hasMessageContaining("11199.00").hasMessageContaining("11999.00");
        assertThat(venda.getValorTotal()).isEqualByComparingTo("11999.00");
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.ABERTA);

        pagar(FormaPagamento.DINHEIRO, "800.00", null);
        assertThat(service.finalizar(1L, finalizar()).getStatus()).isEqualTo(StatusVenda.PENDENTE);
    }

    @Test
    @DisplayName("Tabela nova no parcelado: o financiado acompanha, mas a primeira tentativa de finalizar é recusada")
    void finalizarParceladoComTabelaNova() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        service.financiar(1L);
        precos(TabelaPreco.REGULAR, "11999.00", null, "10799.10", "13480.00");

        assertThatThrownBy(() -> service.finalizar(1L, finalizar())).isInstanceOf(TotalVendaAlteradoException.class);
        assertThat(financiado().getValor()).isEqualByComparingTo("13480.00");

        assertThat(service.finalizar(1L, finalizar()).getStatus()).isEqualTo(StatusVenda.PENDENTE);
    }

    @Test
    @DisplayName("Venda já finalizada não é reprecificada: finalizar de novo só recusa a transição")
    void finalizarDeNovoNaoReprecifica() {
        pagar(FormaPagamento.DINHEIRO, "11199.00", null);
        service.finalizar(1L, finalizar());
        precos(TabelaPreco.REGULAR, "11999.00", null, "10799.10", "13480.00");

        assertThatThrownBy(() -> service.finalizar(1L, finalizar())).isInstanceOf(TransicaoStatusInvalidaException.class);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("11199.00");
    }

    private void pagar(FormaPagamento forma, String valor, String voucher) {
        service.adicionarPagamento(1L, new PagamentoVendaRequestDTO(forma, new BigDecimal(valor), 1, voucher));
    }

    private PagamentoVenda financiado() {
        return pagamentos.stream().filter(p -> p.getTipo() == TipoPagamento.FINANCIADO).findFirst().orElseThrow();
    }

    private static SimulacaoPagamentoDTO opcao(List<SimulacaoPagamentoDTO> simulacao, CondicaoPagamento condicao) {
        return simulacao.stream().filter(s -> s.condicao() == condicao).findFirst().orElseThrow();
    }

    private static ItemVenda recarga() {
        return ItemVenda.builder().id(103L).categoria(CategoriaItemVenda.RECARGA).produtoId(12L)
                .descricaoProduto("Recarga").quantidade(BigDecimal.ONE)
                .estoque(EstoqueGeral.builder().id(12L).build())
                .valorUnitario(new BigDecimal("20.00")).build();
    }

    private void precos(TabelaPreco tabela, String cheio, String desconto, String pix, String parcelado21) {
        lenient().when(precoVigenteService.calcular(iphone, PLANO, tabela, null, null))
                .thenReturn(preco(tabela, cheio, cheio, desconto));
        lenient().when(precoVigenteService.calcular(iphone, PLANO, tabela, FormaPagamento.PIX, null))
                .thenReturn(preco(tabela, cheio, pix, desconto));
        lenient().when(precoVigenteService.calcular(iphone, PLANO, tabela, FormaPagamento.CARTAO_CREDITO, 21))
                .thenReturn(preco(tabela, cheio, parcelado21, desconto));
    }

    private static PrecoCalculado preco(TabelaPreco tabela, String cheio, String valor, String desconto) {
        BigDecimal v = new BigDecimal(valor);
        return new PrecoCalculado(1L, LocalDate.of(2026, 10, 29), "iPhone", tabela, GrupoPlano.CONTROLE_ALTO_VALOR,
                new BigDecimal(cheio), v, null, null, null, v, desconto == null ? null : new BigDecimal(desconto), List.of());
    }

    private static FinalizarVendaRequestDTO finalizar() {
        return new FinalizarVendaRequestDTO(new AutenticacaoUsuarioDTO("v@4r.com", "senha"));
    }
}
