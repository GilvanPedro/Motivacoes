package com.motivacional.video;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @EntityGraph(attributePaths = "tags")
    @Query("select v from Video v order by v.createdAt desc")
    List<Video> findAllWithTags();

    @EntityGraph(attributePaths = "tags")
    @Query("select v from Video v where exists (select 1 from v.tags t where t.id = :tagId) order by v.createdAt desc")
    List<Video> findByTagWithTags(@Param("tagId") Long tagId);

    @Query(value = """
            select v.* from videos v
            join video_tags vt on vt.video_id = v.id
            where vt.tag_id = :tagId and v.id <> :excludeId
            order by random()
            limit 1
            """, nativeQuery = true)
    Optional<Video> findRandomByTag(@Param("tagId") Long tagId, @Param("excludeId") long excludeId);
}
