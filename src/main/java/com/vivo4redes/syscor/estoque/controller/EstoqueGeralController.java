package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO;
import com.vivo4redes.syscor.estoque.dto.response.EstoqueDisponivelResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ResultadoImportacaoDTO;
import com.vivo4redes.syscor.estoque.service.EstoqueGeralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** API do estoque unificado ({@code tb_estoque_geral}): importações e consultas do PDV. */
@RestController
@RequestMapping("/estoque-geral")
@RequiredArgsConstructor
public class EstoqueGeralController {

    private final EstoqueGeralService service;

    /** Importa o relatório completo de estoque da Vivo (.csv/.txt/.xlsx) — upsert por serial. */
    @PostMapping(value = "/importacoes/relatorio", consumes = "multipart/form-data")
    public ResponseEntity<ResultadoImportacaoDTO> importarRelatorio(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(service.importarRelatorioVivo(arquivo));
    }

    /** Importa a tabela diária de preços/custos (.csv/.txt/.xlsx) e atualiza em lote. */
    @PostMapping(value = "/importacoes/precos", consumes = "multipart/form-data")
    public ResponseEntity<ResultadoImportacaoDTO> importarPrecos(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(service.atualizarPrecos(arquivo));
    }

    /** PDV: unidades DISPONIVEL por trecho de serial, modelo, marca ou código de acessório. */
    @GetMapping("/disponiveis")
    public ResponseEntity<List<EstoqueDisponivelResponseDTO>> listarDisponiveis(
            @RequestParam(defaultValue = "") String busca,
            @RequestParam(defaultValue = "") String filial,
            @RequestParam(defaultValue = "10") int limite) {
        return ResponseEntity.ok(service.buscarDisponiveis(busca, filial, limite));
    }

    /** PDV: leitura de IMEI/serial exato — 404 se não existir ou não estiver DISPONIVEL. */
    @GetMapping("/disponiveis/{serial}")
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
}