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
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
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
        EstoqueGeralService.class, LeitorTabular.class, ResolvedorTabela.class,
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
    @Autowired PrecoVigenteService precoVigente;
    @Autowired EstoqueGeralService estoqueService;
    @Autowired ResolvedorTabela resolvedor;
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

    // ------------------------------------------------------------------ cálculo do preço (regras 1–6 e 9)

    @Test
    @DisplayName("Preço por grupo do plano: PRÉ, VIVO V (pelo nome ou código do plano) e sem plano")
    void precoPorGrupoDoPlano() {
        EstoqueGeral iphone = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        service.importar(arquivo(0));

        assertThat(precoVigente.calcular(iphone, PLANO_PRE, TabelaPreco.REGULAR, null, null).valorAplicado())
                .isEqualByComparingTo("12909.00");
        PrecoCalculado semPlano = precoVigente.calcular(iphone, null, TabelaPreco.REGULAR, null, null);
        assertThat(semPlano.grupoPlano()).isEqualTo(GrupoPlano.PRE);
        assertThat(semPlano.valorAplicado()).isEqualByComparingTo("12909.00");
        PrecoCalculado vivoV = precoVigente.calcular(iphone, "  vivo v ilimitado ", TabelaPreco.REGULAR, null, null);
        assertThat(vivoV.grupoPlano()).isEqualTo(GrupoPlano.VIVO_V);
        assertThat(vivoV.valorAplicado()).isEqualByComparingTo("10149.00");
        assertThat(vivoV.vigencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(precoVigente.calcular(iphone, "VV01", TabelaPreco.REGULAR, null, null).valorAplicado())
                .isEqualByComparingTo("10149.00");
    }

    @Test
    @DisplayName("Forma de pagamento: PIX, crédito até 12x / débito / dinheiro e 13x–21x")
    void precoPorFormaDePagamento() {
        EstoqueGeral iphone = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        service.importar(arquivo(0));

        assertThat(valor(iphone, PLANO_VIVO_V, FormaPagamento.PIX, null)).isEqualByComparingTo("9134.10");
        assertThat(valor(iphone, PLANO_VIVO_V, FormaPagamento.CARTAO_CREDITO, 12)).isEqualByComparingTo("10149.00");
        assertThat(valor(iphone, PLANO_VIVO_V, FormaPagamento.CARTAO_DEBITO, null)).isEqualByComparingTo("10149.00");
        assertThat(valor(iphone, PLANO_VIVO_V, FormaPagamento.DINHEIRO, null)).isEqualByComparingTo("10149.00");
        assertThat(valor(iphone, PLANO_VIVO_V, FormaPagamento.CARTAO_CREDITO, 13)).isEqualByComparingTo("10500.10");
        assertThat(valor(iphone, PLANO_VIVO_V, FormaPagamento.CARTAO_CREDITO, 21)).isEqualByComparingTo("10900.99");
        assertThatThrownBy(() -> valor(iphone, PLANO_VIVO_V, FormaPagamento.CARTAO_CREDITO, 18))
                .isInstanceOf(NegocioException.class).hasMessageContaining("18x");
        assertThatThrownBy(() -> valor(iphone, PLANO_VIVO_V, FormaPagamento.CARTAO_CREDITO, 24))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Parcelamento inválido");
    }

    @Test
    @DisplayName("Renova x Regular, com a tabela decidida pelo ResolvedorTabela")
    void renovaXRegular() {
        EstoqueGeral iphone = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        service.importar(arquivo(0));

        TabelaPreco troca = resolvedor.resolver("Troca de aparelho");
        TabelaPreco novo = resolvedor.resolver("Alta de linha");
        assertThat(troca).isEqualTo(TabelaPreco.RENOVA);
        assertThat(novo).isEqualTo(TabelaPreco.REGULAR);
        assertThat(precoVigente.calcular(iphone, PLANO_VIVO_V, troca, null, null).valorAplicado())
                .isEqualByComparingTo("9500.00");
        assertThat(precoVigente.calcular(iphone, PLANO_VIVO_V, novo, null, null).valorAplicado())
                .isEqualByComparingTo("10149.00");
        assertThat(precoVigente.calcular(iphone, PLANO_PRE, troca, FormaPagamento.PIX, null).valorAplicado())
                .isEqualByComparingTo("10800.00");
    }

    @Test
    @DisplayName("Acessório sem plano: SEM_PLANO / REGULAR pelo Cod_Sap, qualquer que seja o plano e a tabela")
    void acessorioSemPlano() {
        EstoqueGeral fone = unidade("FN-1", "Nome diferente no estoque", "ACC-FONE", StatusEstoque.DISPONIVEL);
        service.importar(arquivo(0));

        PrecoCalculado p = precoVigente.calcular(fone, PLANO_VIVO_V, TabelaPreco.RENOVA, FormaPagamento.PIX, null);
        assertThat(p.nomeComercial()).isEqualTo(FONE);
        assertThat(p.grupoPlano()).isEqualTo(GrupoPlano.SEM_PLANO);
        assertThat(p.tabela()).isEqualTo(TabelaPreco.REGULAR);
        assertThat(p.valorAplicado()).isEqualByComparingTo("179.91");
    }

    @Test
    @DisplayName("Chip tem preço fixo de configuração")
    void chipFixo() {
        assertThat(precoVigente.precoChip()).isEqualByComparingTo("13.00");
    }

    @Test
    @DisplayName("Sem preço na tabela vigente: 422 com o nome do item; plano desconhecido também é recusado")
    void semPreco() {
        EstoqueGeral fora = unidade("XX-1", "Aparelho Fora da Tabela", null, StatusEstoque.DISPONIVEL);
        EstoqueGeral iphone = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        EstoqueGeral galaxy = unidade("GA-1", GALAXY, null, StatusEstoque.DISPONIVEL);
        service.importar(arquivo(0));

        assertThatThrownBy(() -> precoVigente.calcular(fora, null, TabelaPreco.REGULAR, null, null))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Item sem preço na tabela vigente (Aparelho Fora da Tabela).");
        // O plano existe (CONTROLE ALTO VALOR), mas a planilha não tem esse grupo para o iPhone.
        assertThatThrownBy(() -> precoVigente.calcular(iphone, PLANO_CONTROLE, TabelaPreco.REGULAR, null, null))
                .isInstanceOf(NegocioException.class).hasMessageContaining("sem preço na tabela vigente");
        // O Galaxy não tem preço no Renova.
        assertThatThrownBy(() -> precoVigente.calcular(galaxy, PLANO_PRE, TabelaPreco.RENOVA, null, null))
                .isInstanceOf(NegocioException.class).hasMessageContaining(GALAXY);
        assertThatThrownBy(() -> precoVigente.calcular(iphone, "Plano Que Não Existe", TabelaPreco.REGULAR, null, null))
                .isInstanceOf(NegocioException.class).hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("Sem nenhuma tabela importada, o cálculo é recusado")
    void semTabelaImportada() {
        EstoqueGeral iphone = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        assertThatThrownBy(() -> precoVigente.calcular(iphone, null, TabelaPreco.REGULAR, null, null))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Nenhuma tabela");
    }

    @Test
    @DisplayName("Estoque importado depois do upload recebe o preço vigente (VENDIDO não muda)")
    void estoqueImportadoDepoisRecebePreco() {
        service.importar(arquivo(0));
        EstoqueGeral vendido = unidade("IP-9", IPHONE, null, StatusEstoque.VENDIDO);
        vendido.setValorVenda(new BigDecimal("5000.00"));
        estoque.saveAndFlush(vendido);

        String csv = "imei;modelo;valor_venda\nIP-NOVO;" + IPHONE + ";1,00\nIP-9;" + IPHONE + ";1,00\nGA-NOVO;" + GALAXY + ";\n";
        estoqueService.importarRelatorioVivo(new MockMultipartFile("arquivo", "estoque.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        em.flush();
        em.clear();
        assertThat(estoque.findBySerialAndStatus("IP-NOVO", StatusEstoque.DISPONIVEL).orElseThrow().getValorVenda())
                .isEqualByComparingTo("12909.00");
        assertThat(estoque.findBySerialAndStatus("GA-NOVO", StatusEstoque.DISPONIVEL).orElseThrow().getValorVenda())
                .isEqualByComparingTo("1299.00");
        assertThat(valor(vendido)).isEqualByComparingTo("5000.00");
    }

    // ------------------------------------------------------------------ consultas da API

    @Test
    @DisplayName("Vigente, combo de planos e preço do item pela API do serviço")
    void consultasDaApi() {
        EstoqueGeral iphone = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        ImportacaoTabelaPrecoResponseDTO r = service.importar(arquivo(0));

        var vigente = service.buscarVigente();
        assertThat(vigente.execucaoId()).isEqualTo(r.execucaoId());
        assertThat(vigente.vigencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(vigente.totais().planos()).isEqualTo(3);
        assertThat(vigente.totais().precos()).isEqualTo(r.totalPrecos());

        assertThat(service.listarPlanos(null, null)).hasSize(3);
        assertThat(service.listarPlanos("vivo v", null)).extracting(p -> p.grupoPlano()).containsExactly(GrupoPlano.VIVO_V);
        assertThat(service.listarPlanos(null, false)).extracting(p -> p.nomePlano()).containsExactly(PLANO_PRE);

        var item = service.precoDoItem(iphone.getId(), PLANO_VIVO_V, null, FormaPagamento.PIX, null);
        assertThat(item.nomeComercial()).isEqualTo(IPHONE);
        assertThat(item.tabela()).isEqualTo(TabelaPreco.REGULAR);
        assertThat(item.preco()).isEqualByComparingTo("10149.00");
        assertThat(item.valorAplicado()).isEqualByComparingTo("9134.10");
    }

    @Test
    @DisplayName("Endpoints deprecados respondem a partir da tabela nova (oferta = grupo do plano)")
    void deprecadosSobreTabelaNova() {
        EstoqueGeral iphone = unidade("IP-1", IPHONE, null, StatusEstoque.DISPONIVEL);
        service.importar(arquivo(0));

        assertThat(service.listarOfertasDisponiveis()).containsExactly("PRE", "VIVO_V", "SEM_PLANO");
        assertThat(service.listarProdutosPorOferta("Vivo V")).extracting(PrecoItem::getNomeComercial)
                .containsExactly(GALAXY, IPHONE);
        assertThat(service.listarPrecosPorProduto(iphone.getId())).extracting(PrecoItem::getGrupoPlano)
                .containsExactlyInAnyOrder(GrupoPlano.PRE, GrupoPlano.VIVO_V);
        assertThatThrownBy(() -> service.listarProdutosPorOferta("Oferta Antiga de Marketing"))
                .isInstanceOf(NegocioException.class);
    }

    private BigDecimal valor(EstoqueGeral item, String plano, FormaPagamento forma, Integer parcelas) {
        return precoVigente.calcular(item, plano, TabelaPreco.REGULAR, forma, parcelas).valorAplicado();
    }

    private EstoqueGeral unidade(String serial, String modelo, String codigoAcessorio, StatusEstoque status) {
        return estoque.saveAndFlush(EstoqueGeral.builder().serial(serial).modeloComercial(modelo)
                .codigoAcessorio(codigoAcessorio).status(status).build());
    }

    private BigDecimal valor(EstoqueGeral e) {
        return estoque.findById(e.getId()).orElseThrow().getValorVenda();
    }
}
