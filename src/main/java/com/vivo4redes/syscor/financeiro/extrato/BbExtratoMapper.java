package com.vivo4redes.syscor.financeiro.extrato;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Todo o mapeamento dos campos do BB mora aqui e só aqui (docs/bb/OpenAPI_BB_Extratos_v2.json, OpenAPI 2.0.1):
 * <ul>
 *   <li>datas chegam como inteiro {@code DDMMAAAA} sem o zero à esquerda (05/11/2026 vem {@code 5112026}):
 *       preenche com zeros a 8 dígitos antes de ler;</li>
 *   <li>{@code valorLancamento} é número JSON: lido direto como {@link BigDecimal}, nunca por {@code float}/{@code double};</li>
 *   <li>a lista mistura lançamentos e linhas de saldo/limite ({@code SA}, {@code RA}, {@code LE}, {@code SD},
 *       {@code LD}, {@code LC}, {@code LU}): só {@code indicadorTipoLancamento = "1"} (contabilizado) com sinal
 *       {@code D} entra; crédito, futuro (2), em processamento (3) e bloqueado ({@code *}) ficam de fora;</li>
 *   <li>não há hora no lançamento, só a data.</li>
 * </ul>
 */
@Component
public class BbExtratoMapper {

    private static final DateTimeFormatter DDMMAAAA =
            DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);

    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    // ------------------------------------------------------------------ página
    public JsonNode lerCorpo(String corpo) {
        try {
            JsonNode raiz = json.readTree(corpo);
            if (raiz == null || !raiz.isObject()) {
                throw new ExtratoBancarioException("Resposta do extrato em formato inesperado.");
            }
            return raiz;
        } catch (JsonProcessingException e) {
            throw new ExtratoBancarioException("Resposta do extrato não é um JSON válido.");
        }
    }

    /** {@code numeroPaginaProximo}: zero (ou ausente) na última página. */
    public int proximaPagina(JsonNode corpo) {
        return corpo.path("numeroPaginaProximo").asInt(0);
    }

    public List<JsonNode> itens(JsonNode corpo) {
        List<JsonNode> itens = new ArrayList<>();
        JsonNode lista = corpo.path("listaLancamento");
        if (lista.isArray()) lista.forEach(itens::add);
        return itens;
    }

    // ------------------------------------------------------------------ lançamentos
    /**
     * Converte os itens (de todas as páginas de um mesmo período) em débitos contabilizados. Itens idênticos (mesma
     * data, documento, lote, valor e histórico) ganham um número de ocorrência, para o hash do id externo não colidir
     * e continuar estável quando a janela de consulta desliza.
     */
    public List<LancamentoExtrato> mapear(List<JsonNode> itens, String agencia, String conta) {
        List<LancamentoExtrato> saida = new ArrayList<>();
        Map<String, Integer> ocorrencias = new HashMap<>();
        for (JsonNode item : itens) {
            if (!ehDebitoContabilizado(item)) continue;

            BigDecimal valor = valor(item);
            if (valor == null || valor.signum() <= 0) continue;

            LocalDate data = dataInteira(item.path("dataLancamento"));
            if (data == null) {
                throw new ExtratoBancarioException("Débito do extrato sem data de lançamento válida.");
            }
            String tuplaEstavel = String.join("|", String.valueOf(agencia), String.valueOf(conta),
                    texto(item, "dataLancamento"), texto(item, "numeroDocumento"), texto(item, "numeroLote"),
                    valor.toPlainString(), texto(item, "indicadorSinalLancamento"), texto(item, "codigoHistorico"));
            int ocorrencia = ocorrencias.merge(tuplaEstavel, 1, Integer::sum);

            String unico = texto(item, "textoIdentificadorUnicoTransacao");
            String idExterno = unico.isBlank() ? "H:" + sha256(tuplaEstavel + "|" + ocorrencia) : unico.trim();

            saida.add(new LancamentoExtrato(
                    idExterno,
                    data,
                    valor,
                    inteiroLongo(item.path("numeroDocumento")),
                    inteiro(item.path("numeroLote")),
                    inteiro(item.path("codigoHistorico")),
                    aparar(texto(item, "textoDescricaoSubHistorico")),
                    aparar(texto(item, "textoInformacaoComplementar")),
                    documentoContraparte(texto(item, "numeroCadastroPessoaFisicaCadastroNacPessoasJuridicasContrapartida"),
                            texto(item, "indicadorTipoPessoaContrapartida")),
                    aparar(texto(item, "indicadorTipoPessoaContrapartida")),
                    inteiro(item.path("codigoBancoContrapartida")),
                    item.toString()));
        }
        return saida;
    }

    static boolean ehDebitoContabilizado(JsonNode item) {
        return "1".equals(texto(item, "indicadorTipoLancamento").trim())
                && "D".equalsIgnoreCase(texto(item, "indicadorSinalLancamento").trim());
    }

    // ------------------------------------------------------------------ datas
    /** {@code 5112026} vira 05/11/2026; zero, ausente ou inválido vira {@code null}. */
    public static LocalDate dataInteira(JsonNode no) {
        if (no == null || no.isMissingNode() || no.isNull()) return null;
        String digitos = no.isNumber() ? String.valueOf(no.asLong()) : no.asText("").trim();
        if (digitos.isEmpty() || !digitos.chars().allMatch(Character::isDigit) || digitos.length() > 8) return null;
        String preenchido = "0".repeat(8 - digitos.length()) + digitos;
        try {
            return LocalDate.parse(preenchido, DDMMAAAA);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * Data como vai na query ({@code dataInicioSolicitacao}/{@code dataFimSolicitacao}): a spec declara {@code int32},
     * então segue o mesmo formato da resposta, o inteiro {@code DDMMAAAA} sem zero à esquerda (05/11/2026 = 5112026).
     * A aceitação do zero à esquerda deve ser confirmada na homologação.
     */
    public static String dataParaQuery(LocalDate data) {
        return String.valueOf(Integer.parseInt(DDMMAAAA.format(data)));
    }

    // ------------------------------------------------------------------ campos
    private static BigDecimal valor(JsonNode item) {
        JsonNode no = item.path("valorLancamento");
        if (no.isMissingNode() || no.isNull()) return null;
        try {
            BigDecimal valor = no.isNumber() ? no.decimalValue() : new BigDecimal(no.asText().trim().replace(',', '.'));
            return valor.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new ExtratoBancarioException("Débito do extrato com valor inválido.");
        }
    }

    /**
     * CPF/CNPJ da contrapartida (até 15 caracteres, em geral com zeros à esquerda) para 11 (pessoa física) ou 14
     * (jurídica) dígitos, o mesmo formato de {@code documento_fornecedor}. Vazio ou só zeros: sem contrapartida.
     */
    static String documentoContraparte(String bruto, String tipoPessoa) {
        String limpo = bruto == null ? "" : bruto.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        if (limpo.isEmpty() || limpo.chars().allMatch(c -> c == '0')) return null;
        if (!limpo.chars().allMatch(Character::isDigit)) return limpo;
        String semZeros = limpo.replaceFirst("^0+", "");
        boolean fisica = "F".equalsIgnoreCase(tipoPessoa == null ? "" : tipoPessoa.trim())
                || (!"J".equalsIgnoreCase(tipoPessoa == null ? "" : tipoPessoa.trim()) && semZeros.length() <= 11);
        int tamanho = fisica ? 11 : 14;
        if (semZeros.length() > tamanho) return semZeros;
        return "0".repeat(tamanho - semZeros.length()) + semZeros;
    }

    private static String texto(JsonNode item, String campo) {
        JsonNode no = item.path(campo);
        return no.isMissingNode() || no.isNull() ? "" : no.asText("");
    }

    private static String aparar(String texto) {
        String limpo = texto == null ? "" : texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    private static Integer inteiro(JsonNode no) {
        return no.isNumber() ? Integer.valueOf(no.asInt()) : null;
    }

    private static Long inteiroLongo(JsonNode no) {
        return no.isNumber() ? Long.valueOf(no.asLong()) : null;
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
