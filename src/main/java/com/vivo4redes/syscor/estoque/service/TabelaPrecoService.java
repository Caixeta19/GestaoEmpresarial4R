package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.LinhaPrecoDTO;
import com.vivo4redes.syscor.estoque.dto.response.TabelaPrecoResumoResponseDTO;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.ProdutoPrecoPlano;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoPrecoPlanoRepository;
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
 * Importação diária (upload manual) da tabela de preço da Telefônica.
 * Escopo v1: só TABELA REGULAR, um preço por (item, oferta de comunicação/plano).
 * Atualiza na hora, sem aprovação; o histórico fica em ProdutoPrecoPlano.
 *
 * Sem tb_produto, o "produto" da planilha é resolvido direto em tb_estoque_geral:
 * por código de acessório (= Cod_Sap) ou pelo modelo comercial (nome normalizado).
 * O preço novo vai para valor_venda das linhas DISPONÍVEIS — unidades já vendidas
 * mantêm o preço da época.
 */
@Service
@RequiredArgsConstructor
public class TabelaPrecoService {

    private final TabelaPrecoTelefonicaParser parser;
    private final EstoqueGeralRepository estoqueGeralRepository;
    private final TabelaPrecoExecucaoRepository execucaoRepository;
    private final ProdutoPrecoPlanoRepository precoPlanoRepository;

    @Transactional
    public TabelaPrecoResumoResponseDTO importar(MultipartFile arquivo) {
        ResultadoParseTabelaPreco resultado = parser.parsear(arquivo);

        // Índices carregados uma vez (evita N+1): por código de acessório e por modelo comercial.
        Map<String, List<EstoqueGeral>> porCodigo = new HashMap<>();
        Map<String, List<EstoqueGeral>> porModelo = new HashMap<>();
        for (EstoqueGeral e : estoqueGeralRepository.findAll()) {
            if (e.getCodigoAcessorio() != null && !e.getCodigoAcessorio().isBlank()) {
                porCodigo.computeIfAbsent(e.getCodigoAcessorio().trim().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(e);
            }
            if (e.getModeloComercial() != null && !e.getModeloComercial().isBlank()) {
                porModelo.computeIfAbsent(normalizarChave(e.getModeloComercial()), k -> new ArrayList<>()).add(e);
            }
        }

        List<ProdutoPrecoPlano> itens = new ArrayList<>();
        Set<Long> atualizados = new HashSet<>();
        Map<Long, EstoqueGeral> linhasAlteradas = new LinkedHashMap<>();
        int naoEncontrados = 0;

        // Sem variação por plano (oferta == null): o preço vira valor_venda direto.
        // Com variação por plano: valor_venda vira referência "a partir de" (o menor entre os planos).
        Map<Long, BigDecimal> menorPrecoPorLinha = new HashMap<>();
        Set<Long> comPrecoFixo = new HashSet<>();

        for (LinhaPrecoDTO linha : resultado.linhasPreco()) {
            String chaveNome = normalizarChave(linha.nomeComercial());
            Set<String> skusCandidatos = resultado.indiceSkuPorNomeComercial().getOrDefault(chaveNome, Set.of());

            Map<Long, EstoqueGeral> resolvidas = new LinkedHashMap<>();
            for (String sku : skusCandidatos) {
                for (EstoqueGeral e : porCodigo.getOrDefault(sku.trim().toUpperCase(Locale.ROOT), List.of())) {
                    resolvidas.put(e.getId(), e);
                }
            }
            for (EstoqueGeral e : porModelo.getOrDefault(chaveNome, List.of())) {
                resolvidas.put(e.getId(), e);
            }

            if (resolvidas.isEmpty()) {
                naoEncontrados++;
                itens.add(ProdutoPrecoPlano.builder()
                        .produtoId(null)
                        .skuResolvido(null)
                        .nomeComercial(linha.nomeComercial())
                        .categoriaPlanilha(linha.categoriaPlanilha())
                        .ofertaComunicacao(linha.ofertaComunicacao())
                        .preco(linha.preco())
                        .encontrado(false)
                        .build());
                continue;
            }

            // Mesmo nome pode apontar para várias linhas (cores/unidades): todas recebem o mesmo preço.
            EstoqueGeral representante = resolvidas.values().iterator().next();
            atualizados.add(representante.getId());
            itens.add(ProdutoPrecoPlano.builder()
                    .produtoId(representante.getId())
                    .skuResolvido(truncar(skuDe(representante), 40))
                    .nomeComercial(linha.nomeComercial())
                    .categoriaPlanilha(linha.categoriaPlanilha())
                    .ofertaComunicacao(linha.ofertaComunicacao())
                    .preco(linha.preco())
                    .encontrado(true)
                    .build());

            for (EstoqueGeral e : resolvidas.values()) {
                if (e.getStatus() != StatusEstoque.DISPONIVEL) {
                    continue;
                }
                if (linha.ofertaComunicacao() == null) {
                    e.setValorVenda(linha.preco());
                    comPrecoFixo.add(e.getId());
                    linhasAlteradas.put(e.getId(), e);
                } else {
                    menorPrecoPorLinha.merge(e.getId(), linha.preco(), BigDecimal::min);
                    linhasAlteradas.putIfAbsent(e.getId(), e);
                }
            }
        }

        // "A partir de" só para quem não teve preço fixo (sem oferta) nesta importação.
        menorPrecoPorLinha.forEach((id, menor) -> {
            if (!comPrecoFixo.contains(id)) {
                linhasAlteradas.get(id).setValorVenda(menor);
            }
        });
        estoqueGeralRepository.saveAll(linhasAlteradas.values());

        TabelaPrecoExecucao execucao = TabelaPrecoExecucao.builder()
                .nomeArquivo(arquivo.getOriginalFilename())
                .vigenciaEm(resultado.vigenciaEm())
                .totalLinhasProcessadas(resultado.linhasPreco().size())
                .totalProdutosAtualizados(atualizados.size())
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
        if (!estoqueGeralRepository.existsById(produtoId)) {
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

    /** Catálogo de itens com preço para uma oferta específica, sempre da importação mais recente. */
    @Transactional(readOnly = true)
    public List<ProdutoPrecoPlano> listarProdutosPorOferta(String oferta) {
        TabelaPrecoExecucao ultima = execucaoRepository.findTopByOrderByCriadoEmDesc()
                .orElseThrow(() -> new NegocioException("Nenhuma tabela de preço foi importada ainda."));
        return precoPlanoRepository.listarPorExecucaoEOferta(ultima.getId(), oferta);
    }

    private String skuDe(EstoqueGeral e) {
        if (e.getCodigoAcessorio() != null && !e.getCodigoAcessorio().isBlank()) {
            return e.getCodigoAcessorio();
        }
        return e.getModeloComercial();
    }

    private String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private String normalizarChave(String texto) {
        if (texto == null) return "";
        String semAcento = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}