package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.exception.BusinessException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.gestao.dto.request.ProtocoloDocumentalRequestDTO;
import com.vivo4redes.syscor.gestao.dto.response.ResultadoImportacaoProtocolosDTO;
import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
import com.vivo4redes.syscor.gestao.repository.ProtocoloDocumentalRepository;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Importação em lote do protocolo documental (CSV ";" ou ","). Colunas pelo nome no cabeçalho:
 * NUMERO_VENDA (obrigatória), PROTOCOLO_GED, STATUS_BKO, NUMERO_ACESSO, DATA_DIGITALIZACAO (dd/MM/yyyy).
 * Cada linha é gravada pelo mesmo serviço do PUT, numa transação própria: uma linha com erro não desfaz as outras.
 * Só os campos da planilha mudam; o resto do protocolo fica como está.
 */
@Service
@RequiredArgsConstructor
public class ImportacaoProtocolosService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final ProtocoloDocumentalService protocoloService;
    private final ProtocoloDocumentalRepository protocoloRepository;
    private final VendaRepository vendaRepository;
    private final TransactionTemplate transacao;

    /** Sem transação aqui de propósito: cada linha abre a sua. */
    public ResultadoImportacaoProtocolosDTO importar(MultipartFile arquivo) {
        List<String> linhas = lerLinhas(arquivo);
        if (linhas.isEmpty()) {
            throw new NegocioException("O arquivo está vazio.");
        }
        String separador = linhas.get(0).contains(";") ? ";" : ",";
        Map<String, Integer> colunas = colunas(linhas.get(0), separador);
        if (!colunas.containsKey("NUMERO_VENDA")) {
            throw new NegocioException("O cabeçalho precisa da coluna NUMERO_VENDA "
                    + "(colunas: NUMERO_VENDA;PROTOCOLO_GED;STATUS_BKO;NUMERO_ACESSO;DATA_DIGITALIZACAO).");
        }

        int total = 0;
        int sucessos = 0;
        List<ResultadoImportacaoProtocolosDTO.Erro> erros = new ArrayList<>();
        for (int n = 1; n < linhas.size(); n++) {
            if (linhas.get(n).isBlank()) continue;
            total++;
            String[] campos = linhas.get(n).split(separador, -1);
            String numeroVenda = campo(campos, colunas, "NUMERO_VENDA");
            try {
                transacao.executeWithoutResult(s -> importarLinha(campos, colunas, numeroVenda));
                sucessos++;
            } catch (BusinessException | RecursoNaoEncontradoException | IllegalArgumentException e) {
                erros.add(new ResultadoImportacaoProtocolosDTO.Erro(n + 1, numeroVenda, e.getMessage()));
            }
        }
        return new ResultadoImportacaoProtocolosDTO(total, sucessos, erros);
    }

    private void importarLinha(String[] campos, Map<String, Integer> colunas, String numeroVendaTexto) {
        Long numeroVenda;
        try {
            numeroVenda = Long.valueOf(numeroVendaTexto);
        } catch (NumberFormatException e) {
            throw new NegocioException("Número da venda inválido: '" + numeroVendaTexto + "'.");
        }
        Venda venda = vendaRepository.findByNumeroVenda(numeroVenda)
                .orElseThrow(() -> new NegocioException("Venda " + numeroVenda + " não encontrada."));

        String acesso = campo(campos, colunas, "NUMERO_ACESSO").replaceAll("\\D", "");
        if (!acesso.isEmpty() && venda.getItens().stream().map(ItemVenda::getNumeroAcesso)
                .noneMatch(a -> a != null && a.replaceAll("\\D", "").equals(acesso))) {
            throw new NegocioException("O número de acesso não é de nenhum item da venda " + numeroVenda + ".");
        }

        String dataTexto = campo(campos, colunas, "DATA_DIGITALIZACAO");
        LocalDate dataDigitalizacao = null;
        if (!dataTexto.isEmpty()) {
            try {
                dataDigitalizacao = LocalDate.parse(dataTexto, DATA);
            } catch (DateTimeParseException e) {
                throw new NegocioException("Data de digitalização inválida: '" + dataTexto + "' (use dd/MM/aaaa).");
            }
        }

        String statusTexto = campo(campos, colunas, "STATUS_BKO");
        String statusBko = null;
        if (!statusTexto.isEmpty()) {
            statusBko = StatusAvaliacaoProcedencia.deTexto(statusTexto)
                    .orElseThrow(() -> new NegocioException("Status BKO inválido: '" + statusTexto
                            + "'. Use Não avaliado, Em avaliação pelo BKO, Procedente ou Improcedente."))
                    .name();
        }

        String ged = campo(campos, colunas, "PROTOCOLO_GED");
        ProtocoloDocumental atual = protocoloRepository.findByVendaId(venda.getId()).orElse(null);
        protocoloService.salvar(venda.getId(), mesclar(atual, ged.isEmpty() ? null : ged, dataDigitalizacao, statusBko));
    }

    /** O que a planilha traz substitui; o que ela não traz continua como estava no protocolo. */
    private static ProtocoloDocumentalRequestDTO mesclar(ProtocoloDocumental atual, String ged, LocalDate data, String statusBko) {
        boolean tem = atual != null;
        return new ProtocoloDocumentalRequestDTO(
                ged != null ? ged : tem ? atual.getNumeroProtocoloGed() : null,
                data != null ? data : tem ? atual.getDataDigitalizacao() : null,
                tem ? atual.getZerarRemuneracao() : null,
                tem ? atual.getGerarPrice() : null,
                tem ? atual.getVencimento() : null,
                tem ? atual.getLiderEquipe() : null,
                tem ? atual.getObservacoes() : null,
                tem ? atual.getObservacoesImportacao() : null,
                tem ? atual.getSituacaoServico() : null,
                tem ? atual.getGerarComissao() : null,
                tem && atual.getMotivosCancelamento() != null ? List.of(atual.getMotivosCancelamento().split(";")) : null,
                statusBko,
                tem ? atual.getAtivacaoPrimeiraChamada() : null,
                tem ? atual.getDataAtivacao() : null,
                tem ? atual.getDataBaixa() : null,
                tem ? atual.getNumeroProtocoloGedPortabilidade() : null,
                tem ? atual.getDataInstalacao() : null);
    }

    private static List<String> lerLinhas(MultipartFile arquivo) {
        try {
            String texto = new String(arquivo.getBytes(), StandardCharsets.UTF_8);
            if (texto.startsWith("﻿")) texto = texto.substring(1);
            return new ArrayList<>(List.of(texto.split("\\r?\\n")));
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler o arquivo.");
        }
    }

    private static Map<String, Integer> colunas(String cabecalho, String separador) {
        Map<String, Integer> colunas = new HashMap<>();
        String[] nomes = cabecalho.split(separador, -1);
        for (int i = 0; i < nomes.length; i++) {
            colunas.putIfAbsent(TextoUtil.identificador(semAspas(nomes[i])), i);
        }
        return colunas;
    }

    private static String campo(String[] campos, Map<String, Integer> colunas, String nome) {
        Integer i = colunas.get(nome);
        return i == null || i >= campos.length ? "" : semAspas(campos[i]);
    }

    private static String semAspas(String valor) {
        String v = valor.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1).trim();
        return v;
    }
}
