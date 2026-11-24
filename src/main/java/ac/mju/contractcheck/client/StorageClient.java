package ac.mju.contractcheck.client;

import java.time.Duration;

/**
 * 계약서 원본 보관소(S3 호환, SeaweedFS) 접근의 유일한 통로다. 서비스는 이 인터페이스만 부른다.
 * 호출은 트랜잭션 밖에서 하고, 테스트에서는 mock한다.
 */
public interface StorageClient {

    /** 객체를 서버 측 암호화 설정에 따라 저장한다. 실패하면 예외를 던진다. */
    void put(String key, byte[] content, String contentType);

    /** 제한 시간 동안만 유효한 읽기 전용 사전서명 URL을 만든다. */
    String presignGet(String key, Duration validity);

    /** 객체의 내용을 읽는다. 없거나 읽지 못하면 예외를 던진다. OCR처럼 원본을 다시 읽는 처리에서 쓴다. */
    byte[] get(String key);

    /** 객체를 지운다. 이미 없어도 성공으로 본다. */
    void delete(String key);
}
