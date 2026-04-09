# 📚 BoKument Backend — Tài liệu Tính năng Mới

Tài liệu này giải thích toàn bộ những gì đã được hiện thực trong Sprint vừa rồi, bao gồm **hệ thống Quota tải xuống** và **tính năng đọc PDF trực tuyến kiểu StuDocu**.

---

## Mục lục

1. [Bảng tổng quan các thay đổi](#1-bảng-tổng-quan-các-thay-đổi)
2. [Hệ thống Quota — uploads >= downloads (PBI-16)](#2-hệ-thống-quota--uploads--downloads-pbi-16)
3. [Tính năng đọc PDF trực tuyến (StuDocu Style)](#3-tính-năng-đọc-pdf-trực-tuyến-studocu-style)
4. [Schema Database đầy đủ](#4-schema-database-đầy-đủ)
5. [API Reference nhanh](#5-api-reference-nhanh)
6. [Lưu ý khi Deploy & Test](#6-lưu-ý-khi-deploy--test)

---

## 1. Bảng tổng quan các thay đổi

| File | Loại | Mô tả tóm tắt |
|---|---|---|
| `docs/DB.sql` | SQL | Thêm 5 cột mới, 2 bảng phụ |
| `Entity/Document.java` | Entity | Thêm 4 field mới vào Entity |
| `Entity/DocumentDownload.java` | **MỚI** | Entity ghi lịch sử download |
| `Repository/DocumentDownloadRepository.java` | **MỚI** | Repo đếm quota |
| `Repository/DocumentRepository.java` | Modified | Thêm `incrementDownloadCount` |
| `Service/PdfConversionService.java` | **MỚI** | Chạy ngầm, cắt PDF thành ảnh |
| `Service/DocumentStorageService.java` | Modified | Tích hợp quota, PDF conversion |
| `Controller/DocumentController.java` | Modified | Thêm API đọc từng trang |
| `Config/AsyncConfig.java` | **MỚI** | Bật tính năng Async |
| `Config/SecurityConfig.java` | Modified | Thêm rule cho endpoint mới |
| `Util/DocumentValidator.java` | Modified | Chỉ cho upload PDF |
| `pom.xml` | Modified | Thêm thư viện PDFBox 2.0.30 |

---

## 2. Hệ thống Quota — uploads >= downloads (PBI-16)

### Ý tưởng nghiệp vụ
> Để tải về một tài liệu, người dùng phải đã **đóng góp ít nhất bằng số bài họ đã tải**. Nếu upload 3 bài thì tải được tối đa 3 bài.

### Cơ sở dữ liệu

**Bảng `document_downloads`** (mới):
```sql
CREATE TABLE document_downloads (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       INT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_id   UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    downloaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```
Mỗi lần user tải một tài liệu = 1 dòng INSERT.

**Cột `download_count`** trong bảng `documents`:
```sql
ALTER TABLE documents ADD COLUMN download_count BIGINT NOT NULL DEFAULT 0;
```
Đây là bộ đếm tổng lượt tải của một tài liệu (hiển thị trên UI), dùng để tránh `COUNT(*)` chậm.

### Luồng tải xuống (có Quota check)

```
User gọi GET /api/documents/{id}
         │
         ▼
DocumentController.getDocument()
 └─ Kiểm tra đã login (nếu chưa → 401)
         │
         ▼
DocumentStorageService.createDownloadUrlForRead()  ← SERIALIZABLE Transaction
 ├─ Đếm uploads   = SELECT COUNT(*) FROM documents WHERE owner_id = ?
 ├─ Đếm downloads = SELECT COUNT(*) FROM document_downloads WHERE user_id = ?
 ├─ Nếu uploads <= downloads → throw AccessDeniedException → 403
 │    "Bạn cần đóng góp ít nhất bằng số bài bạn đã tải về"
 ├─ INSERT INTO document_downloads ...   ← ghi lịch sử
 ├─ UPDATE documents SET download_count = download_count + 1  ← O(1), atomic
 └─ Tạo S3 Presigned URL → trả về cho user
```

### Tại sao dùng SERIALIZABLE Transaction?

**Vấn đề Race Condition (không có SERIALIZABLE):**
```
Thread A: đọc uploads=3, downloads=2 → OK, bắt đầu ghi
Thread B: đọc uploads=3, downloads=2 → OK, bắt đầu ghi
Thread A: INSERT download → commit (downloads = 3)
Thread B: INSERT download → commit (downloads = 4) ← VI PHẠM QUOTA!
```

**Giải pháp SERIALIZABLE:**
```
Thread A: BEGIN SERIALIZABLE, đọc uploads=3, downloads=2 → OK
Thread B: BEGIN SERIALIZABLE, đọc uploads=3, downloads=2 → OK
Thread A: INSERT → COMMIT ✅
Thread B: COMMIT → Postgres phát hiện conflict → ROLLBACK ❌ → 403
```

### Tại sao `download_count` không bị Race Condition?

Cột `download_count` được tăng bằng lệnh atomic tại DB:
```sql
UPDATE documents SET download_count = download_count + 1 WHERE id = ?
```
PostgreSQL tự khóa hàng khi UPDATE → không cần thêm lock trong code Java.
Khác hoàn toàn với pattern nguy hiểm (đọc → cộng → ghi trong Java).

---

## 3. Tính năng đọc PDF trực tuyến (StuDocu Style)

### Ý tưởng: Không bao giờ để file PDF gốc tiếp xúc trình duyệt

Khi user xem bài trên web, thay vì giao file PDF (dễ bị lưu về bằng Ctrl+S), ta cắt nó thành nhiều tấm **ảnh JPEG** và chỉ giao lần lượt từng trang một.

### Luồng sau khi Upload

```
User Upload PDF
      │
      ▼
DocumentStorageService.upload()  ← Xảy ra trong Request Thread
 ├─ Lưu PDF gốc lên S3
 ├─ INSERT database: status = "PENDING", page_count = 0
 └─ [gọi ngầm] pdfConversionService.processAndUploadImages()
      │   Response 200 trả về ngay! User không phải đợi.
      │
      ▼ (chạy trong Thread Pool riêng, @Async)
PdfConversionService.processAndUploadImages()
 ├─ Dùng Apache PDFBox render từng trang PDF → BufferedImage tại 150 DPI
 ├─ Nén thành JPEG và upload lên S3:
 │   users/{ownerId}/{docId}/pages/page_1.jpg
 │   users/{ownerId}/{docId}/pages/page_2.jpg
 │   ...
 ├─ UPDATE documents SET page_count = N, processing_status = 'READY'
 └─ XÓA file PDF tạm thời trên server
```

### API đọc từng trang: `GET /api/documents/{id}/pages/{pageNumber}`

```
Frontend đặt thẻ: <img src="/api/documents/{id}/pages/1">
                                    │
                                    ▼
                        DocumentController.getDocumentPage()
                         ├─ Kiểm tra processing_status == "READY"?
                         │   Không → 400 "Đang xử lý"
                         ├─ Kiểm tra pageNumber trong [1, pageCount]?
                         │   Không → 400 "Trang không tồn tại"
                         ├─ Nếu là KHÁCH (chưa login):
                         │   Tính maxAllowed = ceil(pageCount / 2)
                         │   pageNumber > maxAllowed → 401 "Cần đăng nhập"
                         └─ Tạo S3 Presigned URL cho ảnh trang đó
                              └─ HTTP 302 Redirect → Trình duyệt nhảy sang S3
```

### Tại sao trả 302 thay vì URL text?

Khi trả về 302 với `Location: https://s3.amazonaws.com/...`, thẻ `<img>` sẽ tự động nạp ảnh. Link S3 có hạn dùng 15 phút — mỗi lần reload trang, Frontend tự động lấy link mới qua API này, không sợ link cũ hết hạn.

### Luật bảo mật Quota không bị phá vỡ

| Ai | Xem trang ảnh | Tải file PDF gốc |
|---|---|---|
| **Khách** | Xem được 50% trang đầu | ❌ Không được |
| **User đã login** | Xem được 100% trang | Phải thỏa Quota (PBI-16) |
| **Chủ nhân file** | Xem được 100% trang | Luôn được, không tính Quota |

---

## 4. Schema Database đầy đủ

Chạy script sau trên Supabase (các cột/bảng chưa có):

```sql
-- Các cột mới trong bảng documents
ALTER TABLE documents ADD COLUMN title VARCHAR(255);
ALTER TABLE documents ADD COLUMN description TEXT;
ALTER TABLE documents ADD COLUMN download_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE documents ADD COLUMN page_count INT NOT NULL DEFAULT 0;
ALTER TABLE documents ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'READY';

-- Bảng ghi lịch sử tải xuống (cho quota)
CREATE TABLE document_downloads (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       INT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_id   UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    downloaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX doc_downloads_user_idx ON document_downloads(user_id);
CREATE INDEX doc_downloads_doc_idx  ON document_downloads(document_id);

-- Tags (nếu chưa có)
CREATE TABLE tags (id SERIAL PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL);
CREATE TABLE document_tags (
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    tag_id INT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);

-- Full Text Search (Vietnamese)
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text LANGUAGE sql IMMUTABLE STRICT AS
$$ SELECT public.unaccent('unaccent', $1); $$;
ALTER TABLE documents
ADD COLUMN fts tsvector GENERATED ALWAYS AS (
    to_tsvector('simple', f_unaccent(coalesce(title,'')) || ' ' ||
                           f_unaccent(coalesce(description,'')) || ' ' ||
                           f_unaccent(original_name))
) STORED;
CREATE INDEX documents_fts_idx ON documents USING GIN (fts);
```

---

## 5. API Reference nhanh

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `GET` | `/api/documents` | Public | Danh sách tài liệu (hỗ trợ search FTS) |
| `GET` | `/api/documents/{id}/metadata` | Public | Chi tiết metadata (có `pageCount`, `processingStatus`) |
| `GET` | `/api/documents/{id}/pages/{n}` | Public* | Xem trang thứ n dưới dạng ảnh. Chưa login → 50% |
| `GET` | `/api/documents/{id}` | Auth + Quota | Tải file PDF gốc (check uploads >= downloads) |
| `GET` | `/api/documents/my` | Auth | Danh sách tài liệu của bản thân |
| `POST` | `/api/documents/upload` | Auth | Upload PDF (chỉ PDF, tự động convert sang ảnh ngầm) |
| `PUT` | `/api/documents/{id}/replace` | Auth Owner | Thay thế file PDF |
| `PATCH` | `/api/documents/{id}/rename` | Auth Owner | Đổi tên |
| `PATCH` | `/api/documents/{id}/visibility` | Auth Owner | Ẩn/Hiện |
| `DELETE` | `/api/documents/{id}` | Auth Owner | Xóa |

---

## 6. Lưu ý khi Deploy & Test

**⚠️ Trước khi chạy:**
1. Chạy toàn bộ SQL ở Mục 4 lên Supabase Dashboard.
2. S3 Bucket **phải có CORS** cho phép trình duyệt fetch ảnh từ S3.

**Test luồng PDF Preview bằng Postman:**
```
1. POST /api/auth/login → lấy JWT token
2. POST /api/documents/upload (form-data: file = yourfile.pdf)
   → Lưu lại {id} từ response
3. Đợi ~5 giây (ngầm đang convert ảnh)
4. GET /api/documents/{id}/metadata
   → Kiểm tra processing_status == "READY", pageCount > 0
5. GET /api/documents/{id}/pages/1
   → Sẽ redirect 302 sang link ảnh S3 → Copy link xem trong trình duyệt
6. GET /api/documents/{id}/pages/{pageCount/2 + 1} (không dùng token)
   → Phải nhận 401 "Cần đăng nhập để đọc toàn bộ"
```

**Test luồng Quota tải xuống:**
```
1. Đăng nhập với user CHƯA upload bài nào
2. GET /api/documents/{id} (dùng token đó)
   → Phải nhận 403 "Cần đóng góp ít nhất bằng số bài đã tải"
3. Upload 1 bài → thử lại GET /api/documents/{id}
   → Thành công, nhận được link tải PDF gốc
4. Tải bài thứ 2 → phải 403 lần nữa (đã dùng hết 1 quota)
5. Upload thêm bài → lại tải được
```
