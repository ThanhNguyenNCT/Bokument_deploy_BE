# KE HOACH DIRECT UPLOAD PDF (PRESIGNED URL) - CAP NHAT THUC TE 2026-04-08

## 0) Trang thai thuc hien sau khi duyet

- [DONE] Step 1 (BE): Da tao 3 DTO con thieu de sua clean build:
  - `InitUploadRequestDTO`
  - `CompleteUploadRequestDTO`
  - `InitUploadResponse`
- [DONE] FE + BE dong bo flow direct upload:
  - FE da chuyen upload tu multipart sang `init-upload -> PUT signed URL -> complete-upload`.
- [DONE] Build/Typecheck:
  - `BE: mvnw clean -DskipTests compile` pass.
  - `FE: npm run typecheck` pass.
- [DONE] Cleanup legacy (BE + FE):
  - Da xoa `DocumentResponse.java` (DTO cu khong con duoc su dung).
  - Da xoa `DocumentValidator.java` (validator multipart cu khong con duoc goi).
  - Da xoa API FE `replaceDocument(...)` va bo export tu `services/api/index.ts`.
- [DONE] 8.1 (JWT): Da migrate `JwtUtil` sang API JJWT 0.13 khong deprecated.
- [DONE] 8.3 (Magic bytes):
  - BE da re-check header `%PDF-` tai `complete-upload`.
  - Sai size/magic-bytes se hard-delete object + row draft.
  - FE da bo sung precheck magic-bytes local truoc upload.
- [DONE] 8.4 (Stale cleanup):
  - Da them scheduler cleanup dinh ky.
  - Rule A: hard-delete `UPLOADING` treo.
  - Rule B: doi `PROCESSING` treo thanh `FAILED_PREVIEW`.
- [PARTIAL] 8.6 (FE error handling + BE contract):
  - DONE: Contract loi BE da chot va da code (`message` = errorCode, `data` = user-facing message).
  - DONE: FE da uu tien hien `data`, dung `message` cho mot so branch logic (`UPLOAD_SIGNED_URL_EXPIRED`).
  - PENDING: Toi uu them branch UI chi tiet cho toan bo ma loi va badge/polling rieng cho phase E.
- [DONE] 8.7 (Runtime issue analysis + fix): Da implement 2 huong uu tien (Loi A + Loi B) va compile pass.
- [DONE] Muc 10 -> 12 da duoc hien thuc trong code (quota unlock popup, search tags+rating, owner username tren card).
- [DONE] Verify sau khi hien thuc muc 10 -> 12:
  - `BE: mvnw clean -DskipTests compile` pass.
  - `FE: npm run typecheck` pass.
- [DONE] Muc 16 da hoan tat: tach route owner management (`/my-document`) va route reader rieng (`/read/{id}`).
- [DONE] Muc 17 da hoan tat: fix luong dang xuat (xoa session va quay ve trang chu locale, khong redirect sai host/port).
- [DONE] Muc 18: upload chi cho chon tag co san (khong cho nhap tag tu do).
- [DONE] Muc 19: ke hoach upload nhanh >1000 PDF sample data bang script tu dong + tags ngau nhien.
- [DEFER] 8.5 (Automated tests): Giu nguyen quyet dinh chua implement test tu dong trong dot nay.

> Luu y: Muc 1 -> Muc 5 la baseline ghi nhan truoc khi hien thuc; Muc 6 -> Muc 8 la trang thai cap nhat moi nhat sau khi da code va verify.

## 1) Hien trang da xac minh tu code BE

### File da doc
- `src/main/java/com/qldapm_L01/backend_api/Service/DocumentStorageService.java`
- `src/main/java/com/qldapm_L01/backend_api/Service/PdfConversionService.java`
- `src/main/java/com/qldapm_L01/backend_api/Controller/DocumentController.java`
- `src/main/java/com/qldapm_L01/backend_api/Entity/Document.java`
- `src/main/java/com/qldapm_L01/backend_api/Repository/DocumentRepository.java`
- `src/main/java/com/qldapm_L01/backend_api/Config/S3Config.java`
- `src/main/resources/application.yaml`
- `src/main/java/com/qldapm_L01/backend_api/Util/DocumentValidator.java`
- `src/main/java/com/qldapm_L01/backend_api/DTO/DocumentResponse.java`
- `docs/DB.sql`

### Ket qua doi chieu voi muc tieu
- Luong direct upload da ton tai trong code BE:
  - `POST /api/documents/init-upload`
  - `POST /api/documents/complete-upload`
  - `DocumentStorageService.initUpload(...)` + `completeUpload(...)`
  - `PdfConversionService.downloadAndProcess(...)` da dung temp file.
- Cac endpoint khac nhu list/metadata/download/rename/delete van dang ton tai.
- Endpoint upload multipart cu va replace multipart KHONG con trong `DocumentController`.

## 2) Bat thuong phat hien (can duyet truoc khi code)

### Bat thuong A - Clean build dang FAIL do thieu DTO source files (CRITICAL)
- Chay `mvnw clean -DskipTests compile` cho ket qua fail.
- Nguyen nhan: 3 class duoc import nhung KHONG co source file trong `src/main/java/.../DTO`:
  - `InitUploadRequestDTO`
  - `CompleteUploadRequestDTO`
  - `InitUploadResponse`
- Luu y: `mvnw -DskipTests compile` (khong clean) co the "BUILD SUCCESS" do class stale trong `target/`.

### Bat thuong B - FE chua dong bo voi contract direct upload
- FE hien con goi multipart cu trong `FE/src/services/api/document.ts`:
  - `uploadDocument(...)` -> `POST /documents` voi `multipart/form-data`
  - `replaceDocument(...)` -> `PUT /documents/{id}/replace`
- BE hien da theo huong presigned (`init-upload` + `complete-upload`), nen FE upload hien tai se khong dung contract.

### Bat thuong C - Legacy code chua duoc don dep
- `src/main/java/com/qldapm_L01/backend_api/DTO/DocumentResponse.java` dang khong duoc su dung, model cu (Long id, fileName, storagePath...).
- `src/main/java/com/qldapm_L01/backend_api/Util/DocumentValidator.java` dang khong duoc su dung (chi con file don le).

### Bat thuong D - Quy tac gioi han dung luong o init
- Hien tai init request KHONG co field kich thuoc file, nen BE khong the hard-check size tai `init-upload`.
- Hard-check size dang duoc thuc hien dung cho server tai `completeUpload` qua `HeadObject` + DB CHECK (`size <= 20MB`).
- FE da co pre-check local `MAX_FILE_SIZE = 20MB`.

## 3) Ke hoach sua code sau khi duyet

### Step 1 - Khoi phuc clean build (bat buoc)
- Tao moi 3 DTO source files dung package `com.qldapm_L01.backend_api.DTO`:
  - `InitUploadRequestDTO` (title, tags, description, fileName, ext)
  - `CompleteUploadRequestDTO` (documentId)
  - `InitUploadResponse` (documentId, uploadUrl, objectKey)
- Muc tieu: `mvnw clean -DskipTests compile` pass.

### Step 2 - Xac minh flow direct upload tren BE
- Verify compile + endpoint contract giu nguyen nhu code dang huong toi.
- Khong dong vao cac endpoint list/metadata/download/rename/delete.

### Step 3 - Cleanup nhe (neu duoc duyet)
- Loai bo hoac danh dau deprecate:
  - `DocumentResponse.java`
  - `DocumentValidator.java`

### Step 4 - Dong bo FE theo contract moi (tach thanh scope rieng neu can)
- Doi upload tu multipart sang 2 buoc:
  1. `init-upload` de lay presigned PUT URL
  2. PUT raw PDF len S3
  3. `complete-upload` de kick off processing

## 4) Cac diem can ban duyet truoc khi code

1. Xac nhan cho phep minh code ngay Step 1 (tao 3 DTO de sua clean build fail)?
2. Xac nhan giu chien luoc size check nhu sau:
   - FE pre-check 20MB
   - BE hard-check tai `complete-upload` (khong them size vao init request)
3. Pham vi lan nay chi BE hay gom ca FE dong bo direct upload?

## 5) Tieu chi xac nhan hoan thanh
- `mvnw clean -DskipTests compile` pass on BE.
- `init-upload` tra ve `documentId`, `uploadUrl`, `objectKey`.
- `complete-upload` chuyen `UPLOADING -> PROCESSING` va goi async process.
- Cac endpoint list/metadata/download/rename/delete khong bi anh huong.

## 6) Cap nhat sau khi hien thuc cleanup legacy (2026-04-07)

### Nhung gi da lam
- BE:
  - Tao bo DTO direct upload can thiet: `InitUploadRequestDTO`, `CompleteUploadRequestDTO`, `InitUploadResponse`.
  - Sua `PdfConversionService`: bo goi `setProcessedPages(...)` vi `Document` entity hien tai khong co field tuong ung.
  - Xoa file legacy khong con dung:
   - `src/main/java/com/qldapm_L01/backend_api/DTO/DocumentResponse.java`
   - `src/main/java/com/qldapm_L01/backend_api/Util/DocumentValidator.java`
- FE:
  - Chuyen upload sang flow presigned: `init-upload -> PUT signed URL -> complete-upload`.
  - Xoa ham legacy `replaceDocument(...)` trong `src/services/api/document.ts`.
  - Xoa export `replaceDocument` trong `src/services/api/index.ts`.

### Kiem tra sau cleanup
- `BE: mvnw clean -DskipTests compile` -> `BUILD SUCCESS`.
- `FE: npm run typecheck` -> `EXIT_CODE=0`.

## 7) Van de can theo doi tiep (sau dot 2026-04-08)
Ghi chu hien trang:
- Code hien tai da dung `UPLOADING/PROCESSING/RENDERING_PREVIEW/READY/FAILED_PREVIEW`.
- `RENDERING_PREVIEW` van dang duoc giu de hien thi trang thai trung gian khi render tat ca page.

Flow upload de xuat:
1. FE precheck local truoc khi goi API.
2. FE goi `POST /documents/init-upload`.
3. FE PUT file vao signed URL.
4. FE goi `POST /documents/complete-upload`.
5. BE validate object + size + magic bytes.
6. Neu pass: chuyen `PROCESSING`, chay render async.
7. Render thanh cong: `READY`; render loi: `FAILED_PREVIEW`.

Tra loi cau hoi 1: "Sao khong check truoc roi moi load?"
- Co check truoc o FE, va nen tang cuong them check magic bytes tai FE de fail fast.
- Tuy nhien FE check KHONG du tin cay (client co the bi bypass), nen BE van bat buoc check lai sau upload.
- Vi vay huong dung la: check truoc o FE de UX nhanh + check lai o BE de bao mat/tinh dung.

Tra loi cau hoi 2: "Neu pass magic bytes ma render loi co handle chua?"
- Hien tai: CO handle. `PdfConversionService` co `catch` va set status `FAILED_PREVIEW`.
- Chua day du theo nghiep vu moi vi:
  - chua luu `error_message`/retry attempts.
  - chua co timeout watchdog cho `PROCESSING` treo.
- Ke hoach tiep: giu file goc de user van tai duoc, bo sung thong tin loi cho truy vet.

Khi nao client co the KHONG goi `complete-upload`:
- `Client` khong chi FE web, ma la moi ben goi API (FE web, mobile, script, Postman, service khac).
- Cac case thuc te:
  - User dong tab/refresh/roi trang khi dang upload.
  - PUT fail (het han URL, network, CORS/proxy, 403/5xx).
  - JS loi sau PUT truoc khi goi complete.
  - Script ngoai chi PUT file ma bo qua complete.

Tac dong neu khong cleanup:
- Record treo `UPLOADING` lau, DB ban va co nguy co lam nhiu thong ke/quota.

### 8.3 [DONE] Ke hoach kiem tra magic bytes PDF (byte dau file)

Muc tieu:
- Khong chi tin vao ten file/duoi file; phai xac thuc noi dung object tren storage thuc su la PDF.

Vi tri thuc hien de xuat:
- Trong `DocumentStorageService.completeUpload(...)`, sau buoc `HeadObject` va check dung luong.

Thuat toan de xuat:
1. Goi `GetObject` voi `Range: bytes=0-7` de lay 8 byte dau.
2. Doc byte stream, yeu cau toi thieu 5 byte.
3. Check chu ky PDF:
   - khuyen nghi strict: bat dau bang `%PDF-`
   - toi thieu: `%PDF`
4. Neu KHONG hop le:
   - De xuat mac dinh: xoa object S3 vua upload + xoa ban ghi document o DB.
   - Tra loi 400 voi message ro rang (`Uploaded file content is not a valid PDF`).
5. Neu hop le:
   - tiep tuc `PROCESSING` va goi async convert nhu hien tai.

Bo sung FE precheck (khong thay the BE check):
- FE doc 8 byte dau tu `File.slice(0, 8)` va check `%PDF-` truoc khi goi `init-upload`.
- Muc dich: fail fast, giam upload rac, tiet kiem bang thong.

Ly do chon huong xoa object + row khi sai magic bytes:
- Tranh de lai rac storage/DB.
- Tranh tinh nham vao thong ke upload/quota.
- Giu he thong sach va de van hanh.

### 8.4 [DONE] Ke hoach cleanup tai lieu treo (`UPLOADING` + `PROCESSING`)

Muc tieu:
- Tu dong don dep document treo khi client bo qua `complete-upload`.

Huong thuc hien de xuat:
- Them scheduler job chay moi 10-15 phut.
- Binh thuong can bat `@EnableScheduling` (hien tai moi co `@EnableAsync`).

Rule A - Doi voi `UPLOADING` treo:
- Dieu kien: `processing_status='UPLOADING'` va `created_at < now() - 30 phut`.
- Xu ly:
  - Kiem tra object tren storage.
  - Neu co: xoa object.
  - Xoa row document LUON (hard delete), khong doi trang thai tam.

Rule B - Doi voi `PROCESSING` treo:
- Dieu kien: `processing_status='PROCESSING'` va moc thoi gian cap nhat da qua 30 phut.
- Xu ly:
  - Chuyen status `FAILED_PREVIEW`.
  - Giu file goc.

### 8.5 [DEFER] Pham vi kiem thu da chot

Quyet dinh da duyet:
- Khong lam test tu dong trong pham vi implement dot nay.
- Kiem thu se do tester thuc hien va kiem tra manual truc tiep tren web.

Tac dong:
- Ke hoach code se tap trung vao logic runtime (validation + cleanup + status flow).
- Khi can hardening sau MVP, se mo lai scope test tu dong o dot sau.

### 8.6 [PARTIAL] Ke hoach handle loi cho FE + contract exception tu BE

