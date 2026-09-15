package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.response.ProdutoComPrecoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoPrecoPlanoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.TabelaPrecoResumoResponseDTO;
import com.vivo4redes.syscor.estoque.service.TabelaPrecoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Importação diária (upload manual) da tabela de preço da Telefônica. */
@RestController
@RequestMapping("/produtos/precos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TabelaPrecoController {

    private final TabelaPrecoService tabelaPrecoService;

    @PostMapping(value = "/importar-telefonica", consumes = "multipart/form-data")
    public ResponseEntity<TabelaPrecoResumoResponseDTO> importar(@RequestParam("arquivo") MultipartFile arquivo) {
        var resumo = tabelaPrecoService.importar(arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(resumo);
    }

    @GetMapping("/execucoes/{execucaoId}")
    public ResponseEntity<TabelaPrecoResumoResponseDTO> buscarExecucao(@PathVariable Long execucaoId) {
        return ResponseEntity.ok(tabelaPrecoService.buscarExecucao(execucaoId));
    }

    /** Preço por plano de um produto específico — útil quando o item tem variação por oferta de comunicação. */
    @GetMapping("/produtos/{produtoId}")
    public ResponseEntity<List<ProdutoPrecoPlanoResponseDTO>> listarPrecosPorProduto(@PathVariable Long produtoId) {
        var precos = tabelaPrecoService.listarPrecosPorProduto(produtoId).stream()
                .map(ProdutoPrecoPlanoResponseDTO::from)
                .toList();
        return ResponseEntity.ok(precos);
    }

    /** Tabs disponíveis (nome da oferta como vem da planilha) na importação mais recente. */
    @GetMapping("/ofertas")
    public ResponseEntity<List<String>> listarOfertasDisponiveis() {
        return ResponseEntity.ok(tabelaPrecoService.listarOfertasDisponiveis());
    }

    /** Catálogo de produtos com preço para a oferta selecionada (conteúdo de um tab), sempre da importação mais recente. */
    @GetMapping
    public ResponseEntity<List<ProdutoComPrecoResponseDTO>> listarProdutosPorOferta(@RequestParam String oferta) {
        var produtos = tabelaPrecoService.listarProdutosPorOferta(oferta).stream()
                .map(ProdutoComPrecoResponseDTO::from)
                .toList();
        return ResponseEntity.ok(produtos);
    }
}