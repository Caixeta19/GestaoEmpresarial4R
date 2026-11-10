package com.vivo4redes.syscor.gestao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.gestao.enums.TipoPlano;
import com.vivo4redes.syscor.venda.enums.TipoPessoa;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.ClienteRepository;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Gestão documental ligada à API (docs/gestao.md), com filtros, JWT e PostgreSQL reais. Cada teste usa uma filial
 * própria e filtra por ela, para não enxergar as vendas dos outros testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProtocoloDocumentalIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA = "senha-de-teste";
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.now(ZONA);
    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepository clientes;
    @Autowired FilialRepository filiais;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transacao;

    private int n;
    private long clienteId;
    private long filialId;
    private Usuario vendedor;
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        n = SEQUENCIA.incrementAndGet();
        clienteId = clientes.save(Cliente.builder().tipoPessoa(TipoPessoa.FISICA).nome("Cliente Doc " + n)
                .cpfCnpj("7" + String.format("%010d", n)).build()).getId();
        filialId = filiais.save(Filial.builder().codigo("DOC" + n).nome("Filial Doc " + n).build()).getId();
        vendedor = usuarios.save(Usuario.builder().nome("Vendedor Doc").login("doc" + n).email("doc" + n + "@teste.local")
                .senhaHash(encoder.encode(SENHA)).primeiroAcesso(false).filial("Filial Doc").cargo("VENDEDOR").build());
        token = entrar();
    }

    // ------------------------------------------------------------------ busca

    @Test
    @DisplayName("Busca pelo número da venda acha a venda lançada, com status BKO Não avaliado e tipo de plano")
    void buscaPorNumeroVenda() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Troca de Aparelho", "POS_INDIVIDUAL", "Pós", "LINHA ADICIONAL VIVO FAMÍLIA");

        JsonNode pagina = buscar(Map.of("numeroVenda", numeroDa(id)));

        assertThat(total(pagina)).isEqualTo(1);
        JsonNode v = pagina.get("content").get(0);
        assertThat(v.get("vendaId").asLong()).isEqualTo(id);
        assertThat(v.get("numeroVenda").asText()).isEqualTo(numeroDa(id));
        assertThat(v.get("statusBko").asText()).as("venda sem avaliação").isEqualTo("Nao_avaliado");
        assertThat(v.get("statusVenda").asText()).isEqualTo("CONCLUIDA");
        assertThat(v.get("tipoPlano").asText()).isEqualTo("POS");
        assertThat(v.get("servico").asText()).isEqualTo("Troca de Aparelho");
        assertThat(v.get("numeroAcesso").asText()).isEqualTo("61 998674575");
        assertThat(v.get("filialNome").asText()).isEqualTo("Filial Doc " + n);
        assertThat(v.get("vendedorNome").asText()).isEqualTo("Vendedor Doc");
        assertThat(v.get("protocoloId").isNull()).as("ainda sem protocolo").isTrue();
    }

    @Test
    @DisplayName("Busca sem resultado: 200 com a lista vazia, e não 404")
    void buscaSemResultado() throws Exception {
        JsonNode pagina = buscar(Map.of("numeroVenda", "999999999"));

        assertThat(total(pagina)).isZero();
        assertThat(pagina.get("content")).isEmpty();
    }

    @Test
    @DisplayName("Só vendas lançadas com item de serviço; canceladas só com incluirCanceladas")
    void soLancadasDeServico() throws Exception {
        long lancada = vendaLancada(null, HOJE);
        servico(lancada, "11911110001", "Alta", "CONTROLE_ALTO_VALOR", "Controle", "CONTROLE");
        long cancelada = venda("CANCELADA", null, HOJE.minusDays(1));
        servico(cancelada, "11911110002", "Alta", "PRE", "Pré", "PRE");
        long aberta = venda("ABERTA", null, HOJE);
        servico(aberta, "11911110003", "Alta", "PRE", "Pré", "PRE");
        long soAcessorio = vendaLancada(null, HOJE);
        jdbc.update("""
                INSERT INTO itens_venda (venda_id, categoria, produto_id, descricao_produto, quantidade, valor_unitario)
                VALUES (?, 'ACESSORIO', 1, 'Capa', 1, 50)""", soAcessorio);

        assertThat(ids(buscar(daFilial()))).containsExactly(lancada);
        Map<String, String> comCanceladas = daFilial();
        comCanceladas.put("incluirCanceladas", "true");
        assertThat(ids(buscar(comCanceladas))).containsExactly(lancada, cancelada);
    }

    @Test
    @DisplayName("Filtro de status BKO: Nao_avaliado inclui a venda sem avaliação; Procedente só a procedente")
    void filtroStatusBko() throws Exception {
        long semAvaliacao = vendaLancada(null, HOJE);
        long naoAvaliado = vendaLancada("Nao_avaliado", HOJE.minusDays(1));
        long procedente = vendaLancada("Procedente", HOJE.minusDays(2));
        for (long id : List.of(semAvaliacao, naoAvaliado, procedente)) {
            servico(id, "1191111" + id, "Alta", "PRE", "Pré", "PRE");
        }

        Map<String, String> fila = daFilial();
        fila.put("statusBko", "Nao_avaliado");
        assertThat(ids(buscar(fila))).containsExactly(semAvaliacao, naoAvaliado);
        Map<String, String> procedentes = daFilial();
        procedentes.put("statusBko", "Procedente");
        assertThat(ids(buscar(procedentes))).containsExactly(procedente);
    }

    @Test
    @DisplayName("Filtros de item: tipo de plano (grupo ou segmento), serviço sem acento, nº de acesso e período")
    void filtrosDeItem() throws Exception {
        long pos = vendaLancada(null, HOJE);
        servico(pos, "61 998674575", "Troca de numero", "VIM_20GB", null, "VIM 20GB");
        long pre = vendaLancada(null, HOJE);
        servico(pre, "61 988887777", "Alta", null, "Pré", "PRE");
        long antiga = vendaLancada(null, HOJE.minusDays(40));
        servico(antiga, "61 977776666", "Alta", "PRE", "Pré", "PRE");

        Map<String, String> soPre = daFilial();
        soPre.put("tiposPlano", "PRE");
        assertThat(ids(buscar(soPre))).containsExactlyInAnyOrder(pre, antiga);

        Map<String, String> controleEPos = daFilial();
        controleEPos.put("tiposPlano", "CONTROLE,POS");
        assertThat(ids(buscar(controleEPos))).as("VIM conta como Pós").containsExactly(pos);

        Map<String, String> servico = daFilial();
        servico.put("servicos", "Troca de número");
        assertThat(ids(buscar(servico))).as("'Troca de número' = 'Troca de numero'").containsExactly(pos);

        Map<String, String> acesso = daFilial();
        acesso.put("numeroAcesso", "(61) 99867-4575");
        assertThat(ids(buscar(acesso))).containsExactly(pos);

        Map<String, String> periodo = daFilial();
        periodo.put("dataInicio", HOJE.minusDays(30).toString());
        periodo.put("dataFim", HOJE.toString());
        assertThat(ids(buscar(periodo))).containsExactlyInAnyOrder(pos, pre);
    }

    // ------------------------------------------------------------------ salvar

    @Test
    @DisplayName("Salvar protocolo de venda ABERTA ou CANCELADA: 422 com a mensagem")
    void salvarForaDeLancadaRecusa() throws Exception {
        long aberta = venda("ABERTA", null, HOJE);
        long cancelada = venda("CANCELADA", null, HOJE);

        Resposta r = enviar(put("/documental/protocolos/{id}", aberta), Map.of("numeroProtocoloGed", "GED-1"));
        assertThat(r.status).isEqualTo(422);
        assertThat(r.corpo.get("mensagem").asText())
                .isEqualTo("Só vendas lançadas recebem protocolo documental (venda #" + numeroDa(aberta) + " está ABERTA).");
        assertThat(enviar(put("/documental/protocolos/{id}", cancelada), Map.of("numeroProtocoloGed", "GED-1")).status).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_protocolo_documental WHERE venda_id IN (?, ?)",
                Integer.class, aberta, cancelada)).isZero();
    }

    @Test
    @DisplayName("Salvar protocolo de venda lançada: grava e a busca acha por GED e por líder de equipe")
    void salvarEBuscarPorProtocolo() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "11911112222", "Alta", "PRE", "Pré", "PRE");
        String ged = "GED-" + n;

        Resposta r = enviar(put("/documental/protocolos/{id}", id), Map.of("numeroProtocoloGed", ged,
                "liderEquipe", "José Líder", "statusBko", "Em_avaliacao_pelo_BKO", "dataDigitalizacao", HOJE.toString()));

        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("statusBko").asText()).isEqualTo("Em_avaliacao_pelo_BKO");
        JsonNode detalhe = enviar(get("/documental/protocolos/{id}", id), null).corpo;
        assertThat(detalhe.get("numeroProtocoloGed").asText()).isEqualTo(ged);
        assertThat(detalhe.get("dataDigitalizacao").asText()).isEqualTo(HOJE.toString());
        assertThat(ids(buscar(Map.of("numeroProtocoloGed", ged.toLowerCase())))).containsExactly(id);
        Map<String, String> lider = daFilial();
        lider.put("liderEquipe", "jose lider");
        assertThat(ids(buscar(lider))).containsExactly(id);
    }

    @Test
    @DisplayName("Reanálise: Improcedente não cancela; volta para Em avaliação com a correção na observação; Procedente é final")
    void reanaliseDepoisDeImprocedente() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "11911113333", "Alta", "PRE", "Pré", "PRE");

        Resposta improcedente = enviar(put("/documental/protocolos/{id}", id),
                Map.of("statusBko", "Improcedente", "observacoes", "Falta o RG do cliente"));
        assertThat(improcedente.status).isEqualTo(200);
        assertThat(improcedente.corpo.get("statusBko").asText()).isEqualTo("Improcedente");
        assertThat(improcedente.corpo.get("statusVenda").asText()).as("continua lançada").isEqualTo("CONCLUIDA");

        Resposta semCorrecao = enviar(put("/documental/protocolos/{id}", id),
                Map.of("statusBko", "Em_avaliacao_pelo_BKO", "observacoes", "Falta o RG do cliente"));
        assertThat(semCorrecao.status).as("observação igual: sem correção").isEqualTo(422);
        assertThat(semCorrecao.corpo.get("mensagem").asText()).contains("descreva a correção");

        Resposta voltou = enviar(put("/documental/protocolos/{id}", id), Map.of("statusBko", "Em_avaliacao_pelo_BKO",
                "observacoes", "Falta o RG do cliente. Correção: RG anexado em 10/11."));
        assertThat(voltou.status).as("dá para salvar de novo").isEqualTo(200);
        assertThat(voltou.corpo.get("statusBko").asText()).isEqualTo("Em_avaliacao_pelo_BKO");
        Map<String, String> emAvaliacao = daFilial();
        emAvaliacao.put("statusBko", "Em_avaliacao_pelo_BKO");
        assertThat(ids(buscar(emAvaliacao))).as("aparece na busca sem incluir canceladas").containsExactly(id);

        assertThat(enviar(put("/documental/protocolos/{id}", id), Map.of("statusBko", "Procedente")).corpo
                .get("statusBko").asText()).isEqualTo("Procedente");
        assertThat(jdbc.queryForObject("SELECT status FROM vendas WHERE id = ?", String.class, id)).isEqualTo("CONCLUIDA");

        Resposta depoisDeProcedente = enviar(put("/documental/protocolos/{id}", id), Map.of("statusBko", "Em_avaliacao_pelo_BKO"));
        assertThat(depoisDeProcedente.status).as("Procedente é final").isEqualTo(422);
        assertThat(enviar(put("/documental/protocolos/{id}", id), Map.of("statusBko", "Procedente", "liderEquipe", "Ana"))
                .status).as("o resto do protocolo ainda pode ser salvo").isEqualTo(200);
    }

    // ------------------------------------------------------------------ importação

    @Test
    @DisplayName("Importação com uma linha boa e uma ruim: grava a boa e lista o erro da outra")
    void importacaoBoaERuim() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Alta", "PRE", "Pré", "PRE");
        String csv = "﻿NUMERO_VENDA;PROTOCOLO_GED;STATUS_BKO;NUMERO_ACESSO;DATA_DIGITALIZACAO\n"
                + numeroDa(id) + ";GED-IMP-" + n + ";Procedente;(61) 99867-4575;01/11/2026\n"
                + "999999999;GED-X;Procedente;;01/11/2026\n";

        JsonNode r = importar(csv);

        assertThat(r.get("total").asInt()).isEqualTo(2);
        assertThat(r.get("sucessos").asInt()).isEqualTo(1);
        assertThat(r.get("erros")).hasSize(1);
        assertThat(r.get("erros").get(0).get("linha").asInt()).isEqualTo(3);
        assertThat(r.get("erros").get(0).get("numeroVenda").asText()).isEqualTo("999999999");
        assertThat(r.get("erros").get(0).get("motivo").asText()).isEqualTo("Venda 999999999 não encontrada.");

        JsonNode detalhe = enviar(get("/documental/protocolos/{id}", id), null).corpo;
        assertThat(detalhe.get("numeroProtocoloGed").asText()).isEqualTo("GED-IMP-" + n);
        assertThat(detalhe.get("statusBko").asText()).isEqualTo("Procedente");
        assertThat(detalhe.get("dataDigitalizacao").asText()).isEqualTo("2026-11-01");
    }

    @Test
    @DisplayName("Importação com vírgula: acesso de outra venda, data e status inválidos viram erro; o resto do protocolo fica")
    void importacaoValidaCadaLinha() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Alta", "PRE", "Pré", "PRE");
        assertThat(enviar(put("/documental/protocolos/{id}", id),
                Map.of("numeroProtocoloGed", "GED-ANTES", "liderEquipe", "Maria")).status).isEqualTo(200);
        String numero = numeroDa(id);
        String csv = "NUMERO_VENDA,PROTOCOLO_GED,STATUS_BKO,NUMERO_ACESSO,DATA_DIGITALIZACAO\n"
                + numero + ",GED-1,,11 900000000,\n"
                + numero + ",GED-2,,,31/02/2026\n"
                + numero + ",GED-3,Talvez,,\n"
                + "abc,GED-4,,,\n"
                + numero + ",,Procedente,,\n";

        JsonNode r = importar(csv);

        assertThat(r.get("total").asInt()).isEqualTo(5);
        assertThat(r.get("sucessos").asInt()).isEqualTo(1);
        assertThat(r.get("erros").findValuesAsText("linha")).containsExactly("2", "3", "4", "5");
        JsonNode detalhe = enviar(get("/documental/protocolos/{id}", id), null).corpo;
        assertThat(detalhe.get("numeroProtocoloGed").asText()).as("GED vazio na planilha não apaga").isEqualTo("GED-ANTES");
        assertThat(detalhe.get("liderEquipe").asText()).as("campo fora da planilha fica").isEqualTo("Maria");
        assertThat(detalhe.get("statusBko").asText()).isEqualTo("Procedente");
    }

    // ------------------------------------------------------------------ exportação

    /** As 55 colunas do relatório do sistema atual, escritas aqui de novo a partir do docs/gestao.md. */
    private static final String CABECALHO = "Filial;Data Venda(*);Data Ativação(**);Status BKO(*)(**);Serviço(*);ID Venda;"
            + "Modelo;IMEI;Plano;Valor plano(**);Plano Antigo;Valor plano antigo(**);Vencimento Fatura(**);Vendedor;"
            + "Cliente;CPF/CNPJ;Nº Acesso(*);Nº Portado;Servico Instalado;Data Agendada de Instalação;Armário;"
            + "Data Instalação;Situação do serviço(**);Receita;Status GED;Sim Card;COD;Fidelização;Zerar Remuneração;"
            + "Gerar Price;Nº Solicitação NEXT / RPON;Nº Protocolo GED(**);Nº Protocolo GED Portabilidade (**);"
            + "Motivo Reprovação(**);Data Digitalização;Usuário Inserção;Usuário Alteração;Observação;"
            + "Motivo Cancelamento(**);Comissão de Serviço do Vendedor;Comissão de Aparelho do Vendedor(**);"
            + "Data Última Ativação(**);Data de alteração;Usuário Cancelamento;Data Cancelamento;Líder de Equipe;"
            + "Status Primeira Fatura;Data Primeira Fatura;Status Segunda Fatura;Data Segunda Fatura;"
            + "Status Terceira Fatura;Data Terceira Fatura;Valor Receita;Valor Considerado TFP;Valor Penalizado TFP";

    @Test
    @DisplayName("Exportação: preâmbulo, cabeçalho de 55 colunas e rodapé do layout, em Windows-1252 com LF")
    void exportacaoLayout() throws Exception {
        MvcResult r = mvc.perform(comParametros(get("/documental/protocolos/exportacao"), doMes())).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).isEqualTo("text/csv;charset=windows-1252");
        assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("gestao_documental.csv");
        String csv = csv(doMes());
        assertThat(csv).doesNotContain("\r").doesNotContain("\"");
        String[] linhas = csv.split("\n", -1);
        assertThat(linhas[0]).matches("Data da Emissão do relatório;\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");
        assertThat(linhas[1]).isEmpty();
        assertThat(linhas[2]).isEqualTo("Revenda: 4 REDES");
        assertThat(linhas[3]).isEmpty();
        assertThat(linhas[4]).isEqualTo(CABECALHO);
        assertThat(linhas[4].split(";", -1)).hasSize(55);
        assertThat(csv).endsWith("\n\n\n(*) Campos obrigatórios;\n(**) Campos que podem ser alterados;\n");
    }

    @Test
    @DisplayName("Exportação em ISO-8859-1/Windows-1252: 'Não' e 'Situação' com um byte por letra acentuada")
    void exportacaoAcentos() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Alta", "PRE", "Pré", "PRE");

        byte[] bytes = mvc.perform(comParametros(get("/documental/protocolos/exportacao"), doMes()))
                .andReturn().getResponse().getContentAsByteArray();

        String texto = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(texto).contains("Situação do serviço(**)").contains(";Não avaliado;");
        assertThat(new String(bytes, StandardCharsets.UTF_8)).as("não é UTF-8").doesNotContain("Situação");
    }

    @Test
    @DisplayName("Exportação: status Nao_avaliado traz a venda recém-lançada; com Procedente, nenhuma linha")
    void exportacaoPorStatusBko() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 998674575", "Troca de Plano", "POS_INDIVIDUAL", "Pós", "PLANO POS");
        Map<String, String> filtro = doMes();
        filtro.put("statusBko", "Nao_avaliado");

        List<Map<String, String>> linhas = linhasCsv(filtro);

        assertThat(linhas).hasSize(1);
        Map<String, String> l = linhas.get(0);
        assertThat(l.get("Filial")).isEqualTo("Filial Doc " + n);
        assertThat(l.get("Data Venda(*)")).as("só a data").isEqualTo(HOJE.format(DD_MM_AAAA));
        assertThat(l.get("Status BKO(*)(**)")).isEqualTo("Não avaliado");
        assertThat(l.get("ID Venda")).isEqualTo(numeroDa(id));
        assertThat(l.get("Plano")).isEqualTo("PLANO POS");
        assertThat(l.get("Plano Antigo")).as("sem plano antigo").isEqualTo("-");
        assertThat(l.get("Valor plano antigo(**)")).isEqualTo("0.00");
        assertThat(l.get("Vendedor")).isEqualTo("Vendedor Doc");
        assertThat(l.get("Cliente")).isEqualTo("Cliente Doc " + n);
        assertThat(l.get("Nº Acesso(*)")).isEqualTo("61 998674575");
        assertThat(l.get("Situação do serviço(**)")).as("sem protocolo").isEqualTo("Não avaliado");
        assertThat(l.get("Status GED")).isEqualTo("Serviço Não digitalizado");
        assertThat(l.get("Modelo")).isEmpty();
        assertThat(l.get("Status Primeira Fatura")).as("sem origem: vazia, mas presente").isEmpty();

        filtro.put("statusBko", "Procedente");
        assertThat(linhasCsv(filtro)).isEmpty();
    }

    @Test
    @DisplayName("Exportação: Serviço composto, Receita por tipo e pré-pago com PRE PAGO, 0.00 e vencimento 0")
    void exportacaoServicoEReceita() throws Exception {
        long id = vendaLancada(null, HOJE);
        long trocaPlano = servico(id, "61 911110001", "Troca de Plano", "CONTROLE_BTL", "Controle", "CONTROLE 10GB");
        valores(trocaPlano, "Smartphone", "49.00", "CONTROLE 5GB", "35.00");
        long migracao = servico(id, "61 911110002", "Migração", "POS_INDIVIDUAL", "Pós", "POS 50GB");
        valores(migracao, "Smartphone", "50.00", "POS 100GB", "75.00");
        long simcard = servico(id, "61 911110003", "Troca de Simcard", "POS_INDIVIDUAL", "Pós", "POS 50GB");
        valores(simcard, "Voz", "99.00", null, null);
        long reativacao = servico(id, "61 911110004", "Reativação", "CONTROLE_BTL", "Controle", "CONTROLE 10GB");
        valores(reativacao, "Smartphone", "60.00", null, null);
        long pre = servico(id, "61 911110005", "Alta", "PRE", "Pré", "PRE MENSAL");
        valores(pre, "Voz", "20.00", null, null);
        jdbc.update("UPDATE itens_venda SET vencimento_fatura = '10' WHERE id = ?", pre);

        Map<String, Map<String, String>> porAcesso = new HashMap<>();
        linhasCsv(doMes()).forEach(l -> porAcesso.put(l.get("Nº Acesso(*)"), l));

        assertThat(porAcesso.get("61 911110001").get("Serviço(*)")).isEqualTo("Troca de Plano Smartphone Controle");
        assertThat(porAcesso.get("61 911110001").get("Valor plano(**)")).isEqualTo("49.00");
        assertThat(porAcesso.get("61 911110001").get("Receita")).as("49.00 - 35.00").isEqualTo("14.00");
        assertThat(porAcesso.get("61 911110002").get("Receita")).as("50.00 - 75.00, nunca negativa").isEqualTo("0.00");
        assertThat(porAcesso.get("61 911110003").get("Serviço(*)")).isEqualTo("Troca de Simcard Voz Pós");
        assertThat(porAcesso.get("61 911110003").get("Receita")).as("troca de simcard").isEqualTo("0.00");
        assertThat(porAcesso.get("61 911110004").get("Serviço(*)")).as("reativação sem tipo de linha")
                .isEqualTo("Reativação Controle");
        Map<String, String> l = porAcesso.get("61 911110005");
        assertThat(l.get("Serviço(*)")).isEqualTo("Alta Voz Pré");
        assertThat(l.get("Plano")).isEqualTo("PRE PAGO");
        assertThat(l.get("Valor plano(**)")).isEqualTo("0.00");
        assertThat(l.get("Vencimento Fatura(**)")).isEqualTo("0");
        assertThat(l.get("Receita")).isEqualTo("0.00");
    }

    @Test
    @DisplayName("Exportação: aparelho + 2 serviços = 3 linhas com o mesmo ID Venda; só a Troca de Aparelho tem Modelo e IMEI")
    void exportacaoUmaLinhaPorServico() throws Exception {
        long id = vendaLancada(null, HOJE);
        servico(id, "61 911110001", "Alta", "PRE", "Pré", "PRE MENSAL");
        servico(id, "61 911110002", "Migração", "CONTROLE_BTL", "Controle", "CONTROLE 10GB");
        jdbc.update("""
                INSERT INTO itens_venda (venda_id, categoria, produto_id, descricao_produto, quantidade, valor_unitario,
                                         serial_imei, grupo_plano)
                VALUES (?, 'PRODUTO_VIVO', 1, 'Galaxy Teste', 1, 1500, ?, 'CONTROLE_BTL')""", id, "35000000000000" + n);

        List<Map<String, String>> linhas = linhasCsv(doMes());

        assertThat(linhas).hasSize(3);
        assertThat(linhas).extracting(l -> l.get("ID Venda")).containsOnly(numeroDa(id));
        Map<String, String> aparelho = linhas.stream().filter(l -> l.get("Serviço(*)").startsWith("Troca de Aparelho"))
                .findFirst().orElseThrow();
        assertThat(aparelho.get("Serviço(*)")).isEqualTo("Troca de Aparelho Controle");
        assertThat(aparelho.get("Modelo")).isEqualTo("Galaxy Teste");
        assertThat(aparelho.get("IMEI")).isEqualTo("35000000000000" + n);
        assertThat(aparelho.get("Receita")).isEqualTo("0.00");
        assertThat(linhas).filteredOn(l -> l != aparelho).allSatisfy(l -> {
            assertThat(l.get("Modelo")).isEmpty();
            assertThat(l.get("IMEI")).isEmpty();
        });

        Map<String, String> soMigracao = doMes();
        soMigracao.put("servicos", "migracao");
        assertThat(linhasCsv(soMigracao)).extracting(l -> l.get("Serviço(*)")).containsExactly("Migração Controle");
        Map<String, String> soAparelho = doMes();
        soAparelho.put("servicos", "Troca de Aparelho");
        assertThat(linhasCsv(soAparelho)).extracting(l -> l.get("Modelo")).containsExactly("Galaxy Teste");
        Map<String, String> soPre = doMes();
        soPre.put("tiposPlano", "PRE");
        assertThat(linhasCsv(soPre)).extracting(l -> l.get("Plano")).containsExactly("PRE PAGO");
    }

    @Test
    @DisplayName("Exportação: ordem filial, data, avaliados antes de Não avaliado e ID Venda")
    void exportacaoOrdem() throws Exception {
        long naoAvaliadaAntes = vendaLancada(null, HOJE);
        long procedenteDepois = vendaLancada("Procedente", HOJE);
        servico(naoAvaliadaAntes, "61 911110001", "Alta", "PRE", "Pré", "PRE");
        servico(procedenteDepois, "61 911110002", "Alta", "PRE", "Pré", "PRE");

        assertThat(linhasCsv(doMes())).extracting(l -> l.get("ID Venda"))
                .containsExactly(numeroDa(procedenteDepois), numeroDa(naoAvaliadaAntes));
    }

    @Test
    @DisplayName("Exportação: protocolo GED '' quando vazio e '123' quando preenchido; campos novos e Sim/Não")
    void exportacaoCamposDoProtocolo() throws Exception {
        long id = vendaLancada(null, HOJE);
        long item = servico(id, "61 998674575", "Alta", "CONTROLE_BTL", "Controle", "CONTROLE");
        jdbc.update("UPDATE itens_venda SET simcard_4g = '8955000011', num_ordem_next = '1-ABC', numero_portado = "
                + "'61 33334444', fidelizacao = TRUE, cod = FALSE WHERE id = ?", item);

        Map<String, String> antes = linhasCsv(doMes()).get(0);
        assertThat(antes.get("Nº Protocolo GED(**)")).isEqualTo("''");
        assertThat(antes.get("Nº Protocolo GED Portabilidade (**)")).isEqualTo("''");
        assertThat(antes.get("Motivo Reprovação(**)")).isEqualTo("''");
        assertThat(antes.get("Sim Card")).isEqualTo("'8955000011'");
        assertThat(antes.get("Nº Solicitação NEXT / RPON")).isEqualTo("'1-ABC'");
        assertThat(antes.get("Data de alteração")).isEmpty();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("numeroProtocoloGed", "123");
        corpo.put("numeroProtocoloGedPortabilidade", "PORT-" + n);
        corpo.put("dataAtivacao", "2026-11-02");
        corpo.put("dataInstalacao", "2026-11-04");
        corpo.put("dataAgendadaInstalacao", "2026-11-03");
        corpo.put("dataUltimaAtivacao", "2026-11-05");
        corpo.put("armario", "ARM-7");
        corpo.put("servicoInstalado", true);
        corpo.put("motivoReprovacao", "Documento ilegível");
        corpo.put("comissaoAparelho", false);
        corpo.put("zerarRemuneracao", false);
        corpo.put("observacoes", "linha 1\nlinha 2; fim");
        corpo.put("motivosCancelamento", List.of("Desistência", "Fraude"));
        corpo.put("situacaoServico", "CONFIRMADO");
        corpo.put("liderEquipe", "Ana Líder");
        Resposta r = enviar(put("/documental/protocolos/{id}", id), corpo);
        assertThat(r.status).isEqualTo(200);
        assertThat(r.corpo.get("armario").asText()).isEqualTo("ARM-7");
        assertThat(r.corpo.get("comissaoAparelho").asBoolean()).isFalse();

        Map<String, String> l = linhasCsv(doMes()).get(0);
        assertThat(l.get("Nº Protocolo GED(**)")).isEqualTo("'123'");
        assertThat(l.get("Nº Protocolo GED Portabilidade (**)")).isEqualTo("'PORT-" + n + "'");
        assertThat(l.get("Motivo Reprovação(**)")).isEqualTo("'Documento ilegível'");
        assertThat(l.get("Status GED")).as("GED sem digitalização").isEqualTo("Aguardando digitalização do serviço");
        assertThat(l.get("Data Ativação(**)")).isEqualTo("02/11/2026");
        assertThat(l.get("Data Agendada de Instalação")).isEqualTo("03/11/2026");
        assertThat(l.get("Data Instalação")).isEqualTo("04/11/2026");
        assertThat(l.get("Data Última Ativação(**)")).isEqualTo("05/11/2026");
        assertThat(l.get("Armário")).isEqualTo("ARM-7");
        assertThat(l.get("Nº Portado")).isEqualTo("61 33334444");
        assertThat(l.get("Servico Instalado")).isEqualTo("Sim");
        assertThat(l.get("Fidelização")).isEqualTo("Sim");
        assertThat(l.get("COD")).isEqualTo("Não");
        assertThat(l.get("Zerar Remuneração")).isEqualTo("Não");
        assertThat(l.get("Gerar Price")).isEqualTo("Não");
        assertThat(l.get("Comissão de Serviço do Vendedor")).isEqualTo("Sim");
        assertThat(l.get("Comissão de Aparelho do Vendedor(**)")).isEqualTo("Não");
        assertThat(l.get("Situação do serviço(**)")).isEqualTo("Confirmado");
        assertThat(l.get("Observação")).as("sem quebra de linha nem ;").isEqualTo("linha 1 linha 2, fim");
        assertThat(l.get("Motivo Cancelamento(**)")).isEqualTo("Desistência, Fraude");
        assertThat(l.get("Usuário Alteração")).isEqualTo("Vendedor Doc");
        assertThat(l.get("Líder de Equipe")).isEqualTo("Ana Líder");
        assertThat(l.get("Data de alteração")).as("com hora").matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("View: tipo_plano igual ao TipoPlano.de para todo GrupoPlano e para os segmentos sem grupo")
    void viewTipoPlanoIgualAoEnum() {
        long id = vendaLancada(null, HOJE);
        List<String[]> casos = new ArrayList<>();
        for (GrupoPlano g : GrupoPlano.values()) casos.add(new String[]{g.name(), "Pós"});
        for (String seg : List.of("Pré", "PRE PAGO", "Controle", "Pós", "pos pago", "Outro")) casos.add(new String[]{null, seg});
        casos.add(new String[]{"SEM_PLANO", "Controle"});
        casos.add(new String[]{null, null});
        for (String[] c : casos) servico(id, "6190000" + casos.indexOf(c), "Alta", c[0], c[1], "P");

        for (Map<String, Object> linha : jdbc.queryForList("""
                SELECT i.grupo_plano, i.segmento, w.tipo_plano FROM vw_gestao_documental w
                JOIN itens_venda i ON i.id = w.item_id WHERE w.venda_id = ?""", id)) {
            String grupo = (String) linha.get("grupo_plano");
            String esperado = TipoPlano.de(grupo == null ? null : GrupoPlano.valueOf(grupo), (String) linha.get("segmento"))
                    .map(Enum::name).orElse(null);
            assertThat(linha.get("tipo_plano")).as(grupo + " / " + linha.get("segmento")).isEqualTo(esperado);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vw_gestao_documental WHERE venda_id = ?", Integer.class, id))
                .isEqualTo(casos.size());
    }

    @Test
    @DisplayName("Exportação sem período ou com mais de 93 dias: 422")
    void exportacaoPeriodo() throws Exception {
        assertThat(mvc.perform(comParametros(get("/documental/protocolos/exportacao"), daFilial()))
                .andReturn().getResponse().getStatus()).isEqualTo(422);

        Map<String, String> longo = daFilial();
        longo.put("dataInicio", HOJE.minusDays(93).toString());
        longo.put("dataFim", HOJE.toString());
        assertThat(mvc.perform(comParametros(get("/documental/protocolos/exportacao"), longo))
                .andReturn().getResponse().getStatus()).as("94 dias").isEqualTo(422);

        longo.put("dataInicio", HOJE.minusDays(92).toString());
        assertThat(mvc.perform(comParametros(get("/documental/protocolos/exportacao"), longo))
                .andReturn().getResponse().getStatus()).as("93 dias").isEqualTo(200);
    }

    // ------------------------------------------------------------------ filiais do relatório

    @Test
    @DisplayName("GET /filiais traz só as ativas; com incluirInativas=true, também as inativas")
    void filiaisInativasNoRelatorio() throws Exception {
        Filial inativa = filiais.save(Filial.builder().codigo("INA" + n).nome("Filial Inativa " + n).ativo(false).build());

        JsonNode ativas = enviar(get("/filiais"), null).corpo;
        JsonNode todas = enviar(get("/filiais").param("incluirInativas", "true"), null).corpo;

        assertThat(ativas.findValuesAsText("id")).contains(String.valueOf(filialId)).doesNotContain(String.valueOf(inativa.getId()));
        assertThat(todas.findValuesAsText("id")).contains(String.valueOf(filialId), String.valueOf(inativa.getId()));
    }

    // ------------------------------------------------------------------ apoio

    private long vendaLancada(String avaliacao, LocalDate dia) {
        return venda("CONCLUIDA", avaliacao, dia);
    }

    /** Venda inserida por SQL; finalizado_em gravado pelo Hibernate, que converte o Instant como na leitura. */
    private long venda(String status, String avaliacao, LocalDate dia) {
        long id = jdbc.queryForObject("""
                INSERT INTO vendas (cliente_id, filial_id, usuario_id, numero_venda, status, avaliacao_procedencia, valor_total)
                VALUES (?, ?, ?, nextval('seq_numero_venda'), ?, ?, 1000) RETURNING id
                """, Long.class, clienteId, filialId, vendedor.getId(), status, avaliacao);
        Instant lancamento = dia.atTime(12, 0).atZone(ZONA).toInstant().plusSeconds(id);
        transacao.executeWithoutResult(s -> em.createQuery("update Venda v set v.finalizadoEm = :t where v.id = :id")
                .setParameter("t", lancamento).setParameter("id", id).executeUpdate());
        return id;
    }

    private long servico(long vendaId, String acesso, String tipoServico, String grupo, String segmento, String plano) {
        return jdbc.queryForObject("""
                INSERT INTO itens_venda (venda_id, categoria, descricao_produto, quantidade, valor_unitario,
                                         numero_acesso, tipo_servico, grupo_plano, segmento, plano)
                VALUES (?, 'SERVICO_VIVO', 'Serviço', 1, 0, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, vendaId, acesso, tipoServico, grupo, segmento, plano);
    }

    /** Tipo de linha, valor do plano e plano antigo (com o valor) de um item de serviço. */
    private void valores(long itemId, String tipoLinha, String valorPlano, String planoAntigo, String valorPlanoAntigo) {
        jdbc.update("UPDATE itens_venda SET tipo_linha = ?, valor_plano = ?::NUMERIC, plano_antigo = ?, "
                + "valor_plano_antigo = ?::NUMERIC WHERE id = ?", tipoLinha, valorPlano, planoAntigo, valorPlanoAntigo, itemId);
    }

    private String numeroDa(long vendaId) {
        return String.valueOf(jdbc.queryForObject("SELECT numero_venda FROM vendas WHERE id = ?", Long.class, vendaId));
    }

    private Map<String, String> daFilial() {
        Map<String, String> filtro = new HashMap<>();
        filtro.put("filialIds", String.valueOf(filialId));
        return filtro;
    }

    /** Filial do teste, do 1º dia do mês até hoje. */
    private Map<String, String> doMes() {
        Map<String, String> filtro = daFilial();
        filtro.put("dataInicio", HOJE.withDayOfMonth(1).toString());
        filtro.put("dataFim", HOJE.toString());
        return filtro;
    }

    private String csv(Map<String, String> filtro) throws Exception {
        MvcResult r = mvc.perform(comParametros(get("/documental/protocolos/exportacao"), filtro)).andReturn();
        assertThat(r.getResponse().getStatus()).as("exportação").isEqualTo(200);
        return new String(r.getResponse().getContentAsByteArray(), Charset.forName("windows-1252"));
    }

    /**
     * Linhas de dados do CSV, cada uma como coluna -> valor: depois do preâmbulo (4 linhas) e do cabeçalho, até a
     * primeira linha vazia (antes do rodapé).
     */
    private List<Map<String, String>> linhasCsv(Map<String, String> filtro) throws Exception {
        String[] linhas = csv(filtro).split("\n", -1);
        String[] cabecalho = linhas[4].split(";", -1);
        List<Map<String, String>> resultado = new ArrayList<>();
        for (int i = 5; i < linhas.length && !linhas[i].isEmpty(); i++) {
            String[] campos = linhas[i].split(";", -1);
            assertThat(campos).as("linha " + i).hasSize(cabecalho.length);
            Map<String, String> linha = new HashMap<>();
            for (int c = 0; c < cabecalho.length; c++) linha.put(cabecalho[c], campos[c]);
            resultado.add(linha);
        }
        return resultado;
    }

    private JsonNode buscar(Map<String, String> filtro) throws Exception {
        Resposta r = enviar(comParametros(get("/documental/protocolos/busca"), filtro), null);
        assertThat(r.status).as("busca").isEqualTo(200);
        return r.corpo;
    }

    private static long total(JsonNode pagina) {
        return pagina.get("page").get("totalElements").asLong();
    }

    private static List<Long> ids(JsonNode pagina) {
        List<Long> ids = new ArrayList<>();
        pagina.get("content").forEach(v -> ids.add(v.get("vendaId").asLong()));
        return ids;
    }

    private JsonNode importar(String csv) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "protocolos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        Resposta r = enviar(multipart("/documental/protocolos/importacao").file(arquivo), null);
        assertThat(r.status).as("importação").isEqualTo(200);
        return r.corpo;
    }

    private MockHttpServletRequestBuilder comParametros(MockHttpServletRequestBuilder pedido, Map<String, String> filtro) {
        filtro.forEach(pedido::param);
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String entrar() throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", vendedor.getLogin(), "senha", SENHA)))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private Resposta enviar(MockHttpServletRequestBuilder pedido, Object corpo) throws Exception {
        pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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
