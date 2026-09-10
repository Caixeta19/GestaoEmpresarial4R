package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.response.InventarioResumoResponseDTO;
import com.vivo4redes.syscor.estoque.service.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * US-206: tela "Inventário Estoque" — importa o relatório SAP (transação
 * IQ09) e concilia contra o nosso estoque serializado.
 */
@RestController
@RequestMapping("/estoque/inventario")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InventarioController {

    private final InventarioService inventarioService;

    /**
     * Recebe o arquivo exportado do SAP (.csv, .xlsx ou .txt) via multipart/form-data,
     * concilia com o nosso estoque e retorna os totais (para os cards da tela) + o detalhe linha a linha.
     */
    @PostMapping(value = "/importar-sap", consumes = "multipart/form-data")
    public ResponseEntity<InventarioResumoResponseDTO> importarRelatorioSap(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "executadoPor", required = false) String executadoPor) {
        var resumo = inventarioService.processarRelatorioSap(arquivo, executadoPor);
        return ResponseEntity.status(HttpStatus.CREATED).body(resumo);
    }

    /** Reconsulta uma execução já processada (histórico). */
    @GetMapping("/{execucaoId}")
    public ResponseEntity<InventarioResumoResponseDTO> buscarExecucao(@PathVariable Long execucaoId) {
        return ResponseEntity.ok(inventarioService.buscarExecucao(execucaoId));
    }
}