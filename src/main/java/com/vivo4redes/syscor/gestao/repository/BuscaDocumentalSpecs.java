package com.vivo4redes.syscor.gestao.repository;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.enums.TipoPlano;
import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.ItemVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Busca do documental no banco. Os filtros de item e de protocolo usam EXISTS, para a mesma venda não aparecer
 * repetida. Ordem: lançamento mais recente primeiro.
 */
public final class BuscaDocumentalSpecs {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    // translate(): tira os acentos no banco para comparar serviço e segmento como TextoUtil.normalizar
    private static final String COM_ACENTO = "áàâãäéèêëíìîïóòôõöúùûüç";
    private static final String SEM_ACENTO = "aaaaaeeeeiiiiooooouuuuc";

    private BuscaDocumentalSpecs() {
    }

    public static Specification<Venda> de(FiltroDocumentalDTO f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            ps.add(root.get("status").in(f.incluirCanceladas()
                    ? List.of(StatusVenda.CONCLUIDA, StatusVenda.CANCELADA)
                    : List.of(StatusVenda.CONCLUIDA)));

            Expression<Instant> lancamento = cb.coalesce(root.<Instant>get("finalizadoEm"), root.<Instant>get("criadoEm"));
            if (f.dataInicio() != null) ps.add(cb.greaterThanOrEqualTo(lancamento, inicioDoDia(f.dataInicio())));
            if (f.dataFim() != null) ps.add(cb.lessThan(lancamento, inicioDoDia(f.dataFim().plusDays(1))));
            if (temItens(f.filialIds())) ps.add(root.get("filial").get("id").in(f.filialIds()));
            if (f.vendedorId() != null) ps.add(cb.equal(root.get("usuario").get("id"), f.vendedorId()));
            if (f.numeroVenda() != null) ps.add(cb.equal(root.get("numeroVenda"), f.numeroVenda()));
            if (f.statusBko() == StatusAvaliacaoProcedencia.Nao_avaliado) {
                ps.add(cb.or(cb.isNull(root.get("avaliacaoProcedencia")),
                        cb.equal(root.get("avaliacaoProcedencia"), StatusAvaliacaoProcedencia.Nao_avaliado)));
            } else if (f.statusBko() != null) {
                ps.add(cb.equal(root.get("avaliacaoProcedencia"), f.statusBko()));
            }

            // Pelo menos um item de serviço (com número de acesso e serviço) que atenda aos filtros de item
            Subquery<Long> sq = query.subquery(Long.class);
            Root<ItemVenda> i = sq.from(ItemVenda.class);
            List<Predicate> pi = new ArrayList<>();
            pi.add(cb.equal(i.get("venda"), root));
            pi.add(preenchido(cb, i.get("numeroAcesso")));
            pi.add(preenchido(cb, i.get("tipoServico")));
            String digitos = f.numeroAcesso() == null ? "" : f.numeroAcesso().replaceAll("\\D", "");
            if (!digitos.isEmpty()) {
                pi.add(cb.equal(cb.function("regexp_replace", String.class, i.get("numeroAcesso"),
                        cb.literal("[^0-9]"), cb.literal(""), cb.literal("g")), digitos));
            }
            if (temItens(f.servicos())) {
                pi.add(normalizado(cb, i.get("tipoServico")).in(f.servicos().stream().map(TextoUtil::normalizar).toList()));
            }
            if (temItens(f.tiposPlano())) {
                pi.add(cb.or(f.tiposPlano().stream().map(t -> doTipo(cb, i, t)).toArray(Predicate[]::new)));
            }
            sq.select(i.get("id")).where(pi.toArray(Predicate[]::new));
            ps.add(cb.exists(sq));

            boolean temGed = f.numeroProtocoloGed() != null && !f.numeroProtocoloGed().isBlank();
            boolean temLider = f.liderEquipe() != null && !f.liderEquipe().isBlank();
            if (temGed || temLider) {
                Subquery<Long> sp = query.subquery(Long.class);
                Root<ProtocoloDocumental> p = sp.from(ProtocoloDocumental.class);
                List<Predicate> pp = new ArrayList<>();
                pp.add(cb.equal(p.get("venda"), root));
                if (temGed) {
                    pp.add(cb.equal(cb.upper(cb.trim(p.get("numeroProtocoloGed"))), f.numeroProtocoloGed().trim().toUpperCase()));
                }
                if (temLider) {
                    pp.add(cb.like(normalizado(cb, p.get("liderEquipe")), "%" + TextoUtil.normalizar(f.liderEquipe()) + "%"));
                }
                sp.select(p.get("id")).where(pp.toArray(Predicate[]::new));
                ps.add(cb.exists(sp));
            }

            // A consulta de contagem da página não aceita ORDER BY
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(lancamento), cb.desc(root.get("id")));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    /** Pelo grupo de preço; sem grupo (ou SEM_PLANO), pelo segmento ("Pré", "Controle", "Pós"). */
    private static Predicate doTipo(CriteriaBuilder cb, Root<ItemVenda> i, TipoPlano tipo) {
        Expression<GrupoPlano> grupo = i.get("grupoPlano");
        return cb.or(
                grupo.in(tipo.grupos()),
                cb.and(cb.or(cb.isNull(grupo), cb.equal(grupo, GrupoPlano.SEM_PLANO)),
                        cb.like(normalizado(cb, i.get("segmento")), tipo.prefixoSegmento() + "%")));
    }

    /** Minúsculo, sem acento e sem espaços nas pontas. */
    private static Expression<String> normalizado(CriteriaBuilder cb, Expression<String> texto) {
        return cb.function("translate", String.class, cb.lower(cb.trim(texto)), cb.literal(COM_ACENTO), cb.literal(SEM_ACENTO));
    }

    private static Predicate preenchido(CriteriaBuilder cb, Expression<String> texto) {
        return cb.and(cb.isNotNull(texto), cb.notEqual(cb.trim(texto), ""));
    }

    private static Instant inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(ZONA).toInstant();
    }

    private static boolean temItens(List<?> lista) {
        return lista != null && !lista.isEmpty();
    }
}
