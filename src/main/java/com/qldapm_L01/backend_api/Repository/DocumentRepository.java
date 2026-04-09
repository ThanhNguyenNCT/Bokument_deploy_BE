package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.Document;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  interface TopDocumentProjection {
    UUID getId();

    String getTitle();

    String getOriginalName();

    String getUsername();

    Long getDownloadCount();

    Long getRatingCount();

    Double getRatingAvg();

    Double getHotScore();
  }

    Optional<Document> findByIdAndOwnerId(UUID id, int ownerId);
    Optional<Document> findByIdAndVisibleTrue(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT d FROM Document d WHERE d.id = :id")
  Optional<Document> findByIdForUpdate(@Param("id") UUID id);

    long countByOwnerId(int ownerId);
    List<Document> findByProcessingStatusAndCreatedAtBefore(String processingStatus, Instant createdAt);
    List<Document> findByProcessingStatusAndUploadedAtBefore(String processingStatus, Instant uploadedAt);

    /**
     * Tăng download_count lên 1 một cách nguyên tử tại DB.
     * PostgreSQL đảm bảo UPDATE single row là atomic — không cần lock thêm.
     */
    @Modifying
    @Query(value = "UPDATE documents SET download_count = download_count + 1 WHERE id = :id", nativeQuery = true)
    void incrementDownloadCount(@Param("id") java.util.UUID id);

    @Modifying
    @Query(value = """
        UPDATE documents d
        SET rating_avg = COALESCE(src.avg_rating, 0),
          rating_count = src.total_count
        FROM (
          SELECT AVG(r.rating)::numeric(4,2) AS avg_rating,
               COUNT(*)::bigint AS total_count
          FROM document_ratings r
          WHERE r.document_id = :id
        ) src
        WHERE d.id = :id
        """, nativeQuery = true)
    void syncRatingAggregate(@Param("id") java.util.UUID id);

    // Query helpers used by list APIs
    Page<Document> findByVisibleTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<Document> findByVisibleTrueAndContentTypeOrderByCreatedAtDesc(String contentType, Pageable pageable);

    Page<Document> findByOwnerIdOrderByCreatedAtDesc(int ownerId, Pageable pageable);

    Page<Document> findByOwnerIdAndVisibleOrderByCreatedAtDesc(int ownerId, boolean visible, Pageable pageable);

        long countByVisibleTrue();

        @Query(value = """
          SELECT d.*
          FROM documents d
          WHERE (
              CAST(:q AS text) IS NULL
              OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text)))
              OR unaccent(lower(COALESCE(d.title, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              OR unaccent(lower(COALESCE(d.description, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              OR unaccent(lower(COALESCE(d.username, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              OR unaccent(lower(COALESCE(d.original_name, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
          )
            AND (CAST(:visible AS boolean) IS NULL OR d.visible = CAST(:visible AS boolean))
          ORDER BY d.created_at DESC
          """,
          countQuery = """
          SELECT COUNT(d.id)
          FROM documents d
          WHERE (
              CAST(:q AS text) IS NULL
              OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text)))
              OR unaccent(lower(COALESCE(d.title, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              OR unaccent(lower(COALESCE(d.description, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              OR unaccent(lower(COALESCE(d.username, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              OR unaccent(lower(COALESCE(d.original_name, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
          )
            AND (CAST(:visible AS boolean) IS NULL OR d.visible = CAST(:visible AS boolean))
          """,
          nativeQuery = true)
        Page<Document> searchForAdmin(
          @Param("q") String q,
          @Param("visible") Boolean visible,
          Pageable pageable
        );

        @Query(value = """
          SELECT
            d.id AS id,
            COALESCE(d.title, '') AS title,
            d.original_name AS originalName,
            d.username AS username,
            d.download_count AS downloadCount,
            d.rating_count AS ratingCount,
            d.rating_avg AS ratingAvg,
            CAST((d.download_count + d.rating_count * 2 + d.rating_avg * 10) AS double precision) AS hotScore
          FROM documents d
          ORDER BY hotScore DESC, d.created_at DESC
          LIMIT :limit
          """, nativeQuery = true)
        List<TopDocumentProjection> findTopDocumentsByHotScore(@Param("limit") int limit);

    @Query(value = """
            SELECT d.* FROM documents d
            WHERE d.visible = true
              AND (
                CAST(:q AS text) IS NULL
                OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text)))
                OR unaccent(lower(COALESCE(d.title, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(COALESCE(d.description, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(d.original_name)) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              )
              AND (CAST(:contentType AS text) IS NULL OR d.content_type = CAST(:contentType AS text))
              AND (CAST(:minRating AS numeric) IS NULL OR d.rating_avg >= CAST(:minRating AS numeric))
              AND (CAST(:maxRating AS numeric) IS NULL OR d.rating_avg <= CAST(:maxRating AS numeric))
              AND (CAST(:tagsCount AS int) = 0 OR d.id IN (
                  SELECT dt.document_id
                  FROM document_tags dt
                  JOIN tags t ON dt.tag_id = t.id
                  WHERE lower(t.name) IN (:tags)
                  GROUP BY dt.document_id
                  HAVING COUNT(DISTINCT lower(t.name)) = CAST(:tagsCount AS int)
              ))
            ORDER BY
              CASE WHEN CAST(:sortBy AS text) = 'created_asc' THEN d.created_at END ASC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.rating_avg END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.rating_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.download_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.created_at END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_count_desc' THEN d.rating_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_count_desc' THEN d.rating_avg END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_count_desc' THEN d.download_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'download_desc' THEN d.download_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'download_desc' THEN d.rating_avg END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'download_desc' THEN d.rating_count END DESC NULLS LAST,
              d.created_at DESC
            """,
            countQuery = """
            SELECT count(d.id) FROM documents d
            WHERE d.visible = true
              AND (
                CAST(:q AS text) IS NULL
                OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text)))
                OR unaccent(lower(COALESCE(d.title, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(COALESCE(d.description, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(d.original_name)) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              )
              AND (CAST(:contentType AS text) IS NULL OR d.content_type = CAST(:contentType AS text))
              AND (CAST(:minRating AS numeric) IS NULL OR d.rating_avg >= CAST(:minRating AS numeric))
              AND (CAST(:maxRating AS numeric) IS NULL OR d.rating_avg <= CAST(:maxRating AS numeric))
              AND (CAST(:tagsCount AS int) = 0 OR d.id IN (
                  SELECT dt.document_id
                  FROM document_tags dt
                  JOIN tags t ON dt.tag_id = t.id
                  WHERE lower(t.name) IN (:tags)
                  GROUP BY dt.document_id
                  HAVING COUNT(DISTINCT lower(t.name)) = CAST(:tagsCount AS int)
              ))
            """,
            nativeQuery = true)
    Page<Document> searchVisible(
            @Param("q") String q,
            @Param("contentType") String contentType,
            @Param("tags") java.util.List<String> tags,
            @Param("tagsCount") int tagsCount,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating,
                @Param("sortBy") String sortBy,
            Pageable pageable
    );

    @Query(value = """
            SELECT d.* FROM documents d
            WHERE d.owner_id = :ownerId
              AND (
                CAST(:q AS text) IS NULL
                OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text)))
                OR unaccent(lower(COALESCE(d.title, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(COALESCE(d.description, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(d.original_name)) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              )
              AND (CAST(:visible AS boolean) IS NULL OR d.visible = CAST(:visible AS boolean))
              AND (CAST(:minRating AS numeric) IS NULL OR d.rating_avg >= CAST(:minRating AS numeric))
              AND (CAST(:maxRating AS numeric) IS NULL OR d.rating_avg <= CAST(:maxRating AS numeric))
              AND (CAST(:tagsCount AS int) = 0 OR d.id IN (
                  SELECT dt.document_id
                  FROM document_tags dt
                  JOIN tags t ON dt.tag_id = t.id
                  WHERE lower(t.name) IN (:tags)
                  GROUP BY dt.document_id
                  HAVING COUNT(DISTINCT lower(t.name)) = CAST(:tagsCount AS int)
              ))
            ORDER BY
              CASE WHEN CAST(:sortBy AS text) = 'created_asc' THEN d.created_at END ASC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.rating_avg END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.rating_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.download_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_desc' THEN d.created_at END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_count_desc' THEN d.rating_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_count_desc' THEN d.rating_avg END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'rating_count_desc' THEN d.download_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'download_desc' THEN d.download_count END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'download_desc' THEN d.rating_avg END DESC NULLS LAST,
              CASE WHEN CAST(:sortBy AS text) = 'download_desc' THEN d.rating_count END DESC NULLS LAST,
              d.created_at DESC
            """,
            countQuery = """
            SELECT count(d.id) FROM documents d
            WHERE d.owner_id = :ownerId
              AND (
                CAST(:q AS text) IS NULL
                OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text)))
                OR unaccent(lower(COALESCE(d.title, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(COALESCE(d.description, ''))) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
                OR unaccent(lower(d.original_name)) LIKE '%' || unaccent(lower(CAST(:q AS text))) || '%'
              )
              AND (CAST(:visible AS boolean) IS NULL OR d.visible = CAST(:visible AS boolean))
              AND (CAST(:minRating AS numeric) IS NULL OR d.rating_avg >= CAST(:minRating AS numeric))
              AND (CAST(:maxRating AS numeric) IS NULL OR d.rating_avg <= CAST(:maxRating AS numeric))
              AND (CAST(:tagsCount AS int) = 0 OR d.id IN (
                  SELECT dt.document_id
                  FROM document_tags dt
                  JOIN tags t ON dt.tag_id = t.id
                  WHERE lower(t.name) IN (:tags)
                  GROUP BY dt.document_id
                  HAVING COUNT(DISTINCT lower(t.name)) = CAST(:tagsCount AS int)
              ))
            """,
            nativeQuery = true)
    Page<Document> searchByOwner(
            @Param("ownerId") int ownerId,
            @Param("q") String q,
            @Param("visible") Boolean visible,
            @Param("tags") java.util.List<String> tags,
            @Param("tagsCount") int tagsCount,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating,
            @Param("sortBy") String sortBy,
            Pageable pageable
    );
}
