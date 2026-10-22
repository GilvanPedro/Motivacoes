package com.motivacional.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.motivacional.config.AppProperties;

import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Guarda os vídeos enviados num bucket S3-compatível (ex.: Cloudflare R2).
 * O navegador envia o arquivo direto pro bucket usando uma URL assinada,
 * então o backend nunca precisa receber o vídeo inteiro.
 */
@Service
public class StorageService {

    public static final String PREFIX = "videos/";

    private final AppProperties.Storage cfg;
    private final S3Client s3;
    private final S3Presigner presigner;

    public StorageService(AppProperties props) {
        this.cfg = props.storage();
        if (cfg == null || !cfg.enabled()) {
            this.s3 = null;
            this.presigner = null;
            return;
        }
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey()));
        var region = Region.of(cfg.region() == null || cfg.region().isBlank() ? "auto" : cfg.region());
        var endpoint = URI.create(cfg.endpoint());
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.s3 = S3Client.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
    }

    public boolean enabled() {
        return s3 != null;
    }

    public String presignUpload(String key, String contentType) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(cfg.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest req = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(30))
                .putObjectRequest(put)
                .build();
        return presigner.presignPutObject(req).url().toString();
    }

    public String publicUrl(String key) {
        if (cfg == null || cfg.publicUrl() == null || cfg.publicUrl().isBlank()) {
            return null;
        }
        return cfg.publicUrl().replaceAll("/+$", "") + "/" + key;
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(cfg.bucket()).key(key).build());
    }

    @PreDestroy
    void close() {
        if (s3 != null) {
            s3.close();
            presigner.close();
        }
    }
}
