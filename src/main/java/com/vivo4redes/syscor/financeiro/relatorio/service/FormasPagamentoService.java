package com.vivo4redes.syscor.financeiro.relatorio.service;

import com.vivo4redes.syscor.exception.FinanceiroException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.financeiro.relatorio.dto.AgruparPor;
import com.vivo4redes.syscor.financeiro.relatorio.dto.DetalhePagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.FiltroFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.OpcoesFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.PermissoesFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.RelatorioFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.repository.FormasPagamentoRepository;
import com.vivo4redes.syscor.financeiro.service.CaixaPerfilService;
import com.vivo4redes.syscor.financeiro.service.FinanceiroPerfilService;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Relatório de Formas de Pagamento (Financeiro › Movimento), calculado ao vivo de {@code pagamentos_venda} das vendas
 * CONCLUIDA, na mesma regra de {@code somarPorCaixaEForma} (o tipo da linha não filtra: ENTRADA e FINANCIADO entram).
 * PDV = filial; o caixa é o {@code caixaPdv} da sessão. O dia é o de {@code finalizadoEm} em America/Sao_Paulo.
 * Quem pode ver: {@link FinanceiroPerfilService#podeConsultar}; as filiais seguem {@link CaixaPerfilService}.
 */
@Service
@RequiredArgsConstructor
public class FormasPagamentoService {

    private static final Logger log = LoggerFactory.getLogger(FormasPagamentoService.class);

    static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    static final int MAXIMO_DIAS = 366;
    static final int MAXIMO_LINHAS_DETALHE = 100_000;
    static final String SEM_CAIXA = "Sem caixa";

    private final FormasPagamentoRepository repository;
    private final FilialRepository filialRepository;
    private final UsuarioService usuarioService;
    private final FinanceiroPerfilService perfil;
    private final CaixaPerfilService caixaPerfil;
    private final FormasPagamentoExportacao exportacao;

    /** Arquivo pronto para download. */
    public record Arquivo(String nome, String contentType, byte[] conteudo) {
    }

    // ------------------------------------------------------------------ endpoints

    public PermissoesFormasPagamentoDTO permissoes() {
        return new PermissoesFormasPagamentoDTO(perfil.podeConsultar(usuarioService.usuarioLogado()));
    }

    @Transactional(readOnly = true)
    public OpcoesFormasPagamentoDTO opcoes() {
        Usuario usuario = exigirConsulta();
        Set<Long> visiveis = caixaPerfil.filiaisVisiveis(usuario);
        List<OpcoesFormasPagamentoDTO.FilialOpcao> filiais = filialRepository.findAllByOrderByNomeAsc().stream()
                .filter(f -> visiveis == null || visiveis.contains(f.getId()))
                .map(f -> new OpcoesFormasPagamentoDTO.FilialOpcao(f.getId(), f.getCodigo(), f.getNome()))
                .toList();
        // Só as formas que as vendas realmente usaram (nunca uma lista escrita no código): VOUCHER aparece apenas se
        // houver pagamento histórico com ele. Escopo sem filial nenhuma não tem forma.
        boolean semFilial = visiveis != null && visiveis.isEmpty();
        List<FormaPagamento> usadas = semFilial ? List.of()
                : repository.formasUsadas(visiveis == null, visiveis == null ? List.of(-1L) : List.copyOf(visiveis));
        List<OpcoesFormasPagamentoDTO.FormaOpcao> formas = usadas.stream().sorted()
                .map(f -> new OpcoesFormasPagamentoDTO.FormaOpcao(f.name(), rotulo(f)))
                .toList();
        return new OpcoesFormasPagamentoDTO(filiais, formas);
    }

    @Transactional(readOnly = true)
    public RelatorioFormasPagamentoDTO relatorio(FiltroFormasPagamentoDTO filtro) {
        Usuario usuario = exigirConsulta();
        return calcular(usuario, filtro);
    }

    @Transactional(readOnly = true)
    public Arquivo exportar(FiltroFormasPagamentoDTO filtro, String formato) {
        Usuario usuario = exigirConsulta();
        Formato tipo = Formato.de(formato);
        RelatorioFormasPagamentoDTO relatorio = calcular(usuario, filtro);
        String nome = "formas-pagamento_" + filtro.dataInicio() + "_" + filtro.dataFim() + "." + tipo.extensao;
        log.info("Relatório de formas de pagamento exportado: usuario={}, formato={}, periodo={}..{}, agruparPor={}, "
                        + "filiais={}, formas={}", usuario.getLogin(), tipo.extensao, filtro.dataInicio(),
                filtro.dataFim(), relatorio.agruparPor(), tamanho(filtro.filialIds()), tamanho(filtro.formas()));
        byte[] conteudo = switch (tipo) {
            case XLSX -> exportacao.xlsx(relatorio, detalhe(usuario, filtro));
            case PDF -> exportacao.pdf(relatorio, usuario.getNome(), descreverFiltros(filtro));
            case CSV -> exportacao.csv(relatorio);
        };
        return new Arquivo(nome, tipo.contentType, conteudo);
    }

    // ------------------------------------------------------------------ cálculo

    private RelatorioFormasPagamentoDTO calcular(Usuario usuario, FiltroFormasPagamentoDTO filtro) {
        Consulta c = consulta(usuario, filtro);
        AgruparPor agrupar = filtro.agruparPor() == null ? AgruparPor.FILIAL : filtro.agruparPor();
        OffsetDateTime agora = OffsetDateTime.now(ZONA);
        RelatorioFormasPagamentoDTO.Periodo periodo =
                new RelatorioFormasPagamentoDTO.Periodo(filtro.dataInicio(), filtro.dataFim());
        if (c.escopoVazio) {
            return new RelatorioFormasPagamentoDTO(periodo, agrupar, agora, 0, List.of(), zero(), 0, 0, List.of());
        }
        log.info("Relatório de formas de pagamento consultado: usuario={}, periodo={}..{}, agruparPor={}, filiais={}, "
                        + "formas={}", usuario.getLogin(), filtro.dataInicio(), filtro.dataFim(), agrupar,
                tamanho(filtro.filialIds()), tamanho(filtro.formas()));

        List<Object[]> linhas = repository.agregarPorFilialCaixaForma(c.inicio, c.fim, c.todasFiliais, c.filialIds,
                c.formas);
        long semData = repository.contarSemDataFinalizacao(c.todasFiliais, c.filialIds);
        long vendas = repository.contarVendas(c.inicio, c.fim, c.todasFiliais, c.filialIds, c.formas);

        BigDecimal totalGeral = BigDecimal.ZERO;
        long pagamentos = 0;
        Map<FormaPagamento, BigDecimal> porForma = new EnumMap<>(FormaPagamento.class);
        for (Object[] l : linhas) {
            BigDecimal valor = numero(l[4]);
            totalGeral = totalGeral.add(valor);
            pagamentos += ((Number) l[5]).longValue();
            porForma.merge((FormaPagamento) l[3], valor, BigDecimal::add);
        }
        BigDecimal total = totalGeral;
        List<RelatorioFormasPagamentoDTO.TotalForma> totais = porForma.entrySet().stream()
                .sorted(Map.Entry.<FormaPagamento, BigDecimal>comparingByValue().reversed())
                .map(e -> new RelatorioFormasPagamentoDTO.TotalForma(e.getKey().name(), rotulo(e.getKey()),
                        dinheiro(e.getValue()), percentual(e.getValue(), total)))
                .toList();

        List<RelatorioFormasPagamentoDTO.Grupo> grupos = switch (agrupar) {
            case FILIAL, CAIXA -> gruposPorFilialOuCaixa(agrupar, linhas, c);
            case FORMA -> gruposPorForma(linhas, c);
        };
        return new RelatorioFormasPagamentoDTO(periodo, agrupar, agora, semData, grupos, dinheiro(totalGeral),
                pagamentos, vendas, totais);
    }

    /** Grupo = filial (ou filial + caixa); as linhas são as formas. */
    private List<RelatorioFormasPagamentoDTO.Grupo> gruposPorFilialOuCaixa(AgruparPor agrupar, List<Object[]> linhas,
                                                                           Consulta c) {
        boolean porCaixa = agrupar == AgruparPor.CAIXA;
        Map<String, Acumulador> grupos = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            Long filialId = (Long) l[0];
            String filial = (String) l[1];
            String caixa = l[2] == null ? SEM_CAIXA : (String) l[2];
            String chave = porCaixa ? filialId + "|" + caixa : String.valueOf(filialId);
            String rotulo = porCaixa ? filial + " · " + caixa : filial;
            Acumulador g = grupos.computeIfAbsent(chave, k -> new Acumulador(k, rotulo));
            FormaPagamento forma = (FormaPagamento) l[3];
            g.somar(forma.name(), rotulo(forma), forma, numero(l[4]), ((Number) l[5]).longValue());
        }
        for (Object[] v : repository.vendasPorFilialCaixa(c.inicio, c.fim, c.todasFiliais, c.filialIds, c.formas)) {
            String caixa = v[1] == null ? SEM_CAIXA : (String) v[1];
            String chave = porCaixa ? v[0] + "|" + caixa : String.valueOf(v[0]);
            Acumulador g = grupos.get(chave);
            if (g != null) g.vendas += ((Number) v[2]).longValue();
        }
        return finalizar(grupos);
    }

    /** Grupo = forma; as linhas são as filiais. */
    private List<RelatorioFormasPagamentoDTO.Grupo> gruposPorForma(List<Object[]> linhas, Consulta c) {
        Map<String, Acumulador> grupos = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            FormaPagamento forma = (FormaPagamento) l[3];
            Acumulador g = grupos.computeIfAbsent(forma.name(), k -> new Acumulador(k, rotulo(forma)));
            g.somar(String.valueOf(l[0]), (String) l[1], forma, numero(l[4]), ((Number) l[5]).longValue());
        }
        for (Object[] v : repository.vendasPorForma(c.inicio, c.fim, c.todasFiliais, c.filialIds, c.formas)) {
            Acumulador g = grupos.get(((FormaPagamento) v[0]).name());
            if (g != null) g.vendas = ((Number) v[1]).longValue();
        }
        return finalizar(grupos);
    }

    private List<RelatorioFormasPagamentoDTO.Grupo> finalizar(Map<String, Acumulador> grupos) {
        return grupos.values().stream()
                .sorted(Comparator.comparing(g -> g.rotulo, String.CASE_INSENSITIVE_ORDER))
                .map(Acumulador::paraDto)
                .toList();
    }

    /** Soma por linha dentro de um grupo (chave da linha = forma ou filial). */
    private static final class Acumulador {
        final String chave;
        final String rotulo;
        final Map<String, Linha> linhas = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        long pagamentos;
        long vendas;

        Acumulador(String chave, String rotulo) {
            this.chave = chave;
            this.rotulo = rotulo;
        }

        void somar(String chaveLinha, String rotuloLinha, FormaPagamento forma, BigDecimal valor, long quantidade) {
            Linha l = linhas.computeIfAbsent(chaveLinha, k -> new Linha(k, rotuloLinha, forma));
            l.valor = l.valor.add(valor);
            l.quantidade += quantidade;
            total = total.add(valor);
            pagamentos += quantidade;
        }

        RelatorioFormasPagamentoDTO.Grupo paraDto() {
            List<RelatorioFormasPagamentoDTO.Linha> dto = linhas.values().stream()
                    .sorted(Comparator.comparing((Linha l) -> l.valor).reversed())
                    .map(l -> new RelatorioFormasPagamentoDTO.Linha(l.chave, l.rotulo, l.forma.name(),
                            rotulo(l.forma), dinheiro(l.valor), percentual(l.valor, total), l.quantidade))
                    .toList();
            return new RelatorioFormasPagamentoDTO.Grupo(chave, rotulo, dinheiro(total), pagamentos, vendas, dto);
        }
    }

    private static final class Linha {
        final String chave;
        final String rotulo;
        final FormaPagamento forma;
        BigDecimal valor = BigDecimal.ZERO;
        long quantidade;

        Linha(String chave, String rotulo, FormaPagamento forma) {
            this.chave = chave;
            this.rotulo = rotulo;
            this.forma = forma;
        }
    }

    // ------------------------------------------------------------------ detalhe (exportação)

    private List<DetalhePagamentoDTO> detalhe(Usuario usuario, FiltroFormasPagamentoDTO filtro) {
        Consulta c = consulta(usuario, filtro);
        if (c.escopoVazio) return List.of();
        List<Object[]> linhas = repository.detalhe(c.inicio, c.fim, c.todasFiliais, c.filialIds, c.formas,
                PageRequest.of(0, MAXIMO_LINHAS_DETALHE + 1));
        if (linhas.size() > MAXIMO_LINHAS_DETALHE) {
            throw new NegocioException("O detalhe tem mais de " + MAXIMO_LINHAS_DETALHE
                    + " pagamentos. Reduza o período ou filtre por filial.");
        }
        List<DetalhePagamentoDTO> detalhe = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            FormaPagamento forma = (FormaPagamento) l[4];
            detalhe.add(new DetalhePagamentoDTO(
                    ((Instant) l[0]).atZone(ZONA).toLocalDateTime(), (Long) l[1], (String) l[2],
                    l[3] == null ? SEM_CAIXA : (String) l[3], forma.name(), rotulo(forma), String.valueOf(l[5]),
                    numero(l[6]), (Integer) l[7]));
        }
        return detalhe;
    }

    // ------------------------------------------------------------------ filtros e escopo

    /** Parâmetros já resolvidos para as queries (escopo do usuário aplicado). */
    private record Consulta(Instant inicio, Instant fim, boolean todasFiliais, List<Long> filialIds,
                            Set<FormaPagamento> formas, boolean escopoVazio) {
    }

    private Consulta consulta(Usuario usuario, FiltroFormasPagamentoDTO filtro) {
        validarPeriodo(filtro);
        Instant inicio = filtro.dataInicio().atStartOfDay(ZONA).toInstant();
        Instant fim = filtro.dataFim().plusDays(1).atStartOfDay(ZONA).toInstant();
        Set<FormaPagamento> formas = filtro.formas() == null || filtro.formas().isEmpty()
                ? EnumSet.allOf(FormaPagamento.class) : EnumSet.copyOf(filtro.formas());

        Set<Long> visiveis = caixaPerfil.filiaisVisiveis(usuario);   // null = todas
        List<Long> pedidas = filtro.filialIds() == null ? List.of() : filtro.filialIds();
        if (visiveis == null && pedidas.isEmpty()) {
            return new Consulta(inicio, fim, true, List.of(-1L), formas, false);
        }
        List<Long> efetivas = pedidas.isEmpty()
                ? List.copyOf(visiveis)
                : pedidas.stream().filter(id -> visiveis == null || visiveis.contains(id)).distinct().toList();
        if (efetivas.isEmpty()) return new Consulta(inicio, fim, false, List.of(-1L), formas, true);
        return new Consulta(inicio, fim, false, efetivas, formas, false);
    }

    private static void validarPeriodo(FiltroFormasPagamentoDTO f) {
        if (f.dataInicio() == null || f.dataFim() == null) {
            throw new NegocioException("Informe a data inicial e a data final.");
        }
        if (f.dataFim().isBefore(f.dataInicio())) {
            throw new NegocioException("A data final não pode ser anterior à data inicial.");
        }
        if (ChronoUnit.DAYS.between(f.dataInicio(), f.dataFim()) + 1 > MAXIMO_DIAS) {
            throw new NegocioException("O período pode ter no máximo " + MAXIMO_DIAS + " dias.");
        }
    }

    private Usuario exigirConsulta() {
        Usuario usuario = usuarioService.usuarioLogado();
        if (!perfil.podeConsultar(usuario)) throw FinanceiroException.semPermissaoRelatorio();
        return usuario;
    }

    private String descreverFiltros(FiltroFormasPagamentoDTO f) {
        String filiais = f.filialIds() == null || f.filialIds().isEmpty() ? "todas as filiais permitidas"
                : filialRepository.findAllById(f.filialIds()).stream().map(Filial::getNome).sorted()
                .collect(Collectors.joining(", "));
        String formas = f.formas() == null || f.formas().isEmpty() ? "todas as formas"
                : f.formas().stream().map(FormaPagamentoRotulos::rotulo).sorted().collect(Collectors.joining(", "));
        return "Filiais: " + filiais + " · Formas: " + formas;
    }

    // ------------------------------------------------------------------ apoio

    static String rotulo(FormaPagamento forma) {
        return FormaPagamentoRotulos.rotulo(forma);
    }

    private static int tamanho(java.util.Collection<?> c) {
        return c == null ? 0 : c.size();
    }

    private static BigDecimal numero(Object o) {
        return o instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(o));
    }

    static BigDecimal dinheiro(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return zero();
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private enum Formato {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PDF("pdf", "application/pdf"),
        CSV("csv", "text/csv; charset=UTF-8");

        final String extensao;
        final String contentType;

        Formato(String extensao, String contentType) {
            this.extensao = extensao;
            this.contentType = contentType;
        }

        static Formato de(String texto) {
            if (texto != null) {
                for (Formato f : values()) {
                    if (f.extensao.equalsIgnoreCase(texto.trim())) return f;
                }
            }
            throw new NegocioException("Formato inválido. Use xlsx, pdf ou csv.");
        }
    }
}
