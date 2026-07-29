package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.article.StorageProperties;
import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.time.Duration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * REQ-10 "Open decision a": reuses the existing MinIO/S3 infrastructure {@code
 * ArticleStorageService} already established, same shape, against a distinct {@code avatarBucket}
 * rather than the shared article bucket -- see specify/features/identity-profile-model-v2/PLAN.md.
 */
@Service
public class AvatarStorageService {

    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public AvatarStorageService(
            S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(
                    HeadBucketRequest.builder().bucket(properties.avatarBucket()).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(
                    CreateBucketRequest.builder().bucket(properties.avatarBucket()).build());
        }
    }

    public void upload(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.avatarBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    public URL presignedUrl(String key) {
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder().bucket(properties.avatarBucket()).key(key).build();

        return s3Presigner
                .presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(PRESIGNED_URL_TTL)
                                .getObjectRequest(getObjectRequest)
                                .build())
                .url();
    }
}
