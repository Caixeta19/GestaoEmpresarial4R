package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService;
import com.vivo4redes.syscor.estoque.service.ResolvedorTabela;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.SerialReservadoException;
import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.StatusVendaRequestDTO;
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
import java.time.Instant;
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

/** Acessório entra na venda pelo IMEI/serial lido: cada leitura é 1 unidade da linha do estoque. */
@ExtendWith(MockitoExtension.class)
class VendaServiceAcessorioSerialTest {

    private static final String SERIAL_CAPA = "7899716398012";
    private static final String IMEI_APARELHO = "356789012345678";
    private static final String SERIAL_SAP = "220252761000161";
    private static final String IMEI_RELOGIO = "351111222233334";
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

    private Venda venda;
    private EstoqueGeral capinhas;
    private EstoqueGeral aparelho;
    private final List<PagamentoVenda> pagamentos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        capinhas = EstoqueGeral.builder().id(20L).serial(SERIAL_CAPA).codigoAcessorio("'0007899716398012'")
                .modeloComercial("Capa Galaxy A15").quantidade(3).build();
        aparelho = EstoqueGeral.builder().id(10L).serial(IMEI_APARELHO).modeloComercial("Galaxy A15").build();
        venda = Venda.builder().id(1L).status(StatusVenda.ABERTA)
                .cliente(Cliente.builder().id(2L).nome("Cliente").build())
                .filial(Filial.builder().id(3L).nome("Loja").build())
                .build();

