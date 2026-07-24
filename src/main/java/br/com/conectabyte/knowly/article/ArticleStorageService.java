package br.com.conectabyte.knowly.article;

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

@Service
public class ArticleStorageService {

    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public ArticleStorageService(
            S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(
                    CreateBucketRequest.builder().bucket(properties.bucket()).build());
        }
    }

    public void upload(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    public byte[] download(String key) {
        return s3Client.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                .asByteArray();
    }

    public URL presignedUrl(String key) {
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder().bucket(properties.bucket()).key(key).build();

        return s3Presigner
                .presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(PRESIGNED_URL_TTL)
                                .getObjectRequest(getObjectRequest)
                                .build())
                .url();
    }
}
