package com.vivo4redes.syscor.estoque.service;
import com.vivo4redes.syscor.estoque.dto.LinhaPrecoDTO;
import com.vivo4redes.syscor.estoque.dto.response.TabelaPrecoResumoResponseDTO;
import com.vivo4redes.syscor.estoque.model.Produto;
import com.vivo4redes.syscor.estoque.model.ProdutoPrecoPlano;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import com.vivo4redes.syscor.estoque.repository.ProdutoPrecoPlanoRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.*;

/**
 * US: importação diária (upload manual) da tabela de preço da Telefônica.
 * Escopo v1 (decisão explícita): só TABELA REGULAR, um preço por
 * (produto, oferta de comunicação/plano) — sem forma de pagamento
 * (PIX/parcelas) nem TABELA RENOVA. Atualiza na hora, sem aprovação —
 * o histórico completo fica em ProdutoPrecoPlano para auditoria.
 */
@Service
@RequiredArgsConstructor
public class TabelaPrecoService {

    private final TabelaPrecoTelefonicaParser parser;
    private final ProdutoRepository produtoRepository;
    private final TabelaPrecoExecucaoRepository execucaoRepository;
    private final ProdutoPrecoPlanoRepository precoPlanoRepository;

    @Transactional
    public TabelaPrecoResumoResponseDTO importar(MultipartFile arquivo) {
        ResultadoParseTabelaPreco resultado = parser.parsear(arquivo);

        // SKU (nosso Produto.sku) = Cod_Sap — carregado uma vez para evitar N+1.
        Map<String, Produto> produtosPorSku = new HashMap<>();
        for (Produto p : produtoRepository.findAll()) {
            produtosPorSku.put(p.getSku().trim().toUpperCase(Locale.ROOT), p);
        }

        List<ProdutoPrecoPlano> itens = new ArrayList<>();
        Set<Long> produtosAtualizados = new HashSet<>();
        int naoEncontrados = 0;

        // Para produtos SEM variação por plano (oferta == null), o último preço processado
        // vira o Produto.precoBase diretamente — não há ambiguidade nesse caso.
        // Para produtos COM variação por plano, o precoBase vira uma referência
        // ("a partir de": o menor preço entre os planos) — o preço correto por
        // plano fica disponível via ProdutoPrecoPlano.
        Map<Long, BigDecimal> menorPrecoPorProduto = new HashMap<>();

        for (LinhaPrecoDTO linha : resultado.linhasPreco()) {
            Set<String> skusCandidatos = resultado.indiceSkuPorNomeComercial()
                    .getOrDefault(normalizarChave(linha.nomeComercial()), Set.of());

            List<Produto> produtosResolvidos = skusCandidatos.stream()
                    .map(sku -> produtosPorSku.get(sku.trim().toUpperCase(Locale.ROOT)))
                    .filter(Objects::nonNull)
                    .toList();

            if (produtosResolvidos.isEmpty()) {
                naoEncontrados++;
                itens.add(ProdutoPrecoPlano.builder()
                        .produto(null)
                        .skuResolvido(null)
                        .nomeComercial(linha.nomeComercial())
                        .categoriaPlanilha(linha.categoriaPlanilha())
                        .ofertaComunicacao(linha.ofertaComunicacao())
                        .preco(linha.preco())
                        .encontrado(false)
                        .build());
                continue;
            }

            // Nome Comercial pode apontar para mais de um Cod_Sap (variação de cor) —
            // todos recebem o mesmo preço, já que a Telefônica não diferencia por cor.
            for (Produto produto : produtosResolvidos) {
                produtosAtualizados.add(produto.getId());
                itens.add(ProdutoPrecoPlano.builder()
                        .produto(produto)
                        .skuResolvido(produto.getSku())
                        .nomeComercial(linha.nomeComercial())
                        .categoriaPlanilha(linha.categoriaPlanilha())
                        .ofertaComunicacao(linha.ofertaComunicacao())
                        .preco(linha.preco())
                        .encontrado(true)
                        .build());

                if (linha.ofertaComunicacao() == null) {
                    produto.setPrecoBase(linha.preco());
                } else {
                    menorPrecoPorProduto.merge(produto.getId(), linha.preco(), BigDecimal::min);
                }
            }
        }

        // Aplica o "a partir de" só para quem não teve preço fixo (sem oferta) já setado acima.
        for (Produto produto : produtosResolvidosUnicos(itens)) {
            BigDecimal menorPreco = menorPrecoPorProduto.get(produto.getId());
            if (menorPreco != null) {
                boolean temPrecoSemOferta = itens.stream()
                        .anyMatch(i -> i.getProduto() != null
                                && i.getProduto().getId().equals(produto.getId())
                                && i.getOfertaComunicacao() == null);
                if (!temPrecoSemOferta) {
                    produto.setPrecoBase(menorPreco);
                }
            }
        }
        produtoRepository.saveAll(produtosResolvidosUnicos(itens));

        TabelaPrecoExecucao execucao = TabelaPrecoExecucao.builder()
                .nomeArquivo(arquivo.getOriginalFilename())
                .vigenciaEm(resultado.vigenciaEm())
                .totalLinhasProcessadas(resultado.linhasPreco().size())
                .totalProdutosAtualizados(produtosAtualizados.size())
                .totalNaoEncontrados(naoEncontrados)
                .build();
        execucao = execucaoRepository.save(execucao);

        for (ProdutoPrecoPlano item : itens) {
            item.setExecucao(execucao);
        }
        precoPlanoRepository.saveAll(itens);

        return TabelaPrecoResumoResponseDTO.from(execucao);
    }

