package com.motivacional.video;

import java.time.Instant;
import java.util.List;

public record VideoDto(
        Long id,
        String title,
        VideoSource source,
        String youtubeId,
        String url,
        List<TagRef> tags,
        Instant createdAt) {

    public record TagRef(Long id, String name) {
    }
}
