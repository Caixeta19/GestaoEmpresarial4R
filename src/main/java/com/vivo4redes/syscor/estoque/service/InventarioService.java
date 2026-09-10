package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.InventarioItemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.InventarioResumoResponseDTO;
import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.enums.StatusSerial;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import com.vivo4redes.syscor.estoque.model.ItemEstoque;
import com.vivo4redes.syscor.estoque.repository.InventarioExecucaoRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioItemRepository;
import com.vivo4redes.syscor.estoque.repository.ItemEstoqueRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

import static com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario.*;

/**
 * US-206: conciliação de inventário — cruza o relatório SAP (IQ09) com o
 * nosso {@link ItemEstoque} por número de série. Matching determinístico
 * (normalização de formatação), sem IA generativa: dado o volume e a
 * natureza do dado (serial é um identificador exato, não texto livre),
 * exact-match após normalização é a abordagem correta — fuzzy matching
 * aqui só mascararia erro de digitação real que precisa ser corrigido na
 * origem, não "adivinhado".
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    private final RelatorioSapParser relatorioSapParser;
    private final ItemEstoqueRepository itemEstoqueRepository;
    private final InventarioExecucaoRepository execucaoRepository;
    private final InventarioItemRepository itemRepository;

    @Transactional
    public InventarioResumoResponseDTO processarRelatorioSap(MultipartFile arquivo, String executadoPor) {
        List<LinhaSapDTO> linhasSap = relatorioSapParser.parsear(arquivo);
        if (linhasSap.isEmpty()) {
            throw new NegocioException("O relatório SAP não contém nenhuma linha de dados a conciliar.");
        }

        // Carregado uma única vez para evitar N+1 — se o catálogo crescer muito
        // (dezenas de milhares de seriais), trocar por busca em lote paginada.
        Map<String, ItemEstoque> estoquePorSerial = itemEstoqueRepository.findAll().stream()
                .collect(Collectors.toMap(
                        i -> normalizarSerial(i.getSerialImei()),
                        i -> i,
                        (existente, duplicado) -> existente));

        Set<String> seriaisNoRelatorio = new HashSet<>();
        List<InventarioItem> itens = new ArrayList<>();
        int[] contadores = new int[3]; // [corretos, divergencias, pendentes]

        for (LinhaSapDTO linha : linhasSap) {
            String serialNormalizado = normalizarSerial(linha.serial());
            if (serialNormalizado.isBlank()) {
                continue; // linha sem serial no relatório — nada a conciliar
            }
            seriaisNoRelatorio.add(serialNormalizado);
            itens.add(conciliarLinhaSap(linha, serialNormalizado, estoquePorSerial.get(serialNormalizado), contadores));
        }

        // Checagem reversa: o que está DISPONIVEL no nosso sistema mas não
        // apareceu em nenhuma linha do relatório enviado — possível perda,
        // furto, ou simplesmente um relatório SAP que não cobre esse depósito.
        for (ItemEstoque item : itemEstoqueRepository.findByStatus(StatusSerial.DISPONIVEL)) {
            String serialNormalizado = normalizarSerial(item.getSerialImei());
            if (!seriaisNoRelatorio.contains(serialNormalizado)) {
                contadores[1]++; // divergencia
                itens.add(InventarioItem.builder()
                        .materialSap(null)
                        .denominacaoSap(item.getProduto().getDescricao())
                        .serialSap(item.getSerialImei())
                        .centroSap(null)
                        .depositoSap(item.getDepositoSap())
                        .statusSap(null)
                        .resultado(DIVERGENCIA)
                        .observacao("Consta DISPONÍVEL no nosso sistema, mas não apareceu no relatório SAP enviado.")
                        .itemEstoqueId(item.getId())
                        .build());
            }
        }

        InventarioExecucao execucao = InventarioExecucao.builder()
                .nomeArquivo(arquivo.getOriginalFilename())
                .totalLinhasSap(linhasSap.size())
                .totalCorretos(contadores[0])
                .totalDivergencias(contadores[1])
                .totalPendentes(contadores[2])
                .executadoPor(executadoPor)
                .build();
        execucao = execucaoRepository.save(execucao);

        for (InventarioItem item : itens) {
            item.setExecucao(execucao);
        }
        itens = itemRepository.saveAll(itens);

        return InventarioResumoResponseDTO.from(
                execucao,
                itens.stream().map(InventarioItemResponseDTO::from).toList());
    }

    @Transactional(readOnly = true)
    public InventarioResumoResponseDTO buscarExecucao(Long execucaoId) {
        InventarioExecucao execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução de inventário"));

        List<InventarioItemResponseDTO> itens = itemRepository
                .findByExecucaoIdOrderByResultadoAscSerialSapAsc(execucaoId).stream()
                .map(InventarioItemResponseDTO::from)
                .toList();

        return InventarioResumoResponseDTO.from(execucao, itens);
    }

    private InventarioItem conciliarLinhaSap(LinhaSapDTO linha, String serialNormalizado,
                                             ItemEstoque encontrado, int[] contadores) {
        ResultadoConciliacaoInventario resultado;
        String observacao;
        Long itemEstoqueId = null;

        if (encontrado == null) {
            resultado = PENDENTE;
            observacao = "Serial não encontrado no nosso estoque — nunca deu entrada no sistema (ver US-202, entrada de seriais).";
            contadores[2]++;
        } else {
            itemEstoqueId = encontrado.getId();
            if (encontrado.getStatus() == StatusSerial.DISPONIVEL) {
                resultado = CORRETO;
                observacao = "Disponível nos dois lados.";
                contadores[0]++;
            } else {
                resultado = DIVERGENCIA;
                observacao = "No nosso sistema o status é " + encontrado.getStatus()
                        + ", mas o SAP ainda lista o item em depósito (" + linha.deposito() + ").";
                contadores[1]++;
            }
        }

        return InventarioItem.builder()
                .materialSap(linha.material())
                .denominacaoSap(linha.denominacao())
                .serialSap(linha.serial())
                .centroSap(linha.centro())
                .depositoSap(linha.deposito())
                .statusSap(linha.statusSistema())
                .resultado(resultado)
                .observacao(observacao)
                .itemEstoqueId(itemEstoqueId)
                .build();
    }

    /** Normalização determinística — remove espaços e uniformiza caixa, sem inventar correspondência aproximada. */
    private String normalizarSerial(String serial) {
        if (serial == null) return "";
        return serial.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}