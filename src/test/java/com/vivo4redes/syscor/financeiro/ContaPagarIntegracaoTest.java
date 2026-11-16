package com.vivo4redes.syscor.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Contas a pagar (docs/contas_pagar.md, Fase B), com filtros, login e JWT reais e dados sintéticos: perfis, cadastro,
 * duplicidade, edição, cancelamento, baixa manual, reabertura, histórico e o CHECK de status corrigido na V41.
 */
@SpringBootTest(properties = {
        "syscor.caixa.cargos-auditoria=AUDITORIA DE CAIXA",
        "syscor.caixa.cargos-financeiro=FINANCEIRO",
        "syscor.caixa.cargos-area=GERENTE DE AREA DE VENDAS",
        "syscor.caixa.cargos-gerente=GERENTE DE VENDAS - JUNIOR",
        "syscor.caixa.admins=ADMIN.CONTAS.TESTE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ContaPagarIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";
    private static final String ADMIN = "ADMIN.CONTAS.TESTE";
    private static final Map<String, String> TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired FilialRepository filiais;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private int n;
    private String fornecedor;
    private Usuario financeiro;
    private Usuario admin;
    private Usuario auditor;
    private Usuario gerenteArea;
    private Usuario gerenteLoja;
    private Usuario vendedor;

    @BeforeEach
    void preparar() {
        n = SEQUENCIA.incrementAndGet();
        fornecedor = "Fornecedor Sintetico " + n;
        financeiro = usuario("financeiro", "FINANCEIRO");
        auditor = usuario("auditor", "AUDITORIA DE CAIXA");
        gerenteArea = usuario("gerentearea", "GERENTE DE ÁREA DE VENDAS");
        gerenteLoja = usuario("gerenteloja", "GERENTE DE VENDAS - JÚNIOR");
        vendedor = usuario("vendedor", "VENDEDOR");
        admin = usuarios.findFirstByLoginIgnoreCase(ADMIN).orElseGet(() -> usuarios.save(Usuario.builder()
                .nome("Admin Contas").login(ADMIN).email("admin.contas@teste.local").senhaHash(encoder.encode(SENHA))
                .primeiroAcesso(false).filial("Matriz").cargo("ASSISTENTE DE DESENVOLVIMENTO DE SISTEMA").build()));
    }

    // ------------------------------------------------------------------ cadastro
    @Test
    @DisplayName("Cadastro: conta nasce ABERTO; código de barras e documento gravam só os dígitos; vencimento passado é aceito")
    void cadastro() throws Exception {
        Map<String, Object> corpo = conta("1234.50", LocalDate.now(ZONA).minusDays(3));
        corpo.put("codigoBarras", linhaDigitavel());
        corpo.put("documentoFornecedor", "12.345.678/0001-95");
        corpo.put("formaPagamento", "BOLETO");
        corpo.put("categoria", "ALUGUEL");

        Resposta r = enviar(post("/financeiro/contas-pagar"), corpo, financeiro);

        assertThat(r.status).isEqualTo(201);
        assertThat(r.corpo.get("status").asText()).isEqualTo("ABERTO");
        assertThat(r.corpo.get("codigoBarras").asText()).isEqualTo(linhaDigitavel().replaceAll("[^0-9]", ""))
                .hasSize(47);
        assertThat(r.corpo.get("documentoFornecedor").asText()).isEqualTo("12345678000195");
        assertThat(r.corpo.get("formaPagamento").asText()).isEqualTo("BOLETO");
        assertThat(r.corpo.get("vencida").asBoolean()).isTrue();
        assertThat(r.corpo.get("temComprovante").asBoolean()).isFalse();
        assertThat(r.corpo.get("valor").decimalValue()).isEqualByComparingTo("1234.50");
    }

    @Test
    @DisplayName("Cadastro: campos obrigatórios (400), valor > 0 (400), código de barras e documento com tamanho errado (422)")
    void validacoes() throws Exception {
        Map<String, Object> semDescricao = conta("10.00", LocalDate.now(ZONA));
        semDescricao.put("descricao", " ");
        assertThat(enviar(post("/financeiro/contas-pagar"), semDescricao, financeiro).status).isEqualTo(400);

        assertThat(enviar(post("/financeiro/contas-pagar"), conta("0.00", LocalDate.now(ZONA)), financeiro).status)
                .as("valor zero").isEqualTo(400);

        Map<String, Object> semVencimento = conta("10.00", LocalDate.now(ZONA));
        semVencimento.remove("vencimento");
        assertThat(enviar(post("/financeiro/contas-pagar"), semVencimento, financeiro).status).isEqualTo(400);

        Map<String, Object> barrasCurto = conta("10.00", LocalDate.now(ZONA));
        barrasCurto.put("codigoBarras", "12345");
        assertThat(enviar(post("/financeiro/contas-pagar"), barrasCurto, financeiro).status).isEqualTo(422);

        Map<String, Object> documentoErrado = conta("10.00", LocalDate.now(ZONA));
        documentoErrado.put("documentoFornecedor", "123456789012");
        assertThat(enviar(post("/financeiro/contas-pagar"), documentoErrado, financeiro).status).isEqualTo(422);

        Map<String, Object> filialInexistente = conta("10.00", LocalDate.now(ZONA));
        filialInexistente.put("filialId", 999999);
        assertThat(enviar(post("/financeiro/contas-pagar"), filialInexistente, financeiro).status).isEqualTo(404);
    }

    @Test
    @DisplayName("Duplicidade: mesmo código de barras 409 CONTA_DUPLICADA; mesmo fornecedor+valor+vencimento 409 CONTA_PROVAVEL_DUPLICADA, liberável")
    void duplicidade() throws Exception {
        LocalDate vencimento = LocalDate.now(ZONA).plusDays(10);
        Map<String, Object> primeira = conta("500.00", vencimento);
        primeira.put("codigoBarras", linhaDigitavel());
        long id = idDe(enviar(post("/financeiro/contas-pagar"), primeira, financeiro));

        Map<String, Object> mesmoCodigo = conta("999.00", vencimento.plusDays(1));
        mesmoCodigo.put("fornecedor", "Outro Fornecedor " + n);
        mesmoCodigo.put("codigoBarras", linhaDigitavel().replaceAll("[^0-9]", ""));
        Resposta duplicada = enviar(post("/financeiro/contas-pagar"), mesmoCodigo, financeiro);
        assertThat(duplicada.status).isEqualTo(409);
        assertThat(duplicada.corpo.get("codigo").asText()).isEqualTo("CONTA_DUPLICADA");

        Map<String, Object> mesmaCombinacao = conta("500.00", vencimento);
        mesmaCombinacao.put("fornecedor", fornecedor.toUpperCase());
        Resposta provavel = enviar(post("/financeiro/contas-pagar"), mesmaCombinacao, financeiro);
        assertThat(provavel.status).isEqualTo(409);
        assertThat(provavel.corpo.get("codigo").asText()).isEqualTo("CONTA_PROVAVEL_DUPLICADA");

        mesmaCombinacao.put("confirmarDuplicada", true);
        assertThat(enviar(post("/financeiro/contas-pagar"), mesmaCombinacao, financeiro).status)
                .as("confirmada pelo usuário").isEqualTo(201);

        // Conta cancelada não conta como duplicada: o mesmo código de barras volta a valer
        Map<String, Object> cancelar = Map.of("motivo", "Cadastrada por engano, vai ser refeita");
        assertThat(enviar(post("/financeiro/contas-pagar/{id}/cancelar", id), cancelar, financeiro).status).isEqualTo(200);
        mesmoCodigo.put("confirmarDuplicada", true);
        assertThat(enviar(post("/financeiro/contas-pagar"), mesmoCodigo, financeiro).status).isEqualTo(201);
    }

    // ------------------------------------------------------------------ perfis
    @Test
    @DisplayName("Perfis: gerente de loja e vendedor recebem 403 FINANCEIRO_SEM_PERMISSAO em tudo; auditoria e gerente de área só consultam")
    void perfis() throws Exception {
        long id = idDe(enviar(post("/financeiro/contas-pagar"), conta("77.00", LocalDate.now(ZONA)), financeiro));

        for (Usuario semAcesso : new Usuario[]{gerenteLoja, vendedor}) {
            Resposta lista = enviar(get("/financeiro/contas-pagar"), null, semAcesso);
            assertThat(lista.status).as("lista de " + semAcesso.getCargo()).isEqualTo(403);
            assertThat(lista.corpo.get("codigo").asText()).isEqualTo("FINANCEIRO_SEM_PERMISSAO");
            assertThat(enviar(get("/financeiro/contas-pagar/{id}", id), null, semAcesso).status).isEqualTo(403);
            assertThat(enviar(get("/financeiro/contas-pagar/resumo"), null, semAcesso).status).isEqualTo(403);
            assertThat(enviar(post("/financeiro/contas-pagar"), conta("1.00", LocalDate.now(ZONA)), semAcesso).status)
                    .isEqualTo(403);
            assertThat(enviar(get("/financeiro/contas-pagar/permissoes"), null, semAcesso).corpo.get("consultar").asBoolean())
                    .isFalse();
        }

        for (Usuario consulta : new Usuario[]{auditor, gerenteArea}) {
            assertThat(enviar(get("/financeiro/contas-pagar"), null, consulta).status).isEqualTo(200);
            assertThat(enviar(get("/financeiro/contas-pagar/{id}", id), null, consulta).status).isEqualTo(200);
            assertThat(enviar(get("/financeiro/contas-pagar/resumo"), null, consulta).status).isEqualTo(200);
            assertThat(enviar(post("/financeiro/contas-pagar"), conta("1.00", LocalDate.now(ZONA)), consulta).status)
                    .as("cadastrar").isEqualTo(403);
            assertThat(enviar(put("/financeiro/contas-pagar/{id}", id), conta("78.00", LocalDate.now(ZONA)), consulta).status)
                    .as("editar").isEqualTo(403);
            assertThat(enviar(post("/financeiro/contas-pagar/{id}/cancelar", id),
                    Map.of("motivo", "Tentativa sem permissão"), consulta).status).as("cancelar").isEqualTo(403);
            JsonNode permissoes = enviar(get("/financeiro/contas-pagar/permissoes"), null, consulta).corpo;
            assertThat(permissoes.get("consultar").asBoolean()).isTrue();
            assertThat(permissoes.get("gerenciar").asBoolean()).isFalse();
        }

        assertThat(enviar(post("/financeiro/contas-pagar"), conta("1.00", LocalDate.now(ZONA)), admin).status)
                .as("admin cadastra").isEqualTo(201);
        JsonNode permissoesFinanceiro = enviar(get("/financeiro/contas-pagar/permissoes"), null, financeiro).corpo;
        assertThat(permissoesFinanceiro.get("gerenciar").asBoolean()).isTrue();
        assertThat(permissoesFinanceiro.get("baixaManual").asBoolean()).isTrue();
        assertThat(permissoesFinanceiro.get("sincronizar").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------ edição
    @Test
    @DisplayName("Editar: só ABERTO ou DIVERGENTE; BAIXADO e CANCELADO dão 409; a edição vai para o histórico")
    void editar() throws Exception {
        long id = idDe(enviar(post("/financeiro/contas-pagar"), conta("100.00", LocalDate.now(ZONA).plusDays(5)), financeiro));

        Resposta editada = enviar(put("/financeiro/contas-pagar/{id}", id),
                conta("150.00", LocalDate.now(ZONA).plusDays(6)), financeiro);
        assertThat(editada.status).isEqualTo(200);
        assertThat(editada.corpo.get("valor").decimalValue()).isEqualByComparingTo("150.00");

        jdbc.update("update tb_titulo_financeiro set status = 'DIVERGENTE' where id = ?", id);
        assertThat(enviar(put("/financeiro/contas-pagar/{id}", id), conta("151.00", LocalDate.now(ZONA).plusDays(6)),
                financeiro).status).as("DIVERGENTE edita").isEqualTo(200);

        jdbc.update("update tb_titulo_financeiro set status = 'BAIXADO' where id = ?", id);
        Resposta baixada = enviar(put("/financeiro/contas-pagar/{id}", id),
                conta("152.00", LocalDate.now(ZONA).plusDays(6)), financeiro);
        assertThat(baixada.status).isEqualTo(409);
        assertThat(baixada.corpo.get("mensagem").asText()).contains("Reabra");

        jdbc.update("update tb_titulo_financeiro set status = 'CANCELADO' where id = ?", id);
        assertThat(enviar(put("/financeiro/contas-pagar/{id}", id), conta("153.00", LocalDate.now(ZONA)), financeiro).status)
                .isEqualTo(409);

        JsonNode historico = enviar(get("/financeiro/contas-pagar/{id}", id), null, financeiro).corpo.get("historico");
        assertThat(acoes(historico)).containsExactly("CRIOU", "EDITOU", "EDITOU");
    }

    // ------------------------------------------------------------------ cancelar
    @Test
    @DisplayName("Cancelar: motivo de no mínimo 10 caracteres; vira CANCELADO, não apaga, sai do resumo e não cancela duas vezes")
    void cancelar() throws Exception {
        long id = idDe(enviar(post("/financeiro/contas-pagar"), conta("321.00", LocalDate.now(ZONA).plusDays(2)), financeiro));
        BigDecimal totalAntes = enviar(get("/financeiro/contas-pagar/resumo"), null, financeiro).corpo
                .get("totalEmAberto").decimalValue();

        assertThat(enviar(post("/financeiro/contas-pagar/{id}/cancelar", id), Map.of("motivo", "curto"), financeiro).status)
                .isEqualTo(400);

        Resposta r = enviar(post("/financeiro/contas-pagar/{id}/cancelar", id),
                Map.of("motivo", "Fornecedor emitiu em duplicidade"), financeiro);
        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("status").asText()).isEqualTo("CANCELADO");

        BigDecimal totalDepois = enviar(get("/financeiro/contas-pagar/resumo"), null, financeiro).corpo
                .get("totalEmAberto").decimalValue();
        assertThat(totalAntes.subtract(totalDepois)).isEqualByComparingTo("321.00");

        assertThat(enviar(post("/financeiro/contas-pagar/{id}/cancelar", id),
                Map.of("motivo", "Cancelar de novo, sem efeito"), financeiro).status).isEqualTo(409);

        JsonNode detalhe = enviar(get("/financeiro/contas-pagar/{id}", id), null, financeiro).corpo;
        assertThat(detalhe.get("motivoCancelamento").asText()).isEqualTo("Fornecedor emitiu em duplicidade");
        assertThat(acoes(detalhe.get("historico"))).containsExactly("CRIOU", "CANCELOU");
        assertThat(detalhe.get("historico").get(1).get("usuario").asText()).isEqualTo(financeiro.getNome());
    }

    // ------------------------------------------------------------------ baixa manual e reabrir
    @Test
    @DisplayName("Baixa manual: grava valor, data, banco e origem MANUAL; gera comprovante; não baixa duas vezes nem data futura")
    void baixaManual() throws Exception {
        long id = idDe(enviar(post("/financeiro/contas-pagar"), conta("200.00", LocalDate.now(ZONA).plusDays(1)), financeiro));
        LocalDate hoje = LocalDate.now(ZONA);

        assertThat(enviar(post("/financeiro/contas-pagar/{id}/baixa-manual", id),
                baixa(hoje.plusDays(1), "200.00", "Pago pelo caixa da loja"), financeiro).status)
                .as("data futura").isEqualTo(422);
        assertThat(enviar(post("/financeiro/contas-pagar/{id}/baixa-manual", id),
                baixa(hoje, "200.00", "curto"), financeiro).status).as("justificativa curta").isEqualTo(400);

        Resposta r = enviar(post("/financeiro/contas-pagar/{id}/baixa-manual", id),
                baixa(hoje, "199.90", "Pago em dinheiro no balcão"), financeiro);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("status").asText()).isEqualTo("BAIXADO");
        assertThat(r.corpo.get("origemBaixa").asText()).isEqualTo("MANUAL");
        assertThat(r.corpo.get("valorPago").decimalValue()).isEqualByComparingTo("199.90");
        assertThat(r.corpo.get("banco").asText()).isEqualTo("Caixa");
        assertThat(r.corpo.get("temComprovante").asBoolean()).isTrue();
        assertThat(r.corpo.get("dataPagamento").asText()).startsWith(hoje.toString());

        assertThat(enviar(post("/financeiro/contas-pagar/{id}/baixa-manual", id),
                baixa(hoje, "200.00", "Segunda baixa indevida"), financeiro).status).isEqualTo(409);

        JsonNode detalhe = enviar(get("/financeiro/contas-pagar/{id}", id), null, financeiro).corpo;
        assertThat(detalhe.get("justificativaBaixa").asText()).isEqualTo("Pago em dinheiro no balcão");
        assertThat(acoes(detalhe.get("historico"))).containsExactly("CRIOU", "BAIXA_MANUAL");
    }

    @Test
    @DisplayName("Reabrir: só BAIXADO, com justificativa; limpa valor pago, data, banco, origem e vínculo com o extrato")
    void reabrir() throws Exception {
        long id = idDe(enviar(post("/financeiro/contas-pagar"), conta("90.00", LocalDate.now(ZONA)), financeiro));

        assertThat(enviar(post("/financeiro/contas-pagar/{id}/reabrir", id),
                Map.of("justificativa", "Reabrir conta que nem foi baixada"), financeiro).status)
                .as("ABERTO não reabre").isEqualTo(409);

        assertThat(enviar(post("/financeiro/contas-pagar/{id}/baixa-manual", id),
                baixa(LocalDate.now(ZONA), "90.00", "Pago por transferência"), financeiro).status).isEqualTo(200);
        jdbc.update("update tb_titulo_financeiro set extrato_lancamento_id = 123 where id = ?", id);

        assertThat(enviar(post("/financeiro/contas-pagar/{id}/reabrir", id), Map.of("justificativa", "curta"), financeiro)
                .status).isEqualTo(400);

        Resposta r = enviar(post("/financeiro/contas-pagar/{id}/reabrir", id),
                Map.of("justificativa", "Baixa lançada na conta errada"), financeiro);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("status").asText()).isEqualTo("ABERTO");
        assertThat(r.corpo.get("valorPago").isNull()).isTrue();
        assertThat(r.corpo.get("dataPagamento").isNull()).isTrue();
        assertThat(r.corpo.get("banco").isNull()).isTrue();
        assertThat(r.corpo.get("origemBaixa").isNull()).isTrue();
        assertThat(r.corpo.get("temComprovante").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select extrato_lancamento_id from tb_titulo_financeiro where id = ?",
                Long.class, id)).as("vínculo com o extrato desfeito").isNull();

        JsonNode historico = enviar(get("/financeiro/contas-pagar/{id}", id), null, financeiro).corpo.get("historico");
        assertThat(acoes(historico)).containsExactly("CRIOU", "BAIXA_MANUAL", "REABRIU");
    }

    // ------------------------------------------------------------------ lista e resumo
    @Test
    @DisplayName("Lista mantém o contrato (campos antigos) e filtra por status, vencimento, busca e filial; resumo conta vencidas e divergências")
    void listaEResumo() throws Exception {
        Long filialId = filiais.save(Filial.builder().codigo("CP" + n).nome("Filial Contas " + n).build()).getId();
        LocalDate hoje = LocalDate.now(ZONA);

        Map<String, Object> vencida = conta("10.00", hoje.minusDays(2));
        vencida.put("filialId", filialId);
        vencida.put("documentoFornecedor", "12345678000195");
        long idVencida = idDe(enviar(post("/financeiro/contas-pagar"), vencida, financeiro));
        long idFutura = idDe(enviar(post("/financeiro/contas-pagar"), conta("20.00", hoje.plusDays(20)), financeiro));
        long idDivergente = idDe(enviar(post("/financeiro/contas-pagar"), conta("30.00", hoje.plusDays(1)), financeiro));
        jdbc.update("update tb_titulo_financeiro set status = 'DIVERGENTE' where id = ?", idDivergente);

        Resposta tudo = enviar(get("/financeiro/contas-pagar").param("busca", fornecedor), null, auditor);
        assertThat(tudo.status).isEqualTo(200);
        assertThat(tudo.corpo).hasSize(3);
        JsonNode primeira = tudo.corpo.get(0);
        for (String campoAntigo : new String[]{"id", "descricao", "fornecedor", "vencimento", "valor", "status", "banco",
                "dataPagamento", "valorPago", "comprovanteUrl"}) {
            assertThat(primeira.has(campoAntigo)).as("contrato antigo: " + campoAntigo).isTrue();
        }
        assertThat(primeira.get("id").asLong()).as("ordenada por vencimento").isEqualTo(idVencida);

        assertThat(enviar(get("/financeiro/contas-pagar").param("busca", fornecedor).param("status", "DIVERGENTE"),
                null, auditor).corpo).hasSize(1);
        assertThat(enviar(get("/financeiro/contas-pagar").param("busca", fornecedor)
                .param("vencimentoDe", hoje.plusDays(10).toString()), null, auditor).corpo).hasSize(1);
        assertThat(enviar(get("/financeiro/contas-pagar").param("busca", fornecedor)
                .param("vencimentoAte", hoje.toString()), null, auditor).corpo).hasSize(1);
        assertThat(enviar(get("/financeiro/contas-pagar").param("filialId", filialId.toString()), null, auditor).corpo)
                .hasSize(1);
        assertThat(enviar(get("/financeiro/contas-pagar").param("busca", "12.345.678/0001"), null, auditor).corpo
                .findValuesAsText("id")).contains(String.valueOf(idVencida));
        assertThat(enviar(get("/financeiro/contas-pagar").param("busca", "inexistente-" + n), null, auditor).corpo).isEmpty();

        JsonNode resumo = enviar(get("/financeiro/contas-pagar/resumo"), null, gerenteArea).corpo;
        assertThat(resumo.get("totalEmAberto").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("60.00"));
        assertThat(resumo.get("vencidas").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(resumo.get("divergencias").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(resumo.get("canalBancario").asText()).isEqualTo("DESLIGADO");
        assertThat(idFutura).isPositive();
    }

    // ------------------------------------------------------------------ banco
    @Test
    @DisplayName("V41: o CHECK de status aceita DIVERGENTE e CANCELADO (a V10 só aceitava ABERTO/BAIXADO) e continua barrando o resto")
    void checkDeStatus() throws Exception {
        long id = idDe(enviar(post("/financeiro/contas-pagar"), conta("11.00", LocalDate.now(ZONA)), financeiro));

        for (String status : new String[]{"DIVERGENTE", "CANCELADO", "BAIXADO", "ABERTO"}) {
            assertThat(jdbc.update("update tb_titulo_financeiro set status = ? where id = ?", status, id)).isEqualTo(1);
        }
        assertThatThrownBy(() -> jdbc.update("update tb_titulo_financeiro set status = 'VENCIDO' where id = ?", id))
                .hasMessageContaining("chk_titulo_status");
        assertThatThrownBy(() -> jdbc.update("update tb_titulo_financeiro set forma_pagamento = 'CHEQUE' where id = ?", id))
                .hasMessageContaining("chk_titulo_forma_pagamento");
        assertThatThrownBy(() -> jdbc.update("update tb_titulo_financeiro set documento_fornecedor = '123' where id = ?", id))
                .hasMessageContaining("chk_titulo_documento_fornecedor");
    }

    // ------------------------------------------------------------------ apoio
    private Map<String, Object> conta(String valor, LocalDate vencimento) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("descricao", "Conta sintética " + n);
        corpo.put("fornecedor", fornecedor);
        corpo.put("vencimento", vencimento.toString());
        corpo.put("valor", new BigDecimal(valor));
        return corpo;
    }

    private static Map<String, Object> baixa(LocalDate data, String valorPago, String justificativa) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("dataPagamento", data.toString());
        corpo.put("valorPago", new BigDecimal(valorPago));
        corpo.put("banco", "Caixa");
        corpo.put("justificativa", justificativa);
        return corpo;
    }

    /** Linha digitável sintética de 47 dígitos, com a pontuação de costume, única por teste (o banco é da classe toda). */
    private String linhaDigitavel() {
        String sufixo = String.format("%014d", n);
        return "00190.00009 01234.567890 12345.678901 2 " + sufixo;
    }

    private static long idDe(Resposta r) {
        assertThat(r.status).as("cadastro: " + r.corpo).isEqualTo(201);
        return r.corpo.get("id").asLong();
    }

    private static java.util.List<String> acoes(JsonNode historico) {
        return historico.findValuesAsText("acao");
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
