package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.response.ProdutoResponseDTO;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consulta de catálogo para o PDV. O cadastro manual de produto (POST) deixou de
 * existir: o catálogo agora é a própria tb_estoque_geral, alimentada pela importação.
 */
@RestController
@RequestMapping("/produtos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProdutoController {

    private final EstoqueService estoqueService;

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ProdutoResponseDTO.from(estoqueService.buscarPorId(id)));
    }

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) CategoriaItemVenda categoria) {
        return ResponseEntity.ok(estoqueService.listarCatalogo(busca, categoria));
    }
}