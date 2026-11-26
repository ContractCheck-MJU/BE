package ac.mju.contractcheck.client.mac;

import ac.mju.contractcheck.client.LocalProcessOcrClient;
import ac.mju.contractcheck.client.OcrException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * macOS의 Vision 프레임워크로 OCR을 한다. 로컬 시연용이며 macOS에서만 동작한다(운영은 Upstage 구현체로 바꾼다).
 *
 * <p>Vision은 Swift/Objective-C API라 Java에서 바로 부를 수 없다. 그래서 {@code ocr/apple-vision/main.swift}를
 * 처음 쓸 때 {@code swiftc}로 한 번 컴파일(Xcode Command Line Tools 필요)해 임시 폴더에 두고,
 * 호출할 때마다 그 실행 파일을 별도 프로세스로 실행한다({@link LocalProcessOcrClient}).
 * 첫 인식은 모델을 불러오느라 수십 초 걸릴 수 있고, 이후에는 1초 안팎이다.
 *
 * <p>이 구현체는 {@code contractcheck.ocr.provider=apple-vision}일 때만 빈으로 등록된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "contractcheck.ocr", name = "provider", havingValue = "apple-vision")
public class AppleVisionOcrClient extends LocalProcessOcrClient {

    private static final Duration COMPILE_TIMEOUT = Duration.ofMinutes(2);

    @Override
    protected String provider() {
        return "apple-vision";
    }

    @Override
    protected void requireSupportedOs(String osName) {
        if (!osName.contains("mac")) {
            throw new OcrException("Apple Vision OCR은 macOS에서만 쓸 수 있습니다. contractcheck.ocr.provider 설정을 확인하세요.");
        }
    }

    @Override
    protected String sourceResource() {
        return "/ocr/apple-vision/main.swift";
    }

    @Override
    protected String prepareFailureMessage() {
        return "Apple Vision OCR 헬퍼를 준비하지 못했습니다. Xcode Command Line Tools(swiftc)가 필요합니다.";
    }

    /** 소스 해시가 이름에 들어간 실행 파일이 이미 있으면 그대로 쓰고, 없으면 컴파일한다. */
    @Override
    protected Path prepareHelper(Path directory, String sourceHash, byte[] source) throws IOException {
        Path binary = directory.resolve("vision-ocr-" + sourceHash);
        if (isUsable(binary)) {
            return binary;
        }
        Path work = Files.createTempDirectory(directory, "build-");
        try {
            Path swift = work.resolve("main.swift");
            Files.write(swift, source);
            Path built = work.resolve("vision-ocr");
            log.info("Apple Vision OCR 헬퍼를 컴파일합니다(처음 한 번)");
            run(List.of("swiftc", "-O", "-o", built.toString(), swift.toString()), COMPILE_TIMEOUT);
            Files.move(built, binary, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return binary;
        } finally {
            deleteTreeQuietly(work);
        }
    }

    @Override
    protected boolean isUsable(Path helper) {
        return Files.isExecutable(helper);
    }

    @Override
    protected List<String> command(Path helper, Path input, String kind) {
        return List.of(helper.toString(), input.toString(), kind);
    }

    private static void deleteTreeQuietly(Path directory) {
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> deleteQuietly(path));
        } catch (IOException exception) {
            log.warn("OCR 빌드 폴더를 지우지 못했습니다");
        }
    }
}
