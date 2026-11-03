package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.EstoqueDisponivelResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoResponseDTO;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** V1: a busca do PDV usa buscarDisponiveisPorFiltro com o serial normalizado. */
@ExtendWith(MockitoExtension.class)
class EstoqueGeralServiceBuscaTest {

    @Mock
    private EstoqueGeralRepository repository;

    @Mock
    private LeitorTabular leitor;
    @Mock private PrecoVigenteService precoVigenteService;

    @InjectMocks
    private EstoqueGeralService service;

    @Test
    @DisplayName("buscarDisponiveis normaliza o IMEI e repassa filial e limite")
    void buscarDisponiveisNormalizaSerial() {
        EstoqueGeral unidade = EstoqueGeral.builder().id(1L).serial("356789012345678").build();
        when(repository.buscarDisponiveisPorFiltro(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(unidade));

        List<EstoqueDisponivelResponseDTO> resultado =
                service.buscarDisponiveis(" 3567 8901 2345678 ", " LOJA01 ", 10);

        assertThat(resultado).extracting(EstoqueDisponivelResponseDTO::serial).containsExactly("356789012345678");
        verify(repository).buscarDisponiveisPorFiltro(StatusEstoque.DISPONIVEL,
                "356789012345678", "3567 8901 2345678", "LOJA01", PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("buscarDisponiveis aceita busca e filial nulas e limita o tamanho da página")
    void buscarDisponiveisSemFiltros() {
        when(repository.buscarDisponiveisPorFiltro(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of());

        service.buscarDisponiveis(null, null, 1000);

        verify(repository).buscarDisponiveisPorFiltro(StatusEstoque.DISPONIVEL, "", "", "", PageRequest.of(0, 50));
    }

    @Test
    @DisplayName("listarCatalogo usa a busca por filtro e mantém o filtro de categoria")
    void listarCatalogoUsaBuscaPorFiltro() {
        EstoqueGeral aparelho = EstoqueGeral.builder().id(1L).serial("356789012345678").categoria("PRODUTO_VIVO").build();
        EstoqueGeral acessorio = EstoqueGeral.builder().id(2L).serial("ACC1").categoria("ACESSORIO").build();
        when(repository.buscarDisponiveisPorFiltro(eq(StatusEstoque.DISPONIVEL), eq("35678"), eq("35678"), eq(""), any()))
                .thenReturn(List.of(aparelho, acessorio));

        List<ProdutoResponseDTO> resultado = service.listarCatalogo("35678", CategoriaItemVenda.PRODUTO_VIVO);

        assertThat(resultado).hasSize(1);
    }
}
