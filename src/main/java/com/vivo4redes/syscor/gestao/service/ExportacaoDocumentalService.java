package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.repository.RelatorioDocumentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Relatório de Gestão Documental em CSV, lido da vw_gestao_documental: uma linha por item de serviço Vivo, com as 45
 * colunas da view na ordem dela. Datas em dd/MM/yyyy (com hora em Data da Venda e DATA_SYS), valores com vírgula
 * decimal e booleanos como Sim/Não.
 */
@Service
@RequiredArgsConstructor
public class ExportacaoDocumentalService {

    public static final int MAXIMO_DIAS = 93;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String BOM = "﻿";

    private final RelatorioDocumentalRepository relatorioRepository;

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

        StringBuilder csv = new StringBuilder(BOM);
        linha(csv, RelatorioDocumentalRepository.COLUNAS);
        relatorioRepository.percorrer(filtro, valores ->
                linha(csv, Arrays.stream(valores).map(ExportacaoDocumentalService::formatar).toList()));
        String nome = "Relatorio_Gestao_Documental_" + filtro.dataInicio() + "_a_" + filtro.dataFim() + ".csv";
        return new Arquivo(nome, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String formatar(Object valor) {
        if (valor == null) return "";
        if (valor instanceof Boolean b) return b ? "Sim" : "Não";
        if (valor instanceof LocalDateTime dh) return DATA_HORA.format(dh);
        if (valor instanceof LocalDate d) return DATA.format(d);
        // Decimal com vírgula, como o Excel em pt-BR abre: 4149,00
        if (valor instanceof BigDecimal n) return n.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        return valor.toString().trim();
    }

    private static void linha(StringBuilder csv, List<String> campos) {
        csv.append(String.join(";", campos.stream().map(ExportacaoDocumentalService::escapar).toList())).append("\r\n");
    }

    /** Aspas só quando o campo tem ";", aspas ou quebra de linha. */
    private static String escapar(String campo) {
        if (campo.contains(";") || campo.contains("\"") || campo.contains("\n") || campo.contains("\r")) {
            return "\"" + campo.replace("\"", "\"\"") + "\"";
        }
        return campo;
    }
}
