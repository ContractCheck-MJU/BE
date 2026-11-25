package ac.mju.contractcheck.global.interceptor;

/** 외부 호출의 요청·응답 본문을 로그에 남길 문자열로 바꾸는 방법. 환경마다 구현체가 다르다. */
public interface ExternalBodyView {

    /** 남길 수 없는 본문은 크기만 알리는 문자열을 돌려준다. 빈 본문은 빈 문자열이다. */
    String describe(byte[] body, String contentType);
}