Muc tieu:
- FE phai hien thong bao ro rang cho user theo tung tinh huong loi, khong chi hien 1 toast chung chung.
- BE tra ve loi co ma loi (errorCode) on dinh de FE map UI message nhat quan.

#### 8.6.1 De xuat contract loi tu BE

Huong de xuat da chot (giu nguyen `BaseResponse`, don gian hoa payload loi):
- `statusCode`: http status
- `message`: MA LOI on dinh (errorCode) de FE map logic
- `data`: message BE muon FE hien truc tiep tren UI (user-facing message)

Quy uoc khi tra loi:
- Response loi:
  - `message` = ma loi (vi du: `UPLOAD_MAGIC_BYTES_INVALID`)
  - `data` = thong diep hien cho user (vi du: `Noi dung file khong phai PDF hop le`)
- Response thanh cong:
  - giu nguyen payload object nhu hien tai.

Vi du ma loi de xuat:
- `UPLOAD_INIT_INVALID_INPUT`
- `UPLOAD_SIGNED_URL_EXPIRED`
- `UPLOAD_OBJECT_NOT_FOUND`
- `UPLOAD_SIZE_EXCEEDED`
- `UPLOAD_MAGIC_BYTES_INVALID`
- `UPLOAD_STATUS_INVALID`
- `UPLOAD_PROCESSING_FAILED_PREVIEW`
- `UPLOAD_PROCESSING_TIMEOUT`

#### 8.6.2 Exception moi de xet them trong BE

Co the tao cac exception runtime chuyen biet:
- `UploadValidationException`
- `UploadObjectNotFoundException`
- `UploadSizeExceededException`
- `UploadMagicBytesException`
- `UploadInvalidStateException`
- `PreviewProcessingException`

`CentralException` se map tung exception ->
- `statusCode` = http status
- `message` = errorCode
- `data` = userMessage

Map HTTP de xuat:
- 400: input sai, size vuot, magic bytes sai, status khong hop le
- 404: object khong ton tai tren storage khi complete
- 409: conflict trang thai (da complete/khong con UPLOADING)
- 410: signed URL het han (neu detect duoc)
- 500: loi he thong khong xac dinh

#### 8.6.3 FE UI handling theo tung phase

Phase A - FE precheck local truoc init:
- Loi ext/size/magic-bytes local -> hien inline duoi input file + toast ngan.
- Khong goi API neu fail.

Nguyen tac doc loi tu API o FE:
- Uu tien hien `data` (message user-facing).
- Dung `message` (errorCode) de branch logic UI (retry/re-init/chuyen trang thai).

Phase B - `init-upload` fail:
- Hien thong bao tu `data`, xu ly logic theo `message`.
- Cho phep user sua thong tin va submit lai.

Phase C - PUT signed URL fail:
- 403/401 hoac URL het han -> hien "Link tai len het han, vui long thu lai" + nut "Tao link moi" (goi lai init-upload).
- 5xx/network -> hien "Loi mang/Storage" + nut "Thu lai".

Phase D - `complete-upload` fail:
- `UPLOAD_OBJECT_NOT_FOUND` -> thong bao "File chua tai len thanh cong".
- `UPLOAD_SIZE_EXCEEDED` -> thong bao "File vuot 20MB, da duoc huy".
- `UPLOAD_MAGIC_BYTES_INVALID` -> thong bao "Noi dung file khong phai PDF hop le".
- `UPLOAD_STATUS_INVALID` -> thong bao "Tai lieu khong con o trang thai cho complete".

Phase E - processing/render:
- Neu status `PROCESSING`: hien badge "Dang xu ly" va polling metadata.
- Neu status `FAILED_PREVIEW`: hien canh bao "Preview loi" nhung van cho tai file goc.

#### 8.6.4 Kich ban loi can cover tren FE (manual)

1. User dong tab giua luc upload.
2. PUT fail do het han signed URL.
3. PUT xong nhung complete khong goi duoc (network/JS loi).
4. File doi duoi `.pdf` nhung khong phai PDF that.
5. File PDF hop le nhung render preview loi.

Tung kich ban can co:
- thong bao user nhin thay duoc
- hanh dong tiep theo ro rang (`Thu lai`, `Tai len lai`, `Tai file goc`)
- khong de UI "im lang".

### 8.7 [DONE] Phan tich loi runtime tu log + huong khac phuc

Trang thai thuc hien:
- [DONE] Loi A da sua theo huong uu tien: bo `ResponseTransformer.toFile(...)`, doc stream truc tiep tu S3 roi `PDDocument.load(...)`.
- [DONE] Loi B da sua theo huong uu tien: bo `copyObject` trong `rename(...)`, chi luu `originalName` o DB va tiep tuc dung `responseContentDisposition` khi download.
- [DONE] Verify build: `mvnw clean -DskipTests compile` -> `BUILD SUCCESS`.

#### 8.7.1 Loi A - PDF processing fail du S3 tra 200 OK

Bang chung tu log:
- `SdkClientException -> NonRetryableException -> IOException -> FileAlreadyExistsException`
- Loi xay ra tai luong `PdfConversionService.downloadAndProcess(...)` khi goi `ResponseTransformer.toFile(...)`.

Nguyen nhan goc:
- Code hien tai tao san file tam bang `File.createTempFile(...)`.
- Sau do lai dung `ResponseTransformer.toFile(tempFile.toPath())` de ghi vao chinh duong dan da ton tai.
- SDK sync transformer mong file dich CHUA ton tai, nen throw `FileAlreadyExistsException`.

Tac dong:
- Tai lieu hop le van bi day sang `FAILED_PREVIEW`.
- User upload thanh cong nhung preview that bai.

Huong khac phuc de xuat (uu tien):
1. Doc stream truc tiep tu S3, bo qua luu file tam tren disk:
  - `InputStream s3ObjStream = s3Client.getObject(getObjectRequest)`
  - `PDDocument.load(s3ObjStream)`
2. Loai bo dependency vao temp file cho flow render all pages de tranh va cham file tren Windows.

Phuong an thay the (neu van muon luu disk):
- Khong dung path da ton tai voi `toFile`; can tao path moi chua ton tai truoc khi ghi.

Checklist verify sau fix:
1. Upload PDF hop le, log khong con `FileAlreadyExistsException`.
2. Trang thai chuyen dung: `PROCESSING -> RENDERING_PREVIEW -> READY`.
3. Preview page load duoc tren FE.

#### 8.7.2 Loi B - Rename fail voi `S3Exception 403 signature mismatch`

Bang chung tu log:
- `S3Exception: The request signature we calculated does not match the signature you provided (403)`.
- Xuat hien sau luong rename (`PUT /documents/{id}/rename`) tai doan `s3Client.copyObject(copyReq)`.

Nguyen nhan kha nang cao:
- Lenh self-copy de update metadata (`contentDisposition`) khong tuong thich hoan toan voi backend S3-compatible hien tai (ky ten cho `copy-source`/region/endpoint).

Tac dong:
- Upload thanh cong nhung buoc rename fail.
- FE hien thong bao: "Tai tep len thanh cong nhung doi ten tieu de that bai".

Huong khac phuc de xuat (uu tien):
1. Bo `copyObject` trong `rename(...)`, chi cap nhat `originalName` o DB.
2. Giu ten file tai xuong thong qua `responseContentDisposition` trong API download (da dang duoc set theo `originalName`).

Huong hardening (tuy chon, lam sau):
- Neu bat buoc phai update metadata tren object, can xu ly lai signing/copy-source theo dac thu provider va bo sung error mapping rieng cho rename.

Checklist verify sau fix:
1. Goi rename tra 200 on dinh.
2. List/metadata hien ten moi.
3. Download URL tra ve ten moi qua `responseContentDisposition`.
4. Log khong con 403 signature mismatch o luong rename.

#### 8.7.3 Thu tu uu tien thuc hien de xuat

1. Fix Loi A (preview processing) truoc vi anh huong truc tiep den tinh nang doc tai lieu.
2. Fix Loi B (rename) sau, theo huong bo `copyObject` de giam rui ro.
3. Chay lai compile + test manual upload/preview/rename/download theo checklist tren.

## 9) Tong hop hien trang quota (doc code FE + BE truoc khi code)

### 9.1 BE - Luong upload/download va quota dang co

Endpoint va hanh vi da xac minh:
- `GET /api/documents/{id}` (DocumentController.getDocument):
  - Bat buoc dang nhap. Neu khong dang nhap, controller tra 401 som.
  - Goi `DocumentStorageService.createDownloadUrlForRead(...)` de check quota va tao signed URL.
- `GET /api/documents/{id}/download-url` (DocumentController.getDownloadUrl):
  - Chi chu so huu moi duoc goi.
  - Khong check quota dong gop.
- Chua thay endpoint `GET /api/documents/my/quota` trong `DocumentController`.

Logic quota hien tai trong `DocumentStorageService.createDownloadUrlForRead(...)`:
- Chay trong transaction `SERIALIZABLE`.
- Tinh so lieu:
  - `uploads = documentRepository.countByOwnerId(requesterId)`
  - `downloads = downloadRepository.countByUserId(requesterId)`
- Dieu kien chan:
  - Neu `uploads <= downloads` -> nem `AccessDeniedException` (map thanh 403 boi `CentralException`).
- Neu du quota:
  - Insert 1 dong vao `document_downloads`.
  - Tang `documents.download_count` bang query atomic `incrementDownloadCount`.
  - Tao signed URL GET voi `responseContentDisposition` theo `originalName`.

Nguon du lieu quota hien tai:
- Upload counter: `documents` theo owner (`countByOwnerId`) - khong loc theo status/visibility.
- Download counter: `document_downloads` theo user (`countByUserId`).

Nhan xet quan trong:
- Vi `init-upload` tao row `UPLOADING` ngay tu dau, `countByOwnerId` hien tai se tinh ca draft dang treo.
- Scheduler co cleanup draft treo, nhung trong cua so thoi gian truoc cleanup, draft van co the lam tang upload credit.

### 9.2 FE - Luong upload/download/quota dang su dung

Tai `FE/src/services/api/document.ts`:
- `getDocument(id)` goi `GET documents/{id}` (duong co check quota).
- `getDownloadUrl(id)` goi `GET documents/{id}/download-url` (duong owner-only, hien chua duoc UI goi).
- `getMyQuota()` goi `GET documents/my/quota`.

Tai `FE/src/components/sidebar/sidebar.tsx`:
- `QuotaWidget` goi query key `['my-quota']`, staleTime 30s.
- UI hien `uploads`, `downloads`, `remaining`, `canh bao het quota` dua tren du lieu query.

Tai `FE/src/components/Menu/Menu.tsx`:
- Sau upload success: invalidate `['my-quota']`.
- Sau delete success: invalidate `['my-quota']`.

Tai `FE/src/components/Menu/DocumentDetail.tsx`:
- Nut download hien tai luon goi `getDocument(id)` cho moi user.
- Chua co nhanh owner dung `getDownloadUrl(id)`.
- Khi loi 403 dang uu tien toast `error.response.data.message` (hien tai la errorCode), chua uu tien `data` user-facing message theo contract 8.6.
- Sau download success chua invalidate `['my-quota']`.

### 9.3 Lech FE/BE va rui ro can chot truoc khi code quota

1. FE dang goi `documents/my/quota` nhung BE chua co endpoint tuong ung trong source da doc.
2. FE dang co ham owner-download (`getDownloadUrl`) nhung UI khong dung, dan den owner co the bi tru quota neu tai qua `getDocument`.
3. Cong thuc upload credit BE dang la `countByOwnerId` (all documents), co the tinh ca row `UPLOADING`/`PROCESSING`/`FAILED_PREVIEW`.
4. Contract loi da chot (`message`=errorCode, `data`=user message) chua duoc `DocumentDetail` doc dung o nhanh loi download.
5. `FE/messages/en.json` hien chua co nhom key quota (`quota_label`, `quota_uploaded`, ...), trong khi sidebar dang goi cac key nay.

### 9.4 De xuat scope hien thuc quota (cho buoc code tiep theo)

BE:
1. Chuyen quota source sang bang `users`:
  - Giu `users.upload_count` lam counter upload.
  - Them cot moi `users.download_count` (NOT NULL DEFAULT 0, CHECK >= 0).
2. Bo sung migration + backfill 1 lan:
  - backfill `upload_count` tu bang `documents` (neu can dong bo lai).
  - backfill `download_count` tu bang `document_downloads`.
3. Map counter vao BE model/repository:
  - them field `uploadCount`, `downloadCount` trong `User` entity.
  - them query tang counter theo SQL atomic (`SET col = col + 1`).
4. Sua `createDownloadUrlForRead` de doc counter tu `users` thay vi `countBy...`:
  - check quota bang `upload_count` va `download_count`.
  - neu pass thi tang `users.download_count` + insert `document_downloads` + tang `documents.download_count` trong cung transaction.
5. Upload flow: tang `users.upload_count` tai `complete-upload` sau khi object da pass size + magic bytes.
6. Toan ven du lieu:
  - su dung transaction cho cac buoc check + increment.
  - dam bao idempotent de khong cong 2 lan khi retry (dua tren status guard `UPLOADING -> PROCESSING`).
7. Them endpoint `GET /api/documents/my/quota` doc truc tiep tu `users`:
  - payload: `uploads`, `downloads`, `canDownload`.

FE:
1. `DocumentDetail`: neu nguoi dung la owner thi dung `getDownloadUrl`, nguoc lai dung `getDocument`.
2. `DocumentDetail`: xu ly loi theo contract 8.6 (uu tien hien `data`, dung `message` de branch).
3. `DocumentDetail`: invalidate `['my-quota']` sau download success.
4. Bo sung key quota con thieu trong `messages/en.json` de tranh loi i18n.
5. Sidebar: can nhac them fallback UI khi query quota loi (thay vi ngam dinh 0).

### 9.5 Tieu chi verify cho dot quota (sau khi code)

1. `GET /api/documents/my/quota` tra 200 va doc du lieu tu `users.upload_count`/`users.download_count`.
2. Khi `complete-upload` thanh cong, `users.upload_count` tang dung 1 lan.
3. Khi download qua luong quota (`GET /documents/{id}`), `users.download_count` tang dung 1 lan.
4. Quota check va increment download chay atomically trong transaction (khong vuot quota khi request dong thoi).
5. User owner tai file cua chinh minh qua owner endpoint khong bi tru quota.
6. FE widget quota cap nhat dung sau upload/delete/download.
7. i18n khong con missing key lien quan quota o locale `en`.

### 9.6 BE quota hien tai (snapshot 2026-04-08)

Ly do hien tai van "dem lai" bang `countBy...`:
1. Code BE dang chua map `users.upload_count` vao `User` entity.
2. DB hien tai chua co `users.download_count` de doc counter download truc tiep.
3. Vi vay service dang tam thoi dem runtime tu `documents` va `document_downloads`.

