package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.DocumentDownload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentDownloadRepository extends JpaRepository<DocumentDownload, UUID> {

    interface DownloadSeriesProjection {
        String getBucket();

        Long getTotal();
    }

    /** Tổng số lần user này đã tải về (dùng kiểm tra quota). */
    long countByUserId(int userId);

    /** Tổng lượt tải xuống của một tài liệu cụ thể. */
    long countByDocumentId(UUID documentId);

    @Query(value = """
            SELECT to_char(date_trunc('day', downloaded_at), 'YYYY-MM-DD') AS bucket,
                   COUNT(*)::bigint AS total
            FROM document_downloads
            WHERE downloaded_at >= :fromAt
            GROUP BY 1
            ORDER BY 1 ASC
            """, nativeQuery = true)
    List<DownloadSeriesProjection> aggregateDownloadsByDay(@Param("fromAt") Instant fromAt);

    @Query(value = """
            SELECT to_char(date_trunc('month', downloaded_at), 'YYYY-MM') AS bucket,
                   COUNT(*)::bigint AS total
            FROM document_downloads
            WHERE downloaded_at >= :fromAt
            GROUP BY 1
            ORDER BY 1 ASC
            """, nativeQuery = true)
    List<DownloadSeriesProjection> aggregateDownloadsByMonth(@Param("fromAt") Instant fromAt);
}
