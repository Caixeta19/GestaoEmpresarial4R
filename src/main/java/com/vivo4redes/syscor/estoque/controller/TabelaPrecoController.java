package com.vivo4redes.syscor.estoque.controller;

import com.vivo4redes.syscor.estoque.dto.response.ImportacaoTabelaPrecoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.PlanoVivoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.PrecoItemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoComPrecoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoPrecoPlanoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.TabelaPrecoResumoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.TabelaVigenteResponseDTO;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.service.TabelaPrecoService;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Tabela de preços da Telefônica: upload (a execução mais recente é a vigente) e consultas de preço. */
@RestController
@RequestMapping("/produtos/precos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TabelaPrecoController {

    private final TabelaPrecoService tabelaPrecoService;

    /** Upload da planilha (.xlsx). Devolve o relatório da importação (inclui os campos do formato antigo). */
    @PostMapping(value = "/importar-telefonica", consumes = "multipart/form-data")
    public ResponseEntity<ImportacaoTabelaPrecoResponseDTO> importar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tabelaPrecoService.importar(arquivo));
    }

    /** Execução vigente: id, vigência, data da importação e totais. */
    @GetMapping("/vigente")
    public ResponseEntity<TabelaVigenteResponseDTO> vigente() {
        return ResponseEntity.ok(tabelaPrecoService.buscarVigente());
    }

    /** Planos da execução vigente, para o combo "Plano Ativo / Linha". */
    @GetMapping("/planos")
    public ResponseEntity<List<PlanoVivoResponseDTO>> planos(@RequestParam(required = false) String busca,
                                                             @RequestParam(required = false) Boolean disponivelAlta) {
        return ResponseEntity.ok(tabelaPrecoService.listarPlanos(busca, disponivelAlta));
    }

    /**
     * Preço de uma unidade do estoque na tabela vigente. {@code tabela} padrão REGULAR; sem {@code pagamento}, preço cheio;
     * {@code parcelas} (1–21) vale para CARTAO_CREDITO. 422 se não houver preço.
     */
    @GetMapping("/item")
    public ResponseEntity<PrecoItemResponseDTO> item(@RequestParam Long estoqueId,
                                                     @RequestParam(required = false) String plano,
                                                     @RequestParam(required = false) TabelaPreco tabela,
                                                     @RequestParam(required = false) FormaPagamento pagamento,
                                                     @RequestParam(required = false) Integer parcelas) {
        return ResponseEntity.ok(tabelaPrecoService.precoDoItem(estoqueId, plano, tabela, pagamento, parcelas));
    }

    // ------------------------------------------------------------------ deprecados (formato antigo)

    /** @deprecated use {@code GET /produtos/precos/vigente}. */
    @Deprecated
    @GetMapping("/execucoes/{execucaoId}")
    public ResponseEntity<TabelaPrecoResumoResponseDTO> buscarExecucao(@PathVariable Long execucaoId) {
        return ResponseEntity.ok(tabelaPrecoService.buscarExecucao(execucaoId));
    }

    /** @deprecated use {@code GET /produtos/precos/item}. Agora: preços REGULAR da unidade por grupo do plano. */
    @Deprecated
    @GetMapping("/produtos/{produtoId}")
    public ResponseEntity<List<ProdutoPrecoPlanoResponseDTO>> listarPrecosPorProduto(@PathVariable Long produtoId) {
        return ResponseEntity.ok(tabelaPrecoService.listarPrecosPorProduto(produtoId).stream()
                .map(ProdutoPrecoPlanoResponseDTO::from)
                .toList());
    }

    /** @deprecated use {@code GET /produtos/precos/planos}. Agora: grupos de plano com preço (PRE, VIVO_V...). */
    @Deprecated
    @GetMapping("/ofertas")
    public ResponseEntity<List<String>> listarOfertasDisponiveis() {
        return ResponseEntity.ok(tabelaPrecoService.listarOfertasDisponiveis());
    }

    /** @deprecated use {@code GET /produtos/precos/item}. Agora: {@code oferta} = grupo do plano; produtoId vem nulo. */
    @Deprecated
    @GetMapping
    public ResponseEntity<List<ProdutoComPrecoResponseDTO>> listarProdutosPorOferta(@RequestParam String oferta) {
        return ResponseEntity.ok(tabelaPrecoService.listarProdutosPorOferta(oferta).stream()
                .map(ProdutoComPrecoResponseDTO::from)
                .toList());
    }
}