Nhung gi BE da co:
1. Quota dang duoc enforce trong `DocumentStorageService.createDownloadUrlForRead(...)`, duoc goi boi `GET /api/documents/{id}`.
2. Transaction quota dung `Isolation.SERIALIZABLE` de giam race condition khi download dong thoi.
3. Cong thuc hien tai:
  - `uploads = documentRepository.countByOwnerId(userId)`
  - `downloads = documentDownloadRepository.countByUserId(userId)`
  - Chan khi `uploads <= downloads` (nem `AccessDeniedException` -> HTTP 403).
4. Neu qua duoc quota check:
  - Insert 1 record vao `document_downloads`.
  - Tang `documents.download_count` qua query atomic `incrementDownloadCount(...)`.
  - Tra signed GET URL voi `responseContentDisposition` theo `originalName`.
5. Duong owner `GET /api/documents/{id}/download-url` hien khong ap quota dong gop.

Nhung gi BE chua co:
1. Chua co endpoint `GET /api/documents/my/quota` de FE lay so lieu quota truc tiep.
2. Chua co response model quota rieng (`uploads`, `downloads`, `canDownload`) o BE API.

Luu y nghiep vu hien tai:
1. Upload credit dang dua tren `countByOwnerId` (dem documents theo owner), nen co the tinh ca row dang o trang thai draft/xu ly neu chua duoc cleanup.
2. Quy tac chan download hien tai la "da upload phai lon hon so da download" (vi neu bang nhau thi bi chan cho lan tiep theo).

### 9.7 Flow cap nhat counter BE (de duyet truoc khi code)

1. Upload counter (`users.upload_count`):
  - Trigger: `POST /api/documents/complete-upload` thanh cong (sau khi pass size + magic bytes).
  - Hanh dong: `upload_count = upload_count + 1` trong cung transaction voi cap nhat status document.
2. Download counter (`users.download_count`):
  - Trigger: `GET /api/documents/{id}` khi da login va pass quota.
  - Hanh dong: check `upload_count > download_count`, sau do tang `download_count = download_count + 1` trong cung transaction.
3. Luong owner download (`GET /api/documents/{id}/download-url`):
  - Khong ap quota va khong tang `download_count` (giu nghiep vu owner-only).
4. Yeu cau toan ven:
  - check + increment trong mot transaction.
  - SQL increment atomic, khong read-modify-write o app memory.
  - neu transaction fail thi rollback toan bo increment/insert lien quan.

### 9.8 [DONE] Cap nhat sau khi hien thuc (2026-04-08)

BE da hien thuc:
1. Them map counter vao `User` entity:
  - `upload_count` -> `uploadCount`
  - `download_count` -> `downloadCount`
2. Them methods atomic trong `UserRepository`:
  - `incrementUploadCount(...)`
  - `incrementDownloadCount(...)`
  - `findByIdForUpdate(...)` (pessimistic lock)
3. `completeUpload(...)` da tang `users.upload_count` sau khi pass size + magic bytes.
4. Them endpoint `GET /api/documents/my/quota` (tra `uploads`, `downloads`, `canDownload`).
5. `createDownloadUrlForRead(...)` da chuyen sang doc counter user thay vi `countBy...`.
6. `createDownloadUrlForRead(...)` da tang `users.download_count` atomic khi pass quota.
7. Owner download da khong bi tru quota trong luong contribution-gated.
8. Van giu insert vao `document_downloads` + tang `documents.download_count` de phuc vu lich su va thong ke tai lieu.

FE da hien thuc:
1. `DocumentDetail` uu tien goi owner endpoint `getDownloadUrl(id)`, fallback sang `getDocument(id)` cho non-owner.
2. Sau download success da invalidate `['my-quota']`.
3. Error handling download uu tien hien `response.data.data` (user-facing message).

DB docs/migration:
1. `docs/DB.sql` da bo sung `users.download_count`.
2. Them script migration/backfill: `docs/migrations/2026-04-08-add-users-download-count.sql`.

Build/Typecheck:
1. `BE: mvnw clean -DskipTests compile` -> `BUILD SUCCESS`.
2. `FE: npm run typecheck` -> `EXIT_CODE=0`.

## 10) Ke hoach uu tien 1 - Quota khong du thi mo popup upload, upload READY xong tu dong download

### 10.0 Thu tu thuc hien (bat buoc)

1. Hoan tat muc 10 truoc.
2. Sau khi muc 10 on dinh moi bat dau muc 11 (search/rating).

### 10.1 Hien trang BE/FE sau khi doc lai

BE hien co:
1. Quota check tai `GET /api/documents/{id}` trong `createDownloadUrlForRead(...)`.
2. Neu khong du quota thi throw `AccessDeniedException` -> `CentralException` map `403`, `message=ACCESS_DENIED`, `data=<user message>`.
3. Upload flow direct da co (`init-upload -> PUT -> complete-upload`) va da co `processingStatus` (`PROCESSING/RENDERING_PREVIEW/READY/FAILED_PREVIEW`).
4. Da co endpoint `GET /api/documents/my/quota` (uploads, downloads, canDownload).

FE hien co:
1. Nut Download o `DocumentDetail` dang goi owner endpoint truoc, fallback sang endpoint contribution-gated.
2. Khi 403 hien tai chi toast loi; chua co popup upload de "mo khoa" quota.
3. FE da co upload UI + direct upload flow o `Menu` (co validate file, magic-bytes, error handling).
4. FE chua co flow "upload xong cho READY roi tu dong tai lai tai lieu dang xem".

### 10.2 Ke hoach hien thuc chi tiet

BE:
1. Chot contract loi quota khong du:
  - Doi sang `statusCode=403`, `message=QUOTA_INSUFFICIENT`, `data` la thong bao user-facing.
2. Bo sung endpoint metadata nho gon chi de get processing_staus
3. Khong doi rule quota hien tai (duoc tai khi `uploads > downloads`).

FE:
1. Trong `DocumentDetail`, khi user bam Download va nhan 403 do quota:
  - Mo popup Upload ngay tren trang detail (khong redirect qua trang khac).
2. Popup Upload:
  - Tai su dung logic upload tu `Menu` (`uploadDocument`, validate size/ext/magic-bytes).
  - Luu `pendingDownloadDocumentId` (id tai lieu user dang muon tai).
3. Sau upload thanh cong:
  - Poll metadata cua tai lieu vua upload den khi `processingStatus=READY` (interval 2-3s, timeout 2-5 phut).
  - Khi READY: tu dong goi lai flow download cho `pendingDownloadDocumentId`.
4. Xu ly fail/timeout:
  - Neu upload fail: hien loi trong popup, cho retry.
  - Neu polling timeout hoac `FAILED_PREVIEW`: hien thong bao + cho user download thu cong sau.
5. Dong bo cache:
  - Invalidate `['my-quota']`, `['documents']`, va metadata query lien quan sau upload/download.

### 10.3 Tieu chi nghiem thu

1. User khong du quota bam Download -> popup Upload xuat hien (khong chi toast loi).
2. Upload file hop le thanh cong, den luc tai lieu moi READY -> he thong tu dong tai tai lieu dang xem.
3. Khong auto-download neu upload fail hoac READY timeout.
4. Owner download van khong bi tru quota.
5. Build/typecheck van pass sau khi implement.

## 11) Ke hoach uu tien 2 - Tim kiem theo tags, tu khoa, va rating tai lieu

### 11.1 Hien trang BE/FE sau khi doc lai

BE hien co:
1. `DocumentRepository.searchVisible/searchByOwner` da ho tro `q` + `tags` (FTS + document_tags).
2. `TagController` da co `GET /api/tags` tra danh sach tag + so document public.
3. Chua co model/endpoint rating (khong co bang rating trong schema docs).

FE hien co:
1. Explore dang tim theo `q`, sau do con filter local theo ten file.
2. API client `getAllDocuments/getMyDocuments` chua truyen tham so `tags`.
3. Chua co UI chon tags de filter.
4. Chua co UI/API cho rating.

### 11.2 Scope A - Hoan thien tim kiem theo tags + tu khoa

BE:
1. Sua logic filter tags tu OR sang AND (tai lieu phai chua day du tat ca tags duoc chon).
  - Goi y query: gom nhom theo document va dung HAVING so tag match = tagsCount.
2. Giu tim kiem keyword case-insensitive o backend.
3. Bo sung test query cho case-insensitive va cac to hop keyword/tags hop le (khong focus query rong).

FE:
1. Mo rong params API:
  - `GetAllDocumentsParams` va `GetMyDocumentsParams` them `tags?: string[]`.
  - Truyen `tags` len query string.
2. Neu user khong nhap keyword va khong chon tag nao thi KHONG goi API tim kiem.
  - Hien state mac dinh/huong dan tren UI thay vi query backend.
3. Tai su dung API lay tags da co `GET /api/tags` (khong tao endpoint BE moi).
  - Neu FE chua co ham client thi bo sung ham goi endpoint nay; neu da co thi tai su dung.
4. Them UI filter tags:
  - Multi-select tag chips/checkbox o Explore (va co the o Library).
  - Khi doi tags hoac keyword -> refetch danh sach tu server.
5. Bo filter local trung lap khong can thiet de tranh sai ket qua server.

### 11.3 Scope B - Them rating tai lieu + tim kiem theo rating

DB + BE model:
1. Tao bang `document_ratings`:
  - cot: `id`, `document_id`, `user_id`, `rating` (1..5), `created_at`, `updated_at`.
  - unique `(document_id, user_id)` de moi user chi danh gia 1 lan/tai lieu.
2. Them aggregate vao `documents` (de query nhanh):
  - `rating_avg` (numeric), `rating_count` (int).
3. Chi ho tro danh gia theo sao nguyen (1, 2, 3, 4, 5), khong ho tro muc le.
4. Bo sung thong ke phan bo rating 5->1 sao (breakdown) tu `document_ratings` de hien tren trang detail.

BE API:
1. `POST /api/documents/{id}/rating`:
  - upsert rating user.
  - cap nhat aggregate `rating_avg`, `rating_count` trong transaction.
2. `GET /api/documents/{id}/rating`:
  - tra `avg`, `count`, `myRating`, `breakdown` (so luot theo tung muc 5,4,3,2,1 sao).
3. Mo rong endpoint metadata + list tai lieu:
  - `GET /api/documents/{id}/metadata` tra them `rating_avg`, `rating_count`.
  - `GET /api/documents` va `GET /api/documents/my` tra them `rating_avg`, `rating_count` tren moi item.
4. Mo rong endpoint list:
  - them filter `minRating` (va/hoac `maxRating`).
  - them sort theo rating (`rating_desc`).

Toan ven du lieu rating:
1. Upsert rating + cap nhat aggregate trong cung transaction.
2. Rang buoc CHECK `rating BETWEEN 1 AND 5`.
3. Index can thiet: `(document_id)`, `(user_id)`, `(rating_avg)`.

FE rating:
1. Explore/Library:
  - hien `rating_avg` + `rating_count` tren card, vi tri cot ben phai, dung truoc owner username.
2. DocumentDetail:
  - them block rating cung theme FE, hien diem trung binh + tong luot danh gia.
  - user login danh gia bang cach chon sao nguyen (1..5) va co the sua lai.
  - ve thanh phan bo theo tung muc 5,4,3,2,1 sao (cung theme voi FE).
3. Explore/Library:
  - them filter min-rating.

### 11.4 Tieu chi nghiem thu

1. Khi keyword rong va khong co tag duoc chon, FE khong goi API tim kiem.
2. Tim kiem tags theo logic AND tra dung ket qua backend (public va my-docs).
3. User danh gia duoc tai lieu (tao moi/sua), aggregate `rating_avg`/`rating_count` cap nhat dung.
4. Endpoint metadata/list tra du rating de FE hien tren card (cot ben phai, truoc owner).
5. Trang detail hien block rating (diem trung binh + tong luot danh gia), user chon sao nguyen 1..5.
6. API detail rating tra breakdown 5->1 sao dung du lieu va UI detail hien dung phan bo 5->1 sao.
7. Filter/sort theo rating hoat dong dung va khong phat sinh race/inconsistent aggregate khi rating dong thoi.
8. Build/typecheck pass sau khi implement.

### 11.5 Thu tu thuc hien de xuat sau khi xong muc 10

1. Lam xong Scope A (tags + keyword FE) truoc vi BE da co san.
2. Sau do moi mo Scope B (rating end-to-end DB/BE/FE).

## 12) Ke hoach bo sung username owner tren moi card

### 12.1 Muc tieu

1. Them 1 cot `username` trong bang `documents` de luu ten owner cua tai lieu.
2. BE tra ve thong tin username owner trong cac API list/metadata.
3. FE hien username owner o goc duoi ben phai tren moi document card.

### 12.2 Pham vi BE

DB + migration:
1. Them cot `username` vao `documents` (NOT NULL, default rong de an toan migration).
2. Backfill du lieu 1 lan tu `users.username` theo khoa `documents.owner_id = users.id`.
3. Bo sung cap nhat schema trong `docs/DB.sql` + tao file migration moi trong `docs/migrations`.

Entity + service + response:
1. Map field `username` trong `Document` entity.
2. Tai `initUpload(...)` (hoac `completeUpload(...)`) gan `doc.username` theo username owner hien tai.
3. Mo rong payload list/metadata de FE nhan duoc truong username owner.
4. Khong thay doi logic quota/download hien tai.

### 12.3 Pham vi FE

Data model + map API:
1. Them truong `username` (owner username) vao model document trong `services/api/document.ts`.
2. Cap nhat mapper list + metadata de doc du lieu username tu BE.

UI card:
1. Hien owner username tren card o goc duoi ben phai (uu tien card o Explore va Library/My Documents).
2. Dinh dang hien thi de nhin ro nhung khong lan at title/noi dung chinh (vd: label `Owner: <username>`).
3. Truong hop username trong/rong thi fallback `Unknown owner`.

### 12.4 Dong bo du lieu khi user doi username

1. Chot theo huong luu snapshot tren `documents.username` de list nhanh.
2. Khi co flow doi username user, bo sung job/update SQL dong bo lai tat ca document cua user do.
3. Neu dot nay chua lam flow doi username thi ghi ro la dong bo se duoc xu ly o dot profile/account tiep theo.

### 12.5 Tieu chi nghiem thu

1. Migration chay thanh cong, du lieu cu duoc backfill dung username owner.
2. API list/metadata tra ve du `username` cho tung tai lieu.
3. Moi card tren FE hien owner username o goc duoi ben phai dung yeu cau UI.
4. Build/typecheck BE + FE van pass sau khi implement.

