package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.request.BipagemRequestDTO;
import com.vivo4redes.syscor.estoque.dto.request.JustificativaRequestDTO;
import com.vivo4redes.syscor.estoque.dto.response.BipagemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.InventarioItemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.InventarioResumoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.TotaisInventarioDTO;
import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.enums.StatusInventario;
import com.vivo4redes.syscor.estoque.service.InventarioService;
import com.vivo4redes.syscor.estoque.service.diagnostico.InventarioDiagnosticoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Inventário por filial: IQ09 x sistema x contagem física. Base {@code /estoque/inventario}.
 */
@RestController
@RequestMapping("/estoque/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;
    private final InventarioDiagnosticoService diagnosticoService;

    /** Abre o inventário da filial com a IQ09 (multipart: filialId + arquivo). Já existe um aberto -> 409. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InventarioResumoResponseDTO> abrir(@RequestParam("filialId") Long filialId,
                                                             @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioService.abrir(filialId, arquivo));
    }

    @GetMapping
    public ResponseEntity<List<InventarioResumoResponseDTO>> listar(@RequestParam(required = false) Long filialId,
                                                                    @RequestParam(required = false) StatusInventario status) {
        return ResponseEntity.ok(inventarioService.listar(filialId, status));
    }

    /** Resumo, contadores, itens paginados (filtro {@code resultado}, aceita vários) e as últimas 10 bipagens. */
    @GetMapping("/{id}")
    public ResponseEntity<InventarioResumoResponseDTO> buscar(@PathVariable Long id,
                                                              @RequestParam(required = false) List<ResultadoConciliacaoInventario> resultado,
                                                              @RequestParam(required = false) String busca,
                                                              @RequestParam(defaultValue = "0") int pagina,
                                                              @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok(inventarioService.buscar(id, resultado, busca, pagina, tamanho));
    }

    @PostMapping("/{id}/bipagens")
    public ResponseEntity<BipagemResponseDTO> bipar(@PathVariable Long id, @Valid @RequestBody BipagemRequestDTO dto) {
        return ResponseEntity.ok(inventarioService.bipar(id, dto.serial()));
    }

    @DeleteMapping("/{id}/bipagens/{itemId}")
    public ResponseEntity<TotaisInventarioDTO> desfazerBipagem(@PathVariable Long id, @PathVariable Long itemId) {
        return ResponseEntity.ok(inventarioService.desfazerBipagem(id, itemId));
    }

    @PatchMapping("/{id}/itens/{itemId}/justificativa")
    public ResponseEntity<InventarioItemResponseDTO> justificar(@PathVariable Long id, @PathVariable Long itemId,
                                                                @Valid @RequestBody JustificativaRequestDTO dto) {
        return ResponseEntity.ok(inventarioService.justificar(id, itemId, dto.texto()));
    }

    /** Fecha (422 com pendente sem justificativa) e roda o diagnóstico da IA; falha da IA não desfaz o fechamento. */
    @PostMapping("/{id}/fechar")
    public ResponseEntity<InventarioResumoResponseDTO> fechar(@PathVariable Long id) {
        inventarioService.fechar(id);
        diagnosticoService.diagnosticarAoFechar(id);
        return ResponseEntity.ok(inventarioService.buscar(id, null, null, 0, 50));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<InventarioResumoResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(inventarioService.cancelar(id));
    }

    /** Refaz o diagnóstico (inventário fechado). API do Claude falhando -> 502. */
    @PostMapping("/{id}/diagnostico")
    public ResponseEntity<InventarioResumoResponseDTO> diagnosticar(@PathVariable Long id) {
        diagnosticoService.diagnosticar(id);
        return ResponseEntity.ok(inventarioService.buscar(id, null, null, 0, 50));
    }

    @GetMapping("/{id}/exportacao")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id) {
        byte[] csv = inventarioService.exportarCsv(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("inventario_" + id + ".csv").build().toString())
                .body(csv);
    }
}
