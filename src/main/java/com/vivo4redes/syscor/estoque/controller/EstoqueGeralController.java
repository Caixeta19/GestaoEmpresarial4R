package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO;
import com.vivo4redes.syscor.estoque.dto.response.EstoqueDisponivelResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ResultadoImportacaoDTO;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** API do estoque unificado ({@code tb_estoque_geral}): importações e consultas do PDV. */
@RestController
@RequestMapping("/estoque-geral")
@RequiredArgsConstructor
@Validated
public class EstoqueGeralController {

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of(".csv", ".txt", ".xlsx");

    private final EstoqueGeralService service;

    /** Importa o relatório completo de estoque da Vivo (.csv/.txt/.xlsx) — upsert por serial. */
    @PostMapping(value = "/importacoes/relatorio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResultadoImportacaoDTO> importarRelatorio(@RequestParam("arquivo") MultipartFile arquivo) {
        validarArquivo(arquivo);
        return ResponseEntity.ok(service.importarRelatorioVivo(arquivo));
    }

    /** Importa a tabela diária de preços/custos (.csv/.txt/.xlsx) e atualiza em lote. */
    @PostMapping(value = "/importacoes/precos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResultadoImportacaoDTO> importarPrecos(@RequestParam("arquivo") MultipartFile arquivo) {
        validarArquivo(arquivo);
        return ResponseEntity.ok(service.atualizarPrecos(arquivo));
    }

    /** PDV: unidades DISPONIVEL por trecho de serial, modelo, marca ou código de acessório. */
    @GetMapping("/disponiveis")
    public ResponseEntity<List<EstoqueDisponivelResponseDTO>> listarDisponiveis(
            @RequestParam(defaultValue = "") String busca,
            @RequestParam(defaultValue = "") String filial,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limite) {
        return ResponseEntity.ok(service.buscarDisponiveis(busca, filial, limite));
    }

    /** PDV: leitura de IMEI/serial exato — 404 se não existir ou não estiver DISPONIVEL. */
    @GetMapping("/disponiveis/{serial:.+}")
    public ResponseEntity<EstoqueDisponivelResponseDTO> buscarDisponivelPorSerial(@PathVariable String serial) {
        return ResponseEntity.ok(service.buscarDisponivelPorSerial(serial));
    }

    /** Saldo por modelo (Disponível / Reservado / Avariado). */
    @GetMapping("/consolidado")
    public ResponseEntity<List<EstoqueConsolidadoModeloDTO>> consolidado() {
        return ResponseEntity.ok(service.consolidarPorModelo());
    }

    /** Modelos críticos ou em ruptura (saldo ≤ 2). */
    @GetMapping("/alertas")
    public ResponseEntity<List<EstoqueConsolidadoModeloDTO>> alertas() {
        return ResponseEntity.ok(service.listarAlertas());
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio ou não informado.");
        }
        String nome = arquivo.getOriginalFilename();
        String nomeLower = nome == null ? "" : nome.toLowerCase(Locale.ROOT);
        boolean extensaoValida = EXTENSOES_PERMITIDAS.stream().anyMatch(nomeLower::endsWith);
        if (!extensaoValida) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato inválido. Envie um arquivo .csv, .txt ou .xlsx.");
        }
    }
}