## 13) Cap nhat ket qua da lam (muc 10 -> 12) - 2026-04-08

### 13.1 [DONE] Muc 10 - Quota khong du -> mo popup upload -> READY -> auto download

BE da lam:
1. Chot ma loi quota rieng: `QUOTA_INSUFFICIENT` (HTTP 403) trong `CentralException`.
2. Luong download contribution-gated tiep tuc enforce quota o `GET /api/documents/{id}`.

FE da lam:
1. `DocumentDetail` da co popup upload khi gap loi quota khong du.
2. Popup da dung luong direct upload (chon file -> upload -> complete).
3. Sau upload thanh cong, FE poll metadata tai lieu moi cho den `READY`.
4. Khi `READY`, FE tu dong goi lai download cho tai lieu dang xem.
5. Da co xu ly retry/cancel cho case `FAILED_PREVIEW` hoac timeout polling.
6. Da invalidate cache quota/documents sau cac hanh dong lien quan.

### 13.2 [DONE] Muc 11 - Search theo tags/keyword/rating + rating end-to-end

Search/tags da lam:
1. BE doi logic tags tu OR sang AND (tai lieu phai match day du tags duoc chon).
2. FE da truyen `tags`, `minRating`, `maxRating`, `sort` vao list APIs.
3. FE Explore da tai su dung API tags (`GET /api/tags`) va bo filter local trung lap.
4. FE khong goi API search khi khong co keyword/tag/rating criteria.

Rating da lam:
1. DB: them bang `document_ratings` + bo sung aggregate `documents.rating_avg`, `documents.rating_count`.
2. BE: them API
  - `GET /api/documents/{id}/rating`
  - `POST /api/documents/{id}/rating`
3. BE list/metadata da tra them `ratingAvg`, `ratingCount`.
4. BE co breakdown 5->1 sao cho trang detail.
5. FE card (Explore/Library) da hien rating aggregate.
6. FE detail da hien block rating + user rate bang sao nguyen 1..5.

### 13.3 [DONE] Muc 12 - Owner username snapshot va hien thi tren card

1. DB/docs: bo sung cot `documents.username` va migration tuong ung.
2. BE: snapshot username owner khi tao upload draft, tra ve trong list/metadata.
3. FE: map truong `username` trong model API.
4. FE card da hien owner o goc duoi ben phai theo format fallback khi can.

### 13.4 Ket qua xac minh ky thuat sau khi code

1. `BE: mvnw clean -DskipTests compile` -> `BUILD SUCCESS`.
2. `FE: npm run typecheck` -> `EXIT_CODE=0`.

### 13.5 Viec con theo doi (manual/runtime)

1. Chay QA manual tren browser cho day du nhanh timeout/failed/retry trong popup quota unlock.
2. Apply migration DB tren moi truong thuc te va verify schema voi du lieu that.

## 14) [PARTIAL] Cap nhat yeu cau trang Explore (2026-04-08)

### 14.1 Yeu cau thay doi so voi ke hoach cu

1. Explore phai hien tat ca tai lieu public ngay khi vao trang (khong de trang thai trong khi chua nhap keyword/tag).
2. Can fake data rating cho cac tai lieu da co trong DB de demo du lieu thuc te.
3. Tren Explore can co khu vuc Top 5 theo rating.
4. Them nhom button sort theo:
  - ngay upload
  - so rating
  - luot tai

Luu y thay doi pham vi:
1. Muc 11.2 (rule "khong goi API khi keyword/tag rong") duoc dieu chinh thanh:
  - Khi vao Explore: load danh sach mac dinh.
  - Khi nguoi dung de trong thanh tim kiem va bam Enter: khong goi them API search.

### 14.2 Hien trang (theo UI hien tai)

1. Explore da load danh sach public mac dinh ngay khi vao trang.
2. Neu input tim kiem rong va bam Enter thi khong phat sinh request search bo sung.

### 14.3 Ke hoach implement BE

1. Mo rong sort option cho API list public (`GET /api/documents`):
  - `created_desc` (mac dinh)
  - `created_asc` (neu can)
  - `rating_count_desc`
  - `download_desc`
  - giu `rating_desc` cho top rating
2. Chuan hoa tie-breaker khi sort rating:
  - `rating_avg DESC`, sau do `rating_count DESC`, sau do `download_count DESC`, sau do `created_at DESC`.
3. Khong can endpoint moi cho Top 5: FE goi lai endpoint list voi `size=5`, `sort=rating_desc`.

### 14.4 Ke hoach seed fake rating data (DB)

1. Tao script seed cho moi truong dev/demo, khong dung migration production.
2. De xuat duong dan: `BE/docs/seeds/2026-04-08-fake-document-ratings.sql`.
3. Noi dung seed:
  - Chen rating cho cac document da co bang cach map nhieu user vao moi document.
  - Dam bao unique `(document_id, user_id)` (dung `ON CONFLICT` update/bo qua).
  - Gia tri rating chi nam trong 1..5.
4. Sau seed, dong bo lai aggregate `documents.rating_avg`, `documents.rating_count`.
5. Script phai idempotent de chay lai khong gay duplicate.

### 14.5 Ke hoach implement FE (Explore)

1. Khi vao trang Explore:
  - goi API list public ngay lap tuc voi sort mac dinh `created_desc`.
  - van cho phep ket hop keyword/tag/rating khi user thao tac.
  - neu keyword rong va user bam Enter thi bo qua submit (khong goi them API).
2. Them block `Top 5 Rating` tren Explore:
  - query rieng `size=5`, `sort=rating_desc`.
  - card hien toi thieu: title, ratingAvg, ratingCount, downloadCount.
3. Them button sort tren Explore:
  - `Moi nhat` (created_desc)
  - `Nhieu danh gia` (rating_count_desc)
  - `Nhieu luot tai` (download_desc)
4. Khi bam sort:
  - cap nhat state sort
  - refetch list
  - hien trang thai active cho button dang chon.

### 14.6 Tieu chi nghiem thu de duyet

1. Vao Explore khi chua nhap gi van thay danh sach tai lieu public.
2. Neu de trong o tim kiem va bam Enter thi khong phat sinh request search moi.
3. Top 5 rating hien dung 5 tai lieu diem cao nhat theo quy tac sort da chot.
4. 3 nut sort hoat dong dung:
  - sort theo ngay upload
  - sort theo so rating
  - sort theo luot tai
5. Sau khi chay seed fake data, danh sach va Top 5 co du lieu rating de demo (khong toan 0).
6. `BE clean compile` va `FE typecheck` van pass sau khi implement.

### 14.7 Pham vi ngoai scope dot nay

1. Chua thay doi logic quota/download da on dinh o muc 10.
2. Chua mo rong them bo loc ngoai 3 tieu chi sort neu khong co yeu cau moi.

### 14.8 Ket qua implement va verify

1. BE da mo rong sort cho list public/my-docs: `created_desc`, `created_asc`, `rating_desc`, `rating_count_desc`, `download_desc`.
2. FE Explore da them block `Top 5 Rating` va 3 button sort (`Moi tai len`, `Nhieu danh gia`, `Nhieu luot tai`).
3. FE Explore da load danh sach mac dinh khi vao trang; Enter voi keyword rong khong tao request moi.
4. Da tao script seed fake rating: `BE/docs/seeds/2026-04-08-fake-document-ratings.sql` (idempotent, co dong bo aggregate).
5. Verify thanh cong:
  - `BE: mvnw clean -DskipTests compile` -> `BUILD SUCCESS`.
  - `FE: npm run typecheck` -> `EXIT_CODE=0`.

### 14.9 [DONE] Van de UAT moi: search bi "reload" theo tung ky tu + query ngan khong match

#### 14.9.1 Nguyen nhan xac dinh duoc

Van de A - Moi lan go 1 ky tu thi UI nhu bi reload:
1. FE dang bind `searchQuery` truc tiep vao `queryKey` va tham so API, nen moi lan `onChange` deu tao request moi.
2. FE dang `return <Loading />` khi `isLoading`, nen moi request moi se thay toan trang bang man hinh loading.
3. Ket qua la user cam nhan "reload trang" thay vi fetch ngam.

Van de B - Search `pr` khong ra tai lieu co tieu de `PRODUCT ...`:
1. BE dang dung FTS: `d.fts @@ websearch_to_tsquery('simple', unaccent(:q))`.
2. Kieu query nay match theo lexeme day du, khong phai prefix cho token ngan.
3. `pr` khong tu dong match `product`, nen ket qua rong du user ky vong prefix search.

#### 14.9.2 Cho sai trong implement hien tai

1. FE sai o cho dung live-query theo tung ky tu trong khi UX mong doi la khong giat/reload va khong spam request.
2. FE sai o cho su dung full-page loading (`isLoading`) cho refetch, lam mat danh sach hien tai.
3. BE sai o cho gia dinh FTS hien tai se bao phu ca case prefix ngan (`pr`), trong khi thuc te khong.

#### 14.9.3 Huong sua da implement

FE:
1. Chuyen sang realtime search theo debounce (~350ms):
  - user go den dau thi goi request ngầm den do sau khoang debounce.
2. Khi Enter voi input rong:
  - khong phat sinh request moi.
3. Khong dung full-page loading cho refetch:
  - giu danh sach cu tren man hinh.
  - hien loading nho bang `isFetching` (top bar/spinner nho).
4. Dung `placeholderData: previousData` de tranh nhay trang khi thay doi query.

BE:
1. Mo rong dieu kien search keyword de ho tro prefix:
  - giu FTS hien tai cho case token day du.
  - bo sung prefix fallback cho `title` va `original_name` (vd `ILIKE '%<q>%'` hoac prefix `%` theo rule chot).
2. Uu tien huong an de user go `pr` van tim thay `PRODUCT ...`.
3. Giu nguyen logic tags AND va filter rating hien co.

#### 14.9.4 Tieu chi nghiem thu sau sua

1. Go tung ky tu trong o tim kiem khong lam mat danh sach va khong hien full-page loading.
2. Search realtime duoc thuc hien ngầm theo debounce, khong giat UI.
3. Enter voi input rong khong tao request moi.
4. Search `pr` tra ve tai lieu co `PRODUCT` trong title/originalName.
5. Build/typecheck BE + FE van pass.

#### 14.9.5 Ket qua cap nhat thuc te

1. FE Explore da bo sung debounce query + `placeholderData` + loading nho `isFetching`.
2. BE query search da bo sung fallback substring (`ILIKE`) tren `title`, `description`, `original_name` ben canh FTS.

## 16) [DONE] Tach route quan ly owner va route doc tai lieu rieng

### 16.1 Yeu cau user

1. Doi URL khu quan ly tai lieu cua owner thanh `my-document`.
2. Khi bam card de vao doc detail, phai di den trang doc rieng, khong nam trong khu `my-document`.
3. Khu `my-document` chi dung de owner quan ly tai lieu cua minh.

### 16.2 Phan tich hien trang code

1. Route quan ly hien tai:
  - `FE/src/app/[locale]/(User)/library/page.tsx` -> render `MenuPage`.
2. Route detail hien tai:
  - `FE/src/app/[locale]/(User)/library/[id]/page.tsx` -> render `DocumentDetail`.
3. Van de chinh:
  - Detail dang nam duoi `/library/[id]` trong nhom `(User)` co sidebar, nen UX bi hieu la dang o trong khu quan ly owner.
  - Card o ca Explore va Menu dang link den `/${locale}/library/${id}`.
4. Cac diem lien quan can doi URL:
  - Header, sidebar, callback login, legacy redirects `menu -> library`.

### 16.3 Muc tieu kien truc sau khi sua

1. Route owner management:
  - `/${locale}/my-document` (giu sidebar + upload/rename/delete + quota nhu hien tai).
2. Route reader rieng:
  - `/${locale}/read/${id}` (trang doc/preview/download/rating, tach khoi khu management).
3. Luong dieu huong card:
  - Card o Explore -> `/${locale}/read/${id}`.
  - Card o My Document -> `/${locale}/read/${id}`.

### 16.4 Ke hoach implement FE

1. Tao route owner moi:
  - Tao `FE/src/app/[locale]/(User)/my-document/page.tsx` su dung lai `MenuPage`.
2. Chuyen route doc sang route rieng:
  - Tao `FE/src/app/[locale]/(Guest)/read/[id]/page.tsx` su dung `DocumentDetail`.
  - Tao layout route doc co header (tuong tu Explore) de khong dinh kem sidebar management.
3. Chuyen toan bo link vao detail sang route doc:
  - `Explore.tsx`: `/${locale}/library/${id}` -> `/${locale}/read/${id}`.
  - `Menu.tsx`: `/${locale}/library/${id}` -> `/${locale}/read/${id}`.
4. Doi URL quan ly trong navigation:
  - `header.tsx`: cac nut/library link -> `/${locale}/my-document`.
  - `sidebar.tsx`: toan bo hash/query link -> `/${locale}/my-document`.
  - `Menu.tsx` replace URL sau upload -> `/${locale}/my-document`.
5. Chinh route cu de giu backward compatibility:
  - `FE/src/app/[locale]/(User)/library/page.tsx` redirect sang `/${locale}/my-document`.
  - `FE/src/app/[locale]/(User)/library/[id]/page.tsx` redirect sang `/${locale}/read/${id}`.
  - `FE/src/app/[locale]/(User)/menu/page.tsx` redirect sang `/${locale}/my-document`.
  - `FE/src/app/[locale]/(User)/menu/[id]/page.tsx` redirect sang `/${locale}/read/${id}`.
6. Chinh thao tac back tren `DocumentDetail`:
  - Mac dinh quay ve `/${locale}/my-document` neu vao tu khu owner.
  - Co the cho phep quay ve route truoc do (neu co) de giu UX Explore.

### 16.5 Anh huong BE

1. Khong doi API backend.
2. Toan bo thay doi nam o routing/navigation FE.

### 16.6 Tieu chi nghiem thu

1. URL khu owner la `/${locale}/my-document`.
2. Bam card o Explore/My Document deu vao `/${locale}/read/${id}`.
3. Trang doc khong nam trong layout sidebar owner management.
4. Khu My Document van day du chuc nang quan ly: upload, rename, delete, xem danh sach.
5. Cac URL cu (`/library`, `/library/{id}`, `/menu`, `/menu/{id}`) van vao dung trang moi qua redirect.
6. `npm run typecheck` pass sau khi doi route.

### 16.7 Ket qua implement va verify

1. Da tao route owner moi: `/${locale}/my-document` (`FE/src/app/[locale]/(User)/my-document/page.tsx`).
2. Da tao route doc rieng: `/${locale}/read/${id}` trong nhom `(Guest)` voi layout header rieng.
3. Da doi link detail o Explore/My Document sang `/${locale}/read/${id}`.
4. Da doi navigation (header, sidebar, home contribute callback) sang `/${locale}/my-document`.
5. Da doi route cu sang redirect:
  - `/library` -> `/my-document`
  - `/library/{id}` -> `/read/{id}`
  - `/menu` -> `/my-document`
  - `/menu/{id}` -> `/read/{id}`
