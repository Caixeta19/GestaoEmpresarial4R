package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.request.ProdutoRequestDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoResponseDTO;
import com.vivo4redes.syscor.estoque.service.EstoqueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** US-201: cadastro e consulta de produtos/SKUs. */
@RestController
@RequestMapping("/produtos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProdutoController {

    private final EstoqueService estoqueService;

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> cadastrar(@Valid @RequestBody ProdutoRequestDTO dto) {
        var produto = estoqueService.cadastrarProduto(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProdutoResponseDTO.from(produto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ProdutoResponseDTO.from(estoqueService.buscarProdutoPorId(id)));
    }

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listar() {
        var produtos = estoqueService.listarProdutos().stream().map(ProdutoResponseDTO::from).toList();
        return ResponseEntity.ok(produtos);
    }
}