package com.motivacional.video;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class YouTube {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/|v/))([A-Za-z0-9_-]{11})");
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper json;

    public YouTube(ObjectMapper json) {
        this.json = json;
    }

    /** Aceita links watch, youtu.be, shorts, embed, live ou o próprio ID de 11 caracteres. */
    public static Optional<String> extractId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = input.trim();
        if (ID_PATTERN.matcher(s).matches()) {
            return Optional.of(s);
        }
        Matcher m = URL_PATTERN.matcher(s);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Busca o título público do vídeo (oEmbed, sem chave de API). Retorna vazio se falhar. */
    public Optional<String> fetchTitle(String videoId) {
        try {
            String watch = "https://www.youtube.com/watch?v=" + videoId;
            URI uri = URI.create("https://www.youtube.com/oembed?format=json&url="
                    + URLEncoder.encode(watch, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode title = json.readTree(res.body()).get("title");
            return title == null || title.asText().isBlank() ? Optional.empty() : Optional.of(title.asText());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }
}