6. Da cap nhat back action trong `DocumentDetail` theo huong uu tien `router.back()`, fallback ve `/${locale}/my-document`.
7. Verify: `FE: npm run typecheck` -> `EXIT_CODE=0`.

## 17) [DONE] Fix dang xuat: xoa session va quay ve trang chu

### 17.1 Trieu chung

1. Khi bam dang xuat, he thong bi redirect sai sang host/port khac (vd `localhost:3000`) dan den `ERR_CONNECTION_REFUSED`.

### 17.2 Nguyen nhan

1. Logout flow su dung redirect callback cua next-auth theo URL khong phu hop moi truong dang chay.

### 17.3 Cach sua da ap dung

1. Tai `FE/src/components/header/header.tsx`:
  - goi `logoutLocal()` de xoa local auth data.
  - goi `invalidateSessionCache()` de clear session cache FE.
  - goi `signOut({ redirect: false })` de khong bi next-auth redirect sai origin.
  - sau do dieu huong chu dong ve `/${locale}` tren dung host hien tai.

### 17.4 Tieu chi nghiem thu

1. Bam dang xuat -> session bi xoa.
2. User duoc dua ve trang chu locale (`/${locale}`) cua dung host dang mo.
3. Khong con redirect sang host/port sai.
4. `FE: npm run typecheck` pass.

## 18) [DONE] Upload chi cho chon tag co san (khong nhap tu do)

### 18.1 Yeu cau

1. Tai form upload, truong `The` khong cho user tu nhap text.
2. User chi duoc chon tu danh sach tag co san trong he thong.
3. Ap dung dong bo cho ca:
  - popup upload trong My Document (`Menu.tsx`)
  - popup upload mo quota trong Doc Detail (`DocumentDetail.tsx`).

### 18.2 Hien trang code

1. `Menu.tsx` dang dung state `tags` kieu string va input text.
2. `DocumentDetail.tsx` dang dung `unlockTags` kieu string va input text.
3. Du lieu upload dang parse bang `split(',')`, nen cho phep tag tuy y.
4. FE da co API `getAllTags()` va da dung o Explore (co the tai su dung cho upload).

### 18.3 Muc tieu sau khi sua

1. Chuyen state tags o 2 form upload thanh `string[]` (multi-select).
2. Nguon tag options lay tu API `GET /api/tags`.
3. Payload upload chi gui cac tag duoc chon tu options co san.
4. Khong con input cho nhap tag tu do.

### 18.4 Ke hoach implement FE

1. `FE/src/components/Menu/Menu.tsx`:
  - fetch danh sach tags bang `getAllTags()`.
  - thay input text bang UI chon nhieu tags (chip toggle/checkbox list).
  - bo logic parse `split(',')`; submit truc tiep `selectedTags: string[]`.
  - reset state tags sau upload/clear ve mang rong `[]`.
2. `FE/src/components/Menu/DocumentDetail.tsx` (popup mo quota):
  - lam tuong tu: thay input text bang multi-select tu `getAllTags()`.
  - bo parse `split(',')`; submit truc tiep mang tags da chon.
3. UX states:
  - loading options: hien text/placeholder `Dang tai danh sach tag...`.
  - khong co tag nao: hien thong bao `Chua co tag san co`, van cho upload voi tags rong.
4. i18n:
  - bo sung key text cho nhan/chu thich multi-select tag (neu can), dam bao vi/en khong bi hardcode.

### 18.5 Anh huong BE

1. Khong can doi API BE (BE da nhan `List<String>` cho upload).
2. Dot nay chi thay doi FE UI/submit behavior de chan nhap tag tu do.

### 18.6 Tieu chi nghiem thu de duyet

1. Truong `The` trong 2 popup upload khong con la text input.
2. User chi chon duoc tag ton tai trong danh sach tra ve tu API.
3. Payload upload khong con tag do user go tuy y.
4. Upload van hoat dong binh thuong khi khong chon tag.
5. `FE: npm run typecheck` pass sau khi implement.

### 18.7 Ket qua implement va verify

1. `Menu.tsx` da bo text input cho truong the trong popup upload va thay bang multi-select tag chips tu `getAllTags()`.
2. `DocumentDetail.tsx` (popup mo quota) da bo text input cho truong the va thay bang multi-select tag chips tu `getAllTags()`.
3. Payload upload o ca 2 popup da gui truc tiep `string[]` tag duoc chon, khong con parse `split(',')` tu chuoi user nhap.
4. Da bo sung UX state cho danh sach tag:
  - loading: `Dang tai danh sach tag...`
  - empty: `Chua co tag san co.`
5. Verify: `FE: npm run typecheck` -> `EXIT_CODE=0`.

## 19) [DONE] Ke hoach bulk upload >1000 PDF (co log file, authenticated, tags random tu API)

### 19.0 Thong tin da chot voi user (de tranh quen)

1. Thu muc PDF nguon da chot:
  - `C:/Users/ASUS/Downloads/pdf_data/Pdf`
2. Tai khoan upload da chot:
  - username: `testuser`
  - password: `123456`
3. Yeu cau logging da chot:
  - Bat buoc ghi log qua trinh vao 1 file de theo doi.
4. Yeu cau tags da chot:
  - Lay full danh sach tags tu API `GET /api/tags`, sau do random tu pool nay.
5. Nguyen tac performance da chot:
  - Nut that thuong o backend/DB (Supabase), khong phai S3.
  - Can tach 3 tang: `S3 upload/download -> backend API -> DB`.
6. Base URL da chot:
  - `https://polydisperse-sallie-preliminarily.ngrok-free.dev`

### 19.1 Muc tieu

1. Upload nhanh 1000+ file PDF ma khong thao tac thu cong tren UI.
2. Moi file duoc gan tags ngau nhien chi tu danh sach tags hien co.
3. Co log file day du de theo doi tien do, loi, va resume.
4. Co co che retry va gioi han tai de tranh nghen backend/DB.

### 19.2 Ngon ngu script + kien truc doc lap

1. Ngon ngu script du kien:
  - Node.js JavaScript (ESM, file `.mjs`) de chay nhanh, khong can build TypeScript.
2. Script doc lap voi runtime cua FE/BE:
  - Khong import module noi bo cua FE/BE.
  - Chi goi HTTP API + ghi log.
3. Vi tri script du kien (theo yeu cau user):
  - Tao folder moi cung cap voi `FE` va `BE`: `bulk-uploader/`.
4. Luong xu ly giu dung flow hien tai:
  - `POST /api/documents/init-upload` (authenticated).
  - `PUT` len presigned URL (S3).
  - `POST /api/documents/complete-upload` (authenticated).
5. Backend tiep tuc chi xu ly metadata + trigger processing, khong "vac" byte file.

### 19.3 Cau hinh khoi dau an toan (theo de xuat user)

1. `MAX_S3_CONCURRENCY = 4`
2. `MAX_DB_CONCURRENCY = 2`
3. `DELAY_BETWEEN_DB_CALLS_MS = 300`
4. `RETRY_DELAYS_SEC = [1, 2, 4]` (co jitter nho)
5. `BATCH_SIZE = 10`
6. `PILOT_FILES = 20..30`

Giai thich:
1. S3 co the song song cao hon, nhung DB write phai it concurrent hon de tranh nghen pool connection.
2. Vi `init-upload` va `complete-upload` deu co ghi DB, 2 endpoint nay phai di qua lane `DB limiter`.

### 19.4 Luong chay theo batch de tranh nghen

1. Quet thu muc nguon, lay danh sach `*.pdf`.
2. Chia thanh batch, moi batch 10 file.
3. Trong moi batch:
  - Lane A (DB lane, concurrency=2 + delay 300ms): `init-upload`.
  - Lane B (S3 lane, concurrency=4): `PUT presigned URL`.
  - Lane C (DB lane, concurrency=2 + delay 300ms): `complete-upload`.
4. Batch nao co file fail thi danh dau vao log/report de retry sau, khong lam mat ket qua file da thanh cong.

### 19.5 Chien luoc retry

1. Retry cho loi tam thoi: HTTP `429`, `500`, `502`, `503`, `504`, network timeout/reset.
2. Backoff: `1s -> 2s -> 4s` + jitter ngau nhien nho (100-300ms).
3. Neu `PUT` bi `403/410` (signed URL het han):
  - Tu dong goi lai `init-upload` de lay URL moi.
  - Thu lai PUT + complete toi da 1 vong nua.
4. Neu gap `401` hang loat:
  - Dung batch an toan.
  - Yeu cau cap nhat auth, sau do resume.

### 19.6 Tags random tu API hien co

1. Goi `GET /api/tags` 1 lan dau job de lay tag pool.
2. Moi file random `k` tags (mac dinh 1..3), khong trung trong cung file.
3. Random mode: `balanced-random` (uu tien tag it xuat hien de phan bo deu hon).
4. Ho tro `--seed` de tai lap ket qua random khi can doi soat.
5. Neu tag pool rong: dung job som va ghi loi ro rang trong log file.

### 19.7 Authenticated upload (bat buoc)

1. Script dang nhap qua `POST /api/auth/login` bang tai khoan da chot (`testuser` / `123456`) de lay JWT.
2. Dung JWT Bearer cho:
  - `POST /api/documents/init-upload`
  - `POST /api/documents/complete-upload`
  - `GET /api/tags`
3. `PUT` len presigned URL khong can Bearer.
4. Khong hardcode credential trong source code; doc tu runtime env/args khi chay that.

### 19.8 Logging console + file theo doi (bat buoc)

1. Moi hanh dong deu log ra man hinh theo thoi gian thuc:
  - start/end job, start/end batch, start/end tung file, init/put/complete, retry, fail reason.
2. Dong thoi ghi vao 1 file log chinh:
  - `bulk-uploader/output/bulk-upload-<timestamp>.log`
3. Dinh dang log de xuat theo JSON line de de grep/phuc hoi:
  - `time`, `level`, `batch`, `file`, `step`, `status`, `httpStatus`, `errorCode`, `message`, `documentId`, `tags`.
4. Cuoi job ghi tong ket vao ca console va file:
  - `successCount`, `failedCount`, `durationMs`, `throughputFilesPerMin`.
5. Tu file log co the trich file fail de resume (`--resumeFromLog`).

### 19.9 Dau vao script sau khi da chot

1. `--sourceDir C:/Users/ASUS/Downloads/pdf_data/Pdf`
2. `--username testuser`
3. `--password 123456`
4. `--baseUrl https://polydisperse-sallie-preliminarily.ngrok-free.dev`
5. `--apiPrefix /api` (mac dinh). Neu base URL dang tro toi FE proxy thi cho phep `--apiPrefix /backend/api`.
6. `--maxS3Concurrency 4`
7. `--maxDbConcurrency 2`
8. `--dbDelayMs 300`
9. `--retryDelays 1,2,4`
10. `--batchSize 10`
11. `--minTags 1 --maxTags 3`
12. `--seed` (tuy chon)

### 19.10 Ke hoach trien khai

1. Giai doan 1: Viet script + dry-run check folder/auth/tags + tao log file.
2. Giai doan 2: Pilot 20-30 file voi cau hinh an toan (4/2/300ms), do throughput va ty le loi.
3. Giai doan 3: Chay full 1000+ file theo batch 10.
4. Giai doan 4: Neu fail, resume tu log file cho den khi dat muc tieu.

### 19.11 Tieu chi nghiem thu de duyet

1. Upload 1000+ file thanh cong ma khong thao tac tay tren UI.
2. Tat ca file upload dung flow `init -> PUT -> complete`.
3. Tat ca request can auth deu dung JWT hop le; khong co 401 hang loat.
4. Tags cua moi file deu thuoc pool tu `GET /api/tags`, khong co tag tu do.
5. Moi hanh dong quan trong deu duoc hien thi tren console theo thoi gian thuc.
6. Co 1 file log day du qua trinh, co tong ket va danh sach loi de resume.
7. Qua pilot khong xuat hien dau hieu nghen DB nghiem trong; neu co thi giam concurrency truoc khi full run.

### 19.12 Ket qua implement va verify

1. Da tao bo script doc lap o folder cung cap voi `FE` va `BE`:
  - `bulk-uploader/bulk-upload-sample-data.mjs`
  - `bulk-uploader/package.json`
  - `bulk-uploader/README.md`
  - `bulk-uploader/.gitignore`
2. Script da hien thuc day du flow direct upload:
  - `init-upload -> PUT presigned URL -> complete-upload`.
3. Script da hien thuc mo hinh gioi han tai theo ke hoach:
  - lane S3 (`maxS3Concurrency`) va lane DB (`maxDbConcurrency` + `dbDelayMs`).
4. Script da hien thuc auth bat buoc:
  - login qua `POST /api/auth/login`, dung JWT Bearer cho `init-upload`, `complete-upload`, `GET /api/tags`.
5. Script da hien thuc tags random tu API tags hien co:
  - lay full pool tu `GET /api/tags`, random theo `balanced-random`, co ho tro `--seed`.
6. Script da hien thuc logging theo yeu cau:
  - moi hanh dong log realtime ra console.
  - dong thoi ghi vao 1 file duy nhat tai `bulk-uploader/output/bulk-upload-<timestamp>.log`.
7. Verify da chay:
  - `node bulk-upload-sample-data.mjs --help` -> pass.
  - dry-run authenticated voi thong tin da chot (`sourceDir`, `baseUrl`, `testuser/123456`, `pilotFiles=30`) -> pass:
    - login thanh cong
    - load tags thanh cong
    - ket thuc o mode `dryRun` nhu mong doi.

## 20) [CHO DUYET] Ra soat phan trang list + sua seed rating ve dung 1..30

### 20.1 Ket qua doc code BE (phan trang list)

Ket luan:
1. BE co tra list theo page day du.

Bang chung:
1. `GET /api/documents` nhan `page`, `size` (`default 0/10`) va goi `storageService.listVisible(...)`.
2. `GET /api/documents/my` nhan `page`, `size` (`default 0/20`) va goi `storageService.listByOwner(...)`.
3. Service dung `PageRequest.of(page, size)` de query phan trang.
4. Response tra metadata phan trang day du qua `toPageData(...)`:
  - `items`
  - `page`
  - `size`
  - `totalItems`
  - `totalPages`

### 20.2 Ket qua doc code FE (hien thuc phan trang list)

