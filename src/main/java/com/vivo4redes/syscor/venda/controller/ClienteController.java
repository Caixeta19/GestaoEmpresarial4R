package com.vivo4redes.syscor.venda.controller;

import com.vivo4redes.syscor.venda.dto.request.ClienteRequestDTO;
import com.vivo4redes.syscor.venda.dto.response.ClienteResponseDTO;
import com.vivo4redes.syscor.venda.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    public ResponseEntity<ClienteResponseDTO> cadastrar(@Valid @RequestBody ClienteRequestDTO dto) {
        var cliente = clienteService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponseDTO.from(cliente));
    }

    @GetMapping
    public ResponseEntity<PagedModel<ClienteResponseDTO>> buscar(
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int pagina = Math.max(page, 0);
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA);
        var pageable = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending());

        var resultado = clienteService.buscar(busca, pageable).map(ClienteResponseDTO::from);
        return ResponseEntity.ok(new PagedModel<>(resultado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ClienteResponseDTO.from(clienteService.buscarPorId(id)));
    }
}