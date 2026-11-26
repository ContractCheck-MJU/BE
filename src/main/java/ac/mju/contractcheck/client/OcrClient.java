package ac.mju.contractcheck.client;

import ac.mju.contractcheck.domain.enums.ContractFileType;
import ac.mju.contractcheck.domain.OcrResult;

/**
 * 계약서 파일(PDF·이미지)에서 텍스트를 읽어 오는 유일한 통로다(FR-UPL-02). 서비스는 이 인터페이스만 부른다.
 *
 * <p>구현체는 {@code contractcheck.ocr.provider} 설정(환경변수 {@code OCR_PROVIDER})으로 고른다.
 * 새 구현체를 붙일 때는 이 인터페이스를 구현하고 {@code @ConditionalOnProperty(prefix = "contractcheck.ocr",
 * name = "provider", havingValue = "<이름>")}를 달면 되고, 서비스 코드는 바꾸지 않는다.
 * <ul>
 *   <li>{@code apple-vision}: macOS 로컬 시연용({@link ac.mju.contractcheck.client.mac.AppleVisionOcrClient}). Linux·Windows에서는 쓸 수 없다.</li>
 *   <li>{@code windows-ocr}: Windows 로컬 시연용({@link ac.mju.contractcheck.client.windows.WindowsOcrClient}). Windows 내장 OCR을 PowerShell로 부른다. macOS·Linux에서는 쓸 수 없다.</li>
 *   <li>{@code upstage}: 운영 구현체({@link UpstageOcrClient}, Upstage Document Parse). API 키가 있어야 등록되고, 원본 파일을 외부로 보낸다.</li>
 * </ul>
 *
 * <p>호출은 트랜잭션 밖에서 하고, 테스트에서는 mock한다. 구현체는 계약서 본문을 로그에 남기지 않는다.
 */
public interface OcrClient {

    /**
     * 파일에서 텍스트를 읽는다. 쪽 순서대로 이어 붙이고 쪽 사이는 줄바꿈 한 번이다.
     * 글자를 하나도 찾지 못하면 예외가 아니라 빈 텍스트를 돌려준다. 이를 실패로 볼지는 호출하는 서비스가 정한다.
     *
     * @throws OcrException 파일을 읽을 수 없거나, 인식에 실패했거나, 제한 시간을 넘겼을 때
     */
    OcrResult extract(byte[] content, ContractFileType type);
}
