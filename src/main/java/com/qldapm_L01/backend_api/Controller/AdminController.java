package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Exception.DataNotFoundException;
import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import com.qldapm_L01.backend_api.Repository.DocumentDownloadRepository;
import com.qldapm_L01.backend_api.Repository.DocumentRatingRepository;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import com.qldapm_L01.backend_api.Service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int DEFAULT_PAGE_SIZE = 18;
    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentRepository documentRepository;
    private final DocumentDownloadRepository downloadRepository;
    private final DocumentRatingRepository documentRatingRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService documentStorageService;

    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String visibility
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), sanitizePageSize(size));
        Boolean visibleFilter = parseVisibilityFilter(visibility);

        Page<Document> documents = documentRepository.searchForAdmin(normalizeQuery(q), visibleFilter, pageable);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Admin documents retrieved successfully");
        response.setData(Map.of(
                "items", documents.getContent().stream().map(this::toDocumentSummary).toList(),
                "page", documents.getNumber(),
                "size", documents.getSize(),
                "totalItems", documents.getTotalElements(),
                "totalPages", documents.getTotalPages()
        ));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/documents/{id}/moderation")
    public ResponseEntity<?> moderateDocument(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        String action = normalizeAction(body == null ? null : body.get("action"));

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);

        if ("DELETE".equals(action)) {
            int adminId = currentUserId(authentication);
            documentStorageService.delete(adminId, id, true);
            response.setMessage("Document deleted successfully");
            response.setData(Map.of("id", id, "deleted", true));
            return ResponseEntity.ok(response);
        }

        boolean visible;
        if ("HIDE".equals(action)) {
            visible = false;
        } else if ("KEEP".equals(action) || "SHOW".equals(action) || "UNHIDE".equals(action)) {
            visible = true;
        } else {
            throw new IllegalArgumentException("Hanh dong moderation khong hop le.");
        }

        Document updated = documentStorageService.setVisibilityAsAdmin(id, visible);
        response.setMessage("Document moderation updated successfully");
        response.setData(Map.of(
                "id", updated.getId(),
                "visible", updated.isVisible()
        ));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/documents/{id}/comments/{commentId}")
    public ResponseEntity<?> deleteCommentAsAdmin(
            @PathVariable UUID id,
            @PathVariable UUID commentId,
            Authentication authentication
    ) {
        int adminId = currentUserId(authentication);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Comment deleted successfully");
        response.setData(documentStorageService.deleteDocumentComment(adminId, id, commentId, true));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), sanitizePageSize(size));
        String normalizedStatus = normalizeStatus(status, false);

        Page<User> users = userRepository.searchUsers(normalizeQuery(q), normalizedStatus, pageable);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Admin users retrieved successfully");
        response.setData(Map.of(
                "items", users.getContent().stream().map(this::toUserSummary).toList(),
                "page", users.getNumber(),
                "size", users.getSize(),
                "totalItems", users.getTotalElements(),
                "totalPages", users.getTotalPages()
        ));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable int id,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        int adminId = currentUserId(authentication);
        String targetStatus = normalizeStatus(body == null ? null : body.get("status"), true);

        if (adminId == id && "BANNED".equals(targetStatus)) {
            throw new IllegalArgumentException("Khong the khoa tai khoan admin dang su dung.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("User not found"));

        boolean statusChanged = user.getStatus() == null
                || !targetStatus.equalsIgnoreCase(user.getStatus());

        user.setStatus(targetStatus);
        if ("BANNED".equals(targetStatus)) {
            user.setBannedAt(Instant.now());
        } else {
            user.setBannedAt(null);
        }

        if (statusChanged) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        User saved = userRepository.save(user);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("User status updated successfully");
        response.setData(toUserSummary(saved));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<?> getOverviewStats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", userRepository.count());
        data.put("totalBannedUsers", userRepository.countByStatus("BANNED"));
        data.put("totalDocuments", documentRepository.count());
        data.put("totalVisibleDocuments", documentRepository.countByVisibleTrue());
        data.put("totalDownloads", downloadRepository.count());
        data.put("totalRatings", documentRatingRepository.count());

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Admin overview stats retrieved successfully");
        response.setData(data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/downloads")
    public ResponseEntity<?> getDownloadSeries(
            @RequestParam(defaultValue = "day") String range
    ) {
        String normalizedRange = normalizeRange(range);
        Instant fromAt = "month".equals(normalizedRange)
                ? Instant.now().minus(365, ChronoUnit.DAYS)
                : Instant.now().minus(30, ChronoUnit.DAYS);

        List<DocumentDownloadRepository.DownloadSeriesProjection> series = "month".equals(normalizedRange)
                ? downloadRepository.aggregateDownloadsByMonth(fromAt)
                : downloadRepository.aggregateDownloadsByDay(fromAt);

        List<Map<String, Object>> points = series.stream()
                .map(row -> Map.<String, Object>of(
                        "bucket", row.getBucket(),
                        "total", row.getTotal() == null ? 0L : row.getTotal()
                ))
                .toList();

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Admin download stats retrieved successfully");
        response.setData(Map.of(
                "range", normalizedRange,
                "points", points
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/top-documents")
    public ResponseEntity<?> getTopDocuments(
            @RequestParam(defaultValue = "5") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(20, limit));

        List<Map<String, Object>> items = documentRepository.findTopDocumentsByHotScore(safeLimit)
                .stream()
                .map(this::toTopDocument)
                .toList();

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Admin top documents retrieved successfully");
        response.setData(Map.of(
                "items", items,
                "limit", safeLimit
        ));
        return ResponseEntity.ok(response);
    }

    private int currentUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        return user.getId();
    }

    private int sanitizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(MAX_PAGE_SIZE, size);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }

        String normalized = query.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeAction(String action) {
        if (action == null) {
            return "";
        }
        return action.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRange(String range) {
        if (range == null) {
            return "day";
        }

        String normalized = range.trim().toLowerCase(Locale.ROOT);
        if ("month".equals(normalized)) {
            return "month";
        }
        return "day";
    }

    private String normalizeStatus(String status, boolean required) {
        if (status == null || status.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("status la bat buoc va chi nhan ACTIVE hoac BANNED.");
            }
            return null;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"BANNED".equals(normalized)) {
            throw new IllegalArgumentException("status chi nhan ACTIVE hoac BANNED.");
        }
        return normalized;
    }

    private Boolean parseVisibilityFilter(String visibility) {
        if (visibility == null || visibility.isBlank() || "all".equalsIgnoreCase(visibility.trim())) {
            return null;
        }

        String normalized = visibility.trim().toLowerCase(Locale.ROOT);
        if ("visible".equals(normalized) || "true".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("hidden".equals(normalized) || "false".equals(normalized)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("visibility chi nhan all|visible|hidden.");
    }

    private Map<String, Object> toDocumentSummary(Document doc) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", doc.getId());
        summary.put("originalName", doc.getOriginalName());
        summary.put("title", doc.getTitle());
        summary.put("description", doc.getDescription());
        summary.put("ownerId", doc.getOwnerId());
        summary.put("username", doc.getUsername());
        summary.put("visible", doc.isVisible());
        summary.put("contentType", doc.getContentType());
        summary.put("size", doc.getSize());
        summary.put("downloadCount", doc.getDownloadCount());
        summary.put("ratingAvg", doc.getRatingAvg());
        summary.put("ratingCount", doc.getRatingCount());
        summary.put("createdAt", doc.getCreatedAt());
        summary.put("processingStatus", doc.getProcessingStatus());
        return summary;
    }

    private Map<String, Object> toUserSummary(User user) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", user.getId());
        summary.put("username", user.getUsername());
        summary.put("email", user.getEmail());
        summary.put("role", user.getRole());
        summary.put("status", user.getStatus());
        summary.put("uploadCount", user.getUploadCount());
        summary.put("downloadCount", user.getDownloadCount());
        summary.put("tokenVersion", user.getTokenVersion());
        summary.put("bannedAt", user.getBannedAt());
        return summary;
    }

    private Map<String, Object> toTopDocument(DocumentRepository.TopDocumentProjection projection) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", projection.getId());
        row.put("title", projection.getTitle());
        row.put("originalName", projection.getOriginalName());
        row.put("username", projection.getUsername());
        row.put("downloadCount", projection.getDownloadCount());
        row.put("ratingCount", projection.getRatingCount());
        row.put("ratingAvg", projection.getRatingAvg());
        row.put("hotScore", projection.getHotScore());
        return row;
    }
}
