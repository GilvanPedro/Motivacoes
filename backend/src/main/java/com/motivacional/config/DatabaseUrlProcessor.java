package com.motivacional.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Converte a DATABASE_URL no formato do Neon (postgresql://usuario:senha@host/banco?sslmode=require)
 * nas propriedades spring.datasource.* que o Spring entende. Assim basta colar a string do Neon.
 */
public class DatabaseUrlProcessor implements EnvironmentPostProcessor, Ordered {

    public record JdbcSettings(String url, String username, String password) {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String raw = env.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }
        JdbcSettings s = parse(raw);
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", s.url());
        if (s.username() != null) {
            props.put("spring.datasource.username", s.username());
        }
        if (s.password() != null) {
            props.put("spring.datasource.password", s.password());
        }
        env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
    }

    public static JdbcSettings parse(String raw) {
        String value = raw.trim();
        // aceita também o que o Neon mostra na aba "psql": psql 'postgresql://...'
        if (value.startsWith("psql ")) {
            value = value.substring(5).trim();
        }
        value = value.replaceAll("^['\"]|['\"]$", "");

        if (value.startsWith("jdbc:")) {
            return new JdbcSettings(value, null, null);
        }

        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalStateException("DATABASE_URL precisa começar com postgresql://");
        }

        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            username = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/postgres" : uri.getRawPath());

        // channel_binding é um parâmetro do libpq que o driver JDBC não usa
        String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                .filter(p -> !p.isBlank() && !p.startsWith("channel_binding="))
                .collect(Collectors.joining("&"));
        if (!query.contains("sslmode=")) {
            query = query.isEmpty() ? "sslmode=require" : query + "&sslmode=require";
        }
        jdbc.append('?').append(query);

        return new JdbcSettings(jdbc.toString(), username, password);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        // roda depois do carregamento do application.properties e do .env
        return Ordered.LOWEST_PRECEDENCE;
    }
}
