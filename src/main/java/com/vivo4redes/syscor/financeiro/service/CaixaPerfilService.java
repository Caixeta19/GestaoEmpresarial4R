package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.CaixaException;
import com.vivo4redes.syscor.financeiro.config.CaixaProperties;
import com.vivo4redes.syscor.security.UsuarioDetailsService;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matriz de perfis do caixa, num lugar só (quando o cadastro ganhar a coluna padronizada de cargo/setor, só este
 * serviço muda):
 * <ul>
 *   <li>abrir e fechar: gerente de vendas da própria filial, auditoria de caixa (qualquer filial) e admin;</li>
 *   <li>conferir e reabrir: auditoria de caixa, financeiro e admin;</li>
 *   <li>consultar: quem abre/fecha ou confere, cada gerente só na própria filial.</li>
 * </ul>
 */
@Service
public class CaixaPerfilService {

    private final CaixaProperties props;
    private final FilialRepository filialRepository;

    public CaixaPerfilService(CaixaProperties props, FilialRepository filialRepository) {
        this.props = props;
        this.filialRepository = filialRepository;
    }

    // ------------------------------------------------------------------ perfis
    public boolean ehAdmin(Usuario u) {
        return u != null && u.getLogin() != null
                && props.getAdmins().stream().anyMatch(a -> a.trim().equalsIgnoreCase(u.getLogin().trim()));
    }

    public boolean ehAuditoria(Usuario u) {
        return cargoEm(u, props.getCargosAuditoria());
    }

    public boolean ehFinanceiro(Usuario u) {
        return cargoEm(u, props.getCargosFinanceiro());
    }

    public boolean ehGerente(Usuario u) {
        return cargoEm(u, props.getCargosGerente());
    }

    /** Vê e age em todas as filiais: admin, auditoria e financeiro (o financeiro só consulta, confere e reabre). */
    public boolean veTodasAsFiliais(Usuario u) {
        return ehAdmin(u) || ehAuditoria(u) || ehFinanceiro(u);
    }

    // ------------------------------------------------------------------ regras
    public boolean podeAbrirFechar(Usuario u, Filial filial) {
        return ehAdmin(u) || ehAuditoria(u) || (ehGerente(u) && daFilial(u, filial));
    }

    public boolean podeConferirReabrir(Usuario u) {
        return ehAdmin(u) || ehAuditoria(u) || ehFinanceiro(u);
    }

    public boolean podeConsultar(Usuario u, Filial filial) {
        return veTodasAsFiliais(u) || (ehGerente(u) && daFilial(u, filial));
    }

    public void exigirAbrirFechar(Usuario u, Filial filial) {
        if (!podeAbrirFechar(u, filial)) {
            throw CaixaException.semPermissao(ehGerente(u)
                    ? "O gerente só abre e fecha o caixa da própria filial."
                    : "Seu perfil não pode abrir nem fechar o caixa.");
        }
    }

    public void exigirConferirReabrir(Usuario u) {
        if (!podeConferirReabrir(u)) {
            throw CaixaException.semPermissao("Só a auditoria de caixa e o financeiro podem conferir ou reabrir um caixa.");
        }
    }

    public void exigirConsultar(Usuario u, Filial filial) {
        if (!podeConsultar(u, filial)) {
            throw CaixaException.semPermissao("Você não tem acesso aos caixas dessa filial.");
        }
    }

    /**
     * Filiais que o usuário enxerga nas listas: {@code null} = todas; vazio = nenhuma (cargo sem acesso ou gerente
     * cuja filial do cadastro não casa com nenhuma filial).
     */
    public Set<Long> filiaisVisiveis(Usuario u) {
        if (veTodasAsFiliais(u)) return null;
        if (!ehGerente(u) || u.getFilial() == null) return Set.of();
        return Set.copyOf(filialRepository.listarIdsPorNome(u.getFilial()));
    }

    /** Filial do cadastro do usuário, se o nome casa com exatamente uma filial; senão, null. */
    public Long filialDoUsuario(Usuario u) {
        if (u == null || u.getFilial() == null || u.getFilial().isBlank()) return null;
        List<Long> ids = filialRepository.listarIdsPorNome(u.getFilial());
        return ids.size() == 1 ? ids.get(0) : null;
    }

    // ------------------------------------------------------------------ apoio
    private boolean daFilial(Usuario u, Filial filial) {
        return u.getFilial() != null && filial != null && normalizar(u.getFilial()).equals(normalizar(filial.getNome()));
    }

    private static boolean cargoEm(Usuario u, List<String> cargos) {
        if (u == null || u.getCargo() == null || cargos.isEmpty()) return false;
        String papel = UsuarioDetailsService.papel(u.getCargo());
        Set<String> papeis = cargos.stream().map(UsuarioDetailsService::papel).collect(Collectors.toSet());
        return papeis.contains(papel);
    }

    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
