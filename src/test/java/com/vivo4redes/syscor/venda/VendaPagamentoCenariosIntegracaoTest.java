package com.vivo4redes.syscor.venda;

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
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Cenários do pagamento da venda pela API (docs/renova_voucher.md), com um produto sintético nos preços do iPhone 17
 * Pro Max 512GB no Controle Alto Valor. Sem transação no teste: cada chamada confirma ou desfaz a sua, como em produção.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(SoftAssertionsExtension.class)
class VendaPagamentoCenariosIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String MODELO = "APARELHO SINTETICO 512GB";
    private static final String PLANO = "CONTROLE_ALTO_VALOR";
    private static final String SENHA = "senha-de-teste";

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

    @InjectSoftAssertions
    SoftAssertions s;

    private Long clienteId;
    private Long filialId;
    private String email;
    private EstoqueGeral aparelho;

    @BeforeEach
    void preparar() {
        // O banco é o mesmo em todos os testes: cada um começa com os vouchers livres.
        jdbc.update("UPDATE vendas SET codigo_voucher = NULL");
        jdbc.update("DELETE FROM pagamentos_venda WHERE forma = 'VOUCHER'");
        int n = SEQUENCIA.incrementAndGet();
        clienteId = clientes.save(Cliente.builder().tipoPessoa(TipoPessoa.FISICA).nome("Cliente Teste")
                .cpfCnpj("9000000" + n).build()).getId();
        filialId = filiais.save(Filial.builder().codigo("T" + n).nome("Filial Teste").build()).getId();
        email = "vendedor" + n + "@teste.local";
        usuarios.save(Usuario.builder().nome("Vendedor Teste").login("vendedor" + n).email(email)
                .senhaHash(encoder.encode(SENHA)).filial("Filial Teste").cargo("VENDEDOR").build());
        // Sem filtros neste teste: o usuário logado, que o JWT definiria, entra direto no contexto.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("vendedor" + n, null, List.of()));
        aparelho = estoque.save(EstoqueGeral.builder().serial("SINT-" + n).modeloComercial(MODELO).build());
        tabelaVigente("11199.00", "10079.10", "12581.12", "10699.00", "9579.10", "12081.12", "500.00");
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ cenários 1 a 16

    @Test
    @DisplayName("1. Regular, PIX, pagamento PIX 10.079,10: total 10.079,10, saldo 0, finaliza")
    void cenario01() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PIX");
        JsonNode v = pagar(id, "PIX", "10079.10", 1);

        valor(v, "valorTotal", "10079.10");
        valor(v, "saldo", "0");
        s.assertThat(finalizar(id).status).as("finalizar").isEqualTo(200);
    }

    @Test
    @DisplayName("2. Regular, à vista, crédito 10x 11.199,00: total 11.199,00, finaliza")
    void cenario02() throws Exception {
        long id = vendaComAparelho(null);
        JsonNode v = pagar(id, "CARTAO_CREDITO", "11199.00", 10);

        valor(v, "valorTotal", "11199.00");
        s.assertThat(finalizar(id).status).as("finalizar").isEqualTo(200);
    }

    @Test
    @DisplayName("3. Regular, à vista, PIX 1.199 + crédito 10x 10.000: total 11.199,00, finaliza")
    void cenario03() throws Exception {
        long id = vendaComAparelho(null);
        pagar(id, "PIX", "1199.00", 1);
        JsonNode v = pagar(id, "CARTAO_CREDITO", "10000.00", 10);

        valor(v, "valorTotal", "11199.00");
        s.assertThat(finalizar(id).status).as("finalizar").isEqualTo(200);
    }

    @Test
    @DisplayName("4. Regular, 21x sem entrada, financiar: FINANCIADO 12.581,12, parcela 599,10")
    void cenario04() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PARCELADO_21");
        JsonNode v = financiar(id);

        valor(financiado(v), "valor", "12581.12");
        valor(v, "valorParcela", "599.10");
        valor(v, "saldo", "0");
    }

    @Test
    @DisplayName("5. Regular, 21x, entrada 2.000, financiar: total 12.334,29, financiado 10.334,29, parcela 492,11")
    void cenario05() throws Exception {
        JsonNode v = financiar(vendaEm21xComEntrada(null));

        valor(v, "valorTotal", "12334.29");
        valor(financiado(v), "valor", "10334.29");
        valor(v, "valorParcela", "492.11");
        valor(v, "saldo", "0");
    }

    @Test
    @DisplayName("6. Caso 5 e remover a entrada: financiado volta a 12.581,12")
    void cenario06() throws Exception {
        long id = vendaEm21xComEntrada(null);
        JsonNode v = financiar(id);
        long entrada = linha(v, "ENTRADA").get("id").asLong();

        v = exigir(200, delete("/vendas/{id}/pagamentos/{p}", id, entrada));

        valor(financiado(v), "valor", "12581.12");
        valor(v, "valorTotal", "12581.12");
        valor(v, "valorParcela", "599.10");
    }

    @Test
    @DisplayName("7. Caso 5 e trocar para 18x: FINANCIADO com 18 parcelas e valor recalculado (9.199 × 1,1060324)")
    void cenario07() throws Exception {
        long id = vendaEm21xComEntrada(null);
        financiar(id);

        JsonNode v = condicao(id, "PARCELADO_18");

        JsonNode linha = financiado(v);
        s.assertThat(linha.get("parcelas").asInt()).as("parcelas do FINANCIADO").isEqualTo(18);
        valor(linha, "valor", "10174.39");
        valor(v, "valorTotal", "12174.39");
        valor(v, "valorParcela", "565.24");
    }

    @Test
    @DisplayName("8. Renova (voucher V1), à vista: total 10.699,00, desconto fabricante 500 informativo")
    void cenario08() throws Exception {
        long id = vendaComAparelho(null);
        JsonNode v = ligarRenova(id, "V1");

        valor(v, "valorTotal", "10699.00");
        valor(v, "descontoFabricante", "500.00");
        s.assertThat(v.path("codigoVoucher").asText()).as("codigoVoucher").isEqualTo("V1");
        s.assertThat(v.get("pagamentos")).as("o voucher não vira linha de pagamento").isEmpty();
    }

    @Test
    @DisplayName("9. Renova, 21x, entrada 2.000, financiar: total 11.834,29, financiado 9.834,29, parcela 468,30")
    void cenario09() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PARCELADO_21");
        ligarRenova(id, "V1");
        pagar(id, "DINHEIRO", "2000.00", 1);
        JsonNode v = financiar(id);

        valor(v, "valorTotal", "11834.29");
        valor(financiado(v), "valor", "9834.29");
        valor(v, "valorParcela", "468.30");
    }

    @Test
    @DisplayName("10. Aparelho + chip R$ 13 no PIX: total 10.092,10 (chip sem desconto PIX)")
    void cenario10() throws Exception {
        long id = vendaComAparelho("13.00");
        JsonNode v = condicao(id, "PIX");

        valor(v, "valorTotal", "10092.10");
    }

    @Test
    @DisplayName("11. Aparelho + chip no 21x, entrada 2.000: chip fora dos juros, total 12.347,29")
    void cenario11() throws Exception {
        long id = vendaComAparelho("13.00");
        condicao(id, "PARCELADO_21");
        JsonNode v = pagar(id, "DINHEIRO", "2000.00", 1);

        valor(v, "valorTotal", "12347.29");
    }

    @Test
    @DisplayName("12. Pagar à vista acima do saldo: 422 com o saldo restante")
    void cenario12() throws Exception {
        long id = vendaComAparelho(null);
        pagar(id, "DINHEIRO", "1000.00", 1);

        Resposta r = pagamento(id, "PIX", "10199.01", 1);

        s.assertThat(r.status).as("status").isEqualTo(422);
        s.assertThat(r.corpo.path("mensagem").asText()).as("mensagem").contains("10199.00");
    }

    @Test
    @DisplayName("13. Pagar à vista 11.199 e ligar o Renova: saldo −500 e aviso; finalizar recusado até ajustar")
    void cenario13() throws Exception {
        long id = vendaComAparelho(null);
        pagar(id, "DINHEIRO", "11199.00", 1);

        JsonNode v = ligarRenova(id, "V1");

        valor(v, "saldo", "-500.00");
        s.assertThat(v.path("aviso").asText()).as("aviso").contains("passam do total");
        s.assertThat(v.get("pagamentos")).as("ligar o Renova não apaga pagamentos").hasSize(1);
        s.assertThat(finalizar(id).status).as("finalizar").isEqualTo(422);
    }

    @Test
    @DisplayName("14. Finalizar com soma diferente do total: 422")
    void cenario14() throws Exception {
        long id = vendaComAparelho(null);
        pagar(id, "DINHEIRO", "5000.00", 1);

        s.assertThat(finalizar(id).status).as("finalizar").isEqualTo(422);
        s.assertThat(venda(id).get("status").asText()).as("status da venda").isEqualTo("ABERTA");
    }

    @Test
    @DisplayName("15. Trocar de PIX para à vista com linha PIX lançada: aceita, a linha continua e o total vira 11.199")
    void cenario15() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PIX");
        pagar(id, "PIX", "5000.00", 1);

        JsonNode v = condicao(id, "A_VISTA");

        valor(v, "valorTotal", "11199.00");
        s.assertThat(v.get("pagamentos")).as("pagamentos").hasSize(1);
        s.assertThat(v.get("pagamentos").get(0).get("forma").asText()).as("forma").isEqualTo("PIX");
    }

    @Test
    @DisplayName("16. Trocar de à vista para 21x com crédito 10x lançado: 422 com a lista de incompatíveis")
    void cenario16() throws Exception {
        long id = vendaComAparelho(null);
        pagar(id, "CARTAO_CREDITO", "1000.00", 10);

        Resposta r = enviar(patch("/vendas/{id}/condicao-pagamento", id), Map.of("condicao", "PARCELADO_21"));

        s.assertThat(r.status).as("status").isEqualTo(422);
        s.assertThat(r.corpo.path("pagamentosIncompativeis")).as("pagamentosIncompativeis").hasSize(1);
        s.assertThat(r.corpo.path("pagamentosIncompativeis").path(0).path("forma").asText()).as("forma")
                .isEqualTo("CARTAO_CREDITO");
        s.assertThat(venda(id).get("condicaoPagamento").asText()).as("condição mantida").isEqualTo("A_VISTA");
    }

    // ------------------------------------------------------------------ conferências do código

    @Test
    @DisplayName("Conferência: entrada acima do total recusa e desfaz a linha e o total (rollback)")
    void entradaAcimaDoTotalFazRollback() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PARCELADO_21");

        Resposta r = pagamento(id, "DINHEIRO", "12000.00", 1);

        s.assertThat(r.status).as("status").isEqualTo(422);
        JsonNode v = venda(id);
        s.assertThat(v.get("pagamentos")).as("a linha não fica gravada").isEmpty();
        valor(v, "valorTotal", "12581.12");
    }

    @Test
    @DisplayName("Conferência: entrada igual à base quita a venda e apaga a linha FINANCIADO")
    void entradaIgualABaseQuitaAVenda() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PARCELADO_21");
        financiar(id);

        JsonNode v = pagar(id, "DINHEIRO", "11199.00", 1);

        valor(v, "valorTotal", "11199.00");
        valor(v, "saldo", "0");
        s.assertThat(v.get("pagamentos")).as("sobra só a entrada").hasSize(1);
    }

    @Test
    @DisplayName("Conferência: lançar como entrada o saldo exibido é recusado, porque a entrada reduz os juros")
    void entradaIgualAoSaldoExibido() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PARCELADO_21");
        JsonNode v = pagar(id, "DINHEIRO", "11000.00", 1);
        valor(v, "saldo", "223.56");

        Resposta r = pagamento(id, "DINHEIRO", "223.56", 1);

        s.assertThat(r.status).as("status").isEqualTo(422);
        s.assertThat(venda(id).get("pagamentos")).as("a segunda entrada não fica gravada").hasSize(1);
    }

    @Test
    @DisplayName("Conferência: chip em linha própria, só em valorChip, conta uma vez")
    void chipEmLinhaPropriaSoValorChip() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PIX");

        JsonNode v = adicionarChip(id, "0.00", "13.00");

        valor(v, "valorTotal", "10092.10");
    }

    @Test
    @DisplayName("Conferência: serviço ignora o valorUnitario enviado; sem valorChip, não soma nada")
    void servicoIgnoraValorUnitario() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PIX");

        JsonNode v = adicionarChip(id, "13.00", null);

        valor(v, "valorTotal", "10079.10");
    }

    @Test
    @DisplayName("Conferência: chip em linha própria, com valorUnitario e valorChip, conta uma vez")
    void chipEmLinhaPropriaComOsDoisValores() throws Exception {
        long id = vendaComAparelho(null);
        condicao(id, "PIX");

        JsonNode v = adicionarChip(id, "13.00", "13.00");

        valor(v, "valorTotal", "10092.10");
    }

    @Test
    @DisplayName("Conferência: tabela nova com o carrinho aberto: recusa, grava o total novo e finaliza depois do ajuste")
    void finalizarComTabelaNova() throws Exception {
        long id = vendaComAparelho(null);
        pagar(id, "DINHEIRO", "11199.00", 1);
        tabelaVigente("11999.00", "10799.10", "13480.00", "11499.00", "10299.10", "12980.00", "500.00");

        Resposta r = finalizar(id);

        s.assertThat(r.status).as("finalizar").isEqualTo(422);
        s.assertThat(r.corpo.path("mensagem").asText()).as("mensagem").contains("11199.00", "11999.00");
        JsonNode v = venda(id);
        s.assertThat(v.get("status").asText()).as("status").isEqualTo("ABERTA");
        valor(v, "valorTotal", "11999.00");
        valor(v, "saldo", "800.00");

        pagar(id, "DINHEIRO", "800.00", 1);
        s.assertThat(finalizar(id).status).as("finalizar depois do ajuste").isEqualTo(200);
    }

    // ------------------------------------------------------------------ regra do Renova pela API

    @Test
    @DisplayName("Renova: desligar limpa o número e volta para a Regular, sem apagar pagamentos")
    void desligarRenova() throws Exception {
        long id = vendaComAparelho(null);
        pagar(id, "DINHEIRO", "100.00", 1);
        ligarRenova(id, "V1");

        JsonNode v = exigir(200, patch("/vendas/{id}/renova", id), Map.of("ativo", false));

        s.assertThat(v.get("renova").asBoolean()).as("renova").isFalse();
        s.assertThat(v.get("codigoVoucher").isNull()).as("codigoVoucher limpo").isTrue();
        valor(v, "valorTotal", "11199.00");
        valor(v, "descontoFabricante", "0");
        s.assertThat(v.get("pagamentos")).as("pagamentos").hasSize(1);
    }

    @Test
    @DisplayName("Renova: sem número é 422 e a venda continua na Regular")
    void renovaSemNumero() throws Exception {
        long id = vendaComAparelho(null);

        Resposta r = enviar(patch("/vendas/{id}/renova", id), Map.of("ativo", true, "codigoVoucher", "  "));

        s.assertThat(r.status).as("status").isEqualTo(422);
        valor(venda(id), "valorTotal", "11199.00");
    }

    @Test
    @DisplayName("Renova: mesmo número em outra venda é 409 com o id; depois de cancelar a outra, o número fica livre")
    void voucherRepetido() throws Exception {
        long outra = vendaComAparelho(null);
        ligarRenova(outra, "V1");
        aparelho = estoque.save(EstoqueGeral.builder().serial("SINT-B-" + SEQUENCIA.get()).modeloComercial(MODELO).build());
        long id = vendaComAparelho(null);

        Resposta r = enviar(patch("/vendas/{id}/renova", id), Map.of("ativo", true, "codigoVoucher", "V1"));

        s.assertThat(r.status).as("status").isEqualTo(409);
        s.assertThat(r.corpo.path("mensagem").asText()).as("mensagem").isEqualTo("Voucher já usado na venda #" + outra);
        valor(venda(id), "valorTotal", "11199.00");

        exigir(200, patch("/vendas/{id}/status", outra),
                Map.of("novoStatus", "CANCELADA", "autenticacaoUsuario", autenticacao()));
        valor(ligarRenova(id, "V1"), "valorTotal", "10699.00");
    }

    @Test
    @DisplayName("Renova: número usado em linha VOUCHER de venda antiga é 409")
    void voucherEmVendaAntiga() throws Exception {
        long antiga = vendaComAparelho(null);
        jdbc.update("INSERT INTO pagamentos_venda (venda_id, forma, valor, codigo_voucher) VALUES (?, 'VOUCHER', 100, 'V-ANTIGO')",
                antiga);
        jdbc.update("UPDATE vendas SET status = 'CONCLUIDA' WHERE id = ?", antiga);
        aparelho = estoque.save(EstoqueGeral.builder().serial("SINT-B-" + SEQUENCIA.get()).modeloComercial(MODELO).build());
        long id = vendaComAparelho(null);

        Resposta r = enviar(patch("/vendas/{id}/renova", id), Map.of("ativo", true, "codigoVoucher", "V-ANTIGO"));

        s.assertThat(r.status).as("status").isEqualTo(409);
        s.assertThat(r.corpo.path("mensagem").asText()).as("mensagem").isEqualTo("Voucher já usado na venda #" + antiga);
    }

    @Test
    @DisplayName("Renova: POST de pagamento com VOUCHER é 422 com a mensagem do bloco Renova")
    void voucherComoPagamento() throws Exception {
        long id = vendaComAparelho(null);

        Resposta r = pagamento(id, "VOUCHER", "100.00", 1);

        s.assertThat(r.status).as("status").isEqualTo(422);
        s.assertThat(r.corpo.path("mensagem").asText()).as("mensagem")
                .isEqualTo("Voucher não é forma de pagamento: informe o número no bloco Renova.");
        s.assertThat(venda(id).get("pagamentos")).as("pagamentos").isEmpty();
    }

    @Test
    @DisplayName("Renova: finaliza sem linha de voucher, com o número na venda")
    void finalizarComRenova() throws Exception {
        long id = vendaComAparelho(null);
        ligarRenova(id, "V1");
        pagar(id, "DINHEIRO", "10699.00", 1);

        s.assertThat(finalizar(id).status).as("finalizar").isEqualTo(200);
    }

    // ------------------------------------------------------------------ apoio

    private long vendaEm21xComEntrada(String valorChip) throws Exception {
        long id = vendaComAparelho(valorChip);
        condicao(id, "PARCELADO_21");
        pagar(id, "DINHEIRO", "2000.00", 1);
        return id;
    }

    private long vendaComAparelho(String valorChip) throws Exception {
        JsonNode aberta = exigir(201, post("/vendas"), Map.of("clienteId", clienteId, "filialId", filialId,
                "estoqueAvancado", false));
        long id = aberta.get("id").asLong();

        Map<String, Object> item = new HashMap<>();
        item.put("categoria", "PRODUTO_VIVO");
        item.put("produtoId", aparelho.getId());
        item.put("descricaoProduto", MODELO);
        item.put("quantidade", 1);
        item.put("imeiOuSerial", aparelho.getSerial());
        item.put("plano", PLANO);
        item.put("valorChip", valorChip);
        exigir(201, post("/vendas/{id}/itens", id), item);
        return id;
    }

    private JsonNode adicionarChip(long id, String valorUnitario, String valorChip) throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("categoria", "SERVICO_VIVO");
        item.put("descricaoProduto", "Chip");
        item.put("quantidade", 1);
        item.put("valorUnitario", valorUnitario);
        item.put("valorChip", valorChip);
        return exigir(201, post("/vendas/{id}/itens", id), item);
    }

    private JsonNode condicao(long id, String condicao) throws Exception {
        return exigir(200, patch("/vendas/{id}/condicao-pagamento", id), Map.of("condicao", condicao));
    }

    private JsonNode pagar(long id, String forma, String valor, int parcelas) throws Exception {
        Resposta r = pagamento(id, forma, valor, parcelas);
        if (r.status != 201) {
            throw new AssertionError("Pagamento " + forma + " " + valor + ": HTTP " + r.status + " " + r.corpo);
        }
        return r.corpo;
    }

    private Resposta pagamento(long id, String forma, String valor, int parcelas) throws Exception {
        return enviar(post("/vendas/{id}/pagamentos", id), Map.of("forma", forma, "valor", valor, "parcelas", parcelas));
    }

    private JsonNode financiar(long id) throws Exception {
        return exigir(200, post("/vendas/{id}/financiar", id), Map.of());
    }

    private JsonNode ligarRenova(long id, String voucher) throws Exception {
        return exigir(200, patch("/vendas/{id}/renova", id), Map.of("ativo", true, "codigoVoucher", voucher));
    }

    private Resposta finalizar(long id) throws Exception {
        return enviar(patch("/vendas/{id}/finalizar", id), Map.of("autenticacaoVendedor", autenticacao()));
    }

    private JsonNode venda(long id) throws Exception {
        MvcResult r = mvc.perform(get("/vendas/{id}", id)).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Map<String, String> autenticacao() {
        return Map.of("email", email, "senha", SENHA);
    }

    private JsonNode exigir(int status, MockHttpServletRequestBuilder pedido) throws Exception {
        return exigir(status, pedido, null);
    }

    private JsonNode exigir(int status, MockHttpServletRequestBuilder pedido, Object corpo) throws Exception {
        Resposta r = enviar(pedido, corpo);
        if (r.status != status) {
            throw new AssertionError("Esperado HTTP " + status + ", obtido " + r.status + ": " + r.corpo);
        }
        return r.corpo;
    }

    private Resposta enviar(MockHttpServletRequestBuilder pedido, Object corpo) throws Exception {
        if (corpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        }
        MvcResult r = mvc.perform(pedido).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Resposta(r.getResponse().getStatus(), texto.isBlank() ? json.createObjectNode() : json.readTree(texto));
    }

    private JsonNode financiado(JsonNode venda) {
        return linha(venda, "FINANCIADO");
    }

    private JsonNode linha(JsonNode venda, String tipo) {
        for (JsonNode p : venda.get("pagamentos")) {
            if (tipo.equals(p.get("tipo").asText())) return p;
        }
        throw new AssertionError("Sem linha " + tipo + " em " + venda.get("pagamentos"));
    }

    private void valor(JsonNode no, String campo, String esperado) {
        s.assertThat(new BigDecimal(no.get(campo).asText())).as(campo).isEqualByComparingTo(esperado);
    }

    /** A execução mais nova é a vigente: cada chamada publica uma tabela com esses preços. */
    private void tabelaVigente(String cheio, String pix, String total21, String cheioRenova, String pixRenova,
                               String total21Renova, String desconto) {
        TabelaPrecoExecucao execucao = execucoes.save(TabelaPrecoExecucao.builder().nomeArquivo("sintetica.xlsx")
                .totalLinhasProcessadas(2).totalProdutosAtualizados(0).totalNaoEncontrados(0).build());
        precos.saveAll(List.of(
                preco(execucao, TabelaPreco.REGULAR, cheio, pix, total21, null),
                preco(execucao, TabelaPreco.RENOVA, cheioRenova, pixRenova, total21Renova, desconto)));
    }

    private PrecoItem preco(TabelaPrecoExecucao execucao, TabelaPreco tabela, String cheio, String pix, String total21,
                            String desconto) {
        return PrecoItem.builder().execucao(execucao).aba("SMARTPHONES").tabela(tabela)
                .grupoPlano(GrupoPlano.CONTROLE_ALTO_VALOR).nomeComercial(MODELO).chaveNome(TextoUtil.chave(MODELO))
                .preco(new BigDecimal(cheio)).precoPix(new BigDecimal(pix)).parcelas(Map.of(21, new BigDecimal(total21)))
                .descontoFabricante(desconto == null ? null : new BigDecimal(desconto)).build();
    }

    private record Resposta(int status, JsonNode corpo) {
    }
}