        lenient().when(vendaRepository.buscarComDetalhesPorId(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.buscarPorIdForUpdate(1L)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(estoqueService.buscarPorSerial(SERIAL_CAPA)).thenReturn(capinhas);
        lenient().when(estoqueService.buscarPorSerial(IMEI_APARELHO)).thenReturn(aparelho);
        // O estoque mockado aplica as regras reais do domínio na linha.
        lenient().when(estoqueService.reservarUnidadeNaVenda(SERIAL_CAPA)).thenAnswer(inv -> {
            capinhas.reservarUnidade();
            return capinhas;
        });
        lenient().when(estoqueService.confirmarVendaUnidade(capinhas)).thenAnswer(inv -> {
            capinhas.confirmarVendaUnidade();
            return capinhas;
        });
        BigDecimal preco = new BigDecimal("30.00");
        lenient().when(precoVigenteService.calcular(any(), any(), any(), any(), any())).thenReturn(new PrecoCalculado(
                1L, LocalDate.of(2026, 11, 1), "Capa Galaxy A15", TabelaPreco.REGULAR, GrupoPlano.SEM_PLANO,
                preco, preco, null, null, null, preco, null, List.of()));
        lenient().when(pagamentoVendaRepository.findByVendaIdOrderByIdAsc(1L)).thenAnswer(inv -> List.copyOf(pagamentos));
        lenient().when(usuarioService.autenticar(AUTH)).thenReturn(Usuario.builder().id(5L).nome("Vendedor").build());
    }

    @Test
    @DisplayName("Acessório pelo serial (sem produtoId): item com quantidade 1 e uma unidade RESERVADA na linha")
    void acessorioPeloSerial() {
        service.adicionarItem(1L, acessorio(SERIAL_CAPA));

        ItemVenda item = venda.getItens().get(0);
        assertThat(item.getQuantidade()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(item.getSerialImei()).isEqualTo(SERIAL_CAPA);
        assertThat(item.getProdutoId()).isEqualTo(20L);
        assertThat(item.getValorUnitario()).isEqualByComparingTo("30.00");
        assertThat(capinhas.getQuantidadeReservada()).isEqualTo(1);
        assertThat(capinhas.getStatus()).as("ainda sobram 2 capinhas").isEqualTo(StatusEstoque.DISPONIVEL);
        verify(estoqueService, never()).reservarNaVenda(any());
    }

    @Test
    @DisplayName("Duas capinhas iguais são duas leituras: duas linhas no carrinho, duas unidades reservadas")
    void duasLeiturasDoMesmoSku() {
        service.adicionarItem(1L, acessorio(SERIAL_CAPA));
        service.adicionarItem(1L, acessorio(SERIAL_CAPA));

        assertThat(venda.getItens()).hasSize(2);
        assertThat(capinhas.getQuantidadeReservada()).isEqualTo(2);
    }

    @Test
    @DisplayName("Última unidade de linha de quantidade 1 com o serial reservado: o serial vira RESERVADO")
    void ultimaUnidadeReserva() {
        capinhas.setQuantidade(1);

        service.adicionarItem(1L, acessorio(SERIAL_CAPA));

        assertThat(capinhas.getStatus()).isEqualTo(StatusEstoque.RESERVADO);
    }

    @Test
    @DisplayName("IMEI de aparelho na aba Acessório é recusado (422) sem reservar nada")
    void imeiDeAparelhoNoAcessorio() {
        assertThatThrownBy(() -> service.adicionarItem(1L, acessorio(IMEI_APARELHO)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Este IMEI é de um aparelho: lance na aba Produto Vivo.");

        verify(estoqueService, never()).reservarNaVenda(any());
        verify(estoqueService, never()).reservarUnidadeNaVenda(any());
        assertThat(venda.getItens()).isEmpty();
    }

    @Test
    @DisplayName("Serial de acessório na aba Produto Vivo é recusado (422) sem reservar nada")
    void serialDeAcessorioNoProdutoVivo() {
        assertThatThrownBy(() -> service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.PRODUTO_VIVO)
                .imeiOuSerial(SERIAL_CAPA)
                .quantidade(BigDecimal.ONE)
                .descricaoProduto("Capa Galaxy A15")
                .build()))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Este serial é de um acessório: lance na aba Acessório.");

        verify(estoqueService, never()).reservarNaVenda(any());
        verify(estoqueService, never()).reservarUnidadeNaVenda(any());
    }

    @Test
    @DisplayName("Capa com serial SAP (categoria Essenciais, sem código de acessório) entra na aba Acessório e reserva a unidade")
    void capaComSerialSapNaAbaAcessorio() {
        EstoqueGeral capa = EstoqueGeral.builder().id(30L).serial(SERIAL_SAP).categoria("Essenciais")
                .modeloComercial("CAPA OVVI SILICONE").build();
        when(estoqueService.buscarPorSerial(SERIAL_SAP)).thenReturn(capa);
        when(estoqueService.reservarNaVenda(SERIAL_SAP)).thenAnswer(inv -> {
            capa.reservar();
            return capa;
        });

        service.adicionarItem(1L, acessorio(SERIAL_SAP));

        ItemVenda item = venda.getItens().get(0);
        assertThat(item.getCategoria()).isEqualTo(CategoriaItemVenda.ACESSORIO);
        assertThat(item.getSerialImei()).isEqualTo(SERIAL_SAP);
        assertThat(capa.getStatus()).isEqualTo(StatusEstoque.RESERVADO);
        verify(estoqueService, never()).reservarUnidadeNaVenda(any());
    }

    @Test
    @DisplayName("Capa com serial SAP na aba Produto Vivo: 422 para lançar na aba Acessório")
    void capaComSerialSapNoProdutoVivo() {
        when(estoqueService.buscarPorSerial(SERIAL_SAP)).thenReturn(EstoqueGeral.builder().id(30L).serial(SERIAL_SAP)
                .categoria("Essenciais").build());

        assertThatThrownBy(() -> service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.PRODUTO_VIVO).imeiOuSerial(SERIAL_SAP).quantidade(BigDecimal.ONE).build()))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Este serial é de um acessório: lance na aba Acessório.");
    }