Ket luan:
1. FE da gui tham so `page/size` khi goi API.
2. Nhung FE hien CHUA hien thuc UI phan trang cho list (next/prev/page number).
3. Hien tai dang lay page co dinh dau tien.

Bang chung:
1. `getAllDocuments(...)` va `getMyDocuments(...)` co gui `page`, `size`.
2. `Explore.tsx` dang goi co dinh `page: 0`, `size: 50`.
3. `Menu.tsx` dang goi co dinh `page: 0`, `size: 50`.
4. FE service dang map tra ve `data: items[]`, khong tra metadata `page/totalPages` ra component de render pagination control.

### 20.3 Nguyen nhan fake rating max chi den 9

Ket luan:
1. Script seed hien tai bi chan boi so user hien co trong DB.
2. Neu DB dang co 9 user thi moi document toi da chi co 9 rating.

Bang chung ky thuat:
1. Rang buoc DB: `document_ratings` co unique `(document_id, user_id)` -> 1 user chi rate 1 lan / document.
2. Script seed hien tai tinh muc tieu bang:
  - `LEAST(user_count, random_1_30)`
3. Nen max rating/doc = `user_count`.

### 20.4 De xuat sua de dap ung dung yeu cau 1..30

Muc tieu:
1. Moi document co so luot rating trong [1..30].
2. Moi rating van trong [1..5].

Ke hoach sua script seed:
1. Trước khi tao fake ratings, dam bao user pool co it nhat 30 user:
  - Neu thieu, chen them user seed (`seed_rating_user_01..30`) vao `public.users`.
  - Email/username dat prefix rieng de de truy vet va co the cleanup.
2. Dung user pool >= 30 de random chon dung `target_count` trong [1..30] cho moi document.
3. Giu nguyen upsert + dong bo aggregate `documents.rating_avg/rating_count` nhu hien tai.

Ghi chu:
1. Neu khong muon tao them user seed, thi khong the dam bao dung 1..30 voi schema hien tai (do unique theo user/doc).

### 20.5 Tieu chi verify sau khi sua seed

1. `MAX(count_per_document)` phai dat 30 (khi co document va co du user pool).
2. `MIN(count_per_document)` >= 1.
3. `MIN(rating) = 1`, `MAX(rating) = 5`.
4. `documents.rating_count` va `documents.rating_avg` khop voi du lieu trong `document_ratings`.

## 21) [DONE] Chot page size = 20 tren BE+FE + script dang ky user seed

### 21.1 Yeu cau user da chot

1. Chinh ca BE va FE de moi trang chi lay 20 items.
2. Viet script tao user seed bang cach goi API dang ky (khong insert truc tiep DB).
3. Luu script trong folder `bulk-upload` tai root workspace.
4. Quy uoc data user seed:
  - Username: `TestUser_1`, `TestUser_2`, ...
  - Email: `<Username>@gmail.com` (vi du: `TestUser_1@gmail.com`)
  - Password: `123456` cho tat ca.

### 21.2 Ke hoach BE (page size 20)

1. Endpoint `GET /api/documents`:
  - doi default `size` tu 10 -> 20.
2. Endpoint `GET /api/documents/my`:
  - giu default `size = 20` (da dung).
3. Service list (`listVisible`, `listByOwner`):
  - chot co dinh cung page-size = 20, bo qua gia tri `size` client gui len.
4. Cap nhat comment API trong `DocumentController` de dong bo voi page-size moi.

### 21.3 Ke hoach FE (page size 20 + phan trang)

1. Doi default size trong API client:
  - `getAllDocuments`: 10 -> 20.
  - `getMyDocuments`: giu 20.
2. Doi query tren component list:
  - `Explore.tsx`: `size: 50` -> `size: 20`.
  - `Menu.tsx`: `size: 50` -> `size: 20`.
3. Hien thuc phan trang UI cho list (de khong mac dinh page 0 mai):
  - them state `currentPage`.
  - them nut `Prev/Next` va hien `page/totalPages`.
  - khi doi filter/search thi reset ve page 0.
4. Mo rong FE service de tra ve metadata page (`page`, `size`, `totalItems`, `totalPages`) cho component dung.

### 21.4 Ke hoach script dang ky user seed (goi API)

1. Tao folder moi tai root:
  - `bulk-upload/`
2. Tao script:
  - `bulk-upload/register-seed-users.mjs`
3. Contract API script su dung:
  - `POST /api/auth/register`
  - payload: `{ username, email, password }`
4. Input CLI du kien:
  - `--baseUrl` (vi du ngrok URL)
  - `--apiPrefix` (mac dinh `/api`)
  - `--start` (mac dinh 1)
  - `--count` (so user can tao, mac dinh 30)
  - `--password` (mac dinh `123456`)
5. Rule sinh user:
  - username = `TestUser_${index}`
  - email = `${username}@gmail.com`
  - password = `123456` (hoac theo arg)
6. Error handling:
  - neu user da ton tai (`Username already exists`) thi danh dau `skipped` va tiep tuc.
  - script khong stop toan bo chi vi 1 user fail.
7. Logging:
  - console realtime tung user (`created/skipped/failed`).
  - ghi file log JSONL tai `bulk-upload/output/register-users-<timestamp>.log`.
  - tong ket cuoi: `createdCount`, `skippedCount`, `failedCount`.

### 21.5 Tieu chi nghiem thu de duyet

1. BE+FE dong bo page-size 20 cho list public va my-docs.
2. FE co UI phan trang va di duoc nhieu trang (khong khoa o page 0).
3. Script dang ky user seed chay doc lap bang API register, khong can truy cap DB truc tiep.
4. Sinh duoc lo user theo dung mau `TestUser_1..n`, email `@gmail.com`, password `123456`.
5. Co log file trong `bulk-upload/output` de doi soat ket qua tao user.

### 21.6 Ket qua implement va verify

1. BE da co dinh cung page-size = 20 tai service list:
  - `DocumentStorageService` dung `buildFixedListPageable(page)` va always `PageRequest.of(safePage, 20)` cho ca public/my-docs.
2. BE controller da doi default query `size` public ve 20 va cap nhat comment API list.
3. FE da doi list query ve 20 items/page:
  - `Explore.tsx`: `size 20` + state `currentPage` + UI `Prev/Next` + hien `page/totalPages`.
  - `Menu.tsx`: `size 20` + state `currentPage` + UI `Prev/Next` + hien `page/totalPages`.
4. FE API layer da tra metadata phan trang cho component:
  - `PaginatedItems<T>` gom `items/page/size/totalItems/totalPages`.
5. Da tao script dang ky user seed bang API o folder moi `bulk-upload`:
  - `bulk-upload/register-seed-users.mjs`
  - default tao 30 user (`--count` mac dinh 30)
  - username `TestUser_n`, email `${username}@gmail.com`, password `123456`.
6. Logging script:
  - log realtime console.
  - log file JSONL: `bulk-upload/output/register-users-<timestamp>.log`.
7. Verify ky thuat:
  - `BE: .\\mvnw.cmd -DskipTests compile` -> `BUILD SUCCESS`.
  - `FE: npm run typecheck` -> pass (`tsc --noEmit`).

## 22) [DONE] Cap nhat page size = 18 + Top 5 UI dang carousel

### 22.1 Yeu cau moi tu user

1. Moi trang list chi lay 18 item.
2. Top rating chi lay 5 tai lieu dau tien.
3. UI Top rating theo kieu 1 dai 5 card, tu dong truot, co nut dieu huong cho user.
4. Ghi ro ten pattern UI de user len mang tim mau.

### 22.2 Ten pattern UI de tim mau

1. Ten pho bien: `Carousel` / `Slider`.
2. Tu khoa de tim mau:
  - `Top 5 card carousel`
  - `Auto-play carousel with navigation arrows`
  - `Content slider 5 items`
  - `Swiper carousel 5 slides`

### 22.3 Pham vi code da cap nhat

BE:
1. `DocumentStorageService` doi hard-fixed page size tu 20 -> 18.
2. `DocumentController` doi default `size` public/my-docs ve 18 (de dong bo contract).

FE:
1. `Explore.tsx` doi page query size 18 va page model size 18.
2. `Menu.tsx` doi page query size 18 va page model size 18.
3. `services/api/document.ts` doi default/fallback size 18 cho list APIs.
4. `Explore.tsx` doi block `Top 5 Rating` tu list doc sang carousel:
  - chi lay `slice(0, 5)`
  - hien 1 dai 5 card
  - auto-play
  - co nut `Prev/Next` dieu huong.

### 22.4 Tieu chi nghiem thu

1. List public va my-docs hien 18 item/trang.
2. Top rating tren UI chi render 5 tai lieu dau tien.
3. Carousel top rating tu dong truot va user bam Prev/Next duoc.
4. `FE: npm run typecheck` pass sau cap nhat.

## 23) [DONE] Tinh chinh UI Carousel/Slider Top 5 Rating theo mau tham chieu

### 23.1 Yeu cau user da chot cho dot nay

1. Lam lai khu `Top 5 Rating` theo style carousel/slider nhu anh tham chieu.
2. Van giu logic `Top 5 Rating` (KHONG doi thanh tai lieu tai xuong nhieu nhat).
3. Card thong tin van giu nguyen theo du lieu project hien tai, khong doi nghiep vu.
4. Chi bo sung mau sac/nhan manh de noi bat nhu mau tham chieu.
5. Implement theo dung plan sau khi user duyet.

### 23.2 Ten pattern UI de tim mau

1. Ten pattern: `Carousel` / `Slider`.
2. Tu khoa de tim:
  - `top rated carousel ui`
  - `featured documents slider`
  - `card carousel with arrows`
  - `auto play carousel 3 cards`
  - `swiper coverflow cards` (neu can hieu ung manh hon)

### 23.3 Dinh huong UI cho project nay

1. Header block:
  - title lon ben trai: `TAI LIEU NOI BAT`.
  - subtitle ben canh: `TOP 5 RATING TUAN NAY`.
2. Vung carousel:
  - desktop uu tien hien 3 card cung luc, co mui ten Prev/Next ben phai.
  - tablet hien 2 card, mobile hien 1 card.
3. Card data:
  - giu thong tin hien co cua project (title, owner/username, ratingAvg, ratingCount, link detail).
  - khong them metric ngoai scope (vd: fake view/download K) neu project khong co field tuong ung.
4. Mau sac nhan manh:
  - moi card co 1 accent color (5 mau luan phien) o header/card-top.
  - badge rating giu de nhin ro diem.
  - nut dieu huong co style dam, de thay tren nen.
5. Chuyen dong:
  - auto-play theo chu ky (3-4 giay) + cho user bam Prev/Next.
  - pause auto-play khi hover/focus carousel (de UX tot hon).

### 23.4 Ke hoach implement

1. FE-only, tap trung tai `Explore.tsx` (khong can doi BE API vi da co `sort=rating_desc`).
2. Tach block top-rating thanh slider section rieng de de style va de maintain.
3. Lay data top 5 bang `slice(0, 5)` de dam bao dung yeu cau.
4. Them state/index cho carousel + interval auto-play + handler Prev/Next.
5. Them palette accent color cho 5 card, khong thay doi data contract.
6. Giu nguyen phan card list tai lieu ben duoi, khong doi nghiep vu pagination.

### 23.5 Tieu chi nghiem thu

1. Khu top-rating hien dung 5 tai lieu rating cao nhat.
2. UI co kieu carousel nhu mau: co dai card, auto truot, co nut Prev/Next.
3. Noi dung van dung theo project (khong chen metric fake khong co tren API).
4. Chi doi presentation/mau sac, khong doi nghiep vu rating.
5. Sau khi implement verify lai `npm run typecheck` va test manual tren desktop/mobile.

### 23.6 Ket qua implement va verify

1. Da implement slider Top 5 Rating tai `FE/src/components/Explore/Explore.tsx`:
  - giu logic top rating (`sort=rating_desc`) va chi hien 5 tai lieu dau tien (`slice(0, 5)`).
  - header dung dinh huong: `TAI LIEU NOI BAT` + `TOP 5 RATING TUAN NAY`.
2. Da ap dung layout carousel responsive:
  - desktop 3 card,
  - tablet 2 card,
  - mobile 1 card.
3. Da ap dung dieu huong va chuyen dong:
  - auto-play theo chu ky,
  - co nut Prev/Next,
  - pause auto-play khi hover/focus vung carousel.
4. Da giu card theo data project, khong dua metric fake:
  - title,
  - owner/username,
  - ratingAvg,
  - ratingCount,
  - link detail.
5. Da them mau accent luan phien cho tung card de noi bat block top-rating theo mau tham chieu.
6. Verify ky thuat:
  - `FE: npm run typecheck` -> pass (`tsc --noEmit`).

## 24) [CHO DUYET] Ke hoach hien thuc PBI-007, PBI-008, PBI-012, PBI-013 (Document + Rating + Comment)

### 24.1 Yeu cau nghiep vu can bo sung

1. PBI-007 (Edit document):
  - Chi owner duoc sua.
  - Truong duoc sua: `title`, `description`, `tags`.
  - Tags duoc chon theo kieu them/bot tu danh sach tag he thong (khong nhap tu do).
  - Sua thanh cong phai co thong bao.
2. PBI-008 (Delete document):
  - Chi owner hoac admin duoc xoa.
  - Bat buoc popup xac nhan truoc khi xoa.
  - Giu xoa cung (hard delete): xoa row DB va object storage.
3. PBI-012 (Rating):
  - Chi user login duoc rating.
  - 1 user chi co 1 rating / document (cho phep update).
  - Tu dong tinh lai diem trung binh.
  - Bat buoc chong race condition bang transaction/locking.
4. PBI-013 (Comment):
  - Chi user login duoc comment.
  - Noi dung khong duoc rong.
  - Hien username + thoi gian comment.
  - Trong moi box comment hien them rating.
  - Cho phep sua/xoa comment cua chinh minh.
  - Chi 1 cap comment (khong reply).
  - Comment dung soft delete.
5. Bo sung UI trang doc detail:
  - Hien `description` cua tai lieu tren trang detail.
  - Neu user hien tai la owner, hien nut `Sua` va `Xoa` ben trai trang detail.
  - Comment hien o cuoi trang detail.
  - User da dang nhap co khu vuc de danh gia va de lai comment.
  - User co the danh gia ma khong comment, nhung khong duoc comment neu chua danh gia tai lieu.

### 24.2 Ket qua doi chieu code hien tai FE + BE

PBI-007:
1. BE dang co `PUT /api/documents/{id}/rename` (chi doi `originalName`), chua co endpoint update `title/description/tags`.
2. FE (`Menu.tsx`) dang co modal sua ten file, chua co form edit metadata day du theo `title/description/tags`.
3. Chua co rule ro rang cho viec chon tags tu danh sach he thong trong luong edit metadata.

