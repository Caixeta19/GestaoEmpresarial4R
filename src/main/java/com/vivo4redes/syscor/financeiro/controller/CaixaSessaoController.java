package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.dto.caixa.*;
import com.vivo4redes.syscor.financeiro.service.CaixaSessaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/financeiro/caixa")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CaixaSessaoController {

    private final CaixaSessaoService caixaSessaoService;

    @PostMapping("/abrir")
    public ResponseEntity<CaixaSessaoResponseDTO> abrir(@Valid @RequestBody AbrirCaixaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caixaSessaoService.abrir(dto));
    }

    @GetMapping("/abertos")
    public ResponseEntity<List<CaixaSessaoResponseDTO>> listarAbertos(
            @RequestParam(required = false) Long filialId) {
        return ResponseEntity.ok(caixaSessaoService.listarAbertos(filialId));
    }

    @GetMapping("/fechados")
    public ResponseEntity<List<CaixaSessaoResponseDTO>> listarFechados(
            @RequestParam(required = false) Long filialId) {
        return ResponseEntity.ok(caixaSessaoService.listarFechados(filialId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaixaSessaoResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(caixaSessaoService.buscarPorId(id));
    }

    @PostMapping("/{id}/fechar")
    public ResponseEntity<CaixaSessaoResponseDTO> fechar(
            @PathVariable Long id, @Valid @RequestBody FecharCaixaRequestDTO dto) {
        return ResponseEntity.ok(caixaSessaoService.fechar(id, dto));
    }

    @PostMapping("/{id}/reabrir")
    public ResponseEntity<CaixaSessaoResponseDTO> reabrir(@PathVariable Long id) {
        return ResponseEntity.ok(caixaSessaoService.reabrir(id));
    }
}