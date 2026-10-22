package com.motivacional.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class AdminTokensTest {

    private static AdminTokens tokens(String password) {
        return new AdminTokens(new AppProperties(password, List.of(), null));
    }

    @Test
    void validForSixMonthsThenExpires() {
        AdminTokens t = tokens("segredo");
        AdminTokens.Issued issued = t.issue();
        assertTrue(t.verify(issued.token()));
        assertTrue(t.verify(issued.token(), Instant.now().plus(Duration.ofDays(181))));
        assertFalse(t.verify(issued.token(), Instant.now().plus(Duration.ofDays(183))));
    }

    @Test
    void rejectsTamperedOrForeignTokens() {
        AdminTokens t = tokens("segredo");
        String token = t.issue().token();
        long farFuture = Instant.now().plus(Duration.ofDays(3650)).getEpochSecond();
        String extended = farFuture + token.substring(token.indexOf('.'));
        assertFalse(t.verify(extended));
        assertFalse(tokens("outra-senha").verify(token));
        assertFalse(t.verify("lixo"));
        assertFalse(t.verify(null));
    }

    @Test
    void checksPassword() {
        AdminTokens t = tokens("segredo");
        assertTrue(t.passwordMatches("segredo"));
        assertFalse(t.passwordMatches("errada"));
        assertFalse(tokens("").passwordMatches(""));
    }
}
