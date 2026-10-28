package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO;
import com.vivo4redes.syscor.estoque.dto.response.EstoqueDisponivelResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ProdutoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ResultadoImportacaoDTO;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.vivo4redes.syscor.estoque.service.ConversorValores.*;

/**
 * Regras do estoque unificado ({@code tb_estoque_geral}):
 * importação do relatório da Vivo (upsert por serial), atualização diária de
 * preços/custos, baixa transacional no PDV e consultas para a tela de venda.
 */
@Service
@RequiredArgsConstructor
public class EstoqueGeralService {

    private static final Logger log = LoggerFactory.getLogger(EstoqueGeralService.class);

    private static final int TAMANHO_LOTE = 500;
    private static final int MAX_ERROS_NO_RETORNO = 100;
    private static final int LIMITE_MAXIMO_BUSCA = 50;

    private final EstoqueGeralRepository repository;
    private final LeitorTabular leitor;

    // ======================================================================
    // Mapeamento coluna do arquivo -> campo da entidade
    // ======================================================================

    /** Coluna do arquivo: aliases (já normalizados) + como aplicar o texto na entidade. */
    private record Campo(String nome, List<String> aliases, BiConsumer<EstoqueGeral, String> aplicador) {
        static Campo de(String nome, BiConsumer<EstoqueGeral, String> aplicador, String... aliases) {
            List<String> normalizados = new ArrayList<>();
            normalizados.add(LeitorTabular.normalizar(nome));
            for (String a : aliases) normalizados.add(LeitorTabular.normalizar(a));
            return new Campo(nome, normalizados, aplicador);
        }
    }

    private static final String CAMPO_SERIAL = "serial";

    /** Colunas do relatório oficial da Vivo (status/criado_em/data_venda são nossos, não vêm do arquivo). */
    private static final List<Campo> CAMPOS_RELATORIO = List.of(
            Campo.de("tipo_produto", (e, v) -> e.setTipoProduto(texto(v)), "tipo produto", "tipo"),
            Campo.de("categoria", (e, v) -> e.setCategoria(texto(v))),
            Campo.de("subcategoria", (e, v) -> e.setSubcategoria(texto(v)), "sub categoria"),
            Campo.de("marca", (e, v) -> e.setMarca(texto(v)), "fabricante"),
            Campo.de("modelo_comercial", (e, v) -> e.setModeloComercial(texto(v)), "modelo", "nome comercial", "descricao"),
            Campo.de("modelo_dpgc", (e, v) -> e.setModeloDpgc(texto(v)), "modelo dpgc"),
            Campo.de("cor", (e, v) -> e.setCor(texto(v))),
            Campo.de("ncm", (e, v) -> e.setNcm(texto(v))),
            Campo.de(CAMPO_SERIAL, (e, v) -> e.setSerial(serial(v)), "imei", "numero de serie", "no de serie", "nr serie", "serie"),
            Campo.de("codigo_acessorio", (e, v) -> e.setCodigoAcessorio(texto(v)), "cod acessorio", "codigo do acessorio"),
            Campo.de("numero_acesso", (e, v) -> e.setNumeroAcesso(texto(v)), "n acesso", "no acesso", "numero do acesso"),
            Campo.de("filial_estoque", (e, v) -> e.setFilialEstoque(texto(v)), "filial", "deposito", "filial do estoque"),
            Campo.de("num_compra", (e, v) -> e.setNumCompra(texto(v)), "numero compra", "n compra", "no compra", "numero da compra"),
            Campo.de("data_compra", (e, v) -> e.setDataCompra(data(v)), "dt compra", "data da compra"),
            Campo.de("data_emissao_nota", (e, v) -> e.setDataEmissaoNota(data(v)), "data emissao", "dt emissao nota", "emissao nota"),
            Campo.de("filial_compra", (e, v) -> e.setFilialCompra(texto(v)), "filial da compra"),
            Campo.de("fornecedor_compra", (e, v) -> e.setFornecedorCompra(texto(v)), "fornecedor", "fornecedor da compra"),
            Campo.de("tempo_na_rede", (e, v) -> e.setTempoNaRede(texto(v))),
            Campo.de("dias_na_rede", (e, v) -> e.setDiasNaRede(inteiro(v))),
            Campo.de("tempo_no_pdv", (e, v) -> e.setTempoNoPdv(texto(v))),
            Campo.de("dias_no_pdv", (e, v) -> e.setDiasNoPdv(inteiro(v))),
            Campo.de("quantidade", (e, v) -> e.setQuantidade(inteiro(v)), "qtd", "qtde"),
            Campo.de("valor_venda", (e, v) -> e.setValorVenda(decimal(v)), "preco venda", "preco", "pvp"),
            Campo.de("valor_custo", (e, v) -> e.setValorCusto(decimal(v)), "custo"),
            Campo.de("custo_ultima_compra_un", (e, v) -> e.setCustoUltimaCompraUn(decimal(v)), "custo ultima compra", "custo ultima compra un"),
            Campo.de("custo_medio_un", (e, v) -> e.setCustoMedioUn(decimal(v)), "custo medio", "custo medio un")
    );

