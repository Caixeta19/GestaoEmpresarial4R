package com.vivo4redes.syscor.estoque.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.estoque.dto.response.ImportacaoTabelaPrecoResponseDTO;
import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.PrecoItem;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.PlanoVivoRepository;
import com.vivo4redes.syscor.estoque.repository.PrecoItemRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoVivoRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.vivo4redes.syscor.estoque.service.PlanilhaTelefonicaFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Importação da tabela de preços no PostgreSQL real (JSONB, lotes JDBC, referência no estoque). */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TabelaPrecoService.class, TabelaPrecoTelefonicaParser.class, PrecoVigenteService.class,
        TabelaPrecoServiceIntegracaoTest.Config.class})
class TabelaPrecoServiceIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired TabelaPrecoService service;
    @Autowired EstoqueGeralRepository estoque;
    @Autowired TabelaPrecoExecucaoRepository execucoes;
    @Autowired PrecoItemRepository precos;
    @Autowired PlanoVivoRepository planos;
    @Autowired ProdutoVivoRepository produtos;
    @Autowired EntityManager em;

    @Test
    @DisplayName("Upload grava preços, planos e produtos da execução e devolve o relatório")
    void gravaExecucaoERelatorio() {
        unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);

        ImportacaoTabelaPrecoResponseDTO r = service.importar(arquivo(0));

        assertThat(r.vigencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.vigenciaEm()).isEqualTo("01/11/2026");
        assertThat(r.linhasPorAba()).containsEntry("SMARTPHONES", 2).containsEntry("ELETRÔNICOS_LP_Não Conectados", 1);
        assertThat(r.totalPlanos()).isEqualTo(3);
        assertThat(r.totalProdutos()).isEqualTo(3);
        assertThat(r.totalPrecos()).isEqualTo(precos.countByExecucaoId(r.execucaoId()));
        assertThat(planos.countByExecucaoId(r.execucaoId())).isEqualTo(3);
        assertThat(produtos.countByExecucaoId(r.execucaoId())).isEqualTo(3);
        assertThat(execucoes.findTopByOrderByIdDesc()).get().extracting(e -> e.getId()).isEqualTo(r.execucaoId());
        // Galaxy e Fone estão na planilha, mas não no estoque.
        assertThat(r.planilhaSemEstoque()).isEqualTo(2);

        em.flush();
        em.clear();
        List<PrecoItem> vivoV = precos.buscar(r.execucaoId(), "IPHONE 17 PRO MAX 512GB", TabelaPreco.REGULAR,
                List.of(GrupoPlano.VIVO_V));
        assertThat(vivoV).singleElement().satisfies(p -> {
            assertThat(p.getPreco()).isEqualByComparingTo("10149.00");
            assertThat(p.getPrecoPix()).isEqualByComparingTo("9134.10");
            assertThat(p.getParcelas().get(13)).isEqualByComparingTo("10500.10");
        });
    }

    @Test
    @DisplayName("Referência no estoque: DISPONIVEL e RESERVADO recebem REGULAR/PRE; VENDIDO não muda")
    void referenciaNoEstoque() {
        EstoqueGeral disponivel = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        EstoqueGeral reservado = unidade("IP-2", "  iphone 17 pro max 512gb ", null, StatusEstoque.RESERVADO);
        EstoqueGeral vendido = unidade("IP-3", IPHONE, null, StatusEstoque.VENDIDO);
        vendido.setValorVenda(new BigDecimal("5000.00"));
        EstoqueGeral fone = unidade("FN-1", "Fone genérico", "acc-fone", StatusEstoque.DISPONIVEL); // pelo Cod_Sap
        EstoqueGeral semPreco = unidade("XX-1", "Aparelho Fora da Tabela", null, StatusEstoque.DISPONIVEL);
        estoque.saveAllAndFlush(List.of(vendido));

        ImportacaoTabelaPrecoResponseDTO r = service.importar(arquivo(0));

        em.flush();
        em.clear();
        assertThat(valor(disponivel)).isEqualByComparingTo("12909.00");
        assertThat(valor(reservado)).isEqualByComparingTo("12909.00");
        assertThat(valor(vendido)).isEqualByComparingTo("5000.00");
        assertThat(valor(fone)).isEqualByComparingTo("199.90");
        assertThat(valor(semPreco)).isNull();
        assertThat(r.unidadesAtualizadas()).isEqualTo(3);
        assertThat(r.estoqueSemPreco()).containsExactly("Aparelho Fora da Tabela");
    }

    @Test
    @DisplayName("Planilha inválida não grava execução nova (a vigente continua a anterior)")
    void falhaNaoGravaExecucao() {
        service.importar(arquivo(0));
        Long vigenteAntes = execucoes.findTopByOrderByIdDesc().orElseThrow().getId();

        MockMultipartFile invalida = new MockMultipartFile("arquivo", "x.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.importar(invalida)).isInstanceOf(NegocioException.class);

        assertThat(execucoes.findTopByOrderByIdDesc().orElseThrow().getId()).isEqualTo(vigenteAntes);
    }

    private EstoqueGeral unidade(String serial, String modelo, String codigoAcessorio, StatusEstoque status) {
        return estoque.saveAndFlush(EstoqueGeral.builder().serial(serial).modeloComercial(modelo)
                .codigoAcessorio(codigoAcessorio).status(status).build());
    }

    private BigDecimal valor(EstoqueGeral e) {
        return estoque.findById(e.getId()).orElseThrow().getValorVenda();
    }
}
