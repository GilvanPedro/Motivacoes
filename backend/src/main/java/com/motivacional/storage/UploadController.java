package com.motivacional.storage;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UploadController {

    private final StorageService storage;

    public UploadController(StorageService storage) {
        this.storage = storage;
    }

    public record PresignRequest(String fileName, String contentType) {
    }

    public record PresignResponse(String uploadUrl, String key) {
    }

    @GetMapping("/api/config")
    public Map<String, Object> config() {
        return Map.of("uploadsEnabled", storage.enabled());
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/uploads/presign")
    public PresignResponse presign(@RequestBody PresignRequest req) {
        if (!storage.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envio de arquivos não está configurado.");
        }
        String contentType = req.contentType() == null ? "" : req.contentType().trim().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo precisa ser um vídeo.");
        }
        String key = StorageService.PREFIX + UUID.randomUUID() + extension(req.fileName());
        return new PresignResponse(storage.presignUpload(key, contentType), key);
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,5}") ? "." + ext : "";
    }
}
