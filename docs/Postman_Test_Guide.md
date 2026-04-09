# Hướng dẫn Kiểm tra Postman cho Document APIs

Tài liệu này dùng để bạn dễ dàng test các chức năng PBI-16, PBI-009, và PBI-010 đã được hiện thực trên Postman.

## 1. Tìm kiếm Tài liệu (PBI-009) & Lọc theo Tags (PBI-010)

Vì là "Khách", chức năng tìm kiếm không yêu cầu `Authorization Header`.
Endpoint này trỏ tới API Public lấy danh sách (phân trang).

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents`
- **Params (Query):**
  - `q`: Từ khóa tìm kiếm (Ví dụ: `  toán học  ` - Có cả khoảng trắng dư thừa ở hai đầu hoặc ở giữa để test cắt trắng). API sẽ quét trên `title`, `description` và `originalName`.
  - `tags`: Một hoặc nhiều thẻ (Ví dụ: `Math`, hoặc `Math,Physics`, v.v..)
  - `page`: `0` (Mặc định)
  - `size`: `10` (Mặc định)

**Cách cấu hình Postman:**
1. Chọn `GET`.
2. Gõ `http://localhost:8080/api/documents?q=toán học&tags=Math,Physics`.
3. Bấm **Send**.
4. **Kết quả mong đợi:** Mã `200 OK`, JSON trả về danh sách các tài liệu rỗng hoặc chính xác kết quả có chứa tag "Math" hoặc "Physics" và có chữ "toán học". Thông tin trả về sẽ có array `"tags": ["Math", "Physics"]`.

---

## 2. Kiểm tra chặn truy cập chức năng Download (PBI-16)

Trường hợp test bắt buộc Download trên hệ thống đối với file của một ID cụ thể. Chọn 1 mã `id` UUID đang có trên database của bạn, ví dụ: `d9b2d63d-a233-4123-8ff8-e6bbaaabcdef`.

### Kịch bản 2.1: Khách chưa cấu hình Đăng nhập (Chưa có Token)
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents/{id}`
- **Headers:** KHÔNG gửi kèm `Authorization`.
- **Kết quả mong đợi:** 
  - Mã Status: `401 Unauthorized`
  - Body: 
    ```json
    {
      "statusCode": 401,
      "message": "Khách chưa login: Yêu cầu Đăng nhập"
    }
    ```

### Kịch bản 2.2: Đã đăng nhập nhưng **chưa** từng đóng góp (Chưa upload tài liệu nào)
- **Chuẩn bị:** Cần 1 tài khoản mới tinh (số count document = 0).
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents/{id}`
- **Headers:** 
  - Thêm `Authorization` -> Type: `Bearer Token` -> Điền chuỗi Token của User vừa tạo nhưng chưa từng Update File.
- **Kết quả mong đợi:** 
  - Mã Status: `403 Forbidden`
  - Body: 
    ```json
    {
      "statusCode": 403,
      "message": "Bạn cần đóng góp ít nhất 1 tài liệu để mở khóa tính năng tải về"
    }
    ```

### Kịch bản 2.3: Đã đăng nhập & **Đã upload** >= 1 tài liệu hợp lệ
- **Chuẩn bị:** Dùng Token của một User ĐÃ có lịch sử upload file thành công.
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents/{id}`
- **Headers:** 
  - `Authorization`: `Bearer <token>`
- **Kết quả mong đợi:** 
  - Mã Status: `200 OK`
  - Body: Trả về link AWS/Supabase S3 hợp lệ.
    ```json
    {
      "statusCode": 200,
      "message": "Document URL generated successfully",
      "data": {
        "url": "https://sxiyrycwruzxwpqehdmo.storage.supabase.co/storage/v1/s3/... (pre-signed url)"
      }
    }
    ```