PBI-008:
1. FE da co popup xac nhan xoa trong `Menu.tsx`.
2. BE `DELETE /api/documents/{id}` hien dang hard delete row + object storage, chi owner xoa duoc.
3. Chua co role admin trong model user/security de cho phep admin xoa.

PBI-012:
1. BE da co `POST /api/documents/{id}/rating`, unique `(document_id,user_id)` trong `document_ratings`.
2. FE da co UI rating 1..5 tai `DocumentDetail.tsx`.
3. Chua co khoa/lock ro rang cho luong cap nhat aggregate rating khi request dong thoi -> co rui ro sai `rating_avg/rating_count`.

PBI-013:
1. Chua co entity/repository/service/controller comment o BE.
2. Chua co API client + UI comment o FE.
3. Trang detail hien chua co block description rieng va chua co action `Sua/Xoa` ben trai cho owner.
4. Chua co rang buoc UI/BE cho rule `khong comment neu chua rating`.

### 24.3 Ke hoach BE

#### 24.3.1 PBI-007 - Edit metadata document

1. Them endpoint moi:
  - `PUT /api/documents/{id}/metadata`
  - body: `{ title?: string, description?: string, tags?: string[] }`
2. Rule permission:
  - chi owner duoc update.
3. Validation:
  - title/description cho phep null hoac chuoi da trim.
  - tags la danh sach chuoi, bo phan tu rong, bo duplicate theo lowercase.
  - tags chi duoc phep nam trong danh sach tag he thong hien co; tag khong ton tai -> 400.
4. Xu ly tags:
  - lay danh sach tag da ton tai tu `tags` table theo ten (ignore-case).
  - khong tao tag moi trong luong edit metadata.
  - cap nhat many-to-many `document_tags` theo bo tags moi.
5. Response:
  - tra payload gom `id`, `title`, `description`, `tags` de FE cap nhat UI nhanh.

#### 24.3.2 PBI-008 - Hard delete + owner/admin permission

1. DB:
  - khong them cot `deleted_at`/`deleted_by` trong dot nay.
  - giu schema xoa cung hien tai.
2. Entity + query:
  - khong map soft-delete trong `Document`.
  - khong bo sung filter `deleted_at IS NULL` vao query list/search.
  - metadata/read/download/rating/comment mac dinh khong truy cap duoc khi document da bi xoa cung (khong con ban ghi).
3. Permission xoa:
  - owner hoac admin duoc xoa.
  - can bo sung role cho user (vd: `role` hoac `roles`) va map trong security principal.
4. Service delete:
  - giu xoa cung: xoa object storage va xoa row document trong DB.
  - bo sung nhanh admin xoa (khong chi owner).
  - cleanup storage policy sau nay neu can tach scope rieng.

#### 24.3.3 PBI-012 - Rating anti-race-condition

1. Giu unique constraint `(document_id, user_id)`.
2. Them locking trong transaction rating:
  - lock document row `PESSIMISTIC_WRITE` truoc khi upsert rating va sync aggregate.
  - hoac native SQL lock `SELECT ... FOR UPDATE` tren document id.
3. Upsert rating:
  - tiep tuc update neu da ton tai, tao moi neu chua co.
4. Sync aggregate trong cung transaction lock:
  - cap nhat `documents.rating_avg`, `documents.rating_count` tu `document_ratings`.
5. Chuan hoa isolation/transaction:
  - danh dau ro isolation cho `rateDocument(...)` (uu tien `READ_COMMITTED + row lock` hoac `SERIALIZABLE` neu can chat che hon).
6. Test dong thoi:
  - viet integration test mo phong nhieu thread rating cung 1 document, assert aggregate khong lech.

#### 24.3.4 PBI-013 - Comment 1 cap (soft delete cho comment + bat buoc co rating)

1. DB + entity:
  - tao bang `document_comments`:
    - `id` (uuid), `document_id`, `user_id`, `username_snapshot`, `content`, `rating_snapshot`, `created_at`, `updated_at`, `deleted_at` (dung cho soft delete comment).
  - khong tao `parent_comment_id` de dam bao 1 cap.
2. API:
  - `GET /api/documents/{id}/comments` (public/auth, chi tren document ton tai; chi tra comment `deleted_at IS NULL`).
  - `POST /api/documents/{id}/comments` (auth, content bat buoc khong rong, user phai da co rating cho document).
  - `PUT /api/documents/{id}/comments/{commentId}` (auth, chi owner comment, khong cho sua comment da xoa mem).
  - `DELETE /api/documents/{id}/comments/{commentId}` (auth, owner comment hoac ADMIN, thuc hien soft delete bang set `deleted_at = now()`).
3. Validation:
  - content trim xong phai khac rong.
  - neu user chua co rating cho document -> tra loi 400 (`COMMENT_REQUIRES_RATING`).
  - gioi han do dai (de xuat 2000 ky tu) de an toan.
4. Response list:
  - tra `username`, `createdAt`, `updatedAt`, `rating`, `isOwner` (neu co auth) de FE render thao tac.
5. Sorting:
  - mac dinh tang dan theo `created_at` (cu -> moi) hoac giam dan theo quyet dinh UX, can chot 1 huong.

### 24.4 Ke hoach FE

#### 24.4.1 PBI-007 - UI edit metadata

1. My Document (`Menu.tsx`):
  - doi modal sua hien tai thanh form metadata: `title`, `description`, `tags`.
  - tai su dung danh sach tag tu `getAllTags()` + multi-select chip them/bot.
  - khong cho nhap tag tu do.
2. API client:
  - them ham `updateDocumentMetadata(id, payload)` trong `services/api/document.ts`.
3. UX:
  - submit thanh cong hien toast thong bao ro rang.
  - invalidate query `documents` de refetch danh sach.

#### 24.4.2 PBI-008 - Delete flow hard-delete

1. Giu popup xac nhan hien co (da dat yeu cau).
2. Cap nhat message UI:
  - doi wording thanh xoa vinh vien.
3. Sau xoa:
  - invalidate list + quota (neu nghiep vu quota can cap nhat).

#### 24.4.3 PBI-012 - Rating flow

1. Giu UI star rating hien tai trong `DocumentDetail.tsx` va cho phep user danh gia doc lap (khong bat buoc comment).
2. Bo sung xu ly loi ro rang neu BE tra conflict/lock timeout (neu co).
3. Sau rate thanh cong:
  - dong bo lai rating block + invalidate list de cap nhat card rating.

#### 24.4.4 PBI-013 - Comment UI (soft delete)

1. Tai `DocumentDetail.tsx` them section comment o cuoi trang:
  - list comment, o nhap comment, nut gui.
2. Dieu kien login:
  - guest thay thong bao can login de comment.
3. Rule comment/rating:
  - user co the rate ma khong comment.
  - user khong duoc submit comment neu chua co rating cho tai lieu.
  - neu chua rating, disable nut gui comment va hien thong bao huong dan danh gia truoc.
4. Owner comment:
  - moi comment cua user hien tai co nut `Xoa` truc tiep ngay tren card comment.
  - (chuc nang sua comment giu scope PBI-013 va se bo sung theo API PUT).
5. Validation client:
  - content trim khong rong truoc khi goi API.
6. Xoa comment tren UI:
  - khi xoa, an comment khoi danh sach (backend xu ly soft delete).
7. Thong tin hien thi tren moi box comment:
  - nguoi binh luan (username), thoi gian binh luan, rating, noi dung comment.

#### 24.4.5 Doc detail layout + owner action

1. Them block `description` ro rang tren trang detail.
2. Neu user hien tai la owner cua document:
  - hien nut `Sua` + `Xoa` ben trai trang detail (gan khu viewer/header ben trai).
3. Nut `Xoa` su dung lai popup xac nhan truoc khi xoa.
4. Sau xoa thanh cong:
  - dieu huong ve trang danh sach phu hop va invalidate cache lien quan.

### 24.5 Thu tu implement de giam rui ro

1. Buoc 1: PBI-008 (owner/admin permission + hard delete) vi anh huong truc tiep den bao mat thao tac xoa.
2. Buoc 2: PBI-007 (BE endpoint update metadata + FE form edit + tag pick tu list he thong).
3. Buoc 3: Doc detail layout (hien description + owner action ben trai).
4. Buoc 4: PBI-012 (lock rating transaction + test dong thoi).
5. Buoc 5: PBI-013 (comment end-to-end BE + FE, soft delete cho comment, rule bat buoc da rating moi duoc comment).
6. Buoc 6: Chay full verify compile/typecheck + test manual theo checklist.

### 24.6 Tieu chi nghiem thu tong hop

1. Edit metadata:
  - chi owner sua duoc `title/description/tags`.
  - tags chi duoc them/bot tu list tag he thong, khong nhap tag tu do.
  - FE hien toast thanh cong.
2. Delete:
  - chi owner/admin xoa duoc.
  - co popup xac nhan.
  - document bi xoa cung khoi DB va storage.
3. Doc detail UI:
  - hien duoc `description`.
  - neu la owner, thay nut `Sua` + `Xoa` ben trai trang detail.
4. Rating:
  - chi user login duoc rating.
  - 1 user 1 rating/document va update duoc.
  - aggregate dung duoi concurrent load test.
5. Comment:
  - chi user login duoc tao comment.
  - comment khong rong.
  - moi box comment hien username + thoi gian + rating.
  - user co the danh gia ma khong comment.
  - user khong duoc comment neu chua rating tai lieu.
  - owner comment xoa truc tiep duoc tren trang detail; ADMIN cung co quyen xoa comment theo luong admin.
  - xoa comment theo soft delete (`deleted_at`), comment bi an khoi UI.
  - khong co reply (1 cap).
6. Ky thuat:
  - `BE: mvnw clean -DskipTests compile` pass.
  - `FE: npm run typecheck` pass.

## 25) [DA THUC HIEN] Ke hoach fake comment data theo rating (ti le cao, khong bat buoc 1-1 tuyet doi)

### 25.1 Muc tieu va pham vi

1. Muc tieu du lieu demo:
  - moi comment fake phai tham chieu dung 1 cap `(document_id, user_id)` co that trong `document_ratings`.
  - `rating_snapshot` cua comment phai bang dung gia tri `document_ratings.rating` cua cung cap `(document_id, user_id)`.
  - so comment tren moi tai lieu duoc phep `<= rating_count`, nhung khong duoc qua thap.
  - de xuat nguong toi thieu: `comment_count >= CEIL(rating_count * 0.7)` (toi thieu 70% so rating; va toi thieu 1 neu document co rating).
  - khi bo du lieu rating da duoc seed trong khoang `1..30` moi tai lieu, comment cung se nam trong khoang `1..30`.
2. Pham vi:
  - chi tao du lieu fake trong DB (DEV/DEMO).
  - khong thay doi nghiep vu runtime BE/FE.
3. Trang thai:
  - chi de xuat phuong an, cho duyet, chua implement.

### 25.2 Doi chieu hien trang BE + FE

1. BE:
  - bang `document_comments` da co cac cot can thiet: `document_id`, `user_id`, `username_snapshot`, `content`, `rating_snapshot`, `created_at`, `updated_at`, `deleted_at`.
  - endpoint list comment da phan trang va FE dang tai theo page.
  - runtime rule da co: user phai co rating truoc moi duoc comment.
2. FE:
  - trang detail dang load comment theo page va nut `Tai them`.
  - FE doc tu API list comment, khong can doi schema bo sung.

### 25.3 Phuong an ky thuat de xuat (kha thi nhat)

1. Tao SQL seed rieng cho comment fake trong `BE/docs/seeds/`.
2. Nguon sinh comment:
  - lay tu cap `(document_id, user_id, rating)` trong `document_ratings`.
  - join `users` de lay `username_snapshot`.
3. Mapping theo rating (1 chieu):
  - moi dong comment fake phai map vao 1 dong rating hop le theo key `(document_id, user_id)`.
  - tap comment fake la tap con cua tap rating (khong bat buoc bao phu 100% ratings).
4. Dieu kien dat khoang `1..30` moi document:
  - voi moi document, `comment_count` duoc lay ngau nhien trong khoang:
    - `min_comment = max(1, CEIL(rating_count * 0.7))`
    - `max_comment = rating_count`
  - script se random chon user trong tap da rating de dat so luong trong khoang tren.
  - vi vay rating seed phai la nguon su that cho range `1..30` (co script rating seed san trong `BE/docs/seeds/`).
5. Noi dung comment fake:
  - dung mau cau theo nhom sao (1-2 sao: che, 3 sao: trung tinh, 4-5 sao: khen) + bien the ngau nhien.
  - de danh dau du lieu fake bang prefix (vi du `[FAKE]`) de de rerun/cleanup.
6. Thoi gian fake:
  - `created_at` phan bo ngau nhien trong N ngay gan day (vi du 60-120 ngay).
  - `updated_at >= created_at`.

### 25.4 Chien luoc rerun an toan

1. Vi `document_comments` hien chua co unique `(document_id, user_id)`, rerun can co buoc cleanup de tranh duplicate fake comments.
2. De xuat 2 che do rerun:
  - che do A (khuyen nghi de dam bao khong duplicate va dung ti le): reset bang `document_comments` trong moi truong DEV/DEMO, roi seed lai tu `document_ratings`.
  - che do B (it xam lan): xoa toan bo row fake cu theo prefix `[FAKE]`, sau do insert lai fake comments tu rating.
3. Neu can giu du lieu that song song:
  - script phai co canh bao ro rang la che do B co the tao ti le lech neu da ton tai comments that trung `(document_id, user_id)`.
  - de dat ti le fake on dinh theo ke hoach thi uu tien che do A trong moi truong seed demo.

### 25.5 Thu tu thuc hien de xuat

1. Buoc 1: dam bao da co du lieu rating (khuyen nghi dung seed rating 1..30 hien co).
2. Buoc 2: chon che do rerun (A/B), sau do chay script fake comments tu bang rating.
3. Buoc 3: chay query verify mapping theo rating + coverage (70%-100%).
4. Buoc 4: mo FE detail, kiem tra list comment + phan trang 5 item/lần.

### 25.6 Tieu chi nghiem thu cho ke hoach nay

1. Mapping theo rating dung:
  - moi dong fake comment deu co cap `(document_id, user_id)` ton tai trong `document_ratings`.
  - `rating_snapshot` cua fake comment khop rating nguon.
2. Moi dong comment fake co `rating_snapshot` khop voi `document_ratings.rating` cung cap `(document_id, user_id)`.
3. So comment moi tai lieu thoa:
  - `comment_count <= rating_count`
  - `comment_count >= CEIL(rating_count * 0.7)` (toi thieu 70%, khong nho qua)
  - neu bo rating da seed trong `1..30` thi comment cung nam trong khoang hop ly `1..30`.
