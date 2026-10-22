package com.motivacional.video;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.motivacional.storage.StorageService;
import com.motivacional.tag.Tag;
import com.motivacional.tag.TagRepository;

@Service
@Transactional
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoRepository videos;
    private final TagRepository tags;
    private final StorageService storage;
    private final YouTube youtube;

    public VideoService(VideoRepository videos, TagRepository tags, StorageService storage, YouTube youtube) {
        this.videos = videos;
        this.tags = tags;
        this.storage = storage;
        this.youtube = youtube;
    }

    public record CreateVideoRequest(
            VideoSource source,
            String youtubeUrl,
            String storageKey,
            String title,
            List<Long> tagIds) {
    }

    public record UpdateVideoRequest(String title, List<Long> tagIds) {
    }

    @Transactional(readOnly = true)
    public List<VideoDto> list(Long tagId) {
        List<Video> found = tagId == null ? videos.findAllWithTags() : videos.findByTagWithTags(tagId);
        return found.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public VideoDto random(Long tagId, Long excludeId) {
        long exclude = excludeId == null ? -1 : excludeId;
        return videos.findRandomByTag(tagId, exclude)
                // se só existe um vídeo na tag, repete ele mesmo
                .or(() -> videos.findRandomByTag(tagId, -1))
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum vídeo nessa tag ainda."));
    }

    public VideoDto create(CreateVideoRequest req) {
        if (req.source() == null) {
            throw badRequest("Informe a origem do vídeo.");
        }
        Set<Tag> selected = loadTags(req.tagIds());
        String title = clean(req.title());

        Video video;
        if (req.source() == VideoSource.YOUTUBE) {
            String id = YouTube.extractId(req.youtubeUrl())
                    .orElseThrow(() -> badRequest("Link do YouTube inválido."));
            if (title == null) {
                title = youtube.fetchTitle(id).orElse("Vídeo do YouTube");
            }
            video = Video.youtube(truncate(title), id);
        } else {
            if (!storage.enabled()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envio de arquivos não está configurado.");
            }
            String key = req.storageKey();
            if (key == null || !key.startsWith(StorageService.PREFIX) || key.contains("..")) {
                throw badRequest("Arquivo inválido.");
            }
            video = Video.upload(truncate(title == null ? "Vídeo" : title), key);
        }
        video.setTags(selected);
        return toDto(videos.save(video));
    }

    public VideoDto update(Long id, UpdateVideoRequest req) {
        Video video = find(id);
        String title = clean(req.title());
        if (title != null) {
            video.setTitle(truncate(title));
        }
        if (req.tagIds() != null) {
            video.setTags(loadTags(req.tagIds()));
        }
        return toDto(video);
    }

    public void delete(Long id) {
        Video video = find(id);
        videos.delete(video);
        if (video.getSource() == VideoSource.UPLOAD && storage.enabled()) {
            try {
                storage.delete(video.getStorageKey());
            } catch (Exception e) {
                log.warn("Não foi possível apagar o arquivo {}: {}", video.getStorageKey(), e.getMessage());
            }
        }
    }

    private Video find(Long id) {
        return videos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo não encontrado."));
    }

    private Set<Tag> loadTags(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw badRequest("Selecione pelo menos uma tag.");
        }
        Set<Long> unique = new HashSet<>(ids);
        List<Tag> found = tags.findAllById(unique);
        if (found.size() != unique.size()) {
            throw badRequest("Alguma tag selecionada não existe mais.");
        }
        return new HashSet<>(found);
    }

    private VideoDto toDto(Video v) {
        List<VideoDto.TagRef> tagRefs = v.getTags().stream()
                .sorted(Comparator.comparing(t -> t.getName().toLowerCase()))
                .map(t -> new VideoDto.TagRef(t.getId(), t.getName()))
                .toList();
        String url = v.getSource() == VideoSource.UPLOAD ? storage.publicUrl(v.getStorageKey()) : null;
        return new VideoDto(v.getId(), v.getTitle(), v.getSource(), v.getYoutubeId(), url, tagRefs, v.getCreatedAt());
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
