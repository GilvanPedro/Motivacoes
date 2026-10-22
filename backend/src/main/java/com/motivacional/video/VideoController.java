package com.motivacional.video;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService service;

    public VideoController(VideoService service) {
        this.service = service;
    }

    @GetMapping
    public List<VideoDto> list(@RequestParam(required = false) Long tagId) {
        return service.list(tagId);
    }

    @GetMapping("/random")
    public VideoDto random(@RequestParam Long tagId, @RequestParam(required = false) Long exclude) {
        return service.random(tagId, exclude);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VideoDto create(@RequestBody VideoService.CreateVideoRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public VideoDto update(@PathVariable Long id, @RequestBody VideoService.UpdateVideoRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
