package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.exception.CarrinhoNaoEditavelException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.response.VendaResponseDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Troca da condição de pagamento, com os valores do exemplo (Regular R$ 11.199,00, desconto R$ 500,00). */
@ExtendWith(MockitoExtension.class)
class VendaServiceCondicaoPagamentoTest {

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
    private EstoqueGeral fone;
    private ItemVenda aparelho;
    private ItemVenda acessorio;
    private ItemVenda servico;
    private ItemVenda recarga;
    private final List<PagamentoVenda> pagamentos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        iphone = EstoqueGeral.builder().id(10L).serial("356789012345678").modeloComercial("iPhone 17 Pro Max 512GB").build();
        fone = EstoqueGeral.builder().id(11L).serial("FONE-1").codigoAcessorio("ACC-FONE").build();
        aparelho = ItemVenda.builder().id(100L).categoria(CategoriaItemVenda.PRODUTO_VIVO).produtoId(10L)
                .descricaoProduto("iPhone").quantidade(BigDecimal.ONE).estoque(iphone).plano(PLANO)
                .grupoPlano(GrupoPlano.CONTROLE_ALTO_VALOR).tabelaAplicada(TabelaPreco.REGULAR)
                .valorUnitario(new BigDecimal("11199.00")).build();
        acessorio = ItemVenda.builder().id(101L).categoria(CategoriaItemVenda.ACESSORIO).produtoId(11L)
                .descricaoProduto("Fone").quantidade(BigDecimal.ONE).estoque(fone)
                .grupoPlano(GrupoPlano.SEM_PLANO).tabelaAplicada(TabelaPreco.REGULAR)
                .valorUnitario(new BigDecimal("199.90")).build();
        servico = ItemVenda.builder().id(102L).categoria(CategoriaItemVenda.SERVICO_VIVO)
                .descricaoProduto("Plano").quantidade(BigDecimal.ONE).valorChip(new BigDecimal("13.00"))
                .valorUnitario(BigDecimal.ZERO).build();
        recarga = ItemVenda.builder().id(103L).categoria(CategoriaItemVenda.RECARGA).produtoId(12L)
                .descricaoProduto("Recarga").quantidade(BigDecimal.ONE).estoque(EstoqueGeral.builder().id(12L).build())
                .valorUnitario(new BigDecimal("20.00")).build();

