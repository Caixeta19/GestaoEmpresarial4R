    package com.vivo4redes.syscor.financeiro.service;

    import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
    import com.vivo4redes.syscor.financeiro.dto.LinhaConsolidadoVivoDTO;
    import com.vivo4redes.syscor.financeiro.dto.response.ExecucaoRemuneracaoResponseDTO;
    import com.vivo4redes.syscor.gestao.enums.SituacaoServico;
    import com.vivo4redes.syscor.gestao.model.ProtocoloDocumental;
    import com.vivo4redes.syscor.gestao.repository.ProtocoloDocumentalRepository;
    import com.vivo4redes.syscor.financeiro.dto.response.ItemRemuneracaoResponseDTO;
    import com.vivo4redes.syscor.financeiro.enums.StatusRemuneracao;
    import com.vivo4redes.syscor.financeiro.model.ExecucaoRemuneracao;
    import com.vivo4redes.syscor.financeiro.model.ItemRemuneracao;
    import com.vivo4redes.syscor.financeiro.repository.ExecucaoRemuneracaoRepository;
    import com.vivo4redes.syscor.financeiro.repository.ItemRemuneracaoRepository;
    import com.vivo4redes.syscor.venda.model.ItemVenda;
    import com.vivo4redes.syscor.venda.model.Venda;
    import com.vivo4redes.syscor.venda.service.VendaService;
    import lombok.RequiredArgsConstructor;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.web.multipart.MultipartFile;

    import java.util.*;

    import static com.vivo4redes.syscor.financeiro.enums.StatusRemuneracao.*;

    @Service
    @RequiredArgsConstructor
    public class RemuneracaoVariavelService {

        private final ConsolidadoVivoParser parser;
        private final VendaService vendaService;
        private final ProtocoloDocumentalRepository protocoloDocumentalRepository;
        private final ExecucaoRemuneracaoRepository execucaoRepository;
        private final ItemRemuneracaoRepository itemRepository;

        @Transactional
        public ExecucaoRemuneracaoResponseDTO processarConsolidado(MultipartFile arquivo) {
            List<LinhaConsolidadoVivoDTO> linhasVivo = parser.parsear(arquivo);

            List<Venda> todasAsVendas = vendaService.listarTodas();
            Map<String, Venda> vendaPorAcesso = new HashMap<>();
            for (Venda v : todasAsVendas) {
                for (ItemVenda item : v.getItens()) {
                    String acessoNormalizado = normalizarAcesso(item.getNumeroAcesso());
                    if (!acessoNormalizado.isBlank()) {
                        vendaPorAcesso.putIfAbsent(acessoNormalizado, v);
                    }
                }
            }

            Set<String> acessosNoConsolidado = new HashSet<>();
            List<ItemRemuneracao> itens = new ArrayList<>();
            int[] contadores = new int[3];

            for (LinhaConsolidadoVivoDTO linha : linhasVivo) {
                String acessoNormalizado = normalizarAcesso(linha.acesso());
                if (acessoNormalizado.isBlank()) continue;
                acessosNoConsolidado.add(acessoNormalizado);

                itens.add(classificarLinhaVivo(linha, vendaPorAcesso.get(acessoNormalizado), contadores));
            }

            for (Venda v : todasAsVendas) {
                if (!temProtocoloValido(v.getId())) continue;

                for (ItemVenda item : v.getItens()) {
                    String acessoNormalizado = normalizarAcesso(item.getNumeroAcesso());
                    if (acessoNormalizado.isBlank() || acessosNoConsolidado.contains(acessoNormalizado)) continue;

                    contadores[2]++;
                    itens.add(ItemRemuneracao.builder()
                            .idTransacaoVivo(null)
                            .numeroAcessoVivo(item.getNumeroAcesso())
                            .clienteVivo(v.getCliente().getNome())
                            .cpfVivo(v.getCliente().getCpfCnpj())
                            .planoVivo(item.getPlano())
                            .valorComissao(null)
                            .dataVendaVivo(null)
                            .status(PENDENTE_VIVO)
                            .diagnostico("Tem protocolo/GED valido, mas nao apareceu no consolidado da Vivo enviado - repasse pendente.")
                            .vendaId(v.getId())
                            .build());
                    acessosNoConsolidado.add(acessoNormalizado);
                }
            }

            ExecucaoRemuneracao execucao = ExecucaoRemuneracao.builder()
                    .nomeArquivo(arquivo.getOriginalFilename())
                    .totalLinhas(linhasVivo.size())
                    .totalLiberados(contadores[0])
                    .totalGlosados(contadores[1])
                    .totalPendentesVivo(contadores[2])
                    .build();
            execucao = execucaoRepository.save(execucao);

            for (ItemRemuneracao item : itens) {
                item.setExecucao(execucao);
            }
            itens = itemRepository.saveAll(itens);

            return ExecucaoRemuneracaoResponseDTO.from(
                    execucao, itens.stream().map(ItemRemuneracaoResponseDTO::from).toList());
        }

        @Transactional(readOnly = true)
        public ExecucaoRemuneracaoResponseDTO buscarExecucao(Long execucaoId) {
            ExecucaoRemuneracao execucao = execucaoRepository.findById(execucaoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Execucao de remuneracao"));

            List<ItemRemuneracaoResponseDTO> itens = itemRepository
                    .findByExecucaoIdOrderByStatusAscClienteVivoAsc(execucaoId).stream()
                    .map(ItemRemuneracaoResponseDTO::from)
                    .toList();

            return ExecucaoRemuneracaoResponseDTO.from(execucao, itens);
        }

        private ItemRemuneracao classificarLinhaVivo(LinhaConsolidadoVivoDTO linha, Venda vendaEncontrada, int[] contadores) {
            StatusRemuneracao status;
            String diagnostico;
            Long vendaId = null;

            if (vendaEncontrada == null) {
                status = PENDENTE_SISTEMA;
                diagnostico = "Numero de acesso nao encontrado em nenhuma venda do sistema.";
                contadores[1]++;
            } else {
                vendaId = vendaEncontrada.getId();
                if (temProtocoloValido(vendaId)) {
                    status = LIBERADO;
                    diagnostico = "Venda localizada, com protocolo/GED valido.";
                    contadores[0]++;
                } else {
                    status = PENDENTE_SISTEMA;
                    diagnostico = "Venda localizada, mas sem protocolo/GED valido (numero de protocolo nao preenchido ou servico cancelado).";
                    contadores[1]++;
                }
            }

            return ItemRemuneracao.builder()
                    .idTransacaoVivo(linha.idTransacao())
                    .numeroAcessoVivo(linha.acesso())
                    .clienteVivo(linha.cliente())
                    .cpfVivo(linha.cpf())
                    .planoVivo(linha.plano())
                    .valorComissao(linha.valorComissao())
                    .dataVendaVivo(linha.dataVenda())
                    .status(status)
                    .diagnostico(diagnostico)
                    .vendaId(vendaId)
                    .build();
        }

        private boolean temProtocoloValido(Long vendaId) {
            return protocoloDocumentalRepository.findByVendaId(vendaId)
                    .map(this::protocoloEhValido)
                    .orElse(false);
        }

        private boolean protocoloEhValido(ProtocoloDocumental p) {
            return p.getNumeroProtocoloGed() != null
                    && !p.getNumeroProtocoloGed().isBlank()
                    && p.getSituacaoServico() == SituacaoServico.CONFIRMADO;
        }

        private String normalizarAcesso(String acesso) {
            return acesso == null ? "" : acesso.replaceAll("\\D", "");
        }
    }