    @Transactional(readOnly = true)
    public TabelaPrecoResumoResponseDTO buscarExecucao(Long execucaoId) {
        TabelaPrecoExecucao execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução de importação de preço"));
        return TabelaPrecoResumoResponseDTO.from(execucao);
    }

    @Transactional(readOnly = true)
    public List<ProdutoPrecoPlano> listarPrecosPorProduto(Long produtoId) {
        if (!produtoRepository.existsById(produtoId)) {
            throw new RecursoNaoEncontradoException("Produto");
        }
        return precoPlanoRepository.findByProdutoIdOrderByOfertaComunicacaoAsc(produtoId);
    }

    /** Tabs de oferta disponíveis (texto bruto da planilha) na importação mais recente. */
    @Transactional(readOnly = true)
    public List<String> listarOfertasDisponiveis() {
        TabelaPrecoExecucao ultima = execucaoRepository.findTopByOrderByCriadoEmDesc()
                .orElseThrow(() -> new NegocioException("Nenhuma tabela de preço foi importada ainda."));
        return precoPlanoRepository.listarOfertasDistintas(ultima.getId());
    }

    /** Catálogo de produtos com preço para uma oferta específica (o conteúdo de um dos tabs), sempre da importação mais recente. */
    @Transactional(readOnly = true)
    public List<ProdutoPrecoPlano> listarProdutosPorOferta(String oferta) {
        TabelaPrecoExecucao ultima = execucaoRepository.findTopByOrderByCriadoEmDesc()
                .orElseThrow(() -> new NegocioException("Nenhuma tabela de preço foi importada ainda."));
        return precoPlanoRepository.listarPorExecucaoEOferta(ultima.getId(), oferta);
    }

    private List<Produto> produtosResolvidosUnicos(List<ProdutoPrecoPlano> itens) {
        Map<Long, Produto> unicos = new LinkedHashMap<>();
        for (ProdutoPrecoPlano item : itens) {
            if (item.getProduto() != null) {
                unicos.putIfAbsent(item.getProduto().getId(), item.getProduto());
            }
        }
        return new ArrayList<>(unicos.values());
    }

    private String normalizarChave(String texto) {
        if (texto == null) return "";
        String semAcento = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}