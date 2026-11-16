package com.vivo4redes.syscor.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.financeiro.config.ConciliacaoExtratoProperties;
import com.vivo4redes.syscor.financeiro.enums.OrigemConciliacao;
import com.vivo4redes.syscor.financeiro.enums.ResultadoConciliacao;
import com.vivo4redes.syscor.financeiro.enums.SituacaoExtrato;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.extrato.ExtratoBancarioClient;
import com.vivo4redes.syscor.financeiro.extrato.ExtratoBancarioException;
import com.vivo4redes.syscor.financeiro.extrato.LancamentoExtrato;
import com.vivo4redes.syscor.financeiro.model.ExtratoLancamento;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import com.vivo4redes.syscor.financeiro.repository.ExtratoLancamentoRepository;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import com.vivo4redes.syscor.financeiro.service.ConciliacaoExtratoService;
import com.vivo4redes.syscor.financeiro.service.ConciliacaoStream;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Conciliação das contas a pagar pelo extrato do BB (docs/contas_pagar.md, Fase D), com banco real (Testcontainers),
 * login e JWT reais, dados sintéticos e o cliente do banco simulado: matching, sincronização, lock, vínculo manual,
 * reabertura e comprovante. Cada teste usa valores próprios, porque o banco é da classe toda.
 */
