package com.motivacional.video;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.motivacional.tag.Tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VideoSource source;

    @Column(name = "youtube_id", length = 20)
    private String youtubeId;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(
            name = "video_tags",
            joinColumns = @JoinColumn(name = "video_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    protected Video() {
    }

    public static Video youtube(String title, String youtubeId) {
        Video v = new Video();
        v.source = VideoSource.YOUTUBE;
        v.title = title;
        v.youtubeId = youtubeId;
        return v;
    }

    public static Video upload(String title, String storageKey) {
        Video v = new Video();
        v.source = VideoSource.UPLOAD;
        v.title = title;
        v.storageKey = storageKey;
        return v;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public VideoSource getSource() {
        return source;
    }

    public String getYoutubeId() {
        return youtubeId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }
}
