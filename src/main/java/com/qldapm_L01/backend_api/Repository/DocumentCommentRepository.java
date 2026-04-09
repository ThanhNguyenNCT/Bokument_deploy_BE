package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.DocumentComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentCommentRepository extends JpaRepository<DocumentComment, UUID> {
    List<DocumentComment> findByDocumentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID documentId);

    Page<DocumentComment> findByDocumentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID documentId, Pageable pageable);

    Optional<DocumentComment> findByIdAndDocumentIdAndDeletedAtIsNull(UUID id, UUID documentId);
}
