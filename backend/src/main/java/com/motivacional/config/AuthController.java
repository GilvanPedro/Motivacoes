package com.motivacional.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminTokens tokens;

    public AuthController(AdminTokens tokens) {
        this.tokens = tokens;
    }

    public record LoginRequest(String password) {
    }

    @PostMapping("/login")
    public AdminTokens.Issued login(@RequestBody LoginRequest req) {
        if (!tokens.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_PASSWORD não está configurada no servidor.");
        }
        if (!tokens.passwordMatches(req.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta.");
        }
        return tokens.issue();
    }

    /** Só confirma que a sessão ainda vale: o AdminInterceptor já barrou se não valer. */
    @PostMapping("/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void check() {
    }
}
