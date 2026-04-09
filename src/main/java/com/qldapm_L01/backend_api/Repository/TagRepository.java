package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Integer> {
    Optional<Tag> findByNameIgnoreCase(String name);

    @Query("SELECT t FROM Tag t WHERE lower(t.name) IN :names")
    List<Tag> findByLowerNames(@Param("names") List<String> names);

    public interface TagCountProjection {
        String getTagName();
        Long getDocCount();
    }

    @Query(value = "SELECT t.name as tagName, COUNT(d.id) as docCount " +
                   "FROM tags t " +
                   "LEFT JOIN document_tags dt ON t.id = dt.tag_id " +
                   "LEFT JOIN documents d ON d.id = dt.document_id AND d.visible = true " +
                   "GROUP BY t.id, t.name " +
                   "ORDER BY docCount DESC, t.name ASC", nativeQuery = true)
    List<TagCountProjection> findTagsWithCount();
}
