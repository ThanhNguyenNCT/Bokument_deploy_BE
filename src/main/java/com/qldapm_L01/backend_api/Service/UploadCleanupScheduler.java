package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UploadCleanupScheduler {

    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED_PREVIEW = "FAILED_PREVIEW";

    private final DocumentRepository documentRepository;
    private final S3Client s3Client;

    @Value("${app.upload-cleanup.stale-minutes:30}")
    private long staleMinutes;

    @Scheduled(fixedDelayString = "${app.upload-cleanup.fixed-delay-ms:900000}")
    @Transactional
    public void cleanupStaleUploads() {
        long effectiveMinutes = Math.max(1, staleMinutes);
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(effectiveMinutes));

        cleanupUploadingStale(cutoff);
        cleanupProcessingStale(cutoff);
    }

    private void cleanupUploadingStale(Instant cutoff) {
        List<Document> staleUploading = documentRepository
                .findByProcessingStatusAndCreatedAtBefore(STATUS_UPLOADING, cutoff);

        for (Document doc : staleUploading) {
            if (objectExists(doc.getBucketName(), doc.getObjectKey())) {
                tryDeleteObject(doc.getBucketName(), doc.getObjectKey());
            }
            documentRepository.delete(doc);
        }

        if (!staleUploading.isEmpty()) {
            log.info("Cleaned {} stale documents in UPLOADING status", staleUploading.size());
        }
    }

    private void cleanupProcessingStale(Instant cutoff) {
        List<Document> staleProcessing = documentRepository
                .findByProcessingStatusAndUploadedAtBefore(STATUS_PROCESSING, cutoff);

        if (staleProcessing.isEmpty()) {
            return;
        }

        for (Document doc : staleProcessing) {
            doc.setProcessingStatus(STATUS_FAILED_PREVIEW);
        }

        documentRepository.saveAll(staleProcessing);
        log.info("Marked {} stale documents from PROCESSING to FAILED_PREVIEW", staleProcessing.size());
    }

    private boolean objectExists(String bucket, String objectKey) {
        try {
            s3Client.headObject(req -> req.bucket(bucket).key(objectKey));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.warn("Cannot verify S3 object existence for {}/{}: {}", bucket, objectKey, e.getMessage());
            return false;
        }
    }

    private void tryDeleteObject(String bucket, String objectKey) {
        try {
            s3Client.deleteObject(req -> req.bucket(bucket).key(objectKey));
        } catch (S3Exception e) {
            log.warn("Cannot delete S3 object {}/{} during stale cleanup: {}", bucket, objectKey, e.getMessage());
        }
    }
}
