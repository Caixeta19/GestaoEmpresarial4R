package com.vivo4redes.syscor.venda.controller;

import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.dto.response.UsuarioResponseDTO;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login da tela inicial: valida e-mail/senha e devolve os dados do usuário.
 * (Ainda sem token: a emissão de JWT é o próximo passo.)
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@Valid @RequestBody AutenticacaoUsuarioDTO dto) {
        return ResponseEntity.ok(UsuarioResponseDTO.from(usuarioService.autenticar(dto)));
    }
}