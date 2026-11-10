package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.repository.RelatorioDocumentalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Relatório de Gestão Documental no layout do sistema atual (docs/gestao.md), lido da vw_gestao_documental: preâmbulo
 * (emissão e revenda), cabeçalho de 55 colunas, uma linha por serviço e o rodapé dos campos obrigatórios/alteráveis.
 * Windows-1252, ";" e LF, sem aspas duplas. Datas dd/MM/yyyy (com hora só em "Data de alteração" e na emissão),
 * valores com ponto e 2 casas, booleanos Sim/Não e as colunas de número-texto entre apóstrofos.
 */
@Service
public class ExportacaoDocumentalService {

    public static final int MAXIMO_DIAS = 93;
    public static final String NOME_ARQUIVO = "gestao_documental.csv";
    public static final Charset CHARSET = Charset.forName("windows-1252");

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String FIM_LINHA = "\n";

    /** Texto que o Excel leria como número: sai entre apóstrofos, inclusive vazio (''). */
    static final Set<String> ENTRE_APOSTROFOS = Set.of("Sim Card", "Nº Solicitação NEXT / RPON",
            "Nº Protocolo GED(**)", "Nº Protocolo GED Portabilidade (**)", "Motivo Reprovação(**)");

    private final RelatorioDocumentalRepository relatorioRepository;
    private final String revenda;
    private final Clock relogio;

    @Autowired
    public ExportacaoDocumentalService(RelatorioDocumentalRepository relatorioRepository,
                                       @Value("${syscor.revenda.nome:4 REDES}") String revenda) {
        this(relatorioRepository, revenda, Clock.system(ZONA));
    }

    ExportacaoDocumentalService(RelatorioDocumentalRepository relatorioRepository, String revenda, Clock relogio) {
        this.relatorioRepository = relatorioRepository;
        this.revenda = revenda;
        this.relogio = relogio;
    }

    public record Arquivo(String nome, byte[] conteudo) {
    }

    /** Período obrigatório, de no máximo {@value #MAXIMO_DIAS} dias contando as duas pontas (422 fora disso). */
    @Transactional(readOnly = true)
    public Arquivo exportar(FiltroDocumentalDTO filtro) {
        if (filtro.dataInicio() == null || filtro.dataFim() == null) {
            throw new NegocioException("Informe o período (data inicial e final) para exportar.");
        }
        if (filtro.dataFim().isBefore(filtro.dataInicio())) {
            throw new NegocioException("A data final não pode ser anterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(filtro.dataInicio(), filtro.dataFim()) + 1 > MAXIMO_DIAS) {
            throw new NegocioException("O período da exportação pode ter no máximo " + MAXIMO_DIAS + " dias.");
        }

        List<String> colunas = RelatorioDocumentalRepository.COLUNAS;
        StringBuilder csv = new StringBuilder()
                .append("Data da Emissão do relatório;").append(DATA_HORA.format(LocalDateTime.now(relogio))).append(FIM_LINHA)
                .append(FIM_LINHA)
                .append("Revenda: ").append(revenda).append(FIM_LINHA)
                .append(FIM_LINHA)
                .append(String.join(";", colunas)).append(FIM_LINHA);
        relatorioRepository.percorrer(filtro, valores -> {
            String[] campos = new String[valores.length];
            for (int i = 0; i < valores.length; i++) {
                campos[i] = campo(colunas.get(i), valores[i]);
            }
            csv.append(String.join(";", campos)).append(FIM_LINHA);
        });
        csv.append(FIM_LINHA).append(FIM_LINHA)
                .append("(*) Campos obrigatórios;").append(FIM_LINHA)
                .append("(**) Campos que podem ser alterados;").append(FIM_LINHA);
        return new Arquivo(NOME_ARQUIVO, csv.toString().getBytes(CHARSET));
    }

    static String campo(String coluna, Object valor) {
        String texto = formatar(valor);
        return ENTRE_APOSTROFOS.contains(coluna) ? "'" + texto + "'" : texto;
    }

    static String formatar(Object valor) {
        if (valor == null) return "";
        if (valor instanceof Boolean b) return b ? "Sim" : "Não";
        if (valor instanceof LocalDateTime dh) return DATA_HORA.format(dh);
        if (valor instanceof LocalDate d) return DATA.format(d);
        // Ponto decimal e 2 casas, sem separador de milhar: 24.99
        if (valor instanceof BigDecimal n) return n.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return limpar(valor.toString());
    }

    /** Sem aspas no arquivo: ";" viraria coluna nova e a quebra de linha, linha nova. */
    private static String limpar(String texto) {
        return texto.replace(';', ',').replace('"', '\'').replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
