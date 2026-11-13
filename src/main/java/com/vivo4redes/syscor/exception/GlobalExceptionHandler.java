package com.vivo4redes.syscor.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centraliza a tradução de exceções de domínio/validação em respostas HTTP
 * padronizadas. Mantém os controllers limpos (sem try/catch espalhado).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 422: regras de negócio
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(corpo(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()));
    }

    // Caixa: 409 (não aberto, já aberto, fechado) ou 403 (sem perfil), sempre com o código para o front
    @ExceptionHandler(CaixaException.class)
    public ResponseEntity<Map<String, Object>> handleCaixa(CaixaException ex) {
        Map<String, Object> corpo = corpo(ex.getStatus(), ex.getMessage());
        corpo.put("codigo", ex.getCodigo());
        return ResponseEntity.status(ex.getStatus()).body(corpo);
    }

    // 422: troca de condição com pagamentos incompatíveis; o corpo lista quais remover
    @ExceptionHandler(PagamentosIncompativeisException.class)
    public ResponseEntity<Map<String, Object>> handlePagamentosIncompativeis(PagamentosIncompativeisException ex) {
        Map<String, Object> corpo = corpo(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        corpo.put("pagamentosIncompativeis", ex.getPagamentos());
        return ResponseEntity.unprocessableEntity().body(corpo);
    }

    // 409: número do voucher já usado em outra venda
    @ExceptionHandler(VoucherJaUsadoException.class)
    public ResponseEntity<Map<String, Object>> handleVoucherJaUsado(VoucherJaUsadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(corpo(HttpStatus.CONFLICT, ex.getMessage()));
    }

    // 409: IMEI/Serial já reservado no carrinho de outra venda
    @ExceptionHandler(SerialReservadoException.class)
    public ResponseEntity<Map<String, Object>> handleSerialReservado(SerialReservadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(corpo(HttpStatus.CONFLICT, ex.getMessage()));
    }

    // 409: a filial já tem inventário aberto
    @ExceptionHandler(InventarioAbertoException.class)
    public ResponseEntity<Map<String, Object>> handleInventarioAberto(InventarioAbertoException ex) {
        Map<String, Object> corpo = corpo(HttpStatus.CONFLICT, ex.getMessage());
        corpo.put("execucaoId", ex.getExecucaoId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo);
    }

    // 502: a API do Claude falhou no diagnóstico do inventário
    @ExceptionHandler(DiagnosticoIaException.class)
    public ResponseEntity<Map<String, Object>> handleDiagnosticoIa(DiagnosticoIaException ex) {
        log.warn("Diagnóstico da IA falhou: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(corpo(HttpStatus.BAD_GATEWAY, ex.getMessage()));
    }

    // 401: credenciais inválidas (login / reautenticação do vendedor)
    @ExceptionHandler(AutenticacaoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleAutenticacao(AutenticacaoInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(corpo(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    // 401: login recusado. A mesma mensagem não revela se o usuário existe, se está inativo ou se a senha errou.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleLoginRecusado(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(corpo(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos."));
    }

    // 404
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(corpo(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    // 400: CPF/CNPJ inválido
    @ExceptionHandler(DocumentoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentoInvalido(DocumentoInvalidoException ex) {
        return ResponseEntity.badRequest()
                .body(corpo(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    // 400: @Valid no body
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            campos.put(erro.getField(), erro.getDefaultMessage());
        }
        Map<String, Object> corpo = corpo(HttpStatus.BAD_REQUEST, "Erro de validação nos campos enviados.");
        corpo.put("campos", campos);
        return ResponseEntity.badRequest().body(corpo);
    }

    // 400: validações em parâmetros
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(corpo(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    // 400: JSON malformado ou com tipo/valor de enum errado
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(corpo(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou malformado."));
    }

    // 400: parâmetro de URL com tipo errado (ex.: {{variavel}} não resolvida no Postman)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoArgumento(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(corpo(HttpStatus.BAD_REQUEST,
                "Parâmetro '" + ex.getName() + "' com valor inválido: " + ex.getValue()));
    }

    // Fallback: erros do Spring MVC mantêm o status; o resto vira 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenerico(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return ResponseEntity.status(status).body(corpo(status, ex.getMessage()));
        }
        log.error("Erro inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(corpo(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor."));
    }

    private Map<String, Object> corpo(HttpStatus status, String mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", Instant.now().toString());
        corpo.put("status", status.value());
        corpo.put("erro", status.getReasonPhrase());
        corpo.put("mensagem", mensagem);
        return corpo;
    }
}