    /** Colunas da tabela diária de preços (chave: serial OU modelo comercial). */
    private static final List<Campo> CAMPOS_PRECO = List.of(
            Campo.de(CAMPO_SERIAL, (e, v) -> e.setSerial(serial(v)), "imei", "numero de serie", "serie"),
            Campo.de("modelo_comercial", (e, v) -> e.setModeloComercial(texto(v)), "modelo", "nome comercial", "descricao"),
            Campo.de("valor_venda", (e, v) -> e.setValorVenda(decimal(v)), "preco venda", "preco", "pvp", "pvd"),
            Campo.de("valor_custo", (e, v) -> e.setValorCusto(decimal(v)), "custo"),
            Campo.de("custo_ultima_compra_un", (e, v) -> e.setCustoUltimaCompraUn(decimal(v)), "custo ultima compra", "custo ultima compra un"),
            Campo.de("custo_medio_un", (e, v) -> e.setCustoMedioUn(decimal(v)), "custo medio", "custo medio un")
    );

    // ======================================================================
    // 1. Importação do relatório da Vivo (upsert por serial)
    // ======================================================================

    /**
     * Lê o relatório (CSV/XLSX) linha a linha e faz upsert por {@code serial}:
     * serial novo é inserido (DISPONIVEL, depósito DP01 se não informado); serial
     * existente tem os dados atualizados. Nunca altera {@code status}, {@code criado_em}
     * nem {@code data_venda}, e célula em branco NÃO apaga o valor já gravado.
     * Linhas inválidas são reportadas e não abortam o lote.
     */
    @Transactional
    public ResultadoImportacaoDTO importarRelatorioVivo(MultipartFile arquivo) {
        LeitorTabular.Tabela tabela = leitor.ler(arquivo);
        Map<String, Campo> porColuna = resolverColunas(tabela.colunas(), CAMPOS_RELATORIO);
        exigirColuna(porColuna, CAMPO_SERIAL,
                "Não encontrei a coluna de serial/IMEI no relatório. Colunas esperadas: "
                        + CAMPOS_RELATORIO.stream().map(Campo::nome).collect(Collectors.joining(", ")) + ".");

        List<String> erros = new ArrayList<>();
        int ignorados = 0;

        // Última ocorrência de cada serial no arquivo prevalece.
        Map<String, LeitorTabular.Linha> linhasPorSerial = new LinkedHashMap<>();
        for (LeitorTabular.Linha linha : tabela.linhas()) {
            String serial = serial(valorDaColuna(linha, porColuna, CAMPO_SERIAL));
            if (serial.isBlank()) {
                ignorados++;
                registrarErro(erros, "Linha " + linha.numero() + ": serial vazio — linha ignorada.");
                continue;
            }
            if (linhasPorSerial.put(serial, linha) != null) {
                registrarErro(erros, "Serial " + serial + " repetido no arquivo — prevaleceu a linha " + linha.numero() + ".");
            }
        }

        int inseridos = 0;
        int atualizados = 0;
        List<String> seriais = new ArrayList<>(linhasPorSerial.keySet());
        for (int ini = 0; ini < seriais.size(); ini += TAMANHO_LOTE) {
            List<String> lote = seriais.subList(ini, Math.min(ini + TAMANHO_LOTE, seriais.size()));
            Map<String, EstoqueGeral> existentes = repository.findBySerialIn(lote).stream()
                    .collect(Collectors.toMap(EstoqueGeral::getSerial, e -> e));

            List<EstoqueGeral> paraSalvar = new ArrayList<>();
            for (String serial : lote) {
                LeitorTabular.Linha linha = linhasPorSerial.get(serial);
                EstoqueGeral destino = existentes.get(serial);
                boolean novo = destino == null;
                if (novo) destino = EstoqueGeral.builder().serial(serial).build();
                try {
                    aplicarLinha(destino, linha, porColuna);
                    destino.setSerial(serial); // garante a chave normalizada
                    paraSalvar.add(destino);
                    if (novo) inseridos++; else atualizados++;
                } catch (IllegalArgumentException ex) {
                    ignorados++;
                    registrarErro(erros, "Linha " + linha.numero() + " (serial " + serial + "): " + ex.getMessage());
                }
            }
            repository.saveAll(paraSalvar);
            repository.flush();
        }

        log.info("Importação do relatório Vivo: {} linhas, {} inseridos, {} atualizados, {} ignorados",
                tabela.linhas().size(), inseridos, atualizados, ignorados);
        return new ResultadoImportacaoDTO(tabela.linhas().size(), inseridos, atualizados, ignorados, erros);
    }

