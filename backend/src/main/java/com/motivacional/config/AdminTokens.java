package com.motivacional.config;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Sessão de administrador sem banco: o token é "expiraEm.assinatura", assinado com HMAC usando a
 * ADMIN_PASSWORD como chave. Trocar a senha no servidor invalida todas as sessões.
 */
@Component
public class AdminTokens {

    public static final Duration VALIDITY = Duration.ofDays(182);

    public record Issued(String token, Instant expiresAt) {
    }

    private final byte[] password;

    public AdminTokens(AppProperties props) {
        String pw = props.adminPassword();
        this.password = pw == null || pw.isBlank() ? null : pw.getBytes(StandardCharsets.UTF_8);
    }

    public boolean configured() {
        return password != null;
    }

    public boolean passwordMatches(String given) {
        return password != null && given != null
                && MessageDigest.isEqual(password, given.getBytes(StandardCharsets.UTF_8));
    }

    public Issued issue() {
        Instant expiresAt = Instant.now().plus(VALIDITY);
        long exp = expiresAt.getEpochSecond();
        return new Issued(exp + "." + sign(exp), Instant.ofEpochSecond(exp));
    }

    public boolean verify(String token) {
        return verify(token, Instant.now());
    }

    boolean verify(String token, Instant now) {
        if (password == null || token == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long exp;
        try {
            exp = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (now.getEpochSecond() >= exp) {
            return false;
        }
        byte[] expected = sign(exp).getBytes(StandardCharsets.US_ASCII);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }

    private String sign(long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password, "HmacSHA256"));
            byte[] sig = mac.doFinal(("admin:" + exp).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
