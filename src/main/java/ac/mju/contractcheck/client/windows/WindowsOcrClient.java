package ac.mju.contractcheck.client.windows;

import ac.mju.contractcheck.client.LocalProcessOcrClient;
import ac.mju.contractcheck.client.OcrException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Windows 내장 OCR({@code Windows.Media.Ocr})로 텍스트를 읽는다. 로컬 시연용이며 Windows에서만 동작한다(운영은 Upstage 구현체로 바꾼다).
 *
 * <p>WinRT API라 Java에서 바로 부를 수 없다. 그래서 {@code ocr/windows-ocr/ocr.ps1}을 처음 쓸 때 임시 폴더에 꺼내 두고,
 * 호출할 때마다 Windows PowerShell 5.1로 별도 프로세스를 띄워 실행한다({@link LocalProcessOcrClient}, 별도 설치 없음).
 * 한국어 인식은 Windows에 한국어 언어팩(OCR 포함)이 있어야 하고, 없으면 사용자 프로필 언어로 읽는다.
 * 프로세스를 띄우므로 호출마다 1초 안팎이 걸린다.
 *
 * <p>이 구현체는 {@code contractcheck.ocr.provider=windows-ocr}일 때만 빈으로 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "contractcheck.ocr", name = "provider", havingValue = "windows-ocr")
public class WindowsOcrClient extends LocalProcessOcrClient {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    protected String provider() {
        return "windows-ocr";
    }

    @Override
    protected void requireSupportedOs(String osName) {
        if (!osName.contains("windows")) {
            throw new OcrException("Windows OCR은 Windows에서만 쓸 수 있습니다. contractcheck.ocr.provider 설정을 확인하세요.");
        }
    }

    @Override
    protected String sourceResource() {
        return "/ocr/windows-ocr/ocr.ps1";
    }

    @Override
    protected String prepareFailureMessage() {
        return "Windows OCR 스크립트를 준비하지 못했습니다.";
    }

    /**
     * 소스 해시가 이름에 들어간 스크립트가 이미 있으면 그대로 쓰고, 없으면 꺼낸다.
     * Windows PowerShell 5.1은 BOM 없는 파일을 시스템 코드 페이지로 읽어 한글이 깨지므로 UTF-8 BOM을 붙여 쓴다.
     */
    @Override
    protected Path prepareHelper(Path directory, String sourceHash, byte[] source) throws IOException {
        Path target = directory.resolve("windows-ocr-" + sourceHash + ".ps1");
        if (isUsable(target)) {
            return target;
        }
        Path partial = Files.createTempFile(directory, "windows-ocr-", ".tmp");
        try {
            byte[] withBom = new byte[UTF8_BOM.length + source.length];
            System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
            System.arraycopy(source, 0, withBom, UTF8_BOM.length, source.length);
            Files.write(partial, withBom);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteQuietly(partial);
        }
        return target;
    }

    @Override
    protected boolean isUsable(Path helper) {
        return Files.isReadable(helper);
    }

    @Override
    protected List<String> command(Path helper, Path input, String kind) {
        return List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", helper.toString(), input.toString(), kind);
    }
}