    // ======================================================================
    // 2. Atualização diária de preços e custos (em lote)
    // ======================================================================

    /**
     * Aplica valores de venda/custos da tabela diária da operadora. Cada linha
     * identifica o alvo por {@code serial} (uma unidade) ou por {@code modelo_comercial}
     * (todas as unidades do modelo). Serial prevalece sobre modelo. Itens
     * VENDIDOS não são alterados (histórico preservado). Célula vazia não apaga valor.
     */
    @Transactional
    public ResultadoImportacaoDTO atualizarPrecos(MultipartFile arquivo) {
        LeitorTabular.Tabela tabela = leitor.ler(arquivo);
        Map<String, Campo> porColuna = resolverColunas(tabela.colunas(), CAMPOS_PRECO);

        boolean temChave = porColuna.containsKey(CAMPO_SERIAL) || porColuna.containsKey("modelo_comercial");
        boolean temValor = porColuna.containsKey("valor_venda") || porColuna.containsKey("valor_custo")
                || porColuna.containsKey("custo_ultima_compra_un") || porColuna.containsKey("custo_medio_un");
        if (!temChave || !temValor) {
            throw new NegocioException("A tabela de preços precisa de uma coluna de identificação (serial ou modelo comercial) "
                    + "e ao menos uma de valor (valor_venda, valor_custo, custo_ultima_compra_un, custo_medio_un).");
        }

        List<String> erros = new ArrayList<>();
        int ignorados = 0;

        // Valores da linha como uma "entidade-molde" (só os campos de preço preenchidos).
        Map<String, EstoqueGeral> moldePorModelo = new LinkedHashMap<>();
        Map<String, EstoqueGeral> moldePorSerial = new LinkedHashMap<>();
        for (LeitorTabular.Linha linha : tabela.linhas()) {
            try {
                EstoqueGeral molde = new EstoqueGeral();
                aplicarLinha(molde, linha, porColuna);
                String serial = serial(molde.getSerial());
                String modelo = chaveModelo(molde.getModeloComercial());
                if (!serial.isBlank()) {
                    moldePorSerial.put(serial, molde);
                } else if (modelo != null) {
                    moldePorModelo.put(modelo, molde);
                } else {
                    ignorados++;
                    registrarErro(erros, "Linha " + linha.numero() + ": sem serial nem modelo comercial — ignorada.");
                }
            } catch (IllegalArgumentException ex) {
                ignorados++;
                registrarErro(erros, "Linha " + linha.numero() + ": " + ex.getMessage());
            }
        }

        int atualizados = 0;
        Set<String> modelosSemItem = new LinkedHashSet<>(moldePorModelo.keySet());
        Set<String> seriaisSemItem = new LinkedHashSet<>(moldePorSerial.keySet());

        // 1º modelo (abrangente), 2º serial (específico) — o serial sobrescreve o modelo.
        List<String> modelos = new ArrayList<>(moldePorModelo.keySet());
        for (int ini = 0; ini < modelos.size(); ini += TAMANHO_LOTE) {
            List<String> lote = modelos.subList(ini, Math.min(ini + TAMANHO_LOTE, modelos.size()));
            List<EstoqueGeral> itens = repository.findByModeloIn(lote, StatusEstoque.VENDIDO);
            for (EstoqueGeral item : itens) {
                String chave = chaveModelo(item.getModeloComercial());
                copiarPrecos(moldePorModelo.get(chave), item);
                modelosSemItem.remove(chave);
            }
            atualizados += itens.size();
            repository.saveAll(itens);
            repository.flush();
        }

        List<String> seriais = new ArrayList<>(moldePorSerial.keySet());
        for (int ini = 0; ini < seriais.size(); ini += TAMANHO_LOTE) {
            List<String> lote = seriais.subList(ini, Math.min(ini + TAMANHO_LOTE, seriais.size()));
            List<EstoqueGeral> itens = repository.findBySerialInAndStatusNot(lote, StatusEstoque.VENDIDO);
            for (EstoqueGeral item : itens) {
                copiarPrecos(moldePorSerial.get(item.getSerial()), item);
                seriaisSemItem.remove(item.getSerial());
            }
            atualizados += itens.size();
            repository.saveAll(itens);
            repository.flush();
        }

        modelosSemItem.forEach(m -> registrarErro(erros, "Modelo '" + m + "': nenhum item não vendido encontrado."));
        seriaisSemItem.forEach(s -> registrarErro(erros, "Serial '" + s + "': não encontrado ou já vendido."));

        log.info("Atualização de preços: {} linhas, {} itens atualizados, {} ignoradas",
                tabela.linhas().size(), atualizados, ignorados);
        return new ResultadoImportacaoDTO(tabela.linhas().size(), 0, atualizados,
                ignorados + modelosSemItem.size() + seriaisSemItem.size(), erros);
    }