        venda = Venda.builder().id(1L).status(StatusVenda.ABERTA)
                .cliente(Cliente.builder().id(2L).nome("Cliente").build())
                .filial(Filial.builder().id(3L).nome("Loja").build())
                .usuario(Usuario.builder().id(4L).nome("Vendedor").build())
                .build();
        List.of(aparelho, acessorio, servico, recarga).forEach(venda::adicionarItem);
        venda.recalcularValorTotal();

        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagamentoVendaRepository.findByVendaIdOrderByIdAsc(1L)).thenAnswer(inv -> List.copyOf(pagamentos));

        regular(null, null, "11199.00");
        regular(FormaPagamento.PIX, null, "10079.10");
        regular(FormaPagamento.CARTAO_CREDITO, 13, "12066.36");
        regular(FormaPagamento.CARTAO_CREDITO, 21, "12581.12");
        renova(null, null, "10699.00");
        renova(FormaPagamento.PIX, null, "9579.10");
        renova(FormaPagamento.CARTAO_CREDITO, 21, "12081.12");
        acessorio(null, null, "199.90");
        acessorio(FormaPagamento.PIX, null, "179.91");
        acessorio(FormaPagamento.CARTAO_CREDITO, 13, "215.38");
        acessorio(FormaPagamento.CARTAO_CREDITO, 21, "224.57");
    }

    @Test
    @DisplayName("PIX reprecifica aparelho e acessório SEM_PLANO; serviço, chip e recarga não mudam")
    void pixReprecifica() {
        Venda v = service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);

        assertThat(v.getCondicaoPagamento()).isEqualTo(CondicaoPagamento.PIX);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("10079.10");
        assertThat(aparelho.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(acessorio.getValorUnitario()).isEqualByComparingTo("179.91");
        assertThat(acessorio.getGrupoPlano()).isEqualTo(GrupoPlano.SEM_PLANO);
        assertThat(servico.getValorUnitario()).isEqualByComparingTo("0");
        assertThat(servico.getValorChip()).isEqualByComparingTo("13.00");
        assertThat(recarga.getValorUnitario()).isEqualByComparingTo("20.00");
        assertThat(v.getValorTotal()).isEqualByComparingTo("10292.01");
        assertThat(v.getValorItens()).isEqualByComparingTo("11431.90");
        assertThat(v.getAjusteCondicao()).isEqualByComparingTo("-1139.89");
    }

    @Test
    @DisplayName("13x e 21x usam o total da parcela; voltar para A_VISTA restaura o preço cheio")
    void parceladoEVolta() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_13);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("12066.36");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("12581.12");
        assertThat(aparelho.getFormaPagamento()).isEqualTo(FormaPagamento.CARTAO_CREDITO);
        assertThat(aparelho.getParcelas()).isEqualTo(21);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("12838.69");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.A_VISTA);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("11199.00");
        assertThat(aparelho.getFormaPagamento()).isNull();
        assertThat(aparelho.getParcelas()).isNull();
        assertThat(venda.getValorTotal()).isEqualByComparingTo("11431.90");
    }

    @Test
    @DisplayName("Com Renova ligado, o aparelho usa a condição na RENOVA e o acessório fica na REGULAR")
    void renovaComCondicao() {
        venda.setRenova(true);

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("9579.10");
        assertThat(aparelho.getTabelaAplicada()).isEqualTo(TabelaPreco.RENOVA);
        assertThat(aparelho.getDescontoFabricante()).isEqualByComparingTo("500.00");
        assertThat(acessorio.getTabelaAplicada()).isEqualTo(TabelaPreco.REGULAR);

        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("12081.12");

        service.definirCondicaoPagamento(1L, CondicaoPagamento.A_VISTA);
        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("10699.00");
    }

    @Test
    @DisplayName("Ligar o Renova respeita a condição da venda, e o acessório não é reprecificado")
    void aplicarRenovaRespeitaCondicao() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);

        service.aplicarRenova(1L, true, "V-1");

        assertThat(aparelho.getValorUnitario()).isEqualByComparingTo("9579.10");
        assertThat(acessorio.getValorUnitario()).isEqualByComparingTo("179.91");
    }

    @Test
    @DisplayName("Item novo segue a condição da venda; forma e parcelas enviadas no item são ignoradas")
    void itemNovoSegueCondicao() {
        venda.setCondicaoPagamento(CondicaoPagamento.PIX);
        when(estoqueService.buscarPorId(11L)).thenReturn(fone);
        when(estoqueService.reservarNaVenda("FONE-1")).thenReturn(fone);

        service.adicionarItem(1L, ItemVendaRequestDTO.builder().categoria(CategoriaItemVenda.ACESSORIO).produtoId(11L)
                .descricaoProduto("Fone").quantidade(BigDecimal.ONE).imeiOuSerial("FONE-1")
                .formaPagamento(FormaPagamento.CARTAO_CREDITO).parcelas(13).build());

        ItemVenda novo = venda.getItens().get(venda.getItens().size() - 1);
        assertThat(novo.getValorUnitario()).isEqualByComparingTo("179.91");
        assertThat(novo.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(novo.getParcelas()).isNull();
    }

    @Test
    @DisplayName("Para finalizar, os pagamentos precisam fechar o total da condição escolhida")
    void pagamentosFechamOTotalDaCondicao() {
        pagamentos.add(pagamento(FormaPagamento.PIX, "11431.90"));
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);

        assertThatThrownBy(() -> service.finalizar(1L, finalizar()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("10292.01");

        pagamentos.clear();
        pagamentos.add(pagamento(FormaPagamento.PIX, "10292.01"));
        assertThat(service.finalizar(1L, finalizar()).getStatus()).isEqualTo(StatusVenda.CONCLUIDA);
    }

    @Test
    @DisplayName("Resposta traz condição, parcelas e valor da parcela; pagamento acima do novo total gera aviso")
    void respostaDaVenda() {
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PARCELADO_21);

        VendaResponseDTO parcelado = VendaResponseDTO.from(venda, pagamentos);
        assertThat(parcelado.condicaoPagamento()).isEqualTo(CondicaoPagamento.PARCELADO_21);
        assertThat(parcelado.parcelasCondicao()).isEqualTo(21);
        assertThat(parcelado.valorParcela()).isEqualByComparingTo("611.37");
        assertThat(parcelado.valorItens()).isEqualByComparingTo("11431.90");
        assertThat(parcelado.ajusteCondicao()).isEqualByComparingTo("1406.79");
        assertThat(parcelado.aviso()).isNull();

        pagamentos.add(pagamento(FormaPagamento.PIX, "11431.90"));
        service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX);
        VendaResponseDTO pix = VendaResponseDTO.from(venda, pagamentos);
        assertThat(pix.parcelasCondicao()).isEqualTo(1);
        assertThat(pix.valorParcela()).isEqualByComparingTo("10292.01");
        assertThat(pix.aviso()).contains("passam do total");
    }

    @Test
    @DisplayName("Venda fora de ABERTA não troca a condição")
    void vendaNaoEditavel() {
        venda.setStatus(StatusVenda.CONCLUIDA);

        assertThatThrownBy(() -> service.definirCondicaoPagamento(1L, CondicaoPagamento.PIX))
                .isInstanceOf(CarrinhoNaoEditavelException.class);
        assertThat(venda.getCondicaoPagamento()).isEqualTo(CondicaoPagamento.A_VISTA);
    }

    private void regular(FormaPagamento forma, Integer parcelas, String valor) {
        lenient().when(precoVigenteService.calcular(iphone, PLANO, TabelaPreco.REGULAR, forma, parcelas))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.CONTROLE_ALTO_VALOR, "11199.00", valor, null));
    }

    private void renova(FormaPagamento forma, Integer parcelas, String valor) {
        lenient().when(precoVigenteService.calcular(iphone, PLANO, TabelaPreco.RENOVA, forma, parcelas))
                .thenReturn(preco(TabelaPreco.RENOVA, GrupoPlano.CONTROLE_ALTO_VALOR, "10699.00", valor, "500.00"));
    }

    private void acessorio(FormaPagamento forma, Integer parcelas, String valor) {
        lenient().when(precoVigenteService.calcular(fone, null, TabelaPreco.REGULAR, forma, parcelas))
                .thenReturn(preco(TabelaPreco.REGULAR, GrupoPlano.SEM_PLANO, "199.90", valor, null));
    }

    private PagamentoVenda pagamento(FormaPagamento forma, String valor) {
        return PagamentoVenda.builder().id(5L).venda(venda).forma(forma).valor(new BigDecimal(valor)).parcelas(1).build();
    }

    private static FinalizarVendaRequestDTO finalizar() {
        return new FinalizarVendaRequestDTO(new AutenticacaoUsuarioDTO("v@4r.com", "senha"));
    }

    private static PrecoCalculado preco(TabelaPreco tabela, GrupoPlano grupo, String cheio, String valor, String desconto) {
        BigDecimal v = new BigDecimal(valor);
        return new PrecoCalculado(1L, LocalDate.of(2026, 10, 29), "Item", tabela, grupo,
                new BigDecimal(cheio), v, null, null, null, v, desconto == null ? null : new BigDecimal(desconto), List.of());
    }
}
