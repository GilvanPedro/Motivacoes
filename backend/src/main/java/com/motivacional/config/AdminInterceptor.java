package com.motivacional.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Leitura é pública; qualquer alteração (POST/PUT/DELETE) exige o header X-Admin-Password.
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Password";

    private final byte[] expected;

    public AdminInterceptor(AppProperties props) {
        String pw = props.adminPassword();
        this.expected = pw == null || pw.isBlank() ? null : pw.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        if (expected == null) {
            return reject(response, 503, "ADMIN_PASSWORD não está configurada no servidor.");
        }
        String given = request.getHeader(HEADER);
        if (given == null || !MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
            return reject(response, 401, "Senha de administrador inválida.");
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
