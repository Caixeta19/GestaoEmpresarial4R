package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.exception.BusinessException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.venda.dto.request.FilialRequestDTO;
import com.vivo4redes.syscor.venda.dto.response.CargaCentrosSapResponseDTO;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class FilialService {

    private final FilialRepository filialRepository;

    public FilialService(FilialRepository filialRepository) {
        this.filialRepository = filialRepository;
    }

    /**
     * Carga do centro SAP das filiais por CSV {@code ADABAS;CENTRO_SAP[;DEPOSITO_SAP]} (cabeçalho opcional, ; ou ,).
     * Linha com erro fica de fora e entra na lista; as demais são gravadas. Centro já usado por outra filial é erro.
     */
    @Transactional
    public CargaCentrosSapResponseDTO carregarCentrosSap(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie o CSV com as colunas ADABAS;CENTRO_SAP.");
        }
        List<String> linhas;
        try {
            linhas = decodificar(arquivo.getBytes()).lines().map(l -> l.replace("﻿", "").trim()).filter(l -> !l.isEmpty()).toList();
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler o arquivo enviado: " + e.getMessage());
        }
        if (!linhas.isEmpty() && linhas.get(0).toUpperCase(Locale.ROOT).contains("ADABAS")) {
            linhas = linhas.subList(1, linhas.size());
        }

        List<String> erros = new ArrayList<>();
        List<String> naoEncontrados = new ArrayList<>();
        Map<Filial, String[]> atualizacoes = new LinkedHashMap<>();
        Map<String, String> centroPorCodigo = new HashMap<>();
        for (int i = 0; i < linhas.size(); i++) {
            String[] campos = linhas.get(i).split("[;,]", -1);
            int numero = i + 1;
            String codigo = campos[0].trim();
            String centro = campos.length > 1 ? campos[1].trim().toUpperCase(Locale.ROOT) : "";
            String deposito = campos.length > 2 ? campos[2].trim().toUpperCase(Locale.ROOT) : "";
            if (codigo.isEmpty() || centro.isEmpty()) {
                erros.add("Linha " + numero + ": informe ADABAS e CENTRO_SAP.");
                continue;
            }
            if (centro.length() > 10 || deposito.length() > 10) {
                erros.add("Linha " + numero + ": centro e depósito têm no máximo 10 caracteres.");
                continue;
            }
            String repetido = centroPorCodigo.entrySet().stream()
                    .filter(e -> e.getValue().equals(centro) && !e.getKey().equalsIgnoreCase(codigo))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (repetido != null) {
                erros.add("Linha " + numero + ": o centro " + centro + " já está no arquivo para a filial " + repetido + ".");
                continue;
            }
            Optional<Filial> filial = filialRepository.findByCodigoIgnoreCase(codigo);
            if (filial.isEmpty()) {
                naoEncontrados.add(codigo);
                continue;
            }
            centroPorCodigo.put(codigo, centro);
            atualizacoes.put(filial.get(), new String[]{centro, deposito.isEmpty() ? null : deposito});
        }

        // Centro já usado por filial que não está no arquivo: erro (o UNIQUE não aceita dois iguais).
        Set<Long> noArquivo = new HashSet<>();
        atualizacoes.keySet().forEach(f -> noArquivo.add(f.getId()));
        for (Filial outra : filialRepository.findByCentroSapIsNotNull()) {
            if (noArquivo.contains(outra.getId())) continue;
            atualizacoes.entrySet().removeIf(e -> {
                boolean conflito = e.getValue()[0].equalsIgnoreCase(outra.getCentroSap());
                if (conflito) {
                    erros.add("Filial " + e.getKey().getCodigo() + ": o centro " + e.getValue()[0]
                            + " já é da filial " + outra.getCodigo() + ".");
                }
                return conflito;
            });
        }

        // Duas fases: limpa e depois grava, para permitir troca de centro entre filiais do mesmo arquivo.
        atualizacoes.keySet().forEach(f -> f.setCentroSap(null));
        filialRepository.saveAllAndFlush(atualizacoes.keySet());
        atualizacoes.forEach((filial, valores) -> {
            filial.setCentroSap(valores[0]);
            filial.setDepositoSap(valores[1]);
        });
        filialRepository.saveAllAndFlush(atualizacoes.keySet());

        return new CargaCentrosSapResponseDTO(linhas.size(), atualizacoes.size(), naoEncontrados, erros);
    }

    private static String decodificar(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    @Transactional
    public Filial cadastrar(FilialRequestDTO dto) {
        if (filialRepository.existsByCodigoIgnoreCase(dto.codigo())) {
            throw new BusinessException("Já existe filial cadastrada com o código: " + dto.codigo());
        }

        Filial filial = Filial.builder()
                .codigo(dto.codigo())
                .nome(dto.nome())
                .ativo(true)
                .build();
        return filialRepository.save(filial);
    }

    @Transactional(readOnly = true)
    public Filial buscarPorId(Long id) {
        return filialRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Filial"));
    }

    @Transactional(readOnly = true)
    public List<Filial> listarAtivas() {
        return filialRepository.findByAtivoTrueOrderByNomeAsc();
    }

    /** Relatórios mostram também as filiais inativas (vendas antigas continuam nelas). */
    @Transactional(readOnly = true)
    public List<Filial> listarTodas() {
        return filialRepository.findAllByOrderByNomeAsc();
    }
}