    /** Copia só os valores presentes (não nulos) do molde. Uso compartilhado com a tabela Telefônica. */
    private void copiarPrecos(EstoqueGeral molde, EstoqueGeral destino) {
        if (molde.getValorVenda() != null) destino.setValorVenda(molde.getValorVenda());
        if (molde.getValorCusto() != null) destino.setValorCusto(molde.getValorCusto());
        if (molde.getCustoUltimaCompraUn() != null) destino.setCustoUltimaCompraUn(molde.getCustoUltimaCompraUn());
        if (molde.getCustoMedioUn() != null) destino.setCustoMedioUn(molde.getCustoMedioUn());
    }

    /**
     * Atualiza {@code valor_venda} de todas as unidades não vendidas de um modelo
     * (usado pela importação da tabela Telefônica por oferta). Retorna quantas unidades mudaram.
     */
    @Transactional
    public int atualizarValorVendaPorModelos(Map<String, BigDecimal> valorPorModelo) {
        Map<String, BigDecimal> porChave = new HashMap<>();
        valorPorModelo.forEach((modelo, valor) -> {
            String chave = chaveModelo(modelo);
            if (chave != null && valor != null) porChave.put(chave, valor);
        });
        int total = 0;
        List<String> chaves = new ArrayList<>(porChave.keySet());
        for (int ini = 0; ini < chaves.size(); ini += TAMANHO_LOTE) {
            List<String> lote = chaves.subList(ini, Math.min(ini + TAMANHO_LOTE, chaves.size()));
            List<EstoqueGeral> itens = repository.findByModeloIn(lote, StatusEstoque.VENDIDO);
            itens.forEach(i -> i.setValorVenda(porChave.get(chaveModelo(i.getModeloComercial()))));
            repository.saveAll(itens);
            repository.flush();
            total += itens.size();
        }
        return total;
    }

    /** Modelos (chave em maiúsculas) que existem no estoque — para marcar "encontrado" nas ofertas. */
    @Transactional(readOnly = true)
    public Set<String> modelosExistentes(Collection<String> modelos) {
        Set<String> chaves = modelos.stream().map(ConversorValores::chaveModelo)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> existentes = new HashSet<>();
        List<String> lista = new ArrayList<>(chaves);
        for (int ini = 0; ini < lista.size(); ini += TAMANHO_LOTE) {
            List<String> lote = lista.subList(ini, Math.min(ini + TAMANHO_LOTE, lista.size()));
            existentes.addAll(repository.findModelosExistentes(lote));
        }
        return existentes;
    }

    // ======================================================================
    // 3. PDV: baixa e estorno
    // ======================================================================

    /**
     * Baixa uma unidade na venda. Trava a linha (FOR UPDATE), valida que está
     * DISPONIVEL, marca VENDIDO e carimba {@code data_venda}. Além do lock
     * pessimista, {@code @Version} protege contra qualquer escrita concorrente
     * fora desse fluxo (importação, estorno).
     */
    @Transactional
    public EstoqueGeral baixarNaVenda(String serialInformado) {
        String serial = serial(serialInformado);
        if (serial.isBlank()) {
            throw new NegocioException("Informe o IMEI/Serial do item.");
        }
        EstoqueGeral item = repository.findBySerialForUpdate(serial)
                .orElseThrow(() -> new NegocioException("O IMEI/Serial '" + serial + "' não foi encontrado no estoque."));

        if (!item.disponivelParaVenda()) {
            throw new NegocioException("O IMEI/Serial '" + serial + "' não está disponível para venda (status atual: "
                    + item.getStatus() + ").");
        }
        item.baixarPorVenda();
        return repository.save(item);
    }

    /** Desfaz a baixa (item removido do carrinho ou venda cancelada/estornada). */
    @Transactional
    public void estornarBaixa(EstoqueGeral item) {
        if (item == null) return;
        item.estornarBaixa();
        repository.save(item);
    }

