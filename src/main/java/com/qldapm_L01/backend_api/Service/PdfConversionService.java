package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfConversionService {

    private final S3Client s3Client;
    private final DocumentRepository repository;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Async
    public void downloadAndProcess(Document document) {
        log.info("Starting download-and-process for document: {}", document.getId());

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(document.getBucketName())
                    .key(document.getObjectKey())
                    .build();

            try (InputStream s3ObjStream = s3Client.getObject(getObjectRequest);
                 PDDocument pdf = PDDocument.load(s3ObjStream)) {
                int pageCount = pdf.getNumberOfPages();
                log.info("Document {} has {} pages. Rendering ALL pages for progress display...", document.getId(), pageCount);

                document.setPageCount(pageCount);
                document.setProcessingStatus("RENDERING_PREVIEW");
                repository.save(document);

                if (pageCount > 0) {
                    PDFRenderer renderer = new PDFRenderer(pdf);
                    for (int i = 0; i < pageCount; i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i, 150);
                        uploadImageToS3(document, i + 1, image);
                    }
                }

                document.setProcessingStatus("READY");
                repository.save(document);
                log.info("Finished rendering all pages for document {}", document.getId());
            }
        } catch (Exception e) {
            log.error("Failed to process PDF for document " + document.getId(), e);
            document.setProcessingStatus("FAILED_PREVIEW");
            repository.save(document);
        }
    }

    public void renderSpecificPageAndUpload(Document document, int pageNumber) {
        log.info("Lazy rendering page {} for document: {}", pageNumber, document.getId());

        if (pageNumber < 1 || pageNumber > document.getPageCount()) {
            throw new IllegalArgumentException("Invalid page number: " + pageNumber);
        }

        try {
            software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = 
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                    .bucket(document.getBucketName())
                    .key(document.getObjectKey())
                    .build();
            
            try (java.io.InputStream s3ObjStream = s3Client.getObject(getObjectRequest);
                 PDDocument pdf = PDDocument.load(s3ObjStream)) {
                
                PDFRenderer renderer = new PDFRenderer(pdf);
                // pageNumber is 1-indexed, PDDocument is 0-indexed
                BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, 150);
                uploadImageToS3(document, pageNumber, image);

                log.info("Successfully lazy rendered and uploaded page {} for document {}", pageNumber, document.getId());
            }

        } catch (Exception e) {
            log.error("Failed to lazy render page " + pageNumber + " for document " + document.getId(), e);
            throw new RuntimeException("Failed to render page " + pageNumber, e);
        }
    }

    private void uploadImageToS3(Document document, int pageNumber, BufferedImage image) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", os);
        byte[] bytes = os.toByteArray();

        String imgKey = "users/" + document.getOwnerId() + "/" + document.getId() + "/pages/page_" + pageNumber + ".jpg";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(imgKey)
                .contentType("image/jpeg")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));
    }
}
