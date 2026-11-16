package com.vivo4redes.syscor.financeiro.extrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mapeamento do extrato do BB (formato do OpenAPI Extratos v2) com payload sintético, sem rede nem banco. */
class BbExtratoMapperTest {

    private final BbExtratoMapper mapper = new BbExtratoMapper();

    private List<LancamentoExtrato> mapear(String itensJson) {
        JsonNode corpo = mapper.lerCorpo("{\"listaLancamento\":" + itensJson + "}");
        return mapper.mapear(mapper.itens(corpo), "297", "1213");
    }

    private static String debito(String extra) {
        return "{\"indicadorTipoLancamento\":\"1\",\"indicadorSinalLancamento\":\"D\"," + extra + "}";
    }

    @Test
    @DisplayName("Data inteira sem zero à esquerda: 5112026 vira 05/11/2026; valor 1234.5 vira 1234.50 sem erro de float")
    void dataEValor() {
        List<LancamentoExtrato> lista = mapear("[" + debito(
                "\"dataLancamento\":5112026,\"valorLancamento\":1234.5,\"numeroDocumento\":9001,\"numeroLote\":7") + "]");

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).dataLancamento()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(lista.get(0).valor()).hasToString("1234.50");
    }

    @Test
    @DisplayName("Valores que o float estraga (0.1, 19.99, 4.35) saem exatos, direto como BigDecimal")
    void valorSemFloat() {
        List<LancamentoExtrato> lista = mapear("[" + debito("\"dataLancamento\":5112026,\"valorLancamento\":0.1,\"numeroDocumento\":1")
                + "," + debito("\"dataLancamento\":5112026,\"valorLancamento\":19.99,\"numeroDocumento\":2")
                + "," + debito("\"dataLancamento\":5112026,\"valorLancamento\":4.35,\"numeroDocumento\":3") + "]");

        assertThat(lista).extracting(l -> l.valor().toPlainString()).containsExactly("0.10", "19.99", "4.35");
    }

    @Test
    @DisplayName("Só débito contabilizado entra: saldo (SA/SD/RA/LE/LD/LC/LU), crédito, futuro, em processamento e bloqueado ficam de fora")
    void filtro() {
        String saldos = "{\"indicadorTipoLancamento\":\"SA\",\"dataLancamento\":0,\"valorLancamento\":1000.00,\"indicadorSinalLancamento\":\"C\"},"
                + "{\"indicadorTipoLancamento\":\"SD\",\"dataLancamento\":0,\"valorLancamento\":900.00,\"indicadorSinalLancamento\":\"D\"},"
                + "{\"indicadorTipoLancamento\":\"LC\",\"dataLancamento\":0,\"valorLancamento\":50.00,\"indicadorSinalLancamento\":\"D\"}";
        String naoContabilizados =
                "{\"indicadorTipoLancamento\":\"1\",\"indicadorSinalLancamento\":\"C\",\"dataLancamento\":5112026,\"valorLancamento\":10.00},"
                + "{\"indicadorTipoLancamento\":\"2\",\"indicadorSinalLancamento\":\"D\",\"dataLancamento\":5112026,\"valorLancamento\":20.00},"
                + "{\"indicadorTipoLancamento\":\"3\",\"indicadorSinalLancamento\":\"D\",\"dataLancamento\":5112026,\"valorLancamento\":30.00},"
                + "{\"indicadorTipoLancamento\":\"1\",\"indicadorSinalLancamento\":\"*\",\"dataLancamento\":5112026,\"valorLancamento\":40.00}";

        List<LancamentoExtrato> lista = mapear("[" + saldos + "," + naoContabilizados + ","
                + debito("\"dataLancamento\":5112026,\"valorLancamento\":77.00,\"numeroDocumento\":5") + "]");

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).valor()).hasToString("77.00");
    }

    @Test
    @DisplayName("id externo: o identificador único da v2 quando vem; senão H: + SHA-256, estável entre chamadas")
    void idExterno() {
        String comUnico = debito("\"dataLancamento\":5112026,\"valorLancamento\":10.00,\"numeroDocumento\":1,"
                + "\"textoIdentificadorUnicoTransacao\":\"  ABC-123  \"");
        String semUnico = debito("\"dataLancamento\":5112026,\"valorLancamento\":10.00,\"numeroDocumento\":1,"
                + "\"textoIdentificadorUnicoTransacao\":\"\"");

        List<LancamentoExtrato> a = mapear("[" + comUnico + "," + semUnico + "]");
        List<LancamentoExtrato> b = mapear("[" + comUnico + "," + semUnico + "]");

        assertThat(a.get(0).idExterno()).isEqualTo("ABC-123");
        assertThat(a.get(1).idExterno()).matches("H:[0-9a-f]{64}");
        assertThat(b.get(1).idExterno()).isEqualTo(a.get(1).idExterno());
    }

    @Test
    @DisplayName("Lançamentos idênticos sem identificador único não colidem (número de ocorrência) e o id não depende da posição na lista")
    void identicosNaoColidem() {
        String igual = debito("\"dataLancamento\":5112026,\"valorLancamento\":55.00,\"numeroDocumento\":8,\"numeroLote\":3,\"codigoHistorico\":144");
        String outro = debito("\"dataLancamento\":5112026,\"valorLancamento\":99.00,\"numeroDocumento\":9");

        List<LancamentoExtrato> curta = mapear("[" + igual + "," + igual + "]");
        List<LancamentoExtrato> comOutroNoMeio = mapear("[" + outro + "," + igual + "," + outro + "," + igual + "]");

        assertThat(curta.get(0).idExterno()).isNotEqualTo(curta.get(1).idExterno());
        // a janela de consulta desliza: o mesmo lançamento recebe o mesmo id mesmo em outra posição da lista
        assertThat(comOutroNoMeio.get(1).idExterno()).isEqualTo(curta.get(0).idExterno());
        assertThat(comOutroNoMeio.get(3).idExterno()).isEqualTo(curta.get(1).idExterno());
    }

    @Test
    @DisplayName("Contrapartida: CPF/CNPJ de até 15 posições vira 11 ou 14 dígitos; vazio ou só zeros vira sem contrapartida")
    void contraparte() {
        assertThat(BbExtratoMapper.documentoContraparte("012345678000195", "J")).isEqualTo("12345678000195");
        assertThat(BbExtratoMapper.documentoContraparte("000012345678909", "F")).isEqualTo("12345678909");
        assertThat(BbExtratoMapper.documentoContraparte("000001234567890", "F")).isEqualTo("01234567890");
        assertThat(BbExtratoMapper.documentoContraparte("000000000000000", "J")).isNull();
        assertThat(BbExtratoMapper.documentoContraparte("", "F")).isNull();
        assertThat(BbExtratoMapper.documentoContraparte(null, null)).isNull();
    }

    @Test
    @DisplayName("Datas: inteiro e texto com zero, zero, ausente e inválida; data da query segue o inteiro DDMMAAAA da spec")
    void datas() {
        assertThat(BbExtratoMapper.dataInteira(mapper.lerCorpo("{\"d\":5112026}").get("d"))).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(BbExtratoMapper.dataInteira(mapper.lerCorpo("{\"d\":31122026}").get("d"))).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(BbExtratoMapper.dataInteira(mapper.lerCorpo("{\"d\":\"05112026\"}").get("d"))).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(BbExtratoMapper.dataInteira(mapper.lerCorpo("{\"d\":0}").get("d"))).isNull();
        assertThat(BbExtratoMapper.dataInteira(mapper.lerCorpo("{\"d\":32132026}").get("d"))).isNull();
        assertThat(BbExtratoMapper.dataInteira(mapper.lerCorpo("{}").path("d"))).isNull();

        assertThat(BbExtratoMapper.dataParaQuery(LocalDate.of(2026, 11, 5))).isEqualTo("5112026");
        assertThat(BbExtratoMapper.dataParaQuery(LocalDate.of(2026, 12, 20))).isEqualTo("20122026");
    }

    @Test
    @DisplayName("Débito sem data válida falha a consulta (melhor que perder um pagamento em silêncio)")
    void debitoSemData() {
        assertThatThrownBy(() -> mapear("[" + debito("\"dataLancamento\":0,\"valorLancamento\":10.00") + "]"))
                .isInstanceOf(ExtratoBancarioException.class);
    }

    @Test
    @DisplayName("Paginação: numeroPaginaProximo (0 na última) e lista ausente")
    void paginacao() {
        assertThat(mapper.proximaPagina(mapper.lerCorpo("{\"numeroPaginaProximo\":3}"))).isEqualTo(3);
        assertThat(mapper.proximaPagina(mapper.lerCorpo("{\"numeroPaginaProximo\":0}"))).isZero();
        assertThat(mapper.proximaPagina(mapper.lerCorpo("{}"))).isZero();
        assertThat(mapper.itens(mapper.lerCorpo("{}"))).isEmpty();
        assertThatThrownBy(() -> mapper.lerCorpo("isto não é json")).isInstanceOf(ExtratoBancarioException.class);
    }

    @Test
    @DisplayName("Extrato falso: lê o JSON sintético pelo mesmo mapper, só débitos, e respeita o período pedido")
    void extratoFalso() {
        BbExtratoProperties props = new BbExtratoProperties();
        props.setAgencia("297");
        props.setConta("1213");
        ExtratoBancarioFake fake = new ExtratoBancarioFake(props, mapper);

        List<LancamentoExtrato> tudo = fake.listarDebitos(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        List<LancamentoExtrato> soDia5 = fake.listarDebitos(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5));

        assertThat(tudo).hasSize(2);
        assertThat(tudo.get(0).idExterno()).isEqualTo("SINTETICO-ID-0001");
        assertThat(tudo.get(0).valor()).hasToString("1234.50");
        assertThat(tudo.get(0).contraparteDocumento()).isEqualTo("12345678000195");
        assertThat(tudo.get(1).idExterno()).startsWith("H:");
        assertThat(tudo.get(1).contraparteDocumento()).isEqualTo("01234567890");
        assertThat(soDia5).hasSize(1);

        props.setFakeRecurso("extrato-fake/nao-existe.json");
        assertThat(fake.listarDebitos(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).isEmpty();
    }
}
