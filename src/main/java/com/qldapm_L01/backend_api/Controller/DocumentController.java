package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.DTO.CompleteUploadRequestDTO;
import com.qldapm_L01.backend_api.DTO.InitUploadRequestDTO;
import com.qldapm_L01.backend_api.DTO.InitUploadResponse;
import com.qldapm_L01.backend_api.DTO.UpdateDocumentMetadataRequestDTO;
import com.qldapm_L01.backend_api.DTO.UpsertCommentRequestDTO;
import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Exception.DataNotFoundException;
import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import com.qldapm_L01.backend_api.Service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStorageService storageService;
    private final UserRepository userRepository;

    /**
     * [Public] Lấy danh sách tài liệu công khai, hỗ trợ tìm kiếm Full Text Search
     * và lọc theo tag.
     * GET /api/documents?q=từ
         * khóa&tags=Math,Physics&contentType=application/pdf&page=0&size=18
     */
    @GetMapping
    public ResponseEntity<?> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) java.util.List<String> tags,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) String sort) {
        Page<Document> docs = storageService.listVisible(page, size, q, contentType, tags, minRating, maxRating, sort);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Documents retrieved successfully");
        response.setData(toPageData(docs));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Lấy kho tài liệu cá nhân của user đang đăng nhập (bao gồm cả tài liệu
     * ẩn).
     * GET /api/documents/my?q=...&visible=true&tags=...&page=0&size=20
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyDocuments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean visible,
            @RequestParam(required = false) java.util.List<String> tags,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) String sort) {
        int ownerId = currentUserId(authentication);
        Page<Document> docs = storageService.listByOwner(ownerId, page, size, q, visible, tags, minRating, maxRating, sort);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Documents retrieved successfully");
        response.setData(toPageData(docs));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Lấy quota hiện tại của user đăng nhập.
     * GET /api/documents/my/quota
     */
    @GetMapping("/my/quota")
    public ResponseEntity<?> getMyQuota(Authentication authentication) {
        int userId = currentUserId(authentication);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Quota retrieved successfully");
        response.setData(storageService.getMyQuota(userId));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth + Đóng góp] Cấp Pre-signed URL tải về 1 tài liệu (PBI-16).
     * Quota: số bài đã upload phải >= số bài đã tải về (tính cả lần này).
     * - 401: Chưa đăng nhập.
     * - 403: Đa đăng nhập nhưng hết quota (uploads <= downloads đang có).
     * GET /api/documents/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(
            @PathVariable UUID id,
            Authentication authentication) {
        Integer requesterId = currentUserIdOrNull(authentication);

        if (requesterId == null) {
            BaseResponse errorResponse = new BaseResponse();
            errorResponse.setStatusCode(401);
            errorResponse.setMessage("Khách chưa login: Yêu cầu Đăng nhập");
            return ResponseEntity.status(401).body(errorResponse);
        }

        // Quota check (uploads >= downloads) và ghi lượt tải được xử lý trong Service
        // với SERIALIZABLE transaction. Nếu không đủ quota, Service ném
        // AccessDeniedException -> 403.
        String url = storageService.createDownloadUrlForRead(requesterId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document URL generated successfully");
        response.setData(Map.of("url", url));
        return ResponseEntity.ok(response);
    }

    /**
     * [Public] Xem thông tin metadata của 1 tài liệu (tên, loại, dung lượng,
    /**
     * [Public/Auth] Đọc từng trang của bản preview giống StuDocu.
     * Khách (chưa login) chỉ đọc được tối đa 50% số trang.
     * GET /api/documents/{id}/pages/{pageNumber}
     */
    @GetMapping("/{id}/pages/{pageNumber}")
    public ResponseEntity<?> getDocumentPage(
            @PathVariable UUID id,
            @PathVariable int pageNumber,
            Authentication authentication
    ) {
        Integer requesterId = currentUserIdOrNull(authentication);
        Document doc = storageService.getDocumentForRead(requesterId, id);

        if (!"READY".equals(doc.getProcessingStatus())) {
            BaseResponse errorResponse = new BaseResponse();
            errorResponse.setStatusCode(400);
            errorResponse.setMessage("Tài liệu chưa được xử lý xong để đọc trực tuyến. Đang ở trạng thái: " + doc.getProcessingStatus());
            return ResponseEntity.status(400).body(errorResponse);
        }
        
        if (pageNumber < 1 || pageNumber > doc.getPageCount()) {
            BaseResponse errorResponse = new BaseResponse();
            errorResponse.setStatusCode(400);
            errorResponse.setMessage("Trang không tồn tại");
            return ResponseEntity.status(400).body(errorResponse);
        }

        if (requesterId == null) {
            int maxAllowed = Math.max(1, (int) Math.ceil(doc.getPageCount() / 2.0));
            if (pageNumber > maxAllowed) {
                BaseResponse errorResponse = new BaseResponse();
                errorResponse.setStatusCode(401);
                errorResponse.setMessage("Tài liệu quá dài. Bạn cần Đăng nhập để đọc toàn bộ!");
                return ResponseEntity.status(401).body(errorResponse);
            }
        }

        String url = storageService.createPageUrl(doc, pageNumber);
        
        // Trả về 302 Redirect để trình duyệt tự mở ảnh (rất phù hợp đặt trong thẻ <img src="...">)
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .location(java.net.URI.create(url))
                .build();
    }

    /**
     * [Public/Auth] Lấy thông tin chi tiết (metadata) của 1 tài liệu cụ thể (id, tên gốc, tags,
     * lượt tải, thời gian tải lên, chủ nhân, trạng thái xử lý trang,...
     * v.v...).
     * Nếu tài liệu bị ẩn, chỉ chủ nhân mới xem được.
     * GET /api/documents/{id}/metadata
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<?> getDocumentMetadata(
            @PathVariable UUID id,
            Authentication authentication) {
        Integer requesterId = currentUserIdOrNull(authentication);
        Document doc = storageService.getDocumentForRead(requesterId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document metadata retrieved successfully");
        Map<String, Object> data = new HashMap<>();
        data.put("id", doc.getId());
        data.put("originalName", doc.getOriginalName());
        data.put("title", doc.getTitle());
        data.put("description", doc.getDescription());
        data.put("tags", doc.getTags() == null ? java.util.List.of() : doc.getTags().stream().map(t -> t.getName()).toList());
        data.put("contentType", doc.getContentType());
        data.put("size", doc.getSize());
        data.put("visible", doc.isVisible());
        data.put("ownerId", doc.getOwnerId());
        data.put("username", doc.getUsername());
        data.put("createdAt", doc.getCreatedAt());
        data.put("uploadedAt", doc.getUploadedAt());
        data.put("downloadCount", doc.getDownloadCount());
        data.put("ratingAvg", doc.getRatingAvg());
        data.put("ratingCount", doc.getRatingCount());
        data.put("pageCount", doc.getPageCount());
        data.put("processingStatus", doc.getProcessingStatus());
        response.setData(data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/rating")
    public ResponseEntity<?> getRating(
            @PathVariable UUID id,
            Authentication authentication) {
        Integer requesterId = currentUserIdOrNull(authentication);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document rating retrieved successfully");
        response.setData(storageService.getDocumentRating(requesterId, id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<?> rateDocument(
            @PathVariable UUID id,
            @RequestBody Map<String, Integer> body,
            Authentication authentication) {
        int requesterId = currentUserId(authentication);
        Integer rating = body.get("rating");
        if (rating == null) {
            throw new IllegalArgumentException("rating là bắt buộc.");
        }

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document rating updated successfully");
        response.setData(storageService.rateDocument(requesterId, id, rating));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getComments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication) {
        Integer requesterId = currentUserIdOrNull(authentication);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document comments retrieved successfully");
        response.setData(storageService.getDocumentComments(requesterId, id, page, size));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> createComment(
            @PathVariable UUID id,
            @RequestBody UpsertCommentRequestDTO body,
            Authentication authentication) {
        int requesterId = currentUserId(authentication);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Comment created successfully");
        response.setData(storageService.createDocumentComment(requesterId, id, body.getContent()));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/comments/{commentId}")
    public ResponseEntity<?> updateComment(
            @PathVariable UUID id,
            @PathVariable UUID commentId,
            @RequestBody UpsertCommentRequestDTO body,
            Authentication authentication) {
        int requesterId = currentUserId(authentication);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Comment updated successfully");
        response.setData(storageService.updateDocumentComment(requesterId, id, commentId, body.getContent()));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable UUID id,
            @PathVariable UUID commentId,
            Authentication authentication) {
        int requesterId = currentUserId(authentication);
        boolean requesterIsAdmin = isAdmin(authentication);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Comment deleted successfully");
        response.setData(storageService.deleteDocumentComment(requesterId, id, commentId, requesterIsAdmin));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/metadata")
    public ResponseEntity<?> updateMetadata(
            @PathVariable UUID id,
            @RequestBody UpdateDocumentMetadataRequestDTO body,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        Document updated = storageService.updateMetadata(ownerId, id, body.getTitle(), body.getDescription(), body.getTags());

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document metadata updated successfully");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", updated.getId());
        data.put("title", updated.getTitle());
        data.put("description", updated.getDescription());
        data.put("tags", updated.getTags() == null ? java.util.List.of() : updated.getTags().stream().map(t -> t.getName()).toList());
        response.setData(data);
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Ẩn hoặc Hiện tài liệu của bản thân.
     * visible=true => Tài liệu Public, hiển thị cho mọi người tìm kiếm.
     * visible=false => Tài liệu Private, chỉ chủ nhân thấy trong /my.
     * PUT /api/documents/{id}/visibility Body: { "visible": false }
     */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<?> toggleVisibility(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        boolean visible = body.getOrDefault("visible", true);
        Document updated = storageService.toggleVisibility(ownerId, id, visible);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage(visible ? "Document is now visible" : "Document is now hidden");
        response.setData(Map.of(
                "id", updated.getId(),
                "visible", updated.isVisible()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/init-upload")
    public ResponseEntity<?> initUpload(
            @RequestBody InitUploadRequestDTO request,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        InitUploadResponse initResponse = storageService.initUpload(ownerId, request);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Initialized upload. Please PUT file to the signed URL.");
        response.setData(initResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete-upload")
    public ResponseEntity<?> completeUpload(
            @RequestBody CompleteUploadRequestDTO request,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        Document saved = storageService.completeUpload(ownerId, request.getDocumentId());

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Upload completed and processing started.");
        response.setData(Map.of(
                "id", saved.getId(),
                "originalName", saved.getOriginalName(),
                "contentType", saved.getContentType(),
                "size", saved.getSize(),
                "processingStatus", saved.getProcessingStatus()));
        return ResponseEntity.ok(response);
    }


    /**
     * [Auth] Dành cho chủ nhân file tự lấy Pre-signed URL đọc/tải bài riêng của
     * mình.
     * Khác với GET /{id}: Không yêu cầu đóng góp PBI-16, chỉ cần là chủ nhân file.
     * GET /api/documents/{id}/download-url
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<?> getDownloadUrl(
            @PathVariable UUID id,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        String url = storageService.createDownloadUrl(ownerId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Download URL generated successfully");
        response.setData(Map.of("url", url));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Đổi tên hiển thị (originalName) của tài liệu. Extension gốc được tự
     * giữ lại.
     * PUT /api/documents/{id}/rename Body: { "newName": "Tên mới" }
     */
    @PutMapping("/{id}/rename")
    public ResponseEntity<?> rename(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        String newName = body.get("newName");
        Document updated = storageService.rename(ownerId, id, newName);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document renamed successfully");
        response.setData(Map.of(
                "id", updated.getId(),
                "originalName", updated.getOriginalName()));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Xóa vĩnh viễn tài liệu khỏi Database và S3 Storage.
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            Authentication authentication) {
        int requesterId = currentUserId(authentication);
        storageService.delete(requesterId, id, isAdmin(authentication));

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document deleted successfully");
        response.setData(null);
        return ResponseEntity.ok(response);
    }

    private int currentUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        return user.getId();
    }

    private Integer currentUserIdOrNull(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Object> toPageData(Page<Document> docs) {
        return Map.of(
                "items", docs.getContent().stream().map(this::toSummary).toList(),
                "page", docs.getNumber(),
                "size", docs.getSize(),
                "totalItems", docs.getTotalElements(),
                "totalPages", docs.getTotalPages());
    }

    private Map<String, Object> toSummary(Document doc) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", doc.getId());
        summary.put("originalName", doc.getOriginalName());
        summary.put("title", doc.getTitle());
        summary.put("description", doc.getDescription());
        if (doc.getTags() != null) {
            summary.put("tags", doc.getTags().stream().map(t -> t.getName()).toList());
        }
        summary.put("contentType", doc.getContentType());
        summary.put("size", doc.getSize());

        summary.put("ownerId", doc.getOwnerId());
        summary.put("username", doc.getUsername());

        // createdAt may be null for legacy rows, avoid Map.of null crash
        summary.put("createdAt", doc.getCreatedAt());
        summary.put("downloadCount", doc.getDownloadCount());
        summary.put("ratingAvg", doc.getRatingAvg());
        summary.put("ratingCount", doc.getRatingCount());
        summary.put("pageCount", doc.getPageCount());
        summary.put("processingStatus", doc.getProcessingStatus());

        return summary;
    }
}
