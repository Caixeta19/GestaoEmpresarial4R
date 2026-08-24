package com.vivo4redes.syscor.auth;

public record LoginResponse(String token, String tipo) {

    public LoginResponse(String token) {
        this(token, "Bearer");
    }
}
