package com.motivacional.tag;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagRepository tags;

    public TagController(TagRepository tags) {
        this.tags = tags;
    }

    public record CreateTagRequest(@NotBlank @Size(max = 50) String name) {
    }

    @GetMapping
    public List<TagDto> list() {
        return tags.findAllWithCounts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagDto create(@Valid @RequestBody CreateTagRequest req) {
        String name = req.name().trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome da tag é obrigatório.");
        }
        if (tags.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma tag com esse nome.");
        }
        Tag saved = tags.save(new Tag(name));
        return new TagDto(saved.getId(), saved.getName(), 0L);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!tags.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag não encontrada.");
        }
        tags.deleteById(id);
    }
}
