package ac.mju.contractcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Upstage Document Parse 접속 설정(contractcheck.ocr.upstage.*). 키는 환경변수(UPSTAGE_API_KEY)로만 받는다.
 * 키가 비어 있으면 OCR 빈이 만들어지지 않고 계약서 처리가 FAILED로 끝난다(앱은 정상 기동).
 * 호출 제한 시간은 구현체 공통 설정인 contractcheck.ocr.timeout을 쓴다.
 *
 * @param baseUrl Upstage API 주소
 * @param apiKey API 키. 로그에 남기지 않는다.
 * @param model 모델 별칭
 */
@ConfigurationProperties(prefix = "contractcheck.ocr.upstage")
public record UpstageProperties(
        @DefaultValue("https://api.upstage.ai") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("document-parse") String model) {

    /** 잘못된 값이면 호출할 때가 아니라 시작할 때 실패한다. */
    public UpstageProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("contractcheck.ocr.upstage.model은 비어 있을 수 없습니다.");
        }
    }
}