4. FE trang detail hien binh thuong, nut `Tai them` moi lan nap them 5 item.
5. Script co huong dan rerun ro rang cho 2 che do A/B va canh bao tac dong.

### 25.7 Deliverable sau khi duyet

1. `BE/docs/seeds/2026-04-09-fake-document-comments-1-30.sql`.
2. (Neu can) `BE/docs/seeds/2026-04-09-rerun-fake-comments-1-30.sql`.
3. Bo query verify mapping theo rating + coverage 70%-100% ngay trong cuoi file seed.

### 25.8 Cap nhat implement (2026-04-09)

1. Da tao script seed thuong: `BE/docs/seeds/2026-04-09-fake-document-comments-1-30.sql`.
2. Da tao script rerun reset: `BE/docs/seeds/2026-04-09-rerun-fake-comments-1-30.sql`.
3. Ca 2 script deu:
  - fake comment tu `document_ratings` + `users`.
  - gan `rating_snapshot` khop voi rating nguon.
  - dat policy `70%-100%` so rating moi document.
  - co query verify o cuoi script.

## 26) [CHO DUYET] Ke hoach hien thuc PBI-014, PBI-015, PBI-017 (Admin Console)

### 26.1 Yeu cau nghiep vu can bo sung

1. PBI-014 (Quan ly tai lieu vi pham):
  - chi Admin duoc truy cap.
  - xem toan bo danh sach tai lieu (gom ca tai lieu dang hien va dang an).
  - loc/tim kiem tai lieu can moderation.
  - co quyen `giu` (hien lai), `an`, `xoa` tai lieu vi pham.
2. PBI-015 (Thong ke he thong):
  - tong so nguoi dung, tong tai lieu.
  - tong danh gia, luot tai theo ngay/thang.
  - top tai lieu pho bien.
3. PBI-017 (Ban user):
  - Admin xem danh sach user va doi trang thai `ACTIVE/BANNED`.
  - user bi khoa login nhan thong bao: "Tai khoan cua ban da bi khoa".
  - neu user dang online luc bi khoa, he thong thu hoi token/session va buoc logout.
4. Rule bo sung theo yeu cau:
  - tai khoan ADMIN khi login thanh cong se vao thang trang Admin.
  - ADMIN van vao doc detail de doc tai lieu nhu user thong thuong.
  - trong doc detail, ADMIN co quyen xoa moi comment (khong gioi han owner comment).

### 26.2 Doi chieu hien trang code FE + BE

1. BE da co role trong `users.role` va authority `ROLE_<role>`, nhung:
  - chua co API moderation rieng cho admin tren danh sach documents.
  - chua co endpoint `/api/admin/**`.
  - chua co user status `ACTIVE/BANNED`.
  - JWT hien tai chua co co che revoke token theo ban-session.
  - chua co endpoint `/api/auth/logout` de user chu dong revoke token khi dang xuat.
2. FE hien tai chua co route/trang admin (dashboard, moderation, user management).
3. FE da co interceptor 401 -> signOut, co the tan dung cho luong force logout.
4. FE login flow hien tai chua co redirect theo role sau khi dang nhap.
5. API xoa comment hien tai dang theo owner comment, chua mo cho ADMIN.

### 26.3 Ke hoach BE

#### 26.3.1 Data model + migration

1. Bo sung cot cho `users`:
  - `status` (`ACTIVE`/`BANNED`, default `ACTIVE`).
  - `token_version` (int, default 0) de revoke toan bo token cu.
  - (tuy chon) `banned_at` de theo doi thoi diem khoa mo.
2. Khong tao bang audit moderation rieng trong dot nay (he thong 1 admin).

#### 26.3.2 Security + auth enforcement

1. SecurityConfig:
  - bo sung rule `/api/admin/**` -> `hasRole("ADMIN")`.
2. Login check (PBI-017):
  - neu `users.status = BANNED` thi tra 403 + message `ACCOUNT_BANNED` + user message dung AC.
3. Force logout online user:
  - khi ban user: tang `users.token_version`.
  - JWT moi include claim `tokenVersion`.
  - JwtAuthFilter validate moi request: token claim `tokenVersion` phai bang DB `users.token_version` va user status phai `ACTIVE`.
  - mismatch hoac BANNED -> 401 `ACCOUNT_BANNED` (hoac `TOKEN_REVOKED`).
4. Token claims phuc vu redirect sau login:
  - bo sung claim `role` trong JWT (hoac bo sung endpoint `/api/auth/me` tra role).
  - FE dua vao role de dieu huong ADMIN vao thang dashboard admin ngay sau login.
5. Logout API (bo sung de hoan thien auth flow):
  - tao `POST /api/auth/logout` (yeu cau auth).
  - pham vi V1: tang `users.token_version` de revoke token hien tai va cac token cu cua user.
  - `JwtAuthFilter` tra 401 `TOKEN_REVOKED` neu token claim khong con hop le theo `token_version` trong DB.
  - ghi chu: V1 la logout tat ca session cua user; logout theo tung thiet bi de phase 2 (refresh token + session table).

#### 26.3.3 API cho PBI-014

1. Admin moderation APIs (khong co user report flow):
  - `GET /api/admin/documents` (all docs, paging, filter `visibility=all|visible|hidden`, search).
  - `PUT /api/admin/documents/{id}/moderation` body action:
    - `KEEP`: set `documents.visible=true` (hien lai neu dang an).
    - `HIDE`: set `documents.visible=false`.
    - `DELETE`: hard delete tai lieu (tai su dung service delete).
2. Admin doc detail + comment moderation:
  - mo rong read permission de ADMIN xem doc detail (metadata/pages/comments) ke ca tai lieu dang an.
  - mo rong `DELETE /api/documents/{id}/comments/{commentId}` cho phep owner comment HOAC ADMIN xoa.

#### 26.3.4 API cho PBI-015

1. `GET /api/admin/stats/overview`:
  - total users, total documents, total ratings, total downloads.
2. `GET /api/admin/stats/downloads?range=day|month`:
  - series theo ngay/thang (nguon tu `document_downloads` va/hoac `documents.download_count`).
3. `GET /api/admin/stats/top-documents?limit=5`:
  - top pho bien theo weighted score (de xuat): `download_count` + `rating_count` + `rating_avg`.

#### 26.3.5 API cho PBI-017

1. `GET /api/admin/users`:
  - list user + status + so lieu can thiet cho moderation.
2. `PUT /api/admin/users/{id}/status`:
  - chuyen `ACTIVE <-> BANNED`.
  - khi `BANNED`: update status + tang `token_version`.
  - khi `ACTIVE`: cho phep login lai.

### 26.4 Ke hoach FE

#### 26.4.1 Route va guard

1. Tao route admin (de xuat): `src/app/[locale]/admin/**`.
2. Role guard:
  - session phai co role ADMIN.
  - non-admin redirect ve trang 403/khong co quyen.
3. Redirect sau login:
  - neu role = ADMIN thi redirect thang `/{locale}/admin`.
  - user thuong van vao flow trang user nhu hien tai.

#### 26.4.2 UI theo mock

1. Dashboard (PBI-015):
  - cards KPI, chart luot tai theo ngay/thang, top pho bien.
2. Quan ly tai lieu vi pham (PBI-014):
  - table documents + bo loc `All/Visible/Hidden` + tim kiem.
  - actions `Giu` (hien lai), `An`, `Xoa`.
3. Quan ly user (PBI-017):
  - table user + status Active/Banned.
  - nut khoa/mo khoa tai khoan.
4. Doc detail trong role ADMIN:
  - ADMIN vao doc detail nhu user de doc tai lieu.
  - tren danh sach comment, ADMIN thay nut xoa tren tat ca comment.

#### 26.4.3 Session revoke UX

1. Neu API tra `ACCOUNT_BANNED` hoac `TOKEN_REVOKED`:
  - clear session + redirect login.
  - hien thong bao "Tai khoan cua ban da bi khoa".
2. Tan dung interceptor 401 hien co + bo sung mapping message de dung wording AC.

#### 26.4.4 Logout UX + integration

1. Khi user bam dang xuat tren FE:
  - goi `POST /api/auth/logout` qua proxy backend de revoke token tren BE.
  - sau do (hoac neu API fail) van thuc hien clear local auth state + `signOut` NextAuth + redirect ve trang public.
2. Neu `POST /api/auth/logout` tra 401 do token het han/khong hop le:
  - FE xem nhu logout thanh cong o UI (vi session local van duoc clear).
3. Bo sung toast/message ngan gon khi logout thanh cong that bai mang (khuyen nghi), nhung khong chan user roi trang.

### 26.5 Thu tu implement de giam rui ro

1. Buoc 1: migration users status/token_version.
2. Buoc 2: auth hardening (ban login + tokenVersion revoke check).
3. Buoc 3: bo sung `POST /api/auth/logout` va wiring revoke token theo `token_version`.
4. Buoc 4: API PBI-017 (users list + ban/unban).
5. Buoc 5: API PBI-014 (moderation actions tren documents, khong co report flow).
6. Buoc 6: API PBI-015 (overview + series + top docs).
7. Buoc 7: FE admin pages theo 3 module + wiring APIs + logout integration.
8. Buoc 8: UAT luong ban-online-user + moderation + thong ke.
9. Buoc 9: UAT luong redirect login ADMIN + UAT xoa comment bang role ADMIN trong doc detail + UAT logout revoke.

### 26.6 Tieu chi nghiem thu tong hop

1. PBI-014:
  - chi ADMIN vao duoc trang/API admin.
  - xem duoc all documents (visible + hidden), co bo loc/tim kiem.
  - action `KEEP/HIDE/DELETE` hoat dong dung va cap nhat trang thai visible/xoa cung.
  - ADMIN vao duoc doc detail ke ca voi tai lieu dang an va co the xoa comment trong doc detail.
2. PBI-015:
  - dashboard hien KPI dung.
  - chart download day/month tra dung range.
  - top pho bien tra dung thu tu.
3. PBI-017:
  - ban user -> login moi bi chan voi message dung AC.
  - user dang online bi revoke token va bi logout o request tiep theo (session bi thu hoi).
  - unban user -> login lai binh thuong.
4. Admin UX bo sung:
  - login voi tai khoan ADMIN -> vao thang trang admin.
  - user khong phai admin khong bi anh huong redirect nay.
5. Logout:
  - user bam logout -> FE goi `POST /api/auth/logout`, clear session local, va quay ve trang public.
  - token cu khong dung lai duoc cho API protected (401 `TOKEN_REVOKED`).
6. Ky thuat:
  - BE compile pass.
  - FE typecheck pass.
  - API docs cap nhat day du cho `/api/admin/**` va `/api/auth/logout`.

### 26.7 Rui ro va ghi chu

1. Force logout "ngay lap tuc" tren he thong JWT stateless can token-version check moi request; khong can websocket o scope nay.
2. Neu can logout truoc khi user thao tac API tiep, co the bo sung heartbeat endpoint polling nhe tren FE (optional phase 2).
3. Thu tu uu tien: chot data model + auth revoke truoc, roi moi lam UI.

## 27) Su co 500 tai API admin documents (2026-04-10)

Trang thai trien khai:
- [DONE] Da dong bo lai query `searchForAdmin` theo cung nguyen tac xu ly text voi query list thuong (giu query rieng cho admin).
- [DONE] Da them migration `2026-04-10-normalize-documents-text-columns.sql` de chuan hoa cac cot text-like trong `documents` neu DB local con dang `bytea`.
- [DONE] Da chay migration tren DB local dang dung (xac nhan boi user).
- [DONE] Da verify ky thuat BE: `mvnw -DskipTests compile` pass sau khi dong bo query.
- [TODO] Verify API/FE theo muc 27.3 Buoc E.

### 27.1 Trieu chung

- FE proxy goi `GET /backend/api/admin/documents?page=0&size=18&visibility=all` nhan 500.
- BE log SQLState `42883` va loi: `function lower(bytea) does not exist`.
- Stacktrace tro ve truy van list admin documents (`searchForAdmin`).

### 27.2 Phan tich nguyen nhan goc

1. API list admin dang dung query co dieu kien tim kiem dang `lower(coalesce(d.title,''))`, `lower(coalesce(d.description,''))`, `lower(coalesce(d.username,''))`.
2. Tren DB hien tai, co it nhat mot cot lien quan den tim kiem dang o kieu `bytea` (legacy schema), nen PostgreSQL khong co ham `lower(bytea)`.
3. Loi xay ra ngay o phase parse/plan SQL, vi vay request van fail ke ca khi FE khong gui `q`.
4. Ket qua: endpoint `/api/admin/documents` bi nem `InvalidDataAccessResourceUsageException` va tra 500.

### 27.3 Ke hoach sua (giu 2 query rieng trong repo, lam tuong tu)

1. Buoc A - Kiem tra schema local truoc khi sua:
  - query `information_schema.columns` de check datatype thuc te cua cac cot tim kiem trong `documents` (toi thieu: `title`, `description`, `username`; khuyen nghi check them `original_name`).
  - chot ro cot nao dang bi `bytea` gay loi `lower(bytea)`.
2. Buoc B - Sua thang schema bang migration:
  - tao migration convert cot sai kieu ve dung kieu text (`varchar/text`) bang `ALTER TABLE ... ALTER COLUMN ... TYPE ... USING ...`.
  - update lai schema tai lieu (`docs/DB.sql`) de dong bo voi DB sau migration.
3. Buoc C - Giu 2 query rieng, nhung dong bo cach lam:
  - query admin: `searchForAdmin` (danh cho `/api/admin/documents`).
  - query list thuong: `searchVisible` va `searchByOwner` (danh cho list public/my docs).
  - 2 nhom query duoc phep rieng vi filter khac nhau, nhung nguyen tac xu ly text phai dong nhat de tranh lap lai loi `lower(bytea)`.
  - KHONG them fallback tuong thich nguoc vi du an dang chay local va pham vi hoc tap.
4. Buoc D - Ket luan ve script sua DB:
  - VAN CAN chay migration `2026-04-10-normalize-documents-text-columns.sql` tren DB local dang dung (chay 1 lan).
  - ly do: CAST trong query giup chan loi runtime, nhung migration moi xu ly dut diem schema sai kieu.
5. Buoc E - Verify local:
  - goi lai API `GET /api/admin/documents` voi `visibility=all|visible|hidden`, co/khong co `q`.
  - mo FE trang admin documents, xac nhan khong con 500 va danh sach tai lieu load on dinh.

### 27.4 Ghi chu lien quan migration auth

- Migration `docs/migrations/2026-04-10-add-user-status-and-token-version.sql` (status/token_version/banned_at) khong phai nguyen nhan truc tiep cua loi `lower(bytea)` nay.