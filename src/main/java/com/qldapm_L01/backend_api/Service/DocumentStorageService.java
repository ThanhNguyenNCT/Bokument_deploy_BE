package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.DTO.InitUploadRequestDTO;
import com.qldapm_L01.backend_api.DTO.InitUploadResponse;
import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.DocumentComment;
import com.qldapm_L01.backend_api.Entity.DocumentDownload;
import com.qldapm_L01.backend_api.Entity.DocumentRating;
import com.qldapm_L01.backend_api.Entity.Tag;
import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Exception.CommentRequiresRatingException;
import com.qldapm_L01.backend_api.Exception.DataNotFoundException;
import com.qldapm_L01.backend_api.Exception.QuotaInsufficientException;
import com.qldapm_L01.backend_api.Exception.UploadInvalidStateException;
import com.qldapm_L01.backend_api.Exception.UploadMagicBytesException;
import com.qldapm_L01.backend_api.Exception.UploadObjectNotFoundException;
import com.qldapm_L01.backend_api.Exception.UploadSizeExceededException;
import com.qldapm_L01.backend_api.Exception.UploadValidationException;
import com.qldapm_L01.backend_api.Repository.DocumentCommentRepository;
import com.qldapm_L01.backend_api.Repository.DocumentDownloadRepository;
import com.qldapm_L01.backend_api.Repository.DocumentRatingRepository;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import com.qldapm_L01.backend_api.Repository.TagRepository;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private static final int FIXED_LIST_PAGE_SIZE = 18;
    private static final int FIXED_COMMENTS_PAGE_SIZE = 5;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final DocumentRepository documentRepository;
    private final DocumentCommentRepository documentCommentRepository;
    private final DocumentDownloadRepository downloadRepository;
    private final DocumentRatingRepository documentRatingRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PdfConversionService pdfConversionService;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.signed-url-minutes}")
    private long signedUrlMinutes;

    @Value("${app.storage.max-file-size-bytes:20971520}")
    private long maxFileSizeBytes;

    private volatile Boolean originalNameByteaSchema;

    @Transactional
    public InitUploadResponse initUpload(int ownerId, InitUploadRequestDTO request) {
        validateInitUploadRequest(request);

        String extension = normalizeExt(request.getExt());
        String objectKey = buildObjectKey(ownerId, extension);

        Document doc = new Document();
        User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new DataNotFoundException("User not found"));

        doc.setOwnerId(ownerId);
        doc.setUsername(owner.getUsername());
        doc.setBucketName(bucket);
        doc.setObjectKey(objectKey);
        doc.setOriginalName(request.getFileName().trim());
        doc.setTitle(request.getTitle());
        doc.setDescription(request.getDescription());
        doc.setContentType("application/pdf");
        doc.setSize(0L);
        doc.setProcessingStatus("UPLOADING");

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            java.util.Set<Tag> tagEntities = new java.util.HashSet<>();
            for (String tagName : request.getTags()) {
                if (tagName == null || tagName.isBlank()) continue;
                Tag tag = tagRepository.findByNameIgnoreCase(tagName.trim())
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setName(tagName.trim());
                            return tagRepository.save(newTag);
                        });
                tagEntities.add(tag);
            }
            doc.setTags(tagEntities);
        }

        Document saved = documentRepository.save(doc);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType("application/pdf")
                .build();

        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(Math.max(1, signedUrlMinutes)))
                        .putObjectRequest(putObjectRequest)
                        .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        InitUploadResponse resp = new InitUploadResponse();
        resp.setDocumentId(saved.getId());
        resp.setUploadUrl(uploadUrl);
        resp.setObjectKey(objectKey);
        return resp;
    }

    @Transactional
    public Document completeUpload(int ownerId, UUID documentId) {
        if (documentId == null) {
            throw new UploadValidationException("Thiếu documentId để hoàn tất tải lên.");
        }

        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new UploadObjectNotFoundException("Không tìm thấy tài liệu cần hoàn tất tải lên."));

        if (!"UPLOADING".equals(doc.getProcessingStatus())) {
            throw new UploadInvalidStateException("Tài liệu không còn ở trạng thái chờ complete-upload.");
        }

        HeadObjectResponse head = loadUploadedObjectMetadata(doc);
        if (head.contentLength() > maxFileSizeBytes) {
            hardDeleteUploadDraft(doc);
            throw new UploadSizeExceededException("File vượt quá 20MB, hệ thống đã hủy bản nháp tải lên.");
        }

        if (!hasValidPdfMagicBytes(doc)) {
            hardDeleteUploadDraft(doc);
            throw new UploadMagicBytesException("Nội dung file không phải PDF hợp lệ.");
        }

        doc.setSize(head.contentLength());

        doc.setProcessingStatus("PROCESSING");
        Document saved = documentRepository.save(doc);

        if (userRepository.incrementUploadCount(ownerId) != 1) {
            throw new DataNotFoundException("User not found");
        }

        pdfConversionService.downloadAndProcess(saved);

        return saved;
    }

    @Transactional
    public Document toggleVisibility(int ownerId, UUID documentId, boolean visible) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setVisible(visible);
        return documentRepository.save(doc);
    }

    @Transactional
    public Document setVisibilityAsAdmin(UUID documentId, boolean visible) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setVisible(visible);
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public Document getDocument(int ownerId, UUID documentId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Transactional(readOnly = true)
    public Document getDocumentForRead(Integer requesterId, UUID documentId) {
        if (requesterId != null) {
            boolean isAdmin = userRepository.findById(requesterId)
                .map(user -> "ADMIN".equalsIgnoreCase(user.getRole()))
                .orElse(false);

            if (isAdmin) {
            return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
            }

            return documentRepository.findByIdAndOwnerId(documentId, requesterId)
                    .or(() -> documentRepository.findByIdAndVisibleTrue(documentId))
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        }

        return documentRepository.findByIdAndVisibleTrue(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Transactional
    public Document rename(int ownerId, UUID documentId, String newName) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("New name must not be empty");
        }

        // Giữ nguyên extension gốc
        String originalExt = doc.getOriginalName().substring(doc.getOriginalName().lastIndexOf('.'));
        String cleanName = newName.strip();
        if (!cleanName.endsWith(originalExt)) {
            cleanName = cleanName + originalExt;
        }

        // Avoid object self-copy metadata updates because some S3-compatible providers
        // reject signed copy requests. Download endpoints already set filename dynamically
        // via responseContentDisposition using originalName.
        doc.setOriginalName(cleanName);

        return documentRepository.save(doc);
    }

    @Transactional
    public Document updateMetadata(int ownerId, UUID documentId, String title, String description, List<String> tags) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setTitle(normalizeNullableText(title));
        doc.setDescription(normalizeNullableText(description));

        if (tags != null) {
            List<String> normalizedTagNames = new ArrayList<>();
            Set<String> dedup = new HashSet<>();
            for (String tag : tags) {
                if (tag == null) {
                    continue;
                }
                String normalized = tag.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }
                if (dedup.add(normalized)) {
                    normalizedTagNames.add(normalized);
                }
            }

            if (normalizedTagNames.isEmpty()) {
                doc.setTags(new HashSet<>());
            } else {
                List<Tag> existingTags = tagRepository.findByLowerNames(normalizedTagNames);
                Map<String, Tag> existingByLowerName = new HashMap<>();
                for (Tag existingTag : existingTags) {
                    existingByLowerName.put(existingTag.getName().trim().toLowerCase(Locale.ROOT), existingTag);
                }

                List<String> missingTags = new ArrayList<>();
                Set<Tag> resolvedTags = new HashSet<>();
                for (String tagName : normalizedTagNames) {
                    Tag resolved = existingByLowerName.get(tagName);
                    if (resolved == null) {
                        missingTags.add(tagName);
                    } else {
                        resolvedTags.add(resolved);
                    }
                }

                if (!missingTags.isEmpty()) {
                    throw new IllegalArgumentException("Tags không tồn tại trong hệ thống: " + String.join(", ", missingTags));
                }

                doc.setTags(resolvedTags);
            }
        }

        return documentRepository.save(doc);
    }


    @Transactional(readOnly = true)
    public String createDownloadUrl(int ownerId, UUID documentId) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        return buildDownloadUrl(doc);
        }

        @Transactional(readOnly = true)
        public Map<String, Object> getMyQuota(int userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new DataNotFoundException("User not found"));

        long uploads = Math.max(0L, user.getUploadCount());
        long downloads = Math.max(0L, user.getDownloadCount());

        return Map.of(
            "uploads", uploads,
            "downloads", downloads,
            "canDownload", uploads > downloads);
        }

        private String buildDownloadUrl(Document doc) {

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .responseContentDisposition("attachment; filename=\"" + doc.getOriginalName() + "\"")
                .build();

        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(signedUrlMinutes))
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url().toString();
    }

    /**
     * Cấp Pre-signed URL tải về — áp dụng quota (uploads >= downloads + 1).
     *
     * Dùng SERIALIZABLE isolation để tránh race condition:
     * Postgres đảm bảo không có 2 giao dịch đồng thời nào cùng vượt quota.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String createDownloadUrlForRead(Integer requesterId, UUID documentId) {
        Document doc = getDocumentForRead(requesterId, documentId);

        // Owner path should not consume contribution quota.
        if (doc.getOwnerId() == requesterId) {
            return buildDownloadUrl(doc);
        }

        User user = userRepository.findByIdForUpdate(requesterId)
            .orElseThrow(() -> new DataNotFoundException("User not found"));

        long uploads = user.getUploadCount();
        long downloads = user.getDownloadCount();

        if (uploads <= downloads) {
            throw new QuotaInsufficientException(
                    "Bạn cần đóng góp ít nhất bằng số bài bạn đã tải về. " +
                            "Hiện tại: đã upload " + uploads + " bài, đã tải về " + downloads + " bài.");
        }

        if (userRepository.incrementDownloadCount(requesterId) != 1) {
            throw new RuntimeException("Cannot update user download counter");
        }

        // Ghi nhận lượt tải: nguyên tử INSERT vào document_downloads (dùng cho quota
        // user)
        // + UPDATE cột download_count trên documents (O(1), thân thiện với display)
        DocumentDownload record = new DocumentDownload();
        record.setUserId(requesterId);
        record.setDocumentId(documentId);
        downloadRepository.save(record);
        documentRepository.incrementDownloadCount(documentId);

        return buildDownloadUrl(doc);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDocumentRating(Integer requesterId, UUID documentId) {
        Document doc = getDocumentForRead(requesterId, documentId);

        Integer myRating = null;
        if (requesterId != null) {
            myRating = documentRatingRepository.findByDocumentIdAndUserId(doc.getId(), requesterId)
                    .map(DocumentRating::getRating)
                    .orElse(null);
        }

        Map<String, Long> breakdown = new LinkedHashMap<>();
        breakdown.put("5", 0L);
        breakdown.put("4", 0L);
        breakdown.put("3", 0L);
        breakdown.put("2", 0L);
        breakdown.put("1", 0L);

        for (DocumentRatingRepository.RatingBreakdownProjection row : documentRatingRepository.getBreakdownByDocumentId(doc.getId())) {
            if (row.getRating() != null) {
                breakdown.put(String.valueOf(row.getRating()), row.getTotal() == null ? 0L : row.getTotal());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avg", doc.getRatingAvg());
        result.put("count", doc.getRatingCount());
        result.put("myRating", myRating);
        result.put("breakdown", breakdown);
        return result;
    }

    @Transactional
    public Map<String, Object> rateDocument(int requesterId, UUID documentId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating phải là số nguyên từ 1 đến 5.");
        }

        Document readableDoc = getDocumentForRead(requesterId, documentId);
        Document doc = documentRepository.findByIdForUpdate(readableDoc.getId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        DocumentRating saved = documentRatingRepository.findByDocumentIdAndUserId(doc.getId(), requesterId)
                .orElseGet(() -> {
                    DocumentRating created = new DocumentRating();
                    created.setDocumentId(doc.getId());
                    created.setUserId(requesterId);
                    return created;
                });

        saved.setRating(rating);
        documentRatingRepository.save(saved);

        documentRepository.syncRatingAggregate(doc.getId());
        return getDocumentRating(requesterId, doc.getId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDocumentComments(Integer requesterId, UUID documentId, int page, int size) {
        Document doc = getDocumentForRead(requesterId, documentId);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, FIXED_COMMENTS_PAGE_SIZE);
        Page<DocumentComment> commentsPage = documentCommentRepository
                .findByDocumentIdAndDeletedAtIsNullOrderByCreatedAtDesc(doc.getId(), pageable);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentComment comment : commentsPage.getContent()) {
            result.add(toCommentResponse(comment, requesterId));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", result);
        payload.put("page", commentsPage.getNumber());
        payload.put("size", commentsPage.getSize());
        payload.put("totalItems", commentsPage.getTotalElements());
        payload.put("totalPages", commentsPage.getTotalPages());
        payload.put("hasNext", commentsPage.hasNext());
        return payload;
    }

    @Transactional
    public Map<String, Object> createDocumentComment(int requesterId, UUID documentId, String content) {
        Document doc = getDocumentForRead(requesterId, documentId);
        String normalizedContent = normalizeRequiredCommentContent(content);

        DocumentRating rating = documentRatingRepository.findByDocumentIdAndUserId(doc.getId(), requesterId)
                .orElseThrow(() -> new CommentRequiresRatingException("Bạn phải đánh giá tài liệu trước khi bình luận."));

        User commenter = userRepository.findById(requesterId)
                .orElseThrow(() -> new DataNotFoundException("User not found"));

        DocumentComment comment = new DocumentComment();
        comment.setDocumentId(doc.getId());
        comment.setUserId(requesterId);
        comment.setUsernameSnapshot(commenter.getUsername());
        comment.setContent(normalizedContent);
        comment.setRatingSnapshot(rating.getRating());
        DocumentComment saved = documentCommentRepository.save(comment);
        return toCommentResponse(saved, requesterId);
    }

    @Transactional
    public Map<String, Object> updateDocumentComment(int requesterId, UUID documentId, UUID commentId, String content) {
        getDocumentForRead(requesterId, documentId);
        String normalizedContent = normalizeRequiredCommentContent(content);

        DocumentComment comment = documentCommentRepository.findByIdAndDocumentIdAndDeletedAtIsNull(commentId, documentId)
                .orElseThrow(() -> new DataNotFoundException("Comment not found"));

        if (comment.getUserId() != requesterId) {
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        comment.setContent(normalizedContent);
        DocumentComment updated = documentCommentRepository.save(comment);
        return toCommentResponse(updated, requesterId);
    }

    @Transactional
    public Map<String, Object> deleteDocumentComment(int requesterId, UUID documentId, UUID commentId, boolean requesterIsAdmin) {
        getDocumentForRead(requesterId, documentId);

        DocumentComment comment = documentCommentRepository.findByIdAndDocumentIdAndDeletedAtIsNull(commentId, documentId)
                .orElseThrow(() -> new DataNotFoundException("Comment not found"));

        if (!requesterIsAdmin && comment.getUserId() != requesterId) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        comment.setDeletedAt(Instant.now());
        documentCommentRepository.save(comment);

        return Map.of(
                "id", comment.getId(),
                "deleted", true);
    }

    public String createPageUrl(Document doc, int pageNumber) {
        String key = "users/" + doc.getOwnerId() + "/" + doc.getId() + "/pages/page_" + pageNumber + ".jpg";
        
        try {
            s3Client.headObject(h -> h.bucket(bucket).key(key));
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            log.info("Page {} not found for document {}. Rendering lazily...", pageNumber, doc.getId());
            pdfConversionService.renderSpecificPageAndUpload(doc, pageNumber);
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(getObjectRequest)
                        .build()
        ).url().toString();
    }

    /**
     * Đếm lượt tải xuống của một tài liệu — đọc từ cột download_count, O(1).
     * Không dùng COUNT(*) trên document_downloads để tránh full scan khi có nhiều
     * lượt tải.
     */
    @Transactional(readOnly = true)
    public long countDownloadsByDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getDownloadCount)
                .orElse(0L);
    }

    @Transactional
        public void delete(int requesterId, UUID documentId, boolean isAdmin) {
        Document doc = isAdmin
            ? documentRepository.findById(documentId).orElseThrow(() -> new IllegalArgumentException("Document not found"))
            : documentRepository.findByIdAndOwnerId(documentId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .build());

        documentRepository.delete(doc);
    }

    private HeadObjectResponse loadUploadedObjectMetadata(Document doc) {
        try {
            return s3Client.headObject(h -> h.bucket(doc.getBucketName()).key(doc.getObjectKey()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new UploadObjectNotFoundException("File chưa được tải lên thành công hoặc đã hết hạn.");
            }
            throw e;
        }
    }

    private boolean hasValidPdfMagicBytes(Document doc) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .range("bytes=0-7")
                .build();

        try (InputStream inputStream = s3Client.getObject(request)) {
            byte[] header = inputStream.readNBytes(8);
            return header.length >= 5
                    && header[0] == (byte) '%'
                    && header[1] == (byte) 'P'
                    && header[2] == (byte) 'D'
                    && header[3] == (byte) 'F'
                    && header[4] == (byte) '-';
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new UploadObjectNotFoundException("File chưa được tải lên thành công hoặc đã hết hạn.");
            }
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Không thể đọc header của file vừa tải lên.", e);
        }
    }

    private void hardDeleteUploadDraft(Document doc) {
        try {
            s3Client.deleteObject(d -> d.bucket(doc.getBucketName()).key(doc.getObjectKey()));
        } catch (S3Exception ex) {
            log.warn("Failed to delete invalid upload object {}: {}", doc.getObjectKey(), ex.getMessage());
        }
        documentRepository.delete(doc);
    }

    private String buildObjectKey(int ownerId, String extension) {
        return "users/" + ownerId + "/" + UUID.randomUUID() + "." + extension;
    }

    private void validateInitUploadRequest(InitUploadRequestDTO request) {
        if (request == null) {
            throw new UploadValidationException("Thiếu request body cho init-upload.");
        }
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new UploadValidationException("fileName là bắt buộc.");
        }
        if (request.getExt() == null || request.getExt().isBlank()) {
            throw new UploadValidationException("ext là bắt buộc.");
        }

        String ext = normalizeExt(request.getExt());
        if (!"pdf".equals(ext)) {
            throw new UploadValidationException("Chỉ chấp nhận định dạng PDF.");
        }

        String fileNameExt = extractExtFromFileName(request.getFileName());
        if (!ext.equals(fileNameExt)) {
            throw new UploadValidationException("Phần mở rộng trong fileName phải khớp ext.");
        }
    }

    private String normalizeExt(String ext) {
        String normalized = ext.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private String extractExtFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new UploadValidationException("fileName phải bao gồm phần mở rộng.");
        }
        return normalizeExt(fileName.substring(dotIndex + 1));
    }

    public long countByOwnerId(int ownerId) {
        return userRepository.findById(ownerId).map(User::getUploadCount).orElse(0L);
    }

        public Page<Document> listVisible(int page, int size, String q, String contentType, java.util.List<String> tags,
            Double minRating, Double maxRating, String sortBy) {
        Pageable pageable = buildFixedListPageable(page);
        String normalizedQ = normalize(q);
        String normalizedContentType = normalize(contentType);
        String normalizedSort = normalizeSort(sortBy);

        java.util.List<String> validTags = tags == null ? new java.util.ArrayList<String>()
            : tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .toList();
        int tagsCount = validTags.size();
        if (validTags.isEmpty()) {
            validTags = java.util.List.of("");
        }

        if (isOriginalNameByteaSchema()) {
            if (normalizedQ != null) {
                log.warn(
                        "Ignoring q filter because documents.original_name is bytea. Apply DB migration to enable name search.");
            }
            return listVisibleWithoutNameSearch(pageable, normalizedContentType);
        }

        try {
            return documentRepository.searchVisible(normalizedQ, normalizedContentType, validTags, tagsCount,
                    minRating, maxRating, normalizedSort, pageable);
        } catch (DataAccessException ex) {
            if (!isLowerByteaError(ex)) {
                throw ex;
            }
            log.warn("Fallback listVisible: schema mismatch on original_name (bytea). q filter is skipped.");
            return listVisibleWithoutNameSearch(pageable, normalizedContentType);
        }
    }

        public Page<Document> listByOwner(int ownerId, int page, int size, String q, Boolean visible,
            java.util.List<String> tags, Double minRating, Double maxRating, String sortBy) {
        Pageable pageable = buildFixedListPageable(page);
        String normalizedQ = normalize(q);
        String normalizedSort = normalizeSort(sortBy);

        java.util.List<String> validTags = tags == null ? new java.util.ArrayList<String>()
            : tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .toList();
        int tagsCount = validTags.size();
        if (validTags.isEmpty()) {
            validTags = java.util.List.of("");
        }

        if (isOriginalNameByteaSchema()) {
            if (normalizedQ != null) {
                log.warn(
                        "Ignoring q filter because documents.original_name is bytea. Apply DB migration to enable name search.");
            }
            return listByOwnerWithoutNameSearch(ownerId, visible, pageable);
        }

        try {
            return documentRepository.searchByOwner(ownerId, normalizedQ, visible, validTags, tagsCount,
                    minRating, maxRating, normalizedSort, pageable);
        } catch (DataAccessException ex) {
            if (!isLowerByteaError(ex)) {
                throw ex;
            }
            log.warn("Fallback listByOwner: schema mismatch on original_name (bytea). q filter is skipped.");
            return listByOwnerWithoutNameSearch(ownerId, visible, pageable);
        }
    }

    private Page<Document> listVisibleWithoutNameSearch(Pageable pageable, String contentType) {
        if (contentType == null) {
            return documentRepository.findByVisibleTrueOrderByCreatedAtDesc(pageable);
        }
        return documentRepository.findByVisibleTrueAndContentTypeOrderByCreatedAtDesc(contentType, pageable);
    }

    private Page<Document> listByOwnerWithoutNameSearch(int ownerId, Boolean visible, Pageable pageable) {
        if (visible == null) {
            return documentRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
        }
        return documentRepository.findByOwnerIdAndVisibleOrderByCreatedAtDesc(ownerId, visible, pageable);
    }

    private Pageable buildFixedListPageable(int page) {
        int safePage = Math.max(0, page);
        return PageRequest.of(safePage, FIXED_LIST_PAGE_SIZE);
    }

    private boolean isLowerByteaError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("function lower(bytea) does not exist")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isOriginalNameByteaSchema() {
        Boolean cached = originalNameByteaSchema;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (originalNameByteaSchema != null) {
                return originalNameByteaSchema;
            }

            String dataType = jdbcTemplate.query(
                    """
                            SELECT data_type
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'documents'
                              AND column_name = 'original_name'
                            """,
                    rs -> rs.next() ? rs.getString("data_type") : null);

            originalNameByteaSchema = dataType != null && "bytea".equalsIgnoreCase(dataType);
            return originalNameByteaSchema;
        }
    }

    private String normalize(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizeSort(String sortBy) {
        if (sortBy == null) {
            return null;
        }

        String value = sortBy.trim().toLowerCase(Locale.ROOT);
        if ("rating_desc".equals(value)
                || "rating_count_desc".equals(value)
                || "download_desc".equals(value)
                || "created_desc".equals(value)
                || "created_asc".equals(value)) {
            return value;
        }
        return null;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRequiredCommentContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Nội dung bình luận là bắt buộc.");
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Nội dung bình luận không được để trống.");
        }
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Nội dung bình luận không được vượt quá 2000 ký tự.");
        }
        return normalized;
    }

    private Map<String, Object> toCommentResponse(DocumentComment comment, Integer requesterId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", comment.getId());
        payload.put("documentId", comment.getDocumentId());
        payload.put("userId", comment.getUserId());
        payload.put("username", comment.getUsernameSnapshot());
        payload.put("content", comment.getContent());
        payload.put("rating", comment.getRatingSnapshot());
        payload.put("createdAt", comment.getCreatedAt());
        payload.put("updatedAt", comment.getUpdatedAt());
        payload.put("isOwner", requesterId != null && comment.getUserId() == requesterId);
        return payload;
    }
}
