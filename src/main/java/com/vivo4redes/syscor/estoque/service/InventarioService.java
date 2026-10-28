package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.InventarioItemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.InventarioResumoResponseDTO;
import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.InventarioExecucaoRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioItemRepository;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
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
 * US-206: conciliaÃ§Ã£o de inventÃ¡rio â€” cruza o relatÃ³rio SAP (IQ09) com o
 * nosso {@link EstoqueGeral} por nÃºmero de sÃ©rie. Matching determinÃ­stico
 * (normalizaÃ§Ã£o de formataÃ§Ã£o), sem IA generativa: dado o volume e a
 * natureza do dado (serial Ã© um identificador exato, nÃ£o texto livre),
 * exact-match apÃ³s normalizaÃ§Ã£o Ã© a abordagem correta â€” fuzzy matching
 * aqui sÃ³ mascararia erro de digitaÃ§Ã£o real que precisa ser corrigido na
 * origem, nÃ£o "adivinhado".
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    private final RelatorioSapParser relatorioSapParser;
    private final EstoqueGeralRepository estoqueGeralRepository;
    private final InventarioExecucaoRepository execucaoRepository;
    private final InventarioItemRepository itemRepository;

    @Transactional
    public InventarioResumoResponseDTO processarRelatorioSap(MultipartFile arquivo, String executadoPor) {
        List<LinhaSapDTO> linhasSap = relatorioSapParser.parsear(arquivo);
        if (linhasSap.isEmpty()) {
            throw new NegocioException("O relatÃ³rio SAP nÃ£o contÃ©m nenhuma linha de dados a conciliar.");
        }

        // Carregado uma Ãºnica vez para evitar N+1 â€” se o catÃ¡logo crescer muito
        // (dezenas de milhares de seriais), trocar por busca em lote paginada.
        Map<String, EstoqueGeral> estoquePorSerial = estoqueGeralRepository.findAll().stream()
                .collect(Collectors.toMap(
                        i -> normalizarSerial(i.getSerial()),
                        i -> i,
                        (existente, duplicado) -> existente));

        Set<String> seriaisNoRelatorio = new HashSet<>();
        List<InventarioItem> itens = new ArrayList<>();
        int[] contadores = new int[3]; // [corretos, divergencias, pendentes]

        for (LinhaSapDTO linha : linhasSap) {
            String serialNormalizado = normalizarSerial(linha.serial());
            if (serialNormalizado.isBlank()) {
                continue; // linha sem serial no relatÃ³rio â€” nada a conciliar
            }
            seriaisNoRelatorio.add(serialNormalizado);
            itens.add(conciliarLinhaSap(linha, serialNormalizado, estoquePorSerial.get(serialNormalizado), contadores));
        }

        // Checagem reversa: o que estÃ¡ DISPONIVEL no nosso sistema mas nÃ£o
        // apareceu em nenhuma linha do relatÃ³rio enviado â€” possÃ­vel perda,
        // furto, ou simplesmente um relatÃ³rio SAP que nÃ£o cobre esse depÃ³sito.
        for (EstoqueGeral item : estoqueGeralRepository.findByStatus(StatusEstoque.DISPONIVEL)) {
            String serialNormalizado = normalizarSerial(item.getSerial());
            if (!seriaisNoRelatorio.contains(serialNormalizado)) {
                contadores[1]++; // divergencia
                itens.add(InventarioItem.builder()
                        .materialSap(null)
                        .denominacaoSap(item.getModeloComercial())
                        .serialSap(item.getSerial())
                        .centroSap(null)
                        .depositoSap(item.getFilialEstoque())
                        .statusSap(null)
                        .resultado(DIVERGENCIA)
                        .observacao("Consta DISPONÃVEL no nosso sistema, mas nÃ£o apareceu no relatÃ³rio SAP enviado.")
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
                .orElseThrow(() -> new RecursoNaoEncontradoException("ExecuÃ§Ã£o de inventÃ¡rio"));

        List<InventarioItemResponseDTO> itens = itemRepository
                .findByExecucaoIdOrderByResultadoAscSerialSapAsc(execucaoId).stream()
                .map(InventarioItemResponseDTO::from)
                .toList();

        return InventarioResumoResponseDTO.from(execucao, itens);
    }

    private InventarioItem conciliarLinhaSap(LinhaSapDTO linha, String serialNormalizado,
                                             EstoqueGeral encontrado, int[] contadores) {
        ResultadoConciliacaoInventario resultado;
        String observacao;
        Long itemEstoqueId = null;

        if (encontrado == null) {
            resultado = PENDENTE;
            observacao = "Serial nÃ£o encontrado no nosso estoque â€” nunca deu entrada no sistema (ver US-202, entrada de seriais).";
            contadores[2]++;
        } else {
            itemEstoqueId = encontrado.getId();
            if (encontrado.getStatus() == StatusEstoque.DISPONIVEL) {
                resultado = CORRETO;
                observacao = "DisponÃ­vel nos dois lados.";
                contadores[0]++;
            } else {
                resultado = DIVERGENCIA;
                observacao = "No nosso sistema o status Ã© " + encontrado.getStatus()
                        + ", mas o SAP ainda lista o item em depÃ³sito (" + linha.deposito() + ").";
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

    /** NormalizaÃ§Ã£o determinÃ­stica â€” remove espaÃ§os e uniformiza caixa, sem inventar correspondÃªncia aproximada. */
    private String normalizarSerial(String serial) {
        if (serial == null) return "";
        return serial.replace("'", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}