package com.vivo4redes.syscor.financeiro.extrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrato falso para dev e testes: lê um JSON <b>sintético</b> no formato da API Extratos v2 (uma página) e o passa
 * pelo mesmo {@link BbExtratoMapper} do cliente real. Nunca extrato real no repositório. Sem o arquivo, devolve vazio.
 */
public class ExtratoBancarioFake implements ExtratoBancarioClient {

    private static final Logger log = LoggerFactory.getLogger(ExtratoBancarioFake.class);

    private final BbExtratoProperties props;
    private final BbExtratoMapper mapper;

    public ExtratoBancarioFake(BbExtratoProperties props, BbExtratoMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public List<LancamentoExtrato> listarDebitos(LocalDate de, LocalDate ate) {
        ClassPathResource recurso = new ClassPathResource(props.getFakeRecurso());
        if (!recurso.exists()) {
            log.warn("Extrato falso sem arquivo '{}': nada a sincronizar.", props.getFakeRecurso());
            return List.of();
        }
        try (InputStream entrada = recurso.getInputStream()) {
            JsonNode corpo = mapper.lerCorpo(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
            List<JsonNode> itens = mapper.itens(corpo);
            if (props.isFakeDatasRelativas()) itens = comDatasRelativas(itens);
            return mapper.mapear(itens, props.getAgencia(), props.getConta()).stream()
                    .filter(l -> !l.dataLancamento().isBefore(de) && !l.dataLancamento().isAfter(ate))
                    .toList();
        } catch (IOException e) {
            throw new ExtratoBancarioException("Não foi possível ler o extrato falso.");
        }
    }

    /**
     * Reescreve {@code dataLancamento} e {@code dataMovimento} (inteiro DDMMAAAA sem zero à esquerda, como o banco
     * manda) para "hoje - diasAtras". Itens sem {@code diasAtras} (saldos, por exemplo) ficam como estão.
     */
    private static List<JsonNode> comDatasRelativas(List<JsonNode> itens) {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        List<JsonNode> resultado = new ArrayList<>();
        for (JsonNode item : itens) {
            if (item.isObject() && item.has("diasAtras")) {
                LocalDate data = hoje.minusDays(item.get("diasAtras").asLong());
                int ddmmaaaa = data.getDayOfMonth() * 1_000_000 + data.getMonthValue() * 10_000 + data.getYear();
                ObjectNode copia = ((ObjectNode) item).deepCopy();
                copia.put("dataLancamento", ddmmaaaa);
                copia.put("dataMovimento", ddmmaaaa);
                copia.remove("diasAtras");
                resultado.add(copia);
            } else {
                resultado.add(item);
            }
        }
        return resultado;
    }
}
