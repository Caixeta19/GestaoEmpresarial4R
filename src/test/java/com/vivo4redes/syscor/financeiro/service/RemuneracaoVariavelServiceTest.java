package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.dto.LinhaConsolidadoVivoDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ExecucaoRemuneracaoResponseDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ItemRemuneracaoResponseDTO;
import com.vivo4redes.syscor.financeiro.enums.StatusRemuneracao;
import com.vivo4redes.syscor.financeiro.model.ExecucaoRemuneracao;
import com.vivo4redes.syscor.financeiro.repository.ExecucaoRemuneracaoRepository;
import com.vivo4redes.syscor.financeiro.repository.ItemRemuneracaoRepository;
import com.vivo4redes.syscor.gestao.enums.SituacaoServico;
import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
import com.vivo4redes.syscor.gestao.repository.ProtocoloDocumentalRepository;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.service.VendaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Só venda lançada (CONCLUIDA) entra no cruzamento com o consolidado Vivo. */
@ExtendWith(MockitoExtension.class)
class RemuneracaoVariavelServiceTest {

    private static final String ACESSO_LANCADA = "11911110001";
    private static final String ACESSO_CANCELADA = "11911110002";
    private static final String ACESSO_ABERTA = "11911110003";
    private static final String ACESSO_LANCADA_FORA = "11911110004";

    @Mock private ConsolidadoVivoParser parser;
    @Mock private VendaService vendaService;
    @Mock private ProtocoloDocumentalRepository protocoloDocumentalRepository;
    @Mock private ExecucaoRemuneracaoRepository execucaoRepository;
    @Mock private ItemRemuneracaoRepository itemRepository;

    @InjectMocks
    private RemuneracaoVariavelService service;

    @BeforeEach
    void setUp() {
        when(vendaService.listarTodas()).thenReturn(List.of(
                venda(1L, StatusVenda.CONCLUIDA, ACESSO_LANCADA),
                venda(2L, StatusVenda.CANCELADA, ACESSO_CANCELADA),
                venda(3L, StatusVenda.ABERTA, ACESSO_ABERTA),
                venda(4L, StatusVenda.CONCLUIDA, ACESSO_LANCADA_FORA)));
        // Todas com protocolo válido: o que decide é o status da venda.
        lenient().when(protocoloDocumentalRepository.findByVendaId(anyLong())).thenReturn(Optional.of(
                ProtocoloDocumental.builder().numeroProtocoloGed("GED-1").situacaoServico(SituacaoServico.CONFIRMADO).build()));
        when(parser.parsear(any())).thenReturn(List.of(linha(ACESSO_LANCADA), linha(ACESSO_CANCELADA), linha(ACESSO_ABERTA)));
        when(execucaoRepository.save(any(ExecucaoRemuneracao.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Venda lançada libera a comissão; cancelada e carrinho aberto não são encontrados")
    void soVendaLancadaLibera() {
        ExecucaoRemuneracaoResponseDTO r = service.processarConsolidado(arquivo());

        Map<String, ItemRemuneracaoResponseDTO> porAcesso = r.itens().stream()
                .collect(Collectors.toMap(ItemRemuneracaoResponseDTO::numeroAcessoVivo, Function.identity()));

        assertThat(porAcesso.get(ACESSO_LANCADA).status()).isEqualTo(StatusRemuneracao.LIBERADO);
        assertThat(porAcesso.get(ACESSO_LANCADA).vendaId()).isEqualTo(1L);
        for (String acesso : List.of(ACESSO_CANCELADA, ACESSO_ABERTA)) {
            assertThat(porAcesso.get(acesso).status()).as(acesso).isEqualTo(StatusRemuneracao.PENDENTE_SISTEMA);
            assertThat(porAcesso.get(acesso).vendaId()).as(acesso).isNull();
        }
        assertThat(r.totalLiberados()).isEqualTo(1);
        assertThat(r.totalGlosados()).isEqualTo(2);
    }

    @Test
    @DisplayName("Repasse pendente da Vivo só aparece para venda lançada fora do consolidado")
    void pendenteVivoSoDeVendaLancada() {
        ExecucaoRemuneracaoResponseDTO r = service.processarConsolidado(arquivo());

        List<ItemRemuneracaoResponseDTO> pendentesVivo = r.itens().stream()
                .filter(i -> i.status() == StatusRemuneracao.PENDENTE_VIVO).toList();
        assertThat(pendentesVivo).extracting(ItemRemuneracaoResponseDTO::vendaId).containsExactly(4L);
        assertThat(r.totalPendentesVivo()).isEqualTo(1);
    }

    private static Venda venda(Long id, StatusVenda status, String acesso) {
        Venda venda = Venda.builder().id(id).status(status)
                .cliente(Cliente.builder().id(id).nome("Cliente " + id).cpfCnpj("0000000000" + id).build())
                .build();
        venda.adicionarItem(ItemVenda.builder().id(id * 10).categoria(CategoriaItemVenda.SERVICO_VIVO)
                .descricaoProduto("Plano").quantidade(BigDecimal.ONE).valorUnitario(BigDecimal.ZERO)
                .numeroAcesso(acesso).plano("CONTROLE").build());
        return venda;
    }

    private static LinhaConsolidadoVivoDTO linha(String acesso) {
        return new LinhaConsolidadoVivoDTO("T-" + acesso, acesso, "Cliente", "00000000000", "CONTROLE",
                new BigDecimal("50.00"), LocalDate.of(2026, 11, 1));
    }

    private static MockMultipartFile arquivo() {
        return new MockMultipartFile("arquivo", "consolidado.xlsx", null, new byte[0]);
    }
}