    @Test
    @DisplayName("Relógio LTE (categoria Bem Estar, com IMEI) na aba Acessório: 422 para lançar na aba Produto Vivo")
    void relogioLteNaAbaAcessorio() {
        when(estoqueService.buscarPorSerial(IMEI_RELOGIO)).thenReturn(EstoqueGeral.builder().id(31L).serial(IMEI_RELOGIO)
                .categoria("Bem Estar").modeloComercial("Galaxy Watch8 LTE 44mm").build());

        assertThatThrownBy(() -> service.adicionarItem(1L, acessorio(IMEI_RELOGIO)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Este IMEI é de um aparelho: lance na aba Produto Vivo.");
        verify(estoqueService, never()).reservarNaVenda(any());
    }

    @Test
    @DisplayName("Serial sem unidade livre, preso no carrinho de outra venda: 409 com o número dela")
    void serialReservadoEmOutraVenda() {
        capinhas.setQuantidade(1);
        capinhas.setQuantidadeReservada(1);
        capinhas.setStatus(StatusEstoque.RESERVADO);
        when(vendaRepository.buscarVendasAbertasComEstoque(20L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.adicionarItem(1L, acessorio(SERIAL_CAPA)))
                .isInstanceOf(SerialReservadoException.class)
                .hasMessage("Serial já está reservado na venda #7");

        verify(estoqueService, never()).reservarUnidadeNaVenda(any());
    }

    @Test
    @DisplayName("IMEI de aparelho reservado em outro carrinho também dá 409")
    void aparelhoReservadoEmOutraVenda() {
        aparelho.setStatus(StatusEstoque.RESERVADO);
        when(vendaRepository.buscarVendasAbertasComEstoque(10L)).thenReturn(List.of(8L));

        assertThatThrownBy(() -> service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.PRODUTO_VIVO)
                .imeiOuSerial(IMEI_APARELHO)
                .quantidade(BigDecimal.ONE)
                .descricaoProduto("Galaxy A15")
                .build()))
                .isInstanceOf(SerialReservadoException.class)
                .hasMessage("Serial já está reservado na venda #8");
    }

    @Test
    @DisplayName("Finalizar a venda: a unidade sai da linha e o serial da última unidade vira VENDIDO")
    void finalizarVendeOSerial() {
        capinhas.setQuantidade(1);
        service.adicionarItem(1L, acessorio(SERIAL_CAPA));
        pagamentos.add(PagamentoVenda.builder().id(50L).venda(venda).forma(FormaPagamento.DINHEIRO)
                .valor(new BigDecimal("30.00")).parcelas(1).build());

        service.finalizar(1L, new FinalizarVendaRequestDTO(AUTH));

        verify(estoqueService).confirmarVendaUnidade(capinhas);
        verify(estoqueService, never()).confirmarVenda(any());
        assertThat(capinhas.getStatus()).isEqualTo(StatusEstoque.VENDIDO);
        assertThat(capinhas.getQuantidade()).isZero();
        assertThat(capinhas.getQuantidadeReservada()).isZero();
        assertThat(capinhas.getDataVenda()).isNotNull();
    }

    @Test
    @DisplayName("Remover o acessório do carrinho solta só a unidade reservada")
    void removerSoltaUnidade() {
        venda.adicionarItem(itemAcessorio());

        service.removerItem(1L, 100L);

        verify(estoqueService).liberarUnidadeReservada(capinhas);
        verify(estoqueService, never()).liberar(any());
    }

    @Test
    @DisplayName("Cancelar venda concluída devolve a unidade vendida à linha")
    void cancelarConcluidaDevolveUnidade() {
        venda.setStatus(StatusVenda.CONCLUIDA);
        venda.setFinalizadoEm(Instant.now());
        venda.adicionarItem(itemAcessorio());

        service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH));

        verify(estoqueService).devolverUnidadeVendida(capinhas);
        verify(estoqueService, never()).liberarUnidadeReservada(any());
    }

    @Test
    @DisplayName("Cancelar carrinho aberto solta a unidade reservada")
    void cancelarCarrinhoSoltaUnidade() {
        venda.adicionarItem(itemAcessorio());

        service.avancarStatus(1L, new StatusVendaRequestDTO(StatusVenda.CANCELADA, AUTH));

        verify(estoqueService).liberarUnidadeReservada(capinhas);
        verify(estoqueService, never()).devolverUnidadeVendida(any());
    }

    @Test
    @DisplayName("Sem serial e sem produtoId é recusado")
    void semSerialESemProduto() {
        assertThatThrownBy(() -> service.adicionarItem(1L, ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.ACESSORIO)
                .quantidade(BigDecimal.ONE)
                .build()))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Informe o IMEI/Serial");
    }

    private static ItemVendaRequestDTO acessorio(String serial) {
        return ItemVendaRequestDTO.builder()
                .categoria(CategoriaItemVenda.ACESSORIO)
                .imeiOuSerial(serial)
                .quantidade(BigDecimal.ONE)
                .descricaoProduto("Capa Galaxy A15")
                .build();
    }

    private ItemVenda itemAcessorio() {
        return ItemVenda.builder()
                .id(100L)
                .categoria(CategoriaItemVenda.ACESSORIO)
                .produtoId(20L)
                .descricaoProduto("Capa Galaxy A15")
                .quantidade(BigDecimal.ONE)
                .valorUnitario(new BigDecimal("30.00"))
                .estoque(capinhas)
                .serialImei(SERIAL_CAPA)
                .build();
    }
}
