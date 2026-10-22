package com.motivacional.tag;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {

    boolean existsByNameIgnoreCase(String name);

    @Query("""
            select new com.motivacional.tag.TagDto(t.id, t.name, count(v))
            from Tag t left join t.videos v
            group by t.id, t.name
            order by lower(t.name)
            """)
    List<TagDto> findAllWithCounts();
}
