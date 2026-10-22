package com.motivacional.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String adminPassword, List<String> corsOrigins, Storage storage) {

    public record Storage(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            String publicUrl) {

        public boolean enabled() {
            return notBlank(endpoint) && notBlank(bucket) && notBlank(accessKey)
                    && notBlank(secretKey) && notBlank(publicUrl);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }
}
