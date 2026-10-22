package com.motivacional.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Leitura é pública; qualquer alteração (POST/PUT/DELETE) exige "Authorization: Bearer <token>",
 * obtido em POST /api/auth/login.
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    public static final String LOGIN_PATH = "/api/auth/login";

    private final AdminTokens tokens;

    public AdminInterceptor(AdminTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                || LOGIN_PATH.equals(request.getRequestURI())) {
            return true;
        }
        if (!tokens.configured()) {
            return reject(response, 503, "ADMIN_PASSWORD não está configurada no servidor.");
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        if (!tokens.verify(token)) {
            return reject(response, 401, "Sessão expirada. Entre novamente.");
        }
        return true;
    }

    private static boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