@SpringBootTest(properties = {
        "syscor.caixa.cargos-auditoria=AUDITORIA DE CAIXA",
        "syscor.caixa.cargos-financeiro=FINANCEIRO",
        "syscor.caixa.cargos-area=GERENTE DE AREA DE VENDAS",
        "syscor.caixa.cargos-gerente=GERENTE DE VENDAS - JUNIOR",
        "syscor.caixa.admins=ADMIN.EXTRATO.TESTE",
        "syscor.financeiro.bb-extrato.enabled=true",
        "syscor.financeiro.bb-extrato.agencia=297",
        "syscor.financeiro.bb-extrato.conta=1213",
        "syscor.financeiro.bb-extrato.sincronizacao-ms=3600000",
        "syscor.financeiro.conciliacao.codigos-historico-pagamento=144,501",
        "syscor.financeiro.conciliacao.proximidade-vencimento-dias=7"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ExtratoConciliacaoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";
    private static final String ADMIN = "ADMIN.EXTRATO.TESTE";
    // CNPJ sintético próprio de cada teste: a regra de divergência olha o CNPJ de qualquer conta aberta do fornecedor
    private static final long CHAVE_LOCK = 7_410_042_001L;
    private static final Map<String, String> TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    @MockBean ExtratoBancarioClient banco;
    @SpyBean ConciliacaoStream stream;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired ExtratoLancamentoRepository lancamentos;
    @Autowired TituloFinanceiroRepository titulos;
    @Autowired ConciliacaoExtratoService conciliacao;
    @Autowired ConciliacaoExtratoProperties regras;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private int n;
    private LocalDate hoje;
    private BigDecimal valor;
    private String fornecedor;
    private String doc;
    private Usuario financeiro;
    private Usuario auditor;
    private Usuario gerenteLoja;
    private Usuario vendedor;

    @BeforeEach
    void preparar() {
        n = SEQUENCIA.incrementAndGet();
        hoje = LocalDate.now(ZONA);
        valor = new BigDecimal(1000 + n * 10).setScale(2);   // cada teste tem a sua faixa de valores (+0 a +9)
        fornecedor = "Casa Sintetica " + n;
        doc = String.format("%014d", 70_000_000_000_000L + n);
        financeiro = usuario("financeiro", "FINANCEIRO");
        auditor = usuario("auditor", "AUDITORIA DE CAIXA");
        gerenteLoja = usuario("gerenteloja", "GERENTE DE VENDAS - JÚNIOR");
        vendedor = usuario("vendedor", "VENDEDOR");
        usuarios.findFirstByLoginIgnoreCase(ADMIN).orElseGet(() -> usuarios.save(Usuario.builder()
                .nome("Admin Extrato").login(ADMIN).email("admin.extrato@teste.local").senhaHash(encoder.encode(SENHA))
                .primeiroAcesso(false).filial("Matriz").cargo("ASSISTENTE DE DESENVOLVIMENTO DE SISTEMA").build()));
    }

    // ------------------------------------------------------------------ matching automático
    @Test
    @DisplayName("Conta única com reforço forte (CPF/CNPJ da contrapartida): baixa sozinha, vincula o lançamento, grava histórico e avisa o SSE")
    void baixaComDocumento() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(2), fornecedor, doc);
        ExtratoLancamento l = salvar(valor, hoje, doc, "PAGAMENTO", "OUTRO NOME", null);

        ConciliacaoExtratoService.Resultado r = conciliacao.conciliar(l.getId());

        assertThat(r.baixou()).isTrue();
        TituloFinanceiro t = titulos.findById(conta).orElseThrow();
        assertThat(t.getStatus()).isEqualTo(StatusTitulo.BAIXADO);
        assertThat(t.getValorPago()).isEqualByComparingTo(valor);
        assertThat(t.getOrigemBaixa().name()).isEqualTo("CONCILIACAO_BB");
        assertThat(t.getExtratoLancamentoId()).isEqualTo(l.getId());
        assertThat(t.getBancoPagador()).isEqualTo("BANCO_BRASIL");
        assertThat(t.getDataBaixa()).isEqualTo(hoje);
        ExtratoLancamento depois = lancamentos.findById(l.getId()).orElseThrow();
        assertThat(depois.getSituacao()).isEqualTo(SituacaoExtrato.CONCILIADO);
        assertThat(depois.getOrigem()).isEqualTo(OrigemConciliacao.AUTO);
        assertThat(depois.getTituloId()).isEqualTo(conta);
        assertThat(acoes(conta)).containsExactly("CRIOU", "BAIXA_AUTOMATICA");
        verify(stream).publicar(argThat(ev -> ev.contaId() == conta && ev.resultado() == ResultadoConciliacao.CONCILIADA
                && ev.statusTitulo() == StatusTitulo.BAIXADO && ev.valor().compareTo(valor) == 0));
    }

    @Test
    @DisplayName("Reforço médio (nome do fornecedor no histórico do banco, sem acento e sem diferenciar caixa) baixa sozinha")
    void baixaComNome() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(2), "Casa Sintética " + n, null);
        ExtratoLancamento l = salvar(valor, hoje, null, "PAGTO BOLETO", "CASA SINTETICA " + n, null);

        assertThat(conciliacao.conciliar(l.getId()).baixou()).isTrue();
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);
    }

    @Test
    @DisplayName("Reforço fraco (codigoHistorico de pagamento perto do vencimento) baixa; longe do vencimento só pede confirmação")
    void reforcoFraco() throws Exception {
        long perto = criarConta(valor, hoje.plusDays(3), fornecedor, null);
        ExtratoLancamento pertoL = salvar(valor, hoje, null, "SEM NOME", "NADA", 144);
        assertThat(conciliacao.conciliar(pertoL.getId()).baixou()).isTrue();
        assertThat(titulos.findById(perto).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);

        BigDecimal outroValor = valor.add(BigDecimal.ONE);
        long longe = criarConta(outroValor, hoje.plusDays(20), fornecedor + " B", null);
        ExtratoLancamento longeL = salvar(outroValor, hoje, null, "SEM NOME", "NADA", 144);
        assertThat(conciliacao.conciliar(longeL.getId()).situacao()).isEqualTo(SituacaoExtrato.AMBIGUO);
        assertThat(titulos.findById(longe).orElseThrow().getStatus()).isEqualTo(StatusTitulo.ABERTO);

        BigDecimal terceiroValor = valor.add(new BigDecimal("2"));
        long outroCodigo = criarConta(terceiroValor, hoje.plusDays(2), fornecedor + " C", null);
        ExtratoLancamento outroCodigoL = salvar(terceiroValor, hoje, null, "SEM NOME", "NADA", 999);
        assertThat(conciliacao.conciliar(outroCodigoL.getId()).situacao()).as("código fora da lista").isEqualTo(SituacaoExtrato.AMBIGUO);
        assertThat(titulos.findById(outroCodigo).orElseThrow().getStatus()).isEqualTo(StatusTitulo.ABERTO);
    }

    @Test
    @DisplayName("Conta única sem nenhum reforço vira AMBIGUO (não baixa); com exigir-reforco=false baixa sozinha")
    void semReforco() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(2), fornecedor, null);
        ExtratoLancamento l = salvar(valor, hoje, null, "TRANSF", "NINGUEM", null);

        ConciliacaoExtratoService.Resultado r = conciliacao.conciliar(l.getId());

        assertThat(r.situacao()).isEqualTo(SituacaoExtrato.AMBIGUO);
        assertThat(r.baixou()).isFalse();
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.ABERTO);
        assertThat(lancamentos.findById(l.getId()).orElseThrow().getTituloId()).isNull();

        regras.setExigirReforco(false);
        try {
            assertThat(conciliacao.conciliar(l.getId()).baixou()).as("reprocessado sem exigir reforço").isTrue();
            assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);
        } finally {
            regras.setExigirReforco(true);
        }
    }

    @Test
    @DisplayName("Duas contas candidatas: AMBIGUO, nenhuma baixa; as sugestões saem ordenadas pelo reforço e trazem o que casou")
    void doisCandidatos() throws Exception {
        long semReforco = criarConta(valor, hoje.plusDays(1), fornecedor + " X", null);
        long comDocumento = criarConta(valor, hoje.plusDays(6), fornecedor + " Y", doc);
        ExtratoLancamento l = salvar(valor, hoje, doc, "PAGAMENTO", "NINGUEM", null);

        ConciliacaoExtratoService.Resultado r = conciliacao.conciliar(l.getId());

        assertThat(r.situacao()).isEqualTo(SituacaoExtrato.AMBIGUO);
        assertThat(titulos.findById(semReforco).orElseThrow().getStatus()).isEqualTo(StatusTitulo.ABERTO);
        assertThat(titulos.findById(comDocumento).orElseThrow().getStatus()).isEqualTo(StatusTitulo.ABERTO);

        JsonNode item = lancamentoNaLista(financeiro, "AMBIGUO", l.getId());
        JsonNode sugestoes = item.get("sugestoes");
        assertThat(sugestoes).hasSize(2);
        assertThat(sugestoes.get(0).get("tituloId").asLong()).as("o do CNPJ vem primeiro").isEqualTo(comDocumento);
        assertThat(sugestoes.get(0).get("reforcos").toString()).contains("DOCUMENTO");
        assertThat(sugestoes.get(1).get("reforcos")).isEmpty();
        assertThat(sugestoes.get(1).get("tituloId").asLong()).isEqualTo(semReforco);
        assertThat(sugestoes.get(1).get("divergenciaValor").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Valor diferente com o CNPJ batendo: a conta vira DIVERGENTE (não baixa); vincular exige justificativa e o valor do banco manda")
    void divergenciaDeValor() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(2), fornecedor, doc);
        BigDecimal valorBanco = valor.add(new BigDecimal("5.00"));
        ExtratoLancamento l = salvar(valorBanco, hoje, doc, "PAGAMENTO", "NINGUEM", null);

        ConciliacaoExtratoService.Resultado r = conciliacao.conciliar(l.getId());

        assertThat(r.baixou()).isFalse();
        TituloFinanceiro t = titulos.findById(conta).orElseThrow();
        assertThat(t.getStatus()).isEqualTo(StatusTitulo.DIVERGENTE);
        ExtratoLancamento depois = lancamentos.findById(l.getId()).orElseThrow();
        assertThat(depois.getSituacao()).isEqualTo(SituacaoExtrato.AMBIGUO);
        assertThat(depois.getTituloId()).isEqualTo(conta);
        verify(stream).publicar(argThat(ev -> ev.contaId() == conta && ev.resultado() == ResultadoConciliacao.DIVERGENTE));

        JsonNode sugestao = lancamentoNaLista(financeiro, "AMBIGUO", l.getId()).get("sugestoes").get(0);
        assertThat(sugestao.get("divergenciaValor").asBoolean()).isTrue();
        assertThat(sugestao.get("valor").decimalValue()).isEqualByComparingTo(valor);

        Resposta semJustificativa = enviar(post("/financeiro/conciliacao/extrato/lancamentos/{id}/vincular", l.getId()),
                Map.of("tituloId", conta), financeiro);
        assertThat(semJustificativa.status).isEqualTo(422);
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.DIVERGENTE);

        Resposta ok = enviar(post("/financeiro/conciliacao/extrato/lancamentos/{id}/vincular", l.getId()),
                Map.of("tituloId", conta, "justificativa", "Juros e multa pagos no vencimento"), financeiro);
        assertThat(ok.status).isEqualTo(200);
        TituloFinanceiro baixada = titulos.findById(conta).orElseThrow();
        assertThat(baixada.getStatus()).isEqualTo(StatusTitulo.BAIXADO);
        assertThat(baixada.getValorPago()).as("o valor do banco manda").isEqualByComparingTo(valorBanco);
        assertThat(baixada.getJustificativaBaixa()).isEqualTo("Juros e multa pagos no vencimento");
        assertThat(acoes(conta)).containsExactly("CRIOU", "VINCULOU");
    }

    @Test
    @DisplayName("Centavos: 100.5 do banco casa com a conta de 100.50 (compareTo, não equals)")
    void centavos() throws Exception {
        BigDecimal conta50 = valor.add(new BigDecimal("0.50"));
        long conta = criarConta(conta50, hoje.plusDays(1), fornecedor, doc);
        ExtratoLancamento l = lancamentos.save(ExtratoLancamento.builder().idExterno("T-" + UUID.randomUUID())
                .dataLancamento(hoje).valor(new BigDecimal(valor.intValue() + ".5")).contraparteDocumento(doc)
                .build());

        assertThat(conciliacao.conciliar(l.getId()).baixou()).isTrue();
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);
    }

    @Test
    @DisplayName("Conta cancelada, baixada ou fora da janela de 45 dias não é candidata: o lançamento fica SEM_CORRESPONDENCIA")
    void naoCandidatas() throws Exception {
        long cancelada = criarConta(valor, hoje.plusDays(1), fornecedor + " A", doc);
        assertThat(enviar(post("/financeiro/contas-pagar/{id}/cancelar", cancelada),
                Map.of("motivo", "Cadastrada em duplicidade"), financeiro).status).isEqualTo(200);
        long baixada = criarConta(valor, hoje.plusDays(1), fornecedor + " B", doc);
        assertThat(enviar(post("/financeiro/contas-pagar/{id}/baixa-manual", baixada),
                Map.of("dataPagamento", hoje.toString(), "valorPago", valor, "banco", "Caixa",
                        "justificativa", "Pago no balcão da loja"), financeiro).status).isEqualTo(200);
        long distante = criarConta(valor, hoje.plusDays(46), fornecedor + " C", doc);
        ExtratoLancamento l = salvar(valor, hoje, doc, "PAGAMENTO", fornecedor.toUpperCase(), null);

        ConciliacaoExtratoService.Resultado r = conciliacao.conciliar(l.getId());

        assertThat(r.situacao()).isEqualTo(SituacaoExtrato.SEM_CORRESPONDENCIA);
        assertThat(titulos.findById(distante).orElseThrow().getStatus()).isEqualTo(StatusTitulo.ABERTO);
        assertThat(titulos.findById(cancelada).orElseThrow().getStatus()).isEqualTo(StatusTitulo.CANCELADO);
        assertThat(titulos.findById(baixada).orElseThrow().getExtratoLancamentoId()).isNull();
    }

    @Test
    @DisplayName("Idempotência: conciliar de novo um lançamento conciliado não baixa nada outra vez")
    void idempotencia() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(1), fornecedor, doc);
        ExtratoLancamento l = salvar(valor, hoje, doc, "PAGAMENTO", "X", null);

        assertThat(conciliacao.conciliar(l.getId()).baixou()).isTrue();
        assertThat(conciliacao.conciliar(l.getId()).baixou()).isFalse();
        assertThat(acoes(conta).stream().filter("BAIXA_AUTOMATICA"::equals)).hasSize(1);
    }

    @Test
    @DisplayName("O banco barra por constraint: um lançamento só baixa uma conta, uma conta só tem um lançamento, id externo é único")
    void garantiasDoBanco() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(1), fornecedor, doc);
        ExtratoLancamento l = salvar(valor, hoje, doc, "PAGAMENTO", "X", null);
        assertThat(conciliacao.conciliar(l.getId()).baixou()).isTrue();
        ExtratoLancamento outro = salvar(valor, hoje, null, "OUTRO", "OUTRO", null);
        long outraConta = criarConta(valor.add(BigDecimal.ONE), hoje.plusDays(1), fornecedor + " Z", null);

        assertThatThrownBy(() -> jdbc.update(
                "update tb_extrato_lancamento set situacao = 'CONCILIADO', titulo_id = ? where id = ?", conta, outro.getId()))
                .hasMessageContaining("uk_extrato_lancamento_conciliado_titulo");
        assertThatThrownBy(() -> jdbc.update(
                "update tb_titulo_financeiro set extrato_lancamento_id = ? where id = ?", l.getId(), outraConta))
                .hasMessageContaining("uk_titulo_extrato_lancamento");
        assertThatThrownBy(() -> lancamentos.saveAndFlush(ExtratoLancamento.builder().idExterno(l.getIdExterno())
                .dataLancamento(hoje).valor(valor).build())).hasMessageContaining("uk_extrato_lancamento_externo");
    }

    // ------------------------------------------------------------------ sincronização
    @Test
    @DisplayName("Sincronizar: grava os débitos, concilia e responde com novos e conciliados; repetir não duplica nem baixa de novo")
    void sincronizar() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(2), fornecedor, doc);
        String idCasa = "SYNC-A-" + n;
        String idSolto = "SYNC-B-" + n;
        BigDecimal valorSolto = valor.add(new BigDecimal("3.00"));
        List<LancamentoExtrato> lista = List.of(
                novo(idCasa, valor, hoje, doc, "PAGAMENTO", "Z"),
                novo(idSolto, valorSolto, hoje, null, "TARIFA", "TARIFA BANCARIA"),
                novo(idCasa, valor, hoje, doc, "PAGAMENTO", "Z"));   // repetido na mesma resposta
        when(banco.listarDebitos(any(), any())).thenReturn(lista);

        Resposta primeira = enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);

        assertThat(primeira.status).isEqualTo(200);
        assertThat(primeira.corpo.get("status").asText()).isEqualTo("OK");
        assertThat(primeira.corpo.get("lancamentosNovos").asInt()).isEqualTo(2);
        assertThat(primeira.corpo.get("conciliados").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);
        assertThat(lancamentos.existsByProviderAndIdExterno("BANCO_BRASIL", idSolto)).isTrue();

        Resposta segunda = enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
        assertThat(segunda.corpo.get("lancamentosNovos").asInt()).as("nada novo").isZero();
        assertThat(jdbc.queryForObject("select count(*) from tb_extrato_lancamento where id_externo = ?", Integer.class, idCasa))
                .isEqualTo(1);
        assertThat(acoes(conta).stream().filter("BAIXA_AUTOMATICA"::equals)).hasSize(1);

        ArgumentCaptor<LocalDate> de = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> ate = ArgumentCaptor.forClass(LocalDate.class);
        verify(banco, times(2)).listarDebitos(de.capture(), ate.capture());
        assertThat(ate.getValue()).isEqualTo(hoje);
        assertThat(de.getValue()).as("janela de 7 dias").isEqualTo(hoje.minusDays(7));

        assertThat(jdbc.queryForObject("select count(*) from tb_extrato_sincronizacao where status = 'OK' and origem = 'MANUAL'",
                Integer.class)).isGreaterThanOrEqualTo(2);
        JsonNode resumo = enviar(get("/financeiro/contas-pagar/resumo"), null, financeiro).corpo;
        assertThat(resumo.get("canalBancario").asText()).isEqualTo("CONECTADO");
        assertThat(resumo.get("ultimaSincronizacao").isNull()).isFalse();
        assertThat(resumo.get("conciliadas").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Falha do banco: registra ERRO, não grava nem baixa nada, o canal fica COM_ERRO e a próxima rodada se recupera")
    void falhaDoBanco() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(2), fornecedor, doc);
        String id = "FALHA-" + n;
        when(banco.listarDebitos(any(), any())).thenThrow(new ExtratoBancarioException("O banco respondeu HTTP 503 na consulta do extrato depois de 3 tentativas."));

        Resposta r = enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("status").asText()).isEqualTo("ERRO");
        assertThat(r.corpo.get("erro").asText()).contains("HTTP 503");
        assertThat(r.corpo.get("lancamentosNovos").asInt()).isZero();
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.ABERTO);
        assertThat(enviar(get("/financeiro/contas-pagar/resumo"), null, financeiro).corpo.get("canalBancario").asText())
                .isEqualTo("COM_ERRO");

        doThrow(new IllegalStateException("detalhe interno com segredo-xyz")).when(banco).listarDebitos(any(), any());
        Resposta inesperada = enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
        assertThat(inesperada.corpo.get("status").asText()).isEqualTo("ERRO");
        assertThat(inesperada.corpo.get("erro").asText()).contains("Falha inesperada").doesNotContain("segredo-xyz");

        doReturn(List.of(novo(id, valor, hoje, doc, "PAGAMENTO", "Z"))).when(banco).listarDebitos(any(), any());
        Resposta recuperada = enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
        assertThat(recuperada.corpo.get("status").asText()).isEqualTo("OK");
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);
        assertThat(enviar(get("/financeiro/contas-pagar/resumo"), null, financeiro).corpo.get("canalBancario").asText())
                .isEqualTo("CONECTADO");
    }

    @Test
    @DisplayName("Uma execução por vez: com o advisory lock ocupado, sincronizar responde 409 EXTRATO_SINCRONIZANDO sem chamar o banco")
    void lockEvitaDuasExecucoes() throws Exception {
        try (Connection outra = jdbc.getDataSource().getConnection()) {
            outra.createStatement().execute("select pg_advisory_lock(" + CHAVE_LOCK + ")");
            try {
                Resposta r = enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
                assertThat(r.status).isEqualTo(409);
                assertThat(r.corpo.get("codigo").asText()).isEqualTo("EXTRATO_SINCRONIZANDO");
                verify(banco, never()).listarDebitos(any(), any());
            } finally {
                outra.createStatement().execute("select pg_advisory_unlock(" + CHAVE_LOCK + ")");
            }
        }
        assertThat(enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro).status)
                .as("solto o lock, volta a funcionar").isEqualTo(200);
    }

    @Test
    @DisplayName("A fila é reprocessada na rodada seguinte: conta cadastrada depois do pagamento concilia o lançamento que estava sem correspondência")
    void contaCadastradaDepois() throws Exception {
        String id = "DEPOIS-" + n;
        when(banco.listarDebitos(any(), any())).thenReturn(List.of(novo(id, valor, hoje, doc, "PAGAMENTO", "Z")));
        enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
        assertThat(situacaoDe(id)).isEqualTo("SEM_CORRESPONDENCIA");

        long conta = criarConta(valor, hoje.plusDays(3), fornecedor, doc);
        Resposta r = enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);

        assertThat(r.corpo.get("conciliados").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(situacaoDe(id)).isEqualTo("CONCILIADO");
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);
    }

    // ------------------------------------------------------------------ vincular e ignorar
    @Test
    @DisplayName("Vincular à mão: baixa a conta (origem do extrato, quem confirmou), lançamento MANUAL; não vincula duas vezes nem conta cancelada")
    void vincular() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(2), fornecedor, null);
        ExtratoLancamento l = salvar(valor, hoje, null, "TRANSF", "NINGUEM", null);
        conciliacao.conciliar(l.getId());    // sem reforço: fica AMBIGUO
        String url = "/financeiro/conciliacao/extrato/lancamentos/{id}/vincular";

        Resposta r = enviar(post(url, l.getId()), Map.of("tituloId", conta), financeiro);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("situacao").asText()).isEqualTo("CONCILIADO");
        assertThat(r.corpo.get("origem").asText()).isEqualTo("MANUAL");
        TituloFinanceiro t = titulos.findById(conta).orElseThrow();
        assertThat(t.getStatus()).isEqualTo(StatusTitulo.BAIXADO);
        assertThat(t.getValorPago()).isEqualByComparingTo(valor);
        assertThat(t.getExtratoLancamentoId()).isEqualTo(l.getId());
        assertThat(acoes(conta)).containsExactly("CRIOU", "VINCULOU");
        assertThat(enviar(post(url, l.getId()), Map.of("tituloId", conta), financeiro).status)
                .as("lançamento já conciliado").isEqualTo(409);

        BigDecimal outroValor = valor.add(BigDecimal.ONE);
        long cancelada = criarConta(outroValor, hoje.plusDays(2), fornecedor + " K", null);
        enviar(post("/financeiro/contas-pagar/{id}/cancelar", cancelada), Map.of("motivo", "Conta cancelada de teste"), financeiro);
        ExtratoLancamento l2 = salvar(outroValor, hoje, null, "TRANSF", "NINGUEM", null);
        assertThat(enviar(post(url, l2.getId()), Map.of("tituloId", cancelada), financeiro).status)
                .as("conta cancelada").isEqualTo(409);
        assertThat(enviar(post(url, l2.getId()), Map.of("tituloId", 987654321L), financeiro).status)
                .as("conta inexistente").isEqualTo(404);
        assertThat(enviar(post(url, 987654321L), Map.of("tituloId", conta), financeiro).status)
                .as("lançamento inexistente").isEqualTo(404);
    }

    @Test
    @DisplayName("Ignorar: motivo de no mínimo 10 caracteres; vira IGNORADO, sai do reprocesso e não aceita mais vínculo")
    void ignorar() throws Exception {
        ExtratoLancamento l = salvar(valor, hoje, null, "TARIFA", "TARIFA BANCARIA", null);
        conciliacao.conciliar(l.getId());
        String ignorar = "/financeiro/conciliacao/extrato/lancamentos/{id}/ignorar";

        assertThat(enviar(post(ignorar, l.getId()), Map.of("motivo", "curto"), financeiro).status).isEqualTo(400);
        Resposta r = enviar(post(ignorar, l.getId()), Map.of("motivo", "Tarifa de manutenção da conta"), financeiro);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("situacao").asText()).isEqualTo("IGNORADO");
        long conta = criarConta(valor, hoje.plusDays(1), fornecedor, null);
        when(banco.listarDebitos(any(), any())).thenReturn(List.of());
        enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
        assertThat(lancamentos.findById(l.getId()).orElseThrow().getSituacao()).isEqualTo(SituacaoExtrato.IGNORADO);
        assertThat(enviar(post("/financeiro/conciliacao/extrato/lancamentos/{id}/vincular", l.getId()),
                Map.of("tituloId", conta), financeiro).status).isEqualTo(409);
        assertThat(enviar(post(ignorar, l.getId()), Map.of("motivo", "Ignorar de novo, sem efeito"), financeiro).status)
                .isEqualTo(409);

        JsonNode lista = enviar(get("/financeiro/conciliacao/extrato/lancamentos").param("situacao", "IGNORADO")
                .param("de", hoje.toString()).param("ate", hoje.toString()), null, auditor).corpo;
        assertThat(lista.findValuesAsText("id")).contains(String.valueOf(l.getId()));
        assertThat(enviar(get("/financeiro/conciliacao/extrato/lancamentos").param("situacao", "IGNORADO")
                .param("de", hoje.plusDays(1).toString()), null, auditor).corpo.findValuesAsText("id"))
                .doesNotContain(String.valueOf(l.getId()));
    }

    @Test
    @DisplayName("Reabrir a conta baixada pelo extrato desfaz o vínculo: o lançamento volta à fila, desvinculado, e não casa de novo sozinho")
    void reabrirDesfazVinculo() throws Exception {
        long conta = criarConta(valor, hoje.plusDays(1), fornecedor, doc);
        String id = "REABRIR-" + n;
        when(banco.listarDebitos(any(), any())).thenReturn(List.of(novo(id, valor, hoje, doc, "PAGAMENTO", "Z")));
        enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).isEqualTo(StatusTitulo.BAIXADO);

        Resposta r = enviar(post("/financeiro/contas-pagar/{id}/reabrir", conta),
                Map.of("justificativa", "Baixa casada com o pagamento errado"), financeiro);

        assertThat(r.status).isEqualTo(200);
        TituloFinanceiro t = titulos.findById(conta).orElseThrow();
        assertThat(t.getStatus()).isEqualTo(StatusTitulo.ABERTO);
        assertThat(t.getExtratoLancamentoId()).isNull();
        assertThat(t.getValorPago()).isNull();
        Map<String, Object> linha = jdbc.queryForMap(
                "select situacao, titulo_id, desvinculado from tb_extrato_lancamento where id_externo = ?", id);
        assertThat(linha.get("situacao")).isEqualTo("SEM_CORRESPONDENCIA");
        assertThat(linha.get("titulo_id")).isNull();
        assertThat(linha.get("desvinculado")).isEqualTo(true);

        enviar(post("/financeiro/conciliacao/extrato/sincronizar"), null, financeiro);
        assertThat(titulos.findById(conta).orElseThrow().getStatus()).as("não rebaixa a mesma conta errada").isEqualTo(StatusTitulo.ABERTO);
        assertThat(situacaoDe(id)).isEqualTo("SEM_CORRESPONDENCIA");
        assertThat(acoes(conta)).containsExactly("CRIOU", "BAIXA_AUTOMATICA", "REABRIU");
    }

    // ------------------------------------------------------------------ perfis
    @Test
    @DisplayName("Perfis: gerente de loja e vendedor 403 em tudo; auditoria só consulta; financeiro e admin gerenciam")
    void perfis() throws Exception {
        ExtratoLancamento l = salvar(valor, hoje, null, "TARIFA", "TARIFA", null);
        long conta = criarConta(valor.add(BigDecimal.ONE), hoje.plusDays(1), fornecedor, null);
        String base = "/financeiro/conciliacao/extrato";

        for (Usuario semAcesso : new Usuario[]{gerenteLoja, vendedor}) {
            assertThat(enviar(get(base + "/lancamentos"), null, semAcesso).status).isEqualTo(403);
            assertThat(enviar(post(base + "/sincronizar"), null, semAcesso).status).isEqualTo(403);
            assertThat(enviar(post(base + "/lancamentos/{id}/vincular", l.getId()), Map.of("tituloId", conta), semAcesso).status).isEqualTo(403);
            assertThat(enviar(post(base + "/lancamentos/{id}/ignorar", l.getId()), Map.of("motivo", "Sem permissão nenhuma"), semAcesso).status).isEqualTo(403);
        }
        Resposta negado = enviar(post(base + "/sincronizar"), null, gerenteLoja);
        assertThat(negado.corpo.get("codigo").asText()).isEqualTo("FINANCEIRO_SEM_PERMISSAO");

        assertThat(enviar(get(base + "/lancamentos"), null, auditor).status).isEqualTo(200);
        assertThat(enviar(post(base + "/sincronizar"), null, auditor).status).isEqualTo(403);
        assertThat(enviar(post(base + "/lancamentos/{id}/vincular", l.getId()), Map.of("tituloId", conta), auditor).status).isEqualTo(403);
        assertThat(enviar(post(base + "/lancamentos/{id}/ignorar", l.getId()), Map.of("motivo", "Auditoria só consulta"), auditor).status).isEqualTo(403);
        verify(banco, never()).listarDebitos(any(), any());

        assertThat(enviar(post(base + "/sincronizar"), null, financeiro).status).isEqualTo(200);
        assertThat(enviar(post(base + "/lancamentos/{id}/ignorar", l.getId()), Map.of("motivo", "Tarifa bancária do mês"), financeiro).status).isEqualTo(200);
    }

    // ------------------------------------------------------------------ comprovante
    @Test
    @DisplayName("Comprovante da baixa pelo extrato: conta mascarada, histórico, identificador e a origem (automática ou confirmada por pessoa)")
    void comprovanteComExtrato() throws Exception {
        long automatica = criarConta(valor, hoje.plusDays(1), fornecedor, doc);
        ExtratoLancamento l = lancamentos.save(ExtratoLancamento.builder().idExterno("COMPROVANTE-" + n)
                .dataLancamento(hoje).valor(valor).contraparteDocumento(doc).subHistorico("PAGAMENTO BOLETO")
                .infoComplementar("FORNECEDOR SINTETICO").codigoHistorico(144).build());
        conciliacao.conciliar(l.getId());

        String texto = textoDoPdf(comprovante(automatica));

        assertThat(texto).contains("Conciliação automática com extrato do Banco do Brasil").contains("••1213")
                .contains("PAGAMENTO BOLETO FORNECEDOR SINTETICO").contains("COMPROVANTE-" + n)
                .contains("Data do pagamento " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(hoje));

        BigDecimal outroValor = valor.add(BigDecimal.ONE);
        long manual = criarConta(outroValor, hoje.plusDays(1), fornecedor + " M", null);
        ExtratoLancamento l2 = salvar(outroValor, hoje, null, "TRANSF", "NINGUEM", null);
        conciliacao.conciliar(l2.getId());
        enviar(post("/financeiro/conciliacao/extrato/lancamentos/{id}/vincular", l2.getId()), Map.of("tituloId", manual), financeiro);
        assertThat(textoDoPdf(comprovante(manual))).contains("Conciliação com extrato do Banco do Brasil confirmada por "
                + financeiro.getNome());
    }

    // ------------------------------------------------------------------ apoio
    private long criarConta(BigDecimal valorConta, LocalDate vencimento, String nomeFornecedor, String documento) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("descricao", "Conta sintética " + n);
        corpo.put("fornecedor", nomeFornecedor);
        corpo.put("vencimento", vencimento.toString());
        corpo.put("valor", valorConta);
        if (documento != null) corpo.put("documentoFornecedor", documento);
        corpo.put("confirmarDuplicada", true);
        Resposta r = enviar(post("/financeiro/contas-pagar"), corpo, financeiro);
        assertThat(r.status).as("cadastro da conta: " + r.corpo).isEqualTo(201);
        return r.corpo.get("id").asLong();
    }

    /** Lançamento gravado direto, como a sincronização o deixaria (situação NOVO). */
    private ExtratoLancamento salvar(BigDecimal valorBanco, LocalDate data, String documento, String sub, String info,
                                     Integer codigoHistorico) {
        return lancamentos.save(ExtratoLancamento.builder().idExterno("T-" + UUID.randomUUID()).dataLancamento(data)
                .valor(valorBanco).contraparteDocumento(documento).subHistorico(sub).infoComplementar(info)
                .codigoHistorico(codigoHistorico).build());
    }

    private static LancamentoExtrato novo(String idExterno, BigDecimal valorBanco, LocalDate data, String documento,
                                          String sub, String info) {
        return new LancamentoExtrato(idExterno, data, valorBanco, 1L, 1, null, sub, info, documento, null, null, "{}");
    }

    private List<String> acoes(long contaId) throws Exception {
        return enviar(get("/financeiro/contas-pagar/{id}", contaId), null, financeiro).corpo.get("historico").findValuesAsText("acao");
    }

    private String situacaoDe(String idExterno) {
        return jdbc.queryForObject("select situacao from tb_extrato_lancamento where id_externo = ?", String.class, idExterno);
    }

    private JsonNode lancamentoNaLista(Usuario quem, String situacao, long id) throws Exception {
        JsonNode lista = enviar(get("/financeiro/conciliacao/extrato/lancamentos").param("situacao", situacao), null, quem).corpo;
        for (JsonNode item : lista) {
            if (item.get("id").asLong() == id) return item;
        }
        throw new AssertionError("Lançamento " + id + " não está na fila " + situacao);
    }

    private byte[] comprovante(long contaId) throws Exception {
        MvcResult r = mvc.perform(get("/financeiro/contas-pagar/{id}/comprovante", contaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + entrar(financeiro))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return r.getResponse().getContentAsByteArray();
    }

    private static String textoDoPdf(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
    }

    private Usuario usuario(String prefixo, String cargo) {
        return usuarios.save(Usuario.builder().nome(prefixo + " " + n).login(prefixo + "_" + n)
                .email(prefixo + "_" + n + "@teste.local").senhaHash(encoder.encode(SENHA)).primeiroAcesso(false)
                .filial("Matriz").cargo(cargo).build());
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

    private record Resposta(int status, JsonNode corpo) {
    }
}
