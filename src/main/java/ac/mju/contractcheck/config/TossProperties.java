package ac.mju.contractcheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 토스페이먼츠 서버 연동 설정(contractcheck.toss.*). 시크릿 키는 환경변수(TOSS_SECRET_KEY)로만 받는다.
 * 키가 비어 있으면 토스 클라이언트 빈이 만들어지지 않고 결제 승인은 502로 거절된다(앱은 정상 기동).
 * 클라이언트 키는 화면(결제창)에서만 쓰므로 서버 설정에 두지 않는다.
 *
 * @param baseUrl 토스 API 주소
 * @param secretKey 시크릿 키. 테스트 키는 {@code test_sk}로 시작한다. 로그에 남기지 않는다.
 * @param timeout 호출 한 번의 연결·읽기 제한 시간. 스펙에 값이 없어 제안값이다.
 */
@ConfigurationProperties(prefix = "contractcheck.toss")
public record TossProperties(
        @DefaultValue("https://api.tosspayments.com") String baseUrl,
        @DefaultValue("") String secretKey,
        @DefaultValue("15s") Duration timeout) {

    /** 잘못된 값이면 호출할 때가 아니라 시작할 때 실패한다. */
    public TossProperties {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("contractcheck.toss.timeout은 0보다 커야 합니다.");
        }
    }

    public boolean configured() {
        return !secretKey.isBlank();
    }
}
