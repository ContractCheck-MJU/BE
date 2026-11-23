package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.config.StorageConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** 실제 SeaweedFS가 잘못된 접속 키를 거부하는지 확인한다. 실행 방법은 {@link S3StorageClientIntegrationTest}를 본다. */
@Tag("storage")
@SpringJUnitConfig({StorageConfig.class, S3StorageClient.class})
@TestPropertySource(properties = {
    "contractcheck.storage.endpoint=${STORAGE_ENDPOINT:http://localhost:9000}",
    "contractcheck.storage.region=${STORAGE_REGION:us-east-1}",
    "contractcheck.storage.bucket=${STORAGE_BUCKET:contractcheck}",
    "contractcheck.storage.access-key=wrong-access-key",
    "contractcheck.storage.secret-key=wrong-secret-key",
    "contractcheck.storage.server-side-encryption=false"
})
class S3StorageClientCredentialsIntegrationTest {

    @Autowired
    S3StorageClient storage;

    @Test
    @DisplayName("잘못된 접속 키로 올리면 저장소가 거부한다")
    void putWithWrongCredentialsIsRejected() {
        String key = "storage-it/" + UUID.randomUUID() + ".txt";

        assertThatThrownBy(() -> storage.put(key, new byte[] {1}, "application/octet-stream")).isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("잘못된 접속 키로 삭제해도 저장소가 거부한다")
    void deleteWithWrongCredentialsIsRejected() {
        assertThatThrownBy(() -> storage.delete("storage-it/" + UUID.randomUUID() + ".txt")).isInstanceOf(S3Exception.class);
    }
}
