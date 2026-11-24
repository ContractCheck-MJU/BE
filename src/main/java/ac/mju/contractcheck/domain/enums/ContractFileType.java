package ac.mju.contractcheck.domain.enums;

import java.util.Optional;

/**
 * 업로드 가능한 계약서 파일 형식(PDF, JPG, PNG). 확장자나 클라이언트가 보낸 Content-Type을 믿지 않고
 * 파일 앞부분의 매직바이트로 판별한다(TC-UPL-002).
 */
public enum ContractFileType {
    PDF("application/pdf"),
    JPEG("image/jpeg"),
    PNG("image/png");

    private final String contentType;

    ContractFileType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ContractFileType> detect(byte[] head) {
        if (startsWith(head, '%', 'P', 'D', 'F')) {
            return Optional.of(PDF);
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
