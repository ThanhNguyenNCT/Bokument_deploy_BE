# API Documentation (BoKument)

Đây là tài liệu đơn giản tra cứu toàn bộ các Endpoint đang có trong dự án, bao gồm mô tả công dụng, Request (Body/Param) và Response trả về. Chú ý: Các API đánh dấu **[Auth]** yêu cầu phải gắn `Authorization: Bearer <token>` trên Header.

---

## 1. Authentication (Xác thực)

### 1.1 Đăng ký tài khoản (Register) [Public]
- **Method:** `POST /api/auth/register`
- **Công dụng:** Khởi tạo tài khoản dùng cho khách để có thể post bài.
- **Request Body (JSON):**
  ```json
  {
    "username": "user123",
    "email": "user@example.com",
    "password": "my_password"
  }
  ```
- **Response (200 OK):** Trả về chuỗi JWT Token.
  ```json
  {
    "statusCode": 200,
    "message": "Registration successful",
    "data": "ey..." // Token dùng gọi API khác
  }
  ```

### 1.2 Đăng nhập (Login) [Public]
- **Method:** `POST /api/auth/login`
- **Công dụng:** Cấp Token cho user lấy quyền tương tác.
- **Request Body (JSON):**
  ```json
  {
    "username": "user123",
    "password": "my_password"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "statusCode": 200,
    "message": "Login successful",
    "data": "ey..."
  }
  ```

---

## 2. Document (Quản lý Tài liệu)

### 2.1 Lấy danh sách tài liệu công khai (Explore/Search) [Public]
- **Method:** `GET /api/documents`
- **Công dụng:** Tìm kiếm và hiển thị tài liệu đang public (Phục vụ khách xem).
- **Request Params:**
  - `page` (int, default: 0): Số trang 
  - `size` (int, default: 10): Số phần tử 
  - `q` (string): Tìm bằng Full Text Search (Gõ có/không dấu tuỳ ý).
  - `tags` (string list): Tag cách nhau bởi thẻ phẩy (`Math,Physics`)
  - `contentType` (string): Lọc MIME phụ, ví dụ `application/pdf`
- **Response (200 OK):**
  ```json
  {
    "statusCode": 200,
    "message": "Documents retrieved successfully",
    "data": {
       "items": [
          {
             "id": "abc-...",
             "title": "Tên sách",
             "tags": ["Math"],
             "size": 1024,
             "ownerId": 5
             // ...
          }
       ],
       "page": 0,
       "totalPages": 5
    }
  }
  ```

### 2.2 Xem Metadata (Chi tiết tài liệu) [Public]
- **Method:** `GET /api/documents/{id}/metadata`
- **Công dụng:** Đọc thông tin mô tả chi tiết của 1 tài liệu (Mọi loại khách đều xem được).
- **Response (200 OK):** Trả về JSON chứa metadata chi tiết của File.

### 2.3 Xem kho tài liệu của TÔI [Auth]
- **Method:** `GET /api/documents/my`
- **Công dụng:** Liệt kê toàn bộ file do user hiện tại đăng tải (cả ẩn và public).
- **Request Params:** Tương tự `2.1` nhưng có thêm `visible=true/false` để tùy chỉnh.
- **Response (200 OK):** JSON chứa `items[]` tương tự 2.1.

### 2.4 Cấp link S3 Tải Về MỘT tài liệu PBI-16 [Auth]
- **Method:** `GET /api/documents/{id}`
- **Công dụng:** Cấp link Pre-signed S3 tải về 1 tài liệu bất kỳ. Khách **phải Login & Up ít nhất 1 bài** mới được tải.
- **Response (200 OK):** 
  ```json
  { "statusCode": 200, "message": "Document URL...", "data": { "url": "https://s3.supabase.co..." } }
  ```
- **Response Error Lỗi (401 / 403):** Nếu chưa login hoặc chưa có đóng góp, API từ chối cấp URL.

### 2.5 Thay đổi trạng thái Ẩn/Hiện [Auth]
- **Method:** `PUT /api/documents/{id}/visibility`
- **Công dụng:** Chuyển trạng thái Private/Public cho tài liệu của bản thân.
- **Request Body (JSON):** `{ "visible": false }`
- **Response:** Mã 200 kèm Text "Document is now hidden".

### 2.6 Đổi tên File [Auth]
- **Method:** `PUT /api/documents/{id}/rename`
- **Request Body (JSON):** `{ "newName": "Bản báo cáo Final.pdf" }`

### 2.7 Tải tài liệu của mình (URL đọc trực tiếp) [Auth] 
- **Method:** `GET /api/documents/{id}/download-url`
- **Công dụng:** Dành riêng cho chủ nhân file tự lấy Link đọc bài riêng tư của mình (Không bị kiểm tra PBI-16).

### 2.8 Upload 1 File lên Server [Auth]
- **Method:** `POST /api/documents`
- **Công dụng:** Upload file lên hệ thống.
- **Request (Multipart Form-data):** 
  - Key: `file` -> Value: (Chọn file thực tế)
- **Response:** Thông tin ID mới tạo.

### 2.9 Thay thế bản cứng (Replace File) [Auth]
- **Method:** `PUT /api/documents/{id}/replace`
- **Công dụng:** Úp đè 1 file sửa đổi nhưng vẫn giữ nguyên ID, Tag cũ của file cũ.
- **Request (Multipart Form-data):** Giống `2.8`

### 2.10 Xóa tài liệu [Auth]
- **Method:** `DELETE /api/documents/{id}`
- **Công dụng:** Xóa hẳn file khỏi Database và S3 Cloud Storage.
