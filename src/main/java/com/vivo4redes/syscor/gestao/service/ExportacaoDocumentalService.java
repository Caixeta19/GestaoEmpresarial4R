package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.dto.response.ProtocoloDocumentalResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Relatório de Gestão Documental em CSV: uma linha por venda, com o item de serviço principal. */
@Service
@RequiredArgsConstructor
public class ExportacaoDocumentalService {

    public static final int MAXIMO_DIAS = 93;
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONA);
    private static final String BOM = "﻿";
    private static final List<String> CABECALHO = List.of("Data/Hora", "Nº Venda", "Filial", "Cliente", "CPF/CNPJ",
            "Nº de acesso", "Serviço", "Tipo de plano", "Plano", "Protocolo GED", "Vendedor", "Status BKO", "Valor total");

    private final ProtocoloDocumentalService protocoloService;

    public record Arquivo(String nome, byte[] conteudo) {
    }

    /** Período obrigatório, de no máximo {@value #MAXIMO_DIAS} dias contando as duas pontas (422 fora disso). */
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
        linha(csv, CABECALHO);
        for (ProtocoloDocumentalResponseDTO v : protocoloService.listar(filtro)) {
            linha(csv, List.of(
                    v.dataVenda() == null ? "" : DATA_HORA.format(v.dataVenda()),
                    String.valueOf(v.numeroVenda()),
                    texto(v.filialNome()),
                    texto(v.clienteNome()),
                    texto(v.clienteDocumento()),
                    texto(v.numeroAcesso()),
                    texto(v.servico()),
                    v.tipoPlano() == null ? "" : v.tipoPlano().rotulo(),
                    texto(v.plano()),
                    texto(v.numeroProtocoloGed()),
                    texto(v.vendedorNome()),
                    v.statusBko().rotulo(),
                    valor(v.valorTotal())));
        }
        String nome = "Relatorio_Gestao_Documental_" + filtro.dataInicio() + "_a_" + filtro.dataFim() + ".csv";
        return new Arquivo(nome, csv.toString().getBytes(StandardCharsets.UTF_8));
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

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    /** Decimal com vírgula, como o Excel em pt-BR abre: 4149,00. */
    private static String valor(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
