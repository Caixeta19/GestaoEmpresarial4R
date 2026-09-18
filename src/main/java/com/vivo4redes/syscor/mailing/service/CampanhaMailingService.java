package com.vivo4redes.syscor.mailing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.mailing.config.MailingRabbitConfig;
import com.vivo4redes.syscor.mailing.dto.EnviarMailingMessage;
import com.vivo4redes.syscor.mailing.dto.request.CriarCampanhaRequestDTO;
import com.vivo4redes.syscor.mailing.dto.request.DestinatarioRequestDTO;
import com.vivo4redes.syscor.mailing.dto.response.CampanhaResponseDTO;
import com.vivo4redes.syscor.mailing.dto.response.DestinatarioResponseDTO;
import com.vivo4redes.syscor.mailing.enums.StatusCampanha;
import com.vivo4redes.syscor.mailing.enums.StatusEnvio;
import com.vivo4redes.syscor.mailing.model.CampanhaMailing;
import com.vivo4redes.syscor.mailing.model.DestinatarioMailing;
import com.vivo4redes.syscor.mailing.repository.CampanhaMailingRepository;
import com.vivo4redes.syscor.mailing.repository.DestinatarioMailingRepository;
import com.vivo4redes.syscor.venda.model.Cliente;
import com.vivo4redes.syscor.venda.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampanhaMailingService {

    private final CampanhaMailingRepository campanhaRepository;
    private final DestinatarioMailingRepository destinatarioRepository;
    private final ClienteRepository clienteRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PlanilhaMailingParser planilhaMailingParser;

    @Transactional
    public CampanhaResponseDTO criar(CriarCampanhaRequestDTO dto) {
        CampanhaMailing campanha = CampanhaMailing.builder()
                .nome(dto.getNome())
                .templateNome(dto.getTemplateNome())
                .templateIdioma(dto.getTemplateIdioma() == null || dto.getTemplateIdioma().isBlank()
                        ? "pt_BR" : dto.getTemplateIdioma())
                .status(StatusCampanha.RASCUNHO)
                .build();
        campanha = campanhaRepository.save(campanha);

        List<DestinatarioMailing> destinatarios = new ArrayList<>();
        for (DestinatarioRequestDTO d : dto.getDestinatarios()) {
            destinatarios.add(resolverDestinatario(campanha, d));
        }
        destinatarioRepository.saveAll(destinatarios);

        campanha.setTotalDestinatarios(destinatarios.size());
        campanha = campanhaRepository.save(campanha);

        return CampanhaResponseDTO.resumo(campanha);
    }

    @Transactional
    public CampanhaResponseDTO criarAPartirDePlanilha(String nome, String templateNome, String templateIdioma,
                                                      MultipartFile planilha) {
        List<DestinatarioRequestDTO> destinatarios = planilhaMailingParser.parsear(planilha);

        CriarCampanhaRequestDTO dto = CriarCampanhaRequestDTO.builder()
                .nome(nome)
                .templateNome(templateNome)
                .templateIdioma(templateIdioma)
                .destinatarios(destinatarios)
                .build();

        return criar(dto);
    }

    @Transactional
    public CampanhaResponseDTO disparar(Long campanhaId) {
        CampanhaMailing campanha = buscarEntidade(campanhaId);

        if (campanha.getStatus() != StatusCampanha.RASCUNHO && campanha.getStatus() != StatusCampanha.FALHOU) {
            throw new NegocioException("Campanha já está em '" + campanha.getStatus() + "' — só é possível disparar campanhas em RASCUNHO.");
        }

        List<DestinatarioMailing> pendentes = destinatarioRepository.findByCampanhaIdAndStatus(campanhaId, StatusEnvio.PENDENTE);
        if (pendentes.isEmpty()) {
            throw new NegocioException("Essa campanha não tem destinatários pendentes de envio.");
        }

        campanha.setStatus(StatusCampanha.ENVIANDO);
        campanhaRepository.save(campanha);

        for (DestinatarioMailing destinatario : pendentes) {
            rabbitTemplate.convertAndSend(
                    MailingRabbitConfig.EXCHANGE,
                    MailingRabbitConfig.ROUTING_KEY_ENVIO_WHATSAPP,
                    new EnviarMailingMessage(destinatario.getId()));
        }

        return CampanhaResponseDTO.resumo(campanha);
    }

    @Transactional(readOnly = true)
    public CampanhaResponseDTO buscarComDestinatarios(Long campanhaId) {
        CampanhaMailing campanha = buscarEntidade(campanhaId);
        List<DestinatarioResponseDTO> destinatarios = destinatarioRepository
                .findByCampanhaIdOrderByIdAsc(campanhaId).stream()
                .map(DestinatarioResponseDTO::from)
                .toList();
        return CampanhaResponseDTO.comDestinatarios(campanha, destinatarios);
    }

    @Transactional(readOnly = true)
    public List<CampanhaResponseDTO> listar() {
        return campanhaRepository.findAllByOrderByCriadoEmDesc().stream()
                .map(CampanhaResponseDTO::resumo)
                .toList();
    }

    private CampanhaMailing buscarEntidade(Long id) {
        return campanhaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Campanha de mailing"));
    }

    private DestinatarioMailing resolverDestinatario(CampanhaMailing campanha, DestinatarioRequestDTO d) {
        Long clienteId = d.getClienteId();
        String telefone = d.getTelefone();

        if (clienteId != null) {
            Cliente cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente"));
            telefone = cliente.getTelefone();
        }

        if (telefone == null || telefone.isBlank()) {
            throw new NegocioException("Destinatário sem telefone: informe 'telefone' direto ou um 'clienteId' com telefone cadastrado.");
        }

        return DestinatarioMailing.builder()
                .campanha(campanha)
                .clienteId(clienteId)
                .telefone(normalizarTelefone(telefone))
                .parametrosTemplateJson(serializarParametros(d.getParametros()))
                .status(StatusEnvio.PENDENTE)
                .build();
    }

    private String normalizarTelefone(String telefone) {
        String digitos = telefone.replaceAll("\\D", "");
        if ((digitos.length() == 10 || digitos.length() == 11) && !digitos.startsWith("55")) {
            digitos = "55" + digitos;
        }
        return digitos;
    }

    private String serializarParametros(List<String> parametros) {
        if (parametros == null || parametros.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(parametros);
        } catch (JsonProcessingException e) {
            throw new NegocioException("Não foi possível serializar os parâmetros do template.", e);
        }
    }
}