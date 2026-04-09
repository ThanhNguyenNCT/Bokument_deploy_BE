package com.qldapm_L01.backend_api.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Lưu lịch sử mỗi lần một user tải về một tài liệu.
 * Dùng để:
 *   1. Kiểm tra quota: số bài upload >= số bài đã tải về (PBI-16).
 *   2. Đếm lượt tải xuống của từng tài liệu.
 */
@Entity
@Table(name = "document_downloads")
@Data
public class DocumentDownload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant downloadedAt;
}
