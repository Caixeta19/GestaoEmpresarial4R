package com.vivo4redes.syscor.estoque.service.diagnostico;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.enums.StatusInventario;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioExecucaoRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioItemRepository;
import com.vivo4redes.syscor.estoque.service.InventarioService;
import com.vivo4redes.syscor.exception.DiagnosticoIaException;
import com.vivo4redes.syscor.exception.NegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Diagnóstico com cliente falso: nenhuma chamada real à API. */
@ExtendWith(MockitoExtension.class)
class InventarioDiagnosticoServiceTest {

    @Mock private DiagnosticoIaClient cliente;
    @Mock private InventarioExecucaoRepository execucaoRepository;
    @Mock private InventarioItemRepository itemRepository;
    @Mock private EstoqueGeralRepository estoqueGeralRepository;
    @Mock private InventarioService inventarioService;
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private PlatformTransactionManager transacoes;

    private InventarioDiagnosticoService service;
    private InventarioExecucao execucao;
    private final List<InventarioItem> itens = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new InventarioDiagnosticoService(cliente, execucaoRepository, itemRepository, estoqueGeralRepository,
                inventarioService, jdbc, transacoes, 2);
        execucao = InventarioExecucao.builder().id(1L).status(StatusInventario.FECHADO).build();
        lenient().when(execucaoRepository.findById(1L)).thenReturn(Optional.of(execucao));
        lenient().when(itemRepository.findByExecucaoIdAndResultadoIn(eq(1L), anyCollection())).thenReturn(itens);
        lenient().when(inventarioService.outrosCentros(eq(1L), anyCollection())).thenReturn(Map.of());
        lenient().when(cliente.disponivel()).thenReturn(true);
    }

    @Test
    @DisplayName("Grava causa, confiança e ação por IMEI; IMEI que não veio no pedido é descartado; lotes do tamanho configurado")
    void gravaDiagnostico() {
        itens.add(item("111111111111111", ResultadoConciliacaoInventario.FALTA));
        itens.add(item("222222222222222", ResultadoConciliacaoInventario.SOBRA_FISICA));
        itens.add(item("333333333333333", ResultadoConciliacaoInventario.VENDIDO_EM_ESTOQUE));
        when(cliente.diagnosticar(anyList())).thenAnswer(inv -> {
            List<ItemParaDiagnostico> lote = inv.getArgument(0);
            List<RespostaDiagnosticoIa.Item> resposta = new ArrayList<>(lote.stream().map(i -> new RespostaDiagnosticoIa.Item(
                    i.imei(), CausaProvavelIa.POSSIVEL_PERDA_OU_FURTO, ConfiancaIa.MEDIA, "Registrar ocorrência.")).toList());
            resposta.add(new RespostaDiagnosticoIa.Item("999999999999999", CausaProvavelIa.OUTRA, ConfiancaIa.BAIXA, "Inventado"));
            return new RespostaDiagnosticoIa(resposta, "Resumo do lote.");
        });

        service.diagnosticar(1L);

        verify(cliente, times(2)).diagnosticar(anyList()); // 3 itens em lotes de 2
        assertThat(itens).allSatisfy(i -> {
            assertThat(i.getCausaProvavel()).isEqualTo("POSSIVEL_PERDA_OU_FURTO");
            assertThat(i.getConfiancaIa()).isEqualTo("MEDIA");
            assertThat(i.getDiagnosticadoEm()).isNotNull();
        });
        assertThat(execucao.getResumoIa()).contains("Lote 1: Resumo do lote.").contains("Lote 2: Resumo do lote.");
        assertThat(execucao.getResumoIa()).doesNotContain("Inventado");
    }

    @Test
    @DisplayName("Resposta inválida: tenta de novo; na segunda falha, 502 (DiagnosticoIaException)")
    void novaTentativaDepois502() {
        itens.add(item("111111111111111", ResultadoConciliacaoInventario.FALTA));
        when(cliente.diagnosticar(anyList()))
                .thenThrow(new RespostaIaInvalidaException("JSON quebrado"))
                .thenReturn(new RespostaDiagnosticoIa(null, "sem itens"));

        assertThatThrownBy(() -> service.diagnosticar(1L))
                .isInstanceOf(DiagnosticoIaException.class)
                .hasMessageContaining("duas vezes");
        verify(cliente, times(2)).diagnosticar(anyList());
    }

    @Test
    @DisplayName("Resposta inválida na 1ª tentativa e válida na 2ª: grava normalmente")
    void segundaTentativaValida() {
        itens.add(item("111111111111111", ResultadoConciliacaoInventario.FALTA));
        when(cliente.diagnosticar(anyList()))
                .thenThrow(new RespostaIaInvalidaException("JSON quebrado"))
                .thenReturn(new RespostaDiagnosticoIa(List.of(new RespostaDiagnosticoIa.Item("111111111111111",
                        CausaProvavelIa.TRANSFERENCIA_EM_TRANSITO, ConfiancaIa.ALTA, "Conferir a remessa.")), "Ok."));

        service.diagnosticar(1L);

        assertThat(itens.get(0).getCausaProvavel()).isEqualTo("TRANSFERENCIA_EM_TRANSITO");
    }

    @Test
    @DisplayName("Sem chave: não chama a API e registra \"Diagnóstico indisponível\"")
    void semChave() {
        itens.add(item("111111111111111", ResultadoConciliacaoInventario.FALTA));
        when(cliente.disponivel()).thenReturn(false);

        service.diagnosticar(1L);

        verify(cliente, never()).diagnosticar(anyList());
        assertThat(execucao.getResumoIa()).isEqualTo("Diagnóstico indisponível: configure a chave da API");
    }

    @Test
    @DisplayName("Ao fechar, falha da IA não sobe: fica registrada no resumo")
    void falhaAoFecharNaoSobe() {
        itens.add(item("111111111111111", ResultadoConciliacaoInventario.FALTA));
        when(cliente.diagnosticar(anyList())).thenThrow(new DiagnosticoIaException("API fora do ar"));

        service.diagnosticarAoFechar(1L);

        assertThat(execucao.getResumoIa()).startsWith("Diagnóstico falhou: API fora do ar");
    }

    @Test
    @DisplayName("Inventário em contagem não é diagnosticado (422)")
    void soFechado() {
        execucao.setStatus(StatusInventario.EM_CONTAGEM);

        assertThatThrownBy(() -> service.diagnosticar(1L)).isInstanceOf(NegocioException.class);
        verify(cliente, never()).diagnosticar(anyList());
    }

    private static InventarioItem item(String serial, ResultadoConciliacaoInventario resultado) {
        return InventarioItem.builder().serialNormalizado(serial).serialSap(serial).resultado(resultado).build();
    }
}
