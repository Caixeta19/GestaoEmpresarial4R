package com.vivo4redes.syscor.controller;

import com.vivo4redes.syscor.dto.request.UsuarioRequestDTO;
import com.vivo4redes.syscor.dto.response.UsuarioResponseDTO;
import com.vivo4redes.syscor.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> cadastrar(@Valid @RequestBody UsuarioRequestDTO dto) {
        var vendedor = usuarioService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponseDTO.from(vendedor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(UsuarioResponseDTO.from(usuarioService.buscarPorId(id)));
    }
}