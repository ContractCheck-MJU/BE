package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.config.StorageConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 실제 SeaweedFS(S3 호환)에 대한 {@link S3StorageClient} 통합 테스트.
 *
 * <p>테스트에서 실제 외부 API를 부르지 않는다는 규칙 때문에 {@code ./gradlew test}와 CI에서는 제외되고
 * {@code ./gradlew storageTest}로만 돌린다. 먼저 {@code docker compose up -d seaweedfs seaweedfs-init}으로 저장소와
 * 버킷을 준비한다. 접속 정보는 환경변수 {@code STORAGE_ENDPOINT, STORAGE_REGION, STORAGE_BUCKET, STORAGE_ACCESS_KEY,
 * STORAGE_SECRET_KEY}이고 {@code .env}가 있으면 Gradle이 읽어 넘긴다. 로컬 SeaweedFS에는 SSE-S3 키 설정이 없어
 * 암호화는 끈다.
 */
@Tag("storage")
@SpringJUnitConfig({StorageConfig.class, S3StorageClient.class})
@TestPropertySource(properties = {
    "contractcheck.storage.endpoint=${STORAGE_ENDPOINT:http://localhost:9000}",
    "contractcheck.storage.region=${STORAGE_REGION:us-east-1}",
    "contractcheck.storage.bucket=${STORAGE_BUCKET:contractcheck}",
    "contractcheck.storage.access-key=${STORAGE_ACCESS_KEY}",
    "contractcheck.storage.secret-key=${STORAGE_SECRET_KEY}",
    "contractcheck.storage.server-side-encryption=false"
})
class S3StorageClientIntegrationTest {

    private static final Duration VALIDITY = Duration.ofMinutes(1);

    @Autowired
    S3StorageClient storage;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String key = "storage-it/" + UUID.randomUUID() + ".txt";

    @AfterEach
    void cleanUp() {
        storage.delete(key);
    }

    @Test
    @DisplayName("올린 객체는 presigned URL로 같은 내용과 Content-Type으로 내려받을 수 있다")
    void putThenDownloadThroughPresignedUrl() throws Exception {
        byte[] content = "제1조(목적) 통합 테스트 계약서".getBytes(StandardCharsets.UTF_8);

        storage.put(key, content, "text/plain");

        HttpResponse<byte[]> response = get(storage.presignGet(key, VALIDITY));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
    }

    @Test
    @DisplayName("삭제한 객체는 presigned URL로 더 이상 조회되지 않는다")
    void deletedObjectIsNotFound() throws Exception {
        storage.put(key, "삭제 대상".getBytes(StandardCharsets.UTF_8), "text/plain");

        storage.delete(key);

        assertThat(get(storage.presignGet(key, VALIDITY)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("없는 객체를 삭제해도 예외 없이 끝난다")
    void deletingMissingObjectIsHarmless() {
        storage.delete("storage-it/missing-" + UUID.randomUUID());
    }

    @Test
    @DisplayName("같은 키에 다시 올리면 새 내용으로 덮어쓴다")
    void putOverwritesSameKey() throws Exception {
        storage.put(key, "첫 번째".getBytes(StandardCharsets.UTF_8), "text/plain");
        byte[] second = "두 번째".getBytes(StandardCharsets.UTF_8);

        storage.put(key, second, "text/plain");

        assertThat(get(storage.presignGet(key, VALIDITY)).body()).isEqualTo(second);
    }

    private HttpResponse<byte[]> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
