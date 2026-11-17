package ac.mju.contractcheck.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 계약서 원본 보관소(S3 호환, SeaweedFS S3 게이트웨이) 연결 설정. 접속 정보는 환경변수로만 받는다.
 * path-style 주소와 체크섬 "필요할 때만" 설정은 S3 호환 저장소와의 호환을 위해서다.
 * 클라이언트는 만들 때 접속하지 않으므로 저장소가 없어도 앱은 기동한다.
 */
@Configuration
public class StorageConfig {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(10);

    @Bean(destroyMethod = "close")
    S3Client s3Client(
            @Value("${contractcheck.storage.endpoint}") String endpoint,
            @Value("${contractcheck.storage.region}") String region,
            @Value("${contractcheck.storage.access-key}") String accessKey,
            @Value("${contractcheck.storage.secret-key}") String secretKey,
            @Value("${contractcheck.storage.path-style:true}") boolean pathStyle) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(CALL_TIMEOUT)
                        .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(
            @Value("${contractcheck.storage.endpoint}") String endpoint,
            @Value("${contractcheck.storage.region}") String region,
            @Value("${contractcheck.storage.access-key}") String accessKey,
            @Value("${contractcheck.storage.secret-key}") String secretKey,
            @Value("${contractcheck.storage.path-style:true}") boolean pathStyle) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .build();
    }

    private static StaticCredentialsProvider credentials(String accessKey, String secretKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