    // ======================================================================
    // 4. Consultas (PDV / relatórios)
    // ======================================================================

    /** Carrega, para a tela de venda, as unidades DISPONIVEL filtradas por trecho de serial/modelo/marca/código. */
    @Transactional(readOnly = true)
    public List<EstoqueDisponivelResponseDTO> buscarDisponiveis(String busca, String filial, int limite) {
        String termo = busca == null ? "" : busca.trim();
        String filialFiltro = filial == null ? "" : filial.trim();
        int tamanho = Math.min(Math.max(limite, 1), LIMITE_MAXIMO_BUSCA);
        return repository.buscarDisponiveis(StatusEstoque.DISPONIVEL, termo, filialFiltro, PageRequest.of(0, tamanho))
                .stream().map(EstoqueDisponivelResponseDTO::from).toList();
    }

    /** Uma unidade DISPONIVEL pelo serial exato (leitura de código de barras no PDV). */
    @Transactional(readOnly = true)
    public EstoqueDisponivelResponseDTO buscarDisponivelPorSerial(String serialInformado) {
        return repository.findBySerialAndStatus(serial(serialInformado), StatusEstoque.DISPONIVEL)
                .map(EstoqueDisponivelResponseDTO::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item disponível com esse serial"));
    }

    /** Unidade por id (qualquer status) — usado pela venda para validar o item escolhido. */
    @Transactional(readOnly = true)
    public EstoqueGeral buscarPorId(Long id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Item de estoque"));
    }

    /** Catálogo para o PDV: unidades DISPONIVEL, com busca livre e filtro opcional de categoria. */
    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listarCatalogo(String busca, CategoriaItemVenda categoria) {
        String termo = busca == null ? "" : busca.trim();
        return repository.buscarDisponiveis(StatusEstoque.DISPONIVEL, termo, "", PageRequest.of(0, LIMITE_MAXIMO_BUSCA))
                .stream()
                .filter(e -> categoria == null
                        || (e.getCategoria() != null && e.getCategoria().equalsIgnoreCase(categoria.name())))
                .map(ProdutoResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EstoqueConsolidadoModeloDTO> consolidarPorModelo() {
        return repository.consolidarPorModelo();
    }

    /** Modelos críticos/em ruptura (saldo disponível ≤ mínimo, padrão 2). */
    @Transactional(readOnly = true)
    public List<EstoqueConsolidadoModeloDTO> listarAlertas() {
        return repository.consolidarPorModelo().stream().filter(EstoqueConsolidadoModeloDTO::critico).toList();
    }

    @Transactional(readOnly = true)
    public long contarModelosCriticos() {
        return repository.consolidarPorModelo().stream().filter(EstoqueConsolidadoModeloDTO::critico).count();
    }

    // ======================================================================
    // Internos
    // ======================================================================

    private Map<String, Campo> resolverColunas(Set<String> colunasDoArquivo, List<Campo> definicao) {
        Map<String, Campo> porColuna = new LinkedHashMap<>();
        for (Campo campo : definicao) {
            for (String alias : campo.aliases()) {
                if (colunasDoArquivo.contains(alias)) {
                    porColuna.putIfAbsent(alias, campo);
                    break;
                }
            }
        }
        return porColuna;
    }

    private void exigirColuna(Map<String, Campo> porColuna, String nomeCampo, String mensagem) {
        boolean achou = porColuna.values().stream().anyMatch(c -> c.nome().equals(nomeCampo));
        if (!achou) throw new NegocioException(mensagem);
    }

    private String valorDaColuna(LeitorTabular.Linha linha, Map<String, Campo> porColuna, String nomeCampo) {
        for (Map.Entry<String, Campo> e : porColuna.entrySet()) {
            if (e.getValue().nome().equals(nomeCampo)) return linha.valores().get(e.getKey());
        }
        return null;
    }

    /** Aplica na entidade apenas as células preenchidas (vazio não sobrescreve). */
    private void aplicarLinha(EstoqueGeral destino, LeitorTabular.Linha linha, Map<String, Campo> porColuna) {
        for (Map.Entry<String, Campo> e : porColuna.entrySet()) {
            String valor = linha.valores().get(e.getKey());
            if (texto(valor) == null) continue;
            try {
                e.getValue().aplicador().accept(destino, valor);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("coluna '" + e.getValue().nome() + "': " + ex.getMessage());
            }
        }
    }

    private void registrarErro(List<String> erros, String mensagem) {
        if (erros.size() < MAX_ERROS_NO_RETORNO) erros.add(mensagem);
    }
}