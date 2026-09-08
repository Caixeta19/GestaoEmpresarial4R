package com.vivo4redes.syscor.dashboard.service;

import com.vivo4redes.syscor.dashboard.dto.AtividadeRecenteDTO;
import com.vivo4redes.syscor.dashboard.dto.DashboardExecutivoDTO;
import com.vivo4redes.syscor.dashboard.dto.EvolucaoMensalDTO;
import com.vivo4redes.syscor.estoque.enums.StatusSerial;
import com.vivo4redes.syscor.estoque.model.Produto;
import com.vivo4redes.syscor.estoque.repository.ItemEstoqueRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoRepository;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final ItemEstoqueRepository itemEstoqueRepository;

    @Transactional(readOnly = true)
    public DashboardExecutivoDTO obterPainelExecutivo(Long filialId) {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = hoje.atTime(LocalTime.MAX);

        // 1. Faturamento e Volume
        BigDecimal faturamento = vendaRepository.calcularFaturamentoPeriodo(StatusVenda.CONCLUIDA, inicioMes, fimMes, filialId);
        Long volumeTransacoes = vendaRepository.contarTransacoesPeriodo(StatusVenda.CONCLUIDA, inicioMes, fimMes, filialId);

        // 2. Meta da Operação (Parametrizada ou padrão de R$ 85.000,00)
        BigDecimal metaOperacao = new BigDecimal("85000.00");
        double atingimento = (metaOperacao.compareTo(BigDecimal.ZERO) > 0 && faturamento != null)
                ? faturamento.divide(metaOperacao, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

        // 3. Ticket Médio
        BigDecimal ticketMedio = (volumeTransacoes != null && volumeTransacoes > 0 && faturamento != null)
                ? faturamento.divide(BigDecimal.valueOf(volumeTransacoes), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 4. SKUs Críticos ou em Ruptura
        List<Produto> produtos = produtoRepository.findAll();
        long skusCriticos = produtos.stream().filter(p -> {
            long saldo = itemEstoqueRepository.countByProdutoIdAndStatus(p.getId(), StatusSerial.DISPONIVEL);
            return saldo <= p.getEstoqueMinimo();
        }).count();

        // 5. Histórico 6 Meses
        List<EvolucaoMensalDTO> evolucao = new ArrayList<>();
        DateTimeFormatter fmtMes = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("pt-BR"));

        for (int i = 5; i >= 0; i--) {
            LocalDate baseMes = hoje.minusMonths(i);
            LocalDateTime ini = baseMes.withDayOfMonth(1).atStartOfDay();
            LocalDateTime fim = baseMes.withDayOfMonth(baseMes.lengthOfMonth()).atTime(LocalTime.MAX);

            BigDecimal fatMes = vendaRepository.calcularFaturamentoPeriodo(StatusVenda.CONCLUIDA, ini, fim, filialId);
            long valorK = fatMes.divide(new BigDecimal("1000"), 0, RoundingMode.HALF_UP).longValue();

            String mesTexto = baseMes.format(fmtMes);
            String rotuloCapitalizado = mesTexto.substring(0, 1).toUpperCase() + mesTexto.substring(1);

            evolucao.add(new EvolucaoMensalDTO(
                    rotuloCapitalizado,
                    fatMes,
                    valorK + "k"
            ));
        }

        // 6. Atividades Recentes (Limitado a 5 registros com PageRequest)
        List<AtividadeRecenteDTO> atividades = vendaRepository
                .buscarUltimasAtividades(filialId, PageRequest.of(0, 5))
                .stream()
                .map(v -> new AtividadeRecenteDTO(
                        v.getNumeroVenda() != null ? String.valueOf(v.getNumeroVenda()) : "#" + v.getId(),
                        v.getStatus() != null ? v.getStatus().name().toLowerCase() : "aberta",
                        v.getCliente() != null ? v.getCliente().getNome() : "Consumidor",
                        v.getUsuario() != null ? (v.getUsuario().getNome() != null ? v.getUsuario().getNome() : v.getUsuario().getLogin()) : "Operador",
                        v.getValorTotal() != null ? v.getValorTotal() : BigDecimal.ZERO,
                        v.getCriadoEm()
                )).toList();

        return new DashboardExecutivoDTO(
                faturamento != null ? faturamento : BigDecimal.ZERO,
                metaOperacao,
                atingimento,
                volumeTransacoes != null ? volumeTransacoes : 0L,
                ticketMedio,
                skusCriticos,
                evolucao,
                atividades
        );
    }
}