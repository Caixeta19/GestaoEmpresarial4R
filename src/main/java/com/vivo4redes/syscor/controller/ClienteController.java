package com.vivo4redes.syscor.controller;

import com.vivo4redes.syscor.dto.request.ClienteRequestDTO;
import com.vivo4redes.syscor.dto.response.ClienteResponseDTO;
import com.vivo4redes.syscor.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    public ResponseEntity<ClienteResponseDTO> cadastrar(@Valid @RequestBody ClienteRequestDTO dto) {
        var cliente = clienteService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponseDTO.from(cliente));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ClienteResponseDTO.from(clienteService.buscarPorId(id)));
    }

}