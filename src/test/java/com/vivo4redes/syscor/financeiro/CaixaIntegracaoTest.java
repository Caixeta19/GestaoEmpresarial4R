package com.vivo4redes.syscor.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.PrecoItem;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.PrecoItemRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.enums.TipoPessoa;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.ClienteRepository;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Caixa diário obrigatório (docs/financeiro_caixa.md), com filtros, login e JWT reais e dados sintéticos: a matriz de
 * perfis, o bloqueio da venda sem caixa aberto, o cálculo por sessão, fechar, conferir e reabrir.
 */
@SpringBootTest(properties = {
        "syscor.caixa.cargos-auditoria=AUDITORIA DE CAIXA",
        "syscor.caixa.cargos-financeiro=FINANCEIRO",
        "syscor.caixa.admins=ADMIN.CAIXA.TESTE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CaixaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String MODELO = "APARELHO SINTETICO CAIXA";
    private static final String PLANO = "CONTROLE_ALTO_VALOR";
    private static final String SENHA = "senha-de-teste";
    private static final String ADMIN = "ADMIN.CAIXA.TESTE";
    /** O login com BCrypt é lento: um token por usuário basta. */
    private static final Map<String, String> TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepository clientes;
    @Autowired FilialRepository filiais;
    @Autowired UsuarioRepository usuarios;
    @Autowired EstoqueGeralRepository estoque;
    @Autowired TabelaPrecoExecucaoRepository execucoes;
    @Autowired PrecoItemRepository precos;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private int n;
    private Long clienteId;
    private Long filialId;
    private String nomeFilial;
    private Usuario vendedor;
    private Usuario gerente;
    private Usuario gerenteOutraFilial;
    private Usuario auditor;
    private Usuario outroAuditor;
    private Usuario financeiro;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        n = SEQUENCIA.incrementAndGet();
        nomeFilial = "Filial Caixa " + n;
        clienteId = clientes.save(Cliente.builder().tipoPessoa(TipoPessoa.FISICA).nome("Cliente Caixa")
                .cpfCnpj("7" + String.format("%010d", n)).build()).getId();
        filialId = filiais.save(Filial.builder().codigo("CX" + n).nome(nomeFilial).build()).getId();
        vendedor = usuario("vendedor", "VENDEDOR", nomeFilial);
        gerente = usuario("gerente", "GERENTE DE VENDAS - JÚNIOR", nomeFilial);
        gerenteOutraFilial = usuario("gerenteoutra", "GERENTE DE VENDAS - PLENO", "Outra Filial " + n);
        auditor = usuario("auditor", "AUDITORIA DE CAIXA", "Auditoria");
        outroAuditor = usuario("auditor2", "AUDITORIA DE CAIXA", "Auditoria");
        financeiro = usuario("financeiro", "FINANCEIRO", "Matriz");
        admin = usuarios.findFirstByLoginIgnoreCase(ADMIN).orElseGet(() -> usuarios.save(Usuario.builder()
                .nome("Admin Caixa").login(ADMIN).email("admin.caixa@teste.local").senhaHash(encoder.encode(SENHA))
                .primeiroAcesso(false).filial("Matriz").cargo("ASSISTENTE DE DESENVOLVIMENTO DE SISTEMA").build()));
        tabelaVigente("1000.00");
    }

    // ------------------------------------------------------------------ abrir
    @Test
    @DisplayName("Gerente abre o caixa da própria filial: data de hoje, nome em maiúsculas, seis formas, quem abriu")
    void abrir() throws Exception {
        Resposta r = abrirCaixa(gerente, " caixa  principal ", "0.00", null);

        assertThat(r.status).isEqualTo(201);
        assertThat(r.corpo.get("status").asText()).isEqualTo("ABERTO");
        assertThat(r.corpo.get("dataAbertura").asText()).isEqualTo(LocalDate.now(ZONA).toString());
        assertThat(r.corpo.get("caixaPdv").asText()).isEqualTo("CAIXA PRINCIPAL");
        assertThat(r.corpo.get("abertoPor").asText()).isEqualTo(gerente.getNome());
        assertThat(r.corpo.get("conferencia").asText()).isEqualTo("AGUARDANDO");
        assertThat(r.corpo.get("formas")).hasSize(6);
    }

    @Test
    @DisplayName("O valor em dinheiro da abertura é obrigatório (0 vale, mas tem de ser informado); as outras formas abrem com 0")
    void dinheiroDaAberturaObrigatorio() throws Exception {
        Map<String, Object> semDinheiro = new HashMap<>();
        semDinheiro.put("filialId", filialId);
        semDinheiro.put("caixaPdv", "CAIXA 1");
        Resposta semLista = enviar(post("/financeiro/caixa/abrir"), semDinheiro, gerente);
        assertThat(semLista.status).isEqualTo(422);
        assertThat(semLista.corpo.get("mensagem").asText()).contains("dinheiro");

        semDinheiro.put("formasIniciais", List.of(Map.of("forma", "PIX", "valorAbertura", 0)));
        assertThat(enviar(post("/financeiro/caixa/abrir"), semDinheiro, gerente).status).as("só PIX informado").isEqualTo(422);

        Resposta zero = abrirCaixa(gerente, "CAIXA 1", "0.00", null);
        assertThat(zero.status).as("0 informado vale").isEqualTo(201);
        assertThat(forma(zero.corpo, "PIX", "abertura")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Abrir o mesmo caixa no mesmo dia: 409 CAIXA_JA_ABERTO, mesmo com outra grafia do nome")
    void abrirDuplicado() throws Exception {
        assertThat(abrirCaixa(gerente, "CAIXA 1", "0.00", null).status).isEqualTo(201);

        Resposta r = abrirCaixa(gerente, "  caixa 1", "0.00", null);

        assertThat(r.status).isEqualTo(409);
        assertThat(r.corpo.get("codigo").asText()).isEqualTo("CAIXA_JA_ABERTO");
    }

    @Test
    @DisplayName("Gerente de outra filial não abre: 403 CAIXA_SEM_PERMISSAO")
    void gerenteDeOutraFilial() throws Exception {
        Resposta r = abrirCaixa(gerenteOutraFilial, "CAIXA 1", "0.00", null);

        assertThat(r.status).isEqualTo(403);
        assertThat(r.corpo.get("codigo").asText()).isEqualTo("CAIXA_SEM_PERMISSAO");
    }

    @Test
    @DisplayName("Auditoria e admin abrem em qualquer filial; financeiro e vendedor não abrem")
    void quemPodeAbrir() throws Exception {
        assertThat(abrirCaixa(auditor, "CAIXA A", "0.00", null).status).isEqualTo(201);
        assertThat(abrirCaixa(admin, "CAIXA B", "0.00", null).status).isEqualTo(201);
        assertThat(abrirCaixa(financeiro, "CAIXA C", "0.00", null).status).isEqualTo(403);
        assertThat(abrirCaixa(vendedor, "CAIXA D", "0.00", null).status).isEqualTo(403);
    }

    @Test
    @DisplayName("Dinheiro diferente do fechamento anterior exige justificativa (mín. 10); igual, não")
    void aberturaSugerida() throws Exception {
        long ontem = CaixaTestSupport.abrirCaixa(jdbc, filialId, "CAIXA 1", LocalDate.now(ZONA).minusDays(1));
        jdbc.update("update tb_caixa_sessao set status = 'FECHADO', fechado_em = now() where id = ?", ontem);
        jdbc.update("update tb_forma_pagamento_caixa set valor_conferido = 150.00, diferenca = 0 where caixa_sessao_id = ? and forma = 'DINHEIRO'", ontem);

        Resposta sugestao = enviar(get("/financeiro/caixa/sugestao-abertura").param("filialId", filialId.toString())
                .param("caixaPdv", "caixa 1"), null, gerente);
        assertThat(sugestao.status).isEqualTo(200);
        assertThat(sugestao.corpo.get("dinheiro").decimalValue()).isEqualByComparingTo("150.00");

        assertThat(abrirCaixa(gerente, "CAIXA 1", "100.00", null).status).isEqualTo(422);
        assertThat(abrirCaixa(gerente, "CAIXA 1", "100.00", "curto").status).isEqualTo(422);
        Resposta comJustificativa = abrirCaixa(gerente, "CAIXA 1", "100.00", "Sangria feita pelo gerente ontem");
        assertThat(comJustificativa.status).isEqualTo(201);
        assertThat(comJustificativa.corpo.get("justificativaAbertura").asText()).contains("Sangria");

        assertThat(abrirCaixa(gerente, "CAIXA 2", "0.00", null).status).as("outro caixa, sem sugestão").isEqualTo(201);
    }

    // ------------------------------------------------------------------ bloqueio da venda
    @Test
    @DisplayName("Sem caixa aberto hoje o carrinho não abre: 409 CAIXA_NAO_ABERTO com a mensagem do doc")
    void vendaSemCaixa() throws Exception {
        Resposta r = enviar(post("/vendas"), corpoAbertura(), vendedor);

        assertThat(r.status).isEqualTo(409);
        assertThat(r.corpo.get("codigo").asText()).isEqualTo("CAIXA_NAO_ABERTO");
        assertThat(r.corpo.get("mensagem").asText())
                .isEqualTo("O caixa de hoje ainda não foi aberto. Peça ao gerente para abrir o caixa antes de lançar vendas.");
    }

    @Test
    @DisplayName("Nem o admin vende sem caixa aberto")
    void adminTambemBloqueado() throws Exception {
        assertThat(enviar(post("/vendas"), corpoAbertura(), admin).status).isEqualTo(409);
    }

    @Test
    @DisplayName("Caixa de ontem ainda aberto não libera a venda de hoje")
    void caixaDeOntemNaoLibera() throws Exception {
        CaixaTestSupport.abrirCaixa(jdbc, filialId, "CAIXA 1", LocalDate.now(ZONA).minusDays(1));

        Resposta r = enviar(post("/vendas"), corpoAbertura(), vendedor);

        assertThat(r.status).isEqualTo(409);
        assertThat(r.corpo.get("codigo").asText()).isEqualTo("CAIXA_NAO_ABERTO");
    }

    @Test
    @DisplayName("Caixa fechado no meio do carrinho: não adiciona item nem lança a venda")
    void caixaFechadoComCarrinhoPendente() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1");
        long venda = vendaProntaParaFinalizar(vendedor);
        jdbc.update("update tb_caixa_sessao set status = 'FECHADO', fechado_em = now() where id = ?", caixa);

        assertThat(finalizar(venda, vendedor, null).status).isEqualTo(409);
        assertThat(enviar(post("/vendas/{id}/itens", venda), itemAparelho(), vendedor).status).isEqualTo(409);
    }

    @Test
    @DisplayName("Venda lançada com caixa aberto grava caixa_sessao_id")
    void vendaGravaOCaixa() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1");
        long venda = vendaProntaParaFinalizar(vendedor);

        Resposta r = finalizar(venda, vendedor, null);

        assertThat(r.status).isEqualTo(200);
        assertThat(caixaDaVenda(venda)).isEqualTo(caixa);
    }

    @Test
    @DisplayName("Dois caixas abertos: sem caixaSessaoId é 422; com ele, a venda entra no caixa escolhido")
    void doisCaixasAbertos() throws Exception {
        abrirEObterId(gerente, "CAIXA 1");
        long segundo = abrirEObterId(gerente, "CAIXA 2");
        long venda = vendaProntaParaFinalizar(vendedor);

        assertThat(finalizar(venda, vendedor, null).status).isEqualTo(422);
        assertThat(finalizar(venda, vendedor, 999_999_999L).status).isEqualTo(409);
        assertThat(finalizar(venda, vendedor, segundo).status).isEqualTo(200);
        assertThat(caixaDaVenda(venda)).isEqualTo(segundo);
    }

    // ------------------------------------------------------------------ situação
    @Test
    @DisplayName("Permissões: só auditoria, financeiro e admin conferem e reabrem; o gerente fica na própria filial")
    void permissoes() throws Exception {
        for (Usuario u : List.of(auditor, financeiro, admin)) {
            JsonNode p = enviar(get("/financeiro/caixa/permissoes"), null, u).corpo;
            assertThat(p.get("conferirReabrir").asBoolean()).as(u.getLogin()).isTrue();
            assertThat(p.get("todasAsFiliais").asBoolean()).as(u.getLogin()).isTrue();
        }
        for (Usuario u : List.of(gerente, vendedor)) {
            JsonNode p = enviar(get("/financeiro/caixa/permissoes"), null, u).corpo;
            assertThat(p.get("conferirReabrir").asBoolean()).as(u.getLogin()).isFalse();
            assertThat(p.get("todasAsFiliais").asBoolean()).as(u.getLogin()).isFalse();
        }
        // A filial do cadastro vem junto (gerente da filial do teste); quem tem filial sem cadastro fica com null
        assertThat(enviar(get("/financeiro/caixa/permissoes"), null, gerente).corpo.get("filialId").asLong()).isEqualTo(filialId);
        assertThat(enviar(get("/financeiro/caixa/permissoes"), null, auditor).corpo.get("filialId").isNull()).isTrue();
    }

    @Test
    @DisplayName("Situação: sem caixa, aberto, e aviso de caixa anterior ainda aberto (sem bloquear)")
    void situacao() throws Exception {
        Resposta antes = situacao(vendedor);
        assertThat(antes.corpo.get("aberto").asBoolean()).isFalse();
        assertThat(antes.corpo.get("podeAbrir").asBoolean()).isFalse();
        assertThat(situacao(gerente).corpo.get("podeAbrir").asBoolean()).isTrue();

        CaixaTestSupport.abrirCaixa(jdbc, filialId, "CAIXA VELHO", LocalDate.now(ZONA).minusDays(2));
        long hoje = abrirEObterId(gerente, "CAIXA 1");

        Resposta depois = situacao(vendedor);
        assertThat(depois.corpo.get("aberto").asBoolean()).isTrue();
        assertThat(depois.corpo.get("caixaId").asLong()).isEqualTo(hoje);
        assertThat(depois.corpo.get("avisos")).hasSize(1);
        assertThat(depois.corpo.get("avisos").get(0).asText()).contains("dia anterior");
        assertThat(enviar(post("/vendas"), corpoAbertura(), vendedor).status).as("aviso não bloqueia").isEqualTo(201);
    }

    // ------------------------------------------------------------------ cálculo e fechamento
    @Test
    @DisplayName("Entradas por sessão: dinheiro fica na gaveta, PIX liquida (contabilizado 0) e venda cancelada sai da soma")
    void entradasPorSessao() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1", "50.00", null);
        long dinheiro = vendaProntaParaFinalizar(vendedor, "DINHEIRO");
        long pix = vendaProntaParaFinalizar(vendedor, "PIX");
        long cancelada = vendaProntaParaFinalizar(vendedor, "DINHEIRO");
        assertThat(finalizar(dinheiro, vendedor, null).status).isEqualTo(200);
        assertThat(finalizar(pix, vendedor, null).status).isEqualTo(200);
        assertThat(finalizar(cancelada, vendedor, null).status).isEqualTo(200);
        assertThat(cancelar(cancelada, vendedor).status).isEqualTo(200);

        JsonNode detalhe = enviar(get("/financeiro/caixa/{id}", caixa), null, gerente).corpo;

        assertThat(forma(detalhe, "DINHEIRO", "entradas")).isEqualByComparingTo("1000.00");
        assertThat(forma(detalhe, "DINHEIRO", "contabilizadoEsperado")).isEqualByComparingTo("1050.00");
        assertThat(forma(detalhe, "PIX", "entradas")).isEqualByComparingTo("1000.00");
        assertThat(forma(detalhe, "PIX", "contabilizadoEsperado")).isEqualByComparingTo("0.00");

        JsonNode movimento = enviar(get("/financeiro/caixa/{id}/movimento-detalhado", caixa), null, gerente).corpo;
        assertThat(movimento).hasSize(3);
        assertThat(movimento.findValuesAsText("statusVenda")).containsExactlyInAnyOrder("CONCLUIDA", "CONCLUIDA", "CANCELADA");
        assertThat(movimento.get(0).get("vendedor").asText()).isEqualTo(vendedor.getNome());
    }

    @Test
    @DisplayName("Fechar com dinheiro contado igual ao esperado: sem diferença e sem observação; formas que liquidam não precisam ser enviadas")
    void fecharSemDiferenca() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1", "50.00", null);
        long venda = vendaProntaParaFinalizar(vendedor, "DINHEIRO");
        assertThat(finalizar(venda, vendedor, null).status).isEqualTo(200);

        Resposta r = fechar(gerente, caixa, "1050.00", null);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("status").asText()).isEqualTo("FECHADO");
        assertThat(r.corpo.get("fechadoPor").asText()).isEqualTo(gerente.getNome());
        assertThat(r.corpo.get("conferencia").asText()).isEqualTo("AGUARDANDO");
        assertThat(r.corpo.get("totalDiferenca").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(r.corpo.get("totalContabilizado").decimalValue()).isEqualByComparingTo("1050.00");
    }

    @Test
    @DisplayName("Fechar com diferença sem observação: 422; com observação: 200 e a diferença fica registrada")
    void fecharComDiferenca() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1", "50.00", null);

        assertThat(fechar(gerente, caixa, "40.00", null).status).isEqualTo(422);
        assertThat(fechar(gerente, caixa, "40.00", "curto").status).isEqualTo(422);
        Resposta r = fechar(gerente, caixa, "40.00", "Faltou troco na gaveta, conferido com a equipe");

        assertThat(r.status).isEqualTo(200);
        assertThat(forma(r.corpo, "DINHEIRO", "diferenca")).isEqualByComparingTo("-10.00");
        assertThat(r.corpo.get("observacao").asText()).contains("Faltou troco");
    }

    @Test
    @DisplayName("Fechar com carrinho aberto na filial: 409 listando o número das vendas")
    void fecharComCarrinhoAberto() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1");
        Resposta carrinho = enviar(post("/vendas"), corpoAbertura(), vendedor);
        long numero = carrinho.corpo.get("numeroVenda").asLong();

        Resposta r = fechar(gerente, caixa, "0.00", null);

        assertThat(r.status).isEqualTo(409);
        assertThat(r.corpo.get("codigo").asText()).isEqualTo("CAIXA_COM_CARRINHO_ABERTO");
        assertThat(r.corpo.get("mensagem").asText()).contains("#" + numero);
    }

    @Test
    @DisplayName("Gerente de outra filial e vendedor não fecham o caixa")
    void fecharSemPerfil() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1");

        assertThat(fechar(gerenteOutraFilial, caixa, "0.00", null).status).isEqualTo(403);
        assertThat(fechar(vendedor, caixa, "0.00", null).status).isEqualTo(403);
        assertThat(fechar(auditor, caixa, "0.00", null).status).as("auditoria fecha em qualquer filial").isEqualTo(200);
    }

    // ------------------------------------------------------------------ conferir e reabrir
    @Test
    @DisplayName("Conferir: gerente não pode (403); quem fechou não confere o mesmo caixa; outro da auditoria e o financeiro conferem")
    void conferir() throws Exception {
        long caixa = abrirEObterId(auditor, "CAIXA 1");
        assertThat(fechar(auditor, caixa, "0.00", null).status).isEqualTo(200);

        assertThat(conferir(gerente, caixa).status).as("gerente").isEqualTo(403);
        Resposta mesmo = conferir(auditor, caixa);
        assertThat(mesmo.status).as("quem fechou").isEqualTo(403);
        assertThat(mesmo.corpo.get("mensagem").asText()).contains("Quem fechou");

        Resposta outro = conferir(outroAuditor, caixa);
        assertThat(outro.status).isEqualTo(200);
        assertThat(outro.corpo.get("conferencia").asText()).isEqualTo("CONFERIDO");
        assertThat(outro.corpo.get("conferidoPor").asText()).isEqualTo(outroAuditor.getNome());
        assertThat(conferir(financeiro, caixa).status).as("já conferido").isEqualTo(422);
    }

    @Test
    @DisplayName("O financeiro e o admin conferem; o admin que fechou também pode conferir")
    void conferirFinanceiroEAdmin() throws Exception {
        long primeiro = abrirEObterId(gerente, "CAIXA 1");
        assertThat(fechar(gerente, primeiro, "0.00", null).status).isEqualTo(200);
        assertThat(conferir(financeiro, primeiro).status).isEqualTo(200);

        long segundo = abrirEObterId(admin, "CAIXA 2");
        assertThat(fechar(admin, segundo, "0.00", null).status).isEqualTo(200);
        assertThat(conferir(admin, segundo).status).isEqualTo(200);
    }

    @Test
    @DisplayName("Reabrir: sem justificativa é 422; sem perfil é 403; com justificativa volta a ABERTO e zera a conferência")
    void reabrir() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1");
        assertThat(fechar(gerente, caixa, "0.00", null).status).isEqualTo(200);
        assertThat(conferir(financeiro, caixa).status).isEqualTo(200);

        assertThat(reabrir(financeiro, caixa, null).status).isEqualTo(422);
        assertThat(reabrir(financeiro, caixa, "curto").status).isEqualTo(422);
        assertThat(reabrir(gerente, caixa, "Gerente tentando reabrir o caixa").status).isEqualTo(403);

        Resposta r = reabrir(financeiro, caixa, "Venda lançada fora do caixa, ajuste necessário");
        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("status").asText()).isEqualTo("ABERTO");
        assertThat(r.corpo.get("conferencia").asText()).isEqualTo("AGUARDANDO");
        assertThat(r.corpo.get("reabertoPor").asText()).isEqualTo(financeiro.getNome());
        assertThat(r.corpo.get("totalContabilizado").isNull()).isTrue();

        JsonNode historico = enviar(get("/financeiro/caixa/{id}", caixa), null, financeiro).corpo.get("historico");
        assertThat(historico.findValuesAsText("acao")).containsExactly("ABRIU", "FECHOU", "CONFEROU", "REABRIU");
    }

    @Test
    @DisplayName("Cancelar venda lançada com o caixa fechado: 409 CAIXA_FECHADO; reaberto o caixa, cancela")
    void cancelarComCaixaFechado() throws Exception {
        long caixa = abrirEObterId(gerente, "CAIXA 1");
        long venda = vendaProntaParaFinalizar(vendedor);
        assertThat(finalizar(venda, vendedor, null).status).isEqualTo(200);
        assertThat(fechar(gerente, caixa, "1000.00", null).status).isEqualTo(200);

        Resposta bloqueada = cancelar(venda, vendedor);
        assertThat(bloqueada.status).isEqualTo(409);
        assertThat(bloqueada.corpo.get("codigo").asText()).isEqualTo("CAIXA_FECHADO");

        assertThat(reabrir(financeiro, caixa, "Cancelamento de venda do dia pedido pelo cliente").status).isEqualTo(200);
        assertThat(cancelar(venda, vendedor).status).isEqualTo(200);
    }

    // ------------------------------------------------------------------ listas e acesso
    @Test
    @DisplayName("Listas: o gerente só vê a própria filial; auditoria vê todas; o vendedor não vê nenhuma; filtros e paginação")
    void listasEAcesso() throws Exception {
        long meu = abrirEObterId(gerente, "CAIXA 1");
        long outraFilial = filiais.save(Filial.builder().codigo("CXO" + n).nome("Outra Filial " + n).build()).getId();
        long dePutra = CaixaTestSupport.abrirCaixa(jdbc, outraFilial, "CAIXA X", LocalDate.now(ZONA));

        JsonNode doGerente = enviar(get("/financeiro/caixa/abertos").param("busca", "CAIXA"), null, gerente).corpo;
        assertThat(doGerente.get("content").findValues("id").stream().map(JsonNode::asLong)).contains(meu).doesNotContain(dePutra);

        JsonNode daAuditoria = enviar(get("/financeiro/caixa/abertos").param("busca", String.valueOf(dePutra)), null, auditor).corpo;
        assertThat(daAuditoria.get("content").findValues("id").stream().map(JsonNode::asLong)).contains(dePutra);

        assertThat(enviar(get("/financeiro/caixa/abertos"), null, vendedor).corpo.get("totalElements").asLong()).isZero();
        assertThat(enviar(get("/financeiro/caixa/{id}", dePutra), null, gerente).status).isEqualTo(403);
        assertThat(enviar(get("/financeiro/caixa/{id}", meu), null, vendedor).status).isEqualTo(403);

        assertThat(fechar(gerente, meu, "0.00", null).status).isEqualTo(200);
        JsonNode fechados = enviar(get("/financeiro/caixa/fechados")
                .param("filialId", filialId.toString())
                .param("dataInicio", LocalDate.now(ZONA).toString())
                .param("conferencia", "AGUARDANDO").param("size", "1"), null, financeiro).corpo;
        assertThat(fechados.get("totalElements").asLong()).isEqualTo(1);
        assertThat(fechados.get("content").get(0).get("id").asLong()).isEqualTo(meu);
        assertThat(enviar(get("/financeiro/caixa/fechados").param("filialId", filialId.toString())
                .param("conferencia", "CONFERIDO"), null, financeiro).corpo.get("totalElements").asLong()).isZero();
    }

    // ------------------------------------------------------------------ apoio
    private Usuario usuario(String prefixo, String cargo, String filial) {
        return usuarios.save(Usuario.builder().nome(prefixo + " " + n).login(prefixo + "_" + n)
                .email(prefixo + "_" + n + "@teste.local").senhaHash(encoder.encode(SENHA)).primeiroAcesso(false)
                .filial(filial).cargo(cargo).build());
    }

    private Resposta abrirCaixa(Usuario quem, String caixaPdv, String dinheiro, String justificativa) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("filialId", filialId);
        corpo.put("caixaPdv", caixaPdv);
        corpo.put("formasIniciais", List.of(Map.of("forma", "DINHEIRO", "valorAbertura", new BigDecimal(dinheiro))));
        if (justificativa != null) corpo.put("justificativaAbertura", justificativa);
        return enviar(post("/financeiro/caixa/abrir"), corpo, quem);
    }

    private long abrirEObterId(Usuario quem, String caixaPdv) throws Exception {
        return abrirEObterId(quem, caixaPdv, "0.00", null);
    }

    private long abrirEObterId(Usuario quem, String caixaPdv, String dinheiro, String justificativa) throws Exception {
        Resposta r = abrirCaixa(quem, caixaPdv, dinheiro, justificativa);
        assertThat(r.status).as("abrir " + caixaPdv).isEqualTo(201);
        return r.corpo.get("id").asLong();
    }

    private Resposta fechar(Usuario quem, long caixa, String dinheiroContado, String observacao) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("formas", List.of(Map.of("forma", "DINHEIRO", "valorConferido", new BigDecimal(dinheiroContado))));
        if (observacao != null) corpo.put("observacao", observacao);
        return enviar(post("/financeiro/caixa/{id}/fechar", caixa), corpo, quem);
    }

    private Resposta conferir(Usuario quem, long caixa) throws Exception {
        return enviar(post("/financeiro/caixa/{id}/conferir", caixa), null, quem);
    }

    private Resposta reabrir(Usuario quem, long caixa, String justificativa) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        if (justificativa != null) corpo.put("justificativa", justificativa);
        return enviar(post("/financeiro/caixa/{id}/reabrir", caixa), corpo, quem);
    }

    private Resposta situacao(Usuario quem) throws Exception {
        Resposta r = enviar(get("/financeiro/caixa/situacao").param("filialId", filialId.toString()), null, quem);
        assertThat(r.status).isEqualTo(200);
        return r;
    }

    private static BigDecimal forma(JsonNode caixa, String forma, String campo) {
        for (JsonNode f : caixa.get("formas")) {
            if (forma.equals(f.get("forma").asText())) return f.get(campo).decimalValue();
        }
        throw new AssertionError("Forma " + forma + " não encontrada no caixa");
    }

    private long caixaDaVenda(long vendaId) {
        return jdbc.queryForObject("select caixa_sessao_id from vendas where id = ?", Long.class, vendaId);
    }

    private Map<String, Object> corpoAbertura() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("clienteId", clienteId);
        corpo.put("filialId", filialId);
        corpo.put("estoqueAvancado", false);
        return corpo;
    }

    private Map<String, Object> itemAparelho() {
        EstoqueGeral aparelho = estoque.save(EstoqueGeral.builder().serial("CX-" + n + "-" + System.nanoTime())
                .modeloComercial(MODELO).build());
        Map<String, Object> item = new HashMap<>();
        item.put("categoria", "PRODUTO_VIVO");
        item.put("produtoId", aparelho.getId());
        item.put("descricaoProduto", MODELO);
        item.put("quantidade", 1);
        item.put("imeiOuSerial", aparelho.getSerial());
        item.put("plano", PLANO);
        return item;
    }

    private long vendaProntaParaFinalizar(Usuario quem) throws Exception {
        return vendaProntaParaFinalizar(quem, "DINHEIRO");
    }

    /** Aparelho de 1.000,00 à vista, com o pagamento fechando o total. */
    private long vendaProntaParaFinalizar(Usuario quem, String forma) throws Exception {
        Resposta aberta = enviar(post("/vendas"), corpoAbertura(), quem);
        assertThat(aberta.status).as("abrir carrinho").isEqualTo(201);
        long id = aberta.corpo.get("id").asLong();
        assertThat(enviar(post("/vendas/{id}/itens", id), itemAparelho(), quem).status).as("item").isEqualTo(201);
        assertThat(enviar(post("/vendas/{id}/pagamentos", id), Map.of("forma", forma, "valor", "1000.00", "parcelas", 1), quem)
                .status).as("pagamento").isEqualTo(201);
        return id;
    }

    private Resposta finalizar(long id, Usuario quem, Long caixaSessaoId) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("autenticacaoVendedor", Map.of("email", quem.getEmail(), "senha", SENHA));
        if (caixaSessaoId != null) corpo.put("caixaSessaoId", caixaSessaoId);
        return enviar(patch("/vendas/{id}/finalizar", id), corpo, quem);
    }

    private Resposta cancelar(long id, Usuario quem) throws Exception {
        return enviar(patch("/vendas/{id}/status", id), Map.of("novoStatus", "CANCELADA",
                "autenticacaoUsuario", Map.of("email", quem.getEmail(), "senha", SENHA)), quem);
    }

    private String entrar(Usuario usuario) throws Exception {
        String guardado = TOKENS.get(usuario.getLogin());
        if (guardado != null) return guardado;
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", usuario.getLogin(), "senha", SENHA)))).andReturn();
        assertThat(r.getResponse().getStatus()).as("login de " + usuario.getLogin()).isEqualTo(200);
        String token = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
        TOKENS.put(usuario.getLogin(), token);
        return token;
    }

    private Resposta enviar(MockHttpServletRequestBuilder pedido, Object corpo, Usuario quem) throws Exception {
        pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + entrar(quem));
        if (corpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        }
        MvcResult r = mvc.perform(pedido).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Resposta(r.getResponse().getStatus(), texto.isBlank() ? json.createObjectNode() : json.readTree(texto));
    }

    /** A execução mais nova é a vigente. */
    private void tabelaVigente(String preco) {
        TabelaPrecoExecucao execucao = execucoes.save(TabelaPrecoExecucao.builder().nomeArquivo("sintetica.xlsx")
                .totalLinhasProcessadas(1).totalProdutosAtualizados(0).totalNaoEncontrados(0).build());
        precos.saveAll(List.of(PrecoItem.builder().execucao(execucao).aba("SMARTPHONES").tabela(TabelaPreco.REGULAR)
                .grupoPlano(GrupoPlano.CONTROLE_ALTO_VALOR).nomeComercial(MODELO).chaveNome(TextoUtil.chave(MODELO))
                .preco(new BigDecimal(preco)).precoPix(new BigDecimal(preco)).parcelas(Map.of()).build()));
    }

    private record Resposta(int status, JsonNode corpo) {
    }
}
