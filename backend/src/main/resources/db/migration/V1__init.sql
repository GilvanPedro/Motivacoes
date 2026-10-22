CREATE TABLE tags (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX tags_name_lower_idx ON tags (lower(name));

CREATE TABLE videos (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    source      VARCHAR(10)  NOT NULL CHECK (source IN ('YOUTUBE', 'UPLOAD')),
    youtube_id  VARCHAR(20),
    storage_key VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (
        (source = 'YOUTUBE' AND youtube_id IS NOT NULL) OR
        (source = 'UPLOAD' AND storage_key IS NOT NULL)
    )
);

CREATE TABLE video_tags (
    video_id BIGINT NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    tag_id   BIGINT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (video_id, tag_id)
);

CREATE INDEX video_tags_tag_idx ON video_tags (tag_id);
