package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.DocumentRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRatingRepository extends JpaRepository<DocumentRating, UUID> {

    interface RatingBreakdownProjection {
        Integer getRating();

        Long getTotal();
    }

    Optional<DocumentRating> findByDocumentIdAndUserId(UUID documentId, int userId);

    @Query(value = """
            SELECT rating, COUNT(*)::bigint AS total
            FROM document_ratings
            WHERE document_id = :documentId
            GROUP BY rating
            ORDER BY rating DESC
            """, nativeQuery = true)
    List<RatingBreakdownProjection> getBreakdownByDocumentId(@Param("documentId") UUID documentId);
}
