package ac.mju.contractcheck.client.mac;

import ac.mju.contractcheck.client.OcrClient;
import ac.mju.contractcheck.client.OcrException;
import ac.mju.contractcheck.domain.ContractFileType;
import ac.mju.contractcheck.domain.OcrResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * macOS의 Vision 프레임워크로 OCR을 한다. 로컬 시연용이며 macOS에서만 동작한다(운영은 Upstage 구현체로 바꾼다).
 *
 * <p>Vision은 Swift/Objective-C API라 Java에서 바로 부를 수 없다. 그래서 {@code ocr/apple-vision/main.swift}를
 * 처음 쓸 때 {@code swiftc}로 한 번 컴파일(Xcode Command Line Tools 필요)해 임시 폴더에 두고,
 * 호출할 때마다 그 실행 파일을 별도 프로세스로 실행한다. 실행 파일 이름에 소스 해시가 들어 있어 소스가 바뀌면 다시 컴파일한다.
 * 첫 인식은 모델을 불러오느라 수십 초 걸릴 수 있고, 이후에는 1초 안팎이다.
 *
 * <p>계약서 내용은 임시 파일로만 넘기고 끝나면 지운다. 로그에는 쪽 수와 글자 수만 남긴다.
 * 이 구현체는 {@code contractcheck.ocr.provider=apple-vision}일 때만 빈으로 등록된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "contractcheck.ocr", name = "provider", havingValue = "apple-vision")
public class AppleVisionOcrClient implements OcrClient {

    private static final String SOURCE_RESOURCE = "/ocr/apple-vision/main.swift";
    private static final Duration COMPILE_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_ERROR_LENGTH = 200;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${contractcheck.ocr.timeout:120s}")
    private Duration timeout;

    private final Object compileLock = new Object();
    private volatile Path helper;

    @Override
    public OcrResult extract(byte[] content, ContractFileType type) {
        requireMacOs();
        Path binary = helper();
        Path input = null;
        try {
            input = Files.createTempFile("ocr-input-", suffix(type));
            Files.write(input, content);
            String json = run(List.of(binary.toString(), input.toString(), kind(type)), timeout);
            OcrResult result = parse(json);
            log.info("OCR 완료 provider=apple-vision pages={} chars={}", result.pageCount(), result.text().length());
            return result;
        } catch (IOException exception) {
            throw new OcrException("OCR 입력 파일을 다루지 못했습니다.", exception);
        } finally {
            deleteQuietly(input);
        }
    }

    private static void requireMacOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) {
            throw new OcrException("Apple Vision OCR은 macOS에서만 쓸 수 있습니다. contractcheck.ocr.provider 설정을 확인하세요.");
        }
    }

    private Path helper() {
        Path ready = helper;
        if (ready != null && Files.isExecutable(ready)) {
            return ready;
        }
        synchronized (compileLock) {
            if (helper == null || !Files.isExecutable(helper)) {
                helper = prepareHelper();
            }
            return helper;
        }
    }

    /** 소스 해시가 이름에 들어간 실행 파일이 이미 있으면 그대로 쓰고, 없으면 컴파일한다. */
    private Path prepareHelper() {
        Path work = null;
        try {
            byte[] source = readSource();
            Path directory = Path.of(System.getProperty("java.io.tmpdir"), "contractcheck-ocr");
            Files.createDirectories(directory);
            Path binary = directory.resolve("vision-ocr-" + shortHash(source));
            if (Files.isExecutable(binary)) {
                return binary;
            }
            work = Files.createTempDirectory(directory, "build-");
            Path swift = work.resolve("main.swift");
            Files.write(swift, source);
            Path built = work.resolve("vision-ocr");
            log.info("Apple Vision OCR 헬퍼를 컴파일합니다(처음 한 번)");
            run(List.of("swiftc", "-O", "-o", built.toString(), swift.toString()), COMPILE_TIMEOUT);
            Files.move(built, binary, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return binary;
        } catch (IOException exception) {
            throw new OcrException("Apple Vision OCR 헬퍼를 준비하지 못했습니다. Xcode Command Line Tools(swiftc)가 필요합니다.", exception);
        } finally {
            deleteTreeQuietly(work);
        }
    }

    private static byte[] readSource() throws IOException {
        try (InputStream stream = AppleVisionOcrClient.class.getResourceAsStream(SOURCE_RESOURCE)) {
            if (stream == null) {
                throw new IOException("OCR 헬퍼 소스가 없습니다: " + SOURCE_RESOURCE);
            }
            return stream.readAllBytes();
        }
    }

    private static String shortHash(byte[] source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", exception);
        }
    }

    /** 명령을 쉘 없이 실행하고 표준 출력을 돌려준다. 출력은 파일로 받아 파이프가 막히지 않게 한다. */
    private String run(List<String> command, Duration limit) throws IOException {
        Path out = Files.createTempFile("ocr-out-", ".txt");
        Path err = Files.createTempFile("ocr-err-", ".txt");
        try {
            Process process;
            try {
                process = new ProcessBuilder(command)
                        .redirectOutput(out.toFile())
                        .redirectError(err.toFile())
                        .start();
            } catch (IOException exception) {
                throw new OcrException("OCR 프로세스를 시작하지 못했습니다: " + command.get(0), exception);
            }
            if (!waitFor(process, limit)) {
                process.destroyForcibly();
                throw new OcrException("OCR 처리 시간 초과");
            }
            if (process.exitValue() != 0) {
                throw new OcrException("OCR 처리에 실패했습니다: " + firstLine(err));
            }
            return Files.readString(out, StandardCharsets.UTF_8);
        } finally {
            deleteQuietly(out);
            deleteQuietly(err);
        }
    }

    private static boolean waitFor(Process process, Duration limit) {
        try {
            return process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new OcrException("OCR 처리가 중단되었습니다.", exception);
        }
    }

    private static OcrResult parse(String json) {
        JsonNode pages = JSON.readTree(json).path("pages");
        if (!pages.isArray()) {
            throw new OcrException("OCR 결과 형식이 올바르지 않습니다.");
        }
        List<String> texts = new ArrayList<>();
        pages.forEach(page -> texts.add(page.asString("")));
        return new OcrResult(String.join("\n", texts).strip(), texts.size());
    }

    private static String firstLine(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).strip();
            String line = text.lines().findFirst().orElse("사유 없음");
            return line.length() > MAX_ERROR_LENGTH ? line.substring(0, MAX_ERROR_LENGTH) : line;
        } catch (IOException exception) {
            return "사유를 읽지 못함";
        }
    }

    private static String suffix(ContractFileType type) {
        return switch (type) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
        };
    }

    private static String kind(ContractFileType type) {
        return type == ContractFileType.PDF ? "pdf" : "image";
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("OCR 임시 파일을 지우지 못했습니다 path={}", path.getFileName());
        }
    }

    private static void deleteTreeQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(AppleVisionOcrClient::deleteQuietly);
        } catch (IOException exception) {
            log.warn("OCR 빌드 폴더를 지우지 못했습니다");
        }
    }
}
