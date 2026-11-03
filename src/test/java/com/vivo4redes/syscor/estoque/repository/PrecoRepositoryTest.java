package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.PlanoVivo;
import com.vivo4redes.syscor.estoque.model.PrecoItem;
import com.vivo4redes.syscor.estoque.model.ProdutoVivo;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V24: tabelas de preço por plano (JSONB de parcelas, CHECKs, consultas do serviço). */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PrecoRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private TabelaPrecoExecucaoRepository execucoes;
    @Autowired private PrecoItemRepository precos;
    @Autowired private PlanoVivoRepository planos;
    @Autowired private ProdutoVivoRepository produtos;
    @Autowired private EntityManager em;

    private TabelaPrecoExecucao execucao;

    @BeforeEach
    void setUp() {
        execucao = execucoes.save(TabelaPrecoExecucao.builder()
                .nomeArquivo("tabela.xlsx").vigenciaEm(LocalDate.of(2026, 11, 1))
                .totalLinhasProcessadas(0).totalProdutosAtualizados(0).totalNaoEncontrados(0).build());
    }

    @Test
    @DisplayName("Preço com parcelas em JSONB é gravado e lido de volta; busca por item, tabela e grupos")
    void precoComParcelas() {
        precos.save(preco(TabelaPreco.REGULAR, GrupoPlano.VIVO_V, "10149.00",
                Map.of(13, new BigDecimal("10500.10"), 21, new BigDecimal("10900.99"))));
        precos.save(preco(TabelaPreco.RENOVA, GrupoPlano.VIVO_V, "9999.00", null));
        em.flush();
        em.clear();

        List<PrecoItem> achados = precos.buscar(execucao.getId(), "IPHONE 17 PRO MAX 512GB",
                TabelaPreco.REGULAR, List.of(GrupoPlano.VIVO_V, GrupoPlano.SEM_PLANO));

        assertThat(achados).singleElement().satisfies(p -> {
            assertThat(p.getPreco()).isEqualByComparingTo("10149.00");
            assertThat(p.getParcelas()).containsEntry(13, new BigDecimal("10500.10"))
                    .containsEntry(21, new BigDecimal("10900.99"));
        });
        assertThat(precos.gruposDistintos(execucao.getId())).containsExactly(GrupoPlano.VIVO_V);
    }

    @Test
    @DisplayName("Plano é encontrado por nome normalizado ou por código; listagem filtra disponível para alta")
    void planos() {
        planos.save(PlanoVivo.builder().execucao(execucao).nomePlano("Vivo Controle 25GB").chavePlano("VIVO CONTROLE 25GB")
                .codSistema("ctl25").codSap("SAP25").grupoPlano(GrupoPlano.CONTROLE_ALTO_VALOR).disponivelAlta(true).build());
        planos.save(PlanoVivo.builder().execucao(execucao).nomePlano("Vivo Pré Antigo").chavePlano("VIVO PRE ANTIGO")
                .grupoPlano(GrupoPlano.PRE).disponivelAlta(false).build());

        assertThat(planos.buscarPorNomeOuCodigo(execucao.getId(), "VIVO CONTROLE 25GB")).hasSize(1);
        assertThat(planos.buscarPorNomeOuCodigo(execucao.getId(), "CTL25")).hasSize(1);
        assertThat(planos.buscarPorNomeOuCodigo(execucao.getId(), "SAP25")).hasSize(1);
        assertThat(planos.listar(execucao.getId(), "", null)).hasSize(2);
        assertThat(planos.listar(execucao.getId(), "", true)).extracting(PlanoVivo::getNomePlano)
                .containsExactly("Vivo Controle 25GB");
        assertThat(planos.listar(execucao.getId(), "PRE", null)).extracting(PlanoVivo::getNomePlano)
                .containsExactly("Vivo Pré Antigo");
    }

    @Test
    @DisplayName("Produto por Cod_Sap (sem diferenciar maiúsculas) e índice Cod_Sap -> chave do nome")
    void produtos() {
        produtos.save(ProdutoVivo.builder().execucao(execucao).codSap("acc123").nomeComercial("Fone XYZ")
                .chaveNome("FONE XYZ").build());

        assertThat(produtos.buscarPorCodSap(execucao.getId(), "ACC123")).hasSize(1);
        assertThat(produtos.indiceCodSap(execucao.getId())).singleElement()
                .satisfies(r -> assertThat(r).containsExactly("ACC123", "FONE XYZ"));
    }

    @Test
    @DisplayName("Vigência é gravada como DATE")
    void vigenciaComoData() {
        em.flush();
        em.clear();
        assertThat(execucoes.findTopByOrderByIdDesc()).get()
                .extracting(TabelaPrecoExecucao::getVigenciaEm).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    private PrecoItem preco(TabelaPreco tabela, GrupoPlano grupo, String valor, Map<Integer, BigDecimal> parcelas) {
        return PrecoItem.builder().execucao(execucao).aba("SMARTPHONES").tabela(tabela).grupoPlano(grupo)
                .nomeComercial("iPhone 17 Pro Max 512GB").chaveNome("IPHONE 17 PRO MAX 512GB")
                .preco(new BigDecimal(valor)).precoPix(new BigDecimal(valor)).parcelas(parcelas).build();
    }
}
