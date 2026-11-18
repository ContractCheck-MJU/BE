package ac.mju.contractcheck.client;

import java.time.Duration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * AWS SDK v2로 S3 호환 저장소(SeaweedFS)를 호출한다. 타임아웃은 {@code StorageConfig}의 클라이언트 설정이 맡는다.
 * 객체 키에 사용자 입력을 쓰지 않는다(호출자가 users.id와 UUID로 만든다).
 */
@Component
@RequiredArgsConstructor
public class S3StorageClient implements StorageClient {

    @NonNull
    private final S3Client s3;
    @NonNull
    private final S3Presigner presigner;

    @Value("${contractcheck.storage.bucket}")
    private String bucket;

    @Value("${contractcheck.storage.server-side-encryption:true}")
    private boolean serverSideEncryption;

    @Override
    public void put(String key, byte[] content, String contentType) {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);
        if (serverSideEncryption) {
            request.serverSideEncryption(ServerSideEncryption.AES256);
        }
        s3.putObject(request.build(), RequestBody.fromBytes(content));
    }

    @Override
    public String presignGet(String key, Duration validity) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
