package com.vivo4redes.syscor.controller;

import com.vivo4redes.syscor.dto.request.FilialRequestDTO;
import com.vivo4redes.syscor.dto.response.FilialResponseDTO;
import com.vivo4redes.syscor.service.FilialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/filiais")
public class FilialController {

    private final FilialService filialService;

    public FilialController(FilialService filialService) {
        this.filialService = filialService;
    }

    @PostMapping
    public ResponseEntity<FilialResponseDTO> cadastrar(@Valid @RequestBody FilialRequestDTO dto) {
        var filial = filialService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(FilialResponseDTO.from(filial));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FilialResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(FilialResponseDTO.from(filialService.buscarPorId(id)));
    }
}