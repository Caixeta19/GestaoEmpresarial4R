package com.vivo4redes.syscor.estoque.controller;
import com.vivo4redes.syscor.estoque.dto.request.EntradaSeriaisRequestDTO;
import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoResponseDTO;
import com.vivo4redes.syscor.estoque.service.EstoqueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estoque")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EstoqueController {

    private final EstoqueService estoqueService;

    @GetMapping("/consolidado")
    public ResponseEntity<List<EstoqueConsolidadoResponseDTO>> listarConsolidado() {
        return ResponseEntity.ok(estoqueService.listarEstoqueConsolidado());
    }

    /** US-204: itens no nível mínimo ou em ruptura — alimenta o painel de alertas. */
    @GetMapping("/alertas")
    public ResponseEntity<List<EstoqueConsolidadoResponseDTO>> listarAlertas() {
        return ResponseEntity.ok(estoqueService.listarAlertasEstoqueBaixo());
    }

    @PostMapping("/entrada-seriais")
    public ResponseEntity<Void> registrarEntradaSeriais(@RequestBody @Valid EntradaSeriaisRequestDTO request) {
        estoqueService.registrarEntradaSeriais(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}