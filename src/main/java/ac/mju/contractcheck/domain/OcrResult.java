package ac.mju.contractcheck.domain;

/**
 * OCR 결과.
 *
 * @param text 읽어 낸 전체 텍스트. 글자가 없으면 빈 문자열이다.
 * @param pageCount 처리한 쪽 수. 이미지는 1이다.
 */
public record OcrResult(String text, int pageCount) {

    public boolean empty() {
        return text.isBlank();
    }
}
