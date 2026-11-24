package ac.mju.contractcheck.client;

/** OCR이 제한 시간 안에 끝나지 않았다. 호출하는 쪽이 "처리 시간 초과" 사유를 구분해 안내하는 데 쓴다. */
public class OcrTimeoutException extends OcrException {
    public OcrTimeoutException(String message) {
        super(message);
    }
}
