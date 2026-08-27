package com.vivo4redes.syscor.controller;

import com.vivo4redes.syscor.dto.request.VendedorRequestDTO;
import com.vivo4redes.syscor.dto.response.VendedorResponseDTO;
import com.vivo4redes.syscor.service.VendedorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendedores")
public class VendedorController {

    private final VendedorService vendedorService;

    public VendedorController(VendedorService vendedorService) {
        this.vendedorService = vendedorService;
    }

    @PostMapping
    public ResponseEntity<VendedorResponseDTO> cadastrar(@Valid @RequestBody VendedorRequestDTO dto) {
        var vendedor = vendedorService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(VendedorResponseDTO.from(vendedor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendedorResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(VendedorResponseDTO.from(vendedorService.buscarPorId(id)));
    }
}