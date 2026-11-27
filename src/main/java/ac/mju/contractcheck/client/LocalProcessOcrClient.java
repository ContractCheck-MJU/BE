package ac.mju.contractcheck.client;

import ac.mju.contractcheck.domain.OcrResult;
import ac.mju.contractcheck.domain.enums.ContractFileType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이 컴퓨터의 OS 내장 OCR을 별도 프로세스로 부르는 구현체의 공통 부분이다.
 *
 * <p>하위 클래스는 리소스에 담긴 헬퍼 소스를 실행할 수 있는 파일로 준비하는 방법과 실행 명령만 정한다.
 * 헬퍼는 처음 쓸 때 임시 폴더에 한 번 준비하고, 파일 이름에 소스 해시를 넣어 소스가 바뀌면 다시 준비한다.
 * 헬퍼는 {@code <입력 파일> <pdf|image>}를 받아 {@code {"pages": ["1쪽 텍스트", ...]}} JSON을 표준 출력에 써야 한다.
 *
 * <p>계약서 내용은 임시 파일로만 넘기고 끝나면 지운다. 로그에는 쪽 수와 글자 수만 남긴다.
 */
@Slf4j
public abstract class LocalProcessOcrClient implements OcrClient {

    private static final int MAX_ERROR_LENGTH = 200;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${contractcheck.ocr.timeout:120s}")
    private Duration timeout;

    private final Object prepareLock = new Object();
    private volatile Path helper;

    @Override
    public final OcrResult extract(byte[] content, ContractFileType type) {
        requireSupportedOs(System.getProperty("os.name", "").toLowerCase(Locale.ROOT));
        Path ready = helper();
        Path input = null;
        try {
            input = Files.createTempFile("ocr-input-", suffix(type));
            Files.write(input, content);
            String json = run(command(ready, input, kind(type)), timeout);
            OcrResult result = parse(json);
            log.info("OCR 완료 provider={} pages={} chars={}", provider(), result.pageCount(), result.text().length());
            return result;
        } catch (IOException exception) {
            throw new OcrException("OCR 입력 파일을 다루지 못했습니다.", exception);
        } finally {
            deleteQuietly(input);
        }
    }

    /** {@code contractcheck.ocr.provider}에 쓰는 이름. */
    protected abstract String provider();

    /**
     * 지원하지 않는 OS면 {@link OcrException}을 던진다.
     *
     * @param osName 소문자로 바꾼 {@code os.name}
     */
    protected abstract void requireSupportedOs(String osName);

    /** 헬퍼 소스의 클래스패스 위치. */
    protected abstract String sourceResource();

    /** 헬퍼를 준비하다 입출력에 실패했을 때의 예외 메시지. */
    protected abstract String prepareFailureMessage();

    /**
     * 헬퍼를 {@code directory}에 준비해 그 경로를 돌려준다. 같은 해시의 헬퍼가 이미 있으면 그대로 쓴다.
     */
    protected abstract Path prepareHelper(Path directory, String sourceHash, byte[] source) throws IOException;

    /** 준비해 둔 헬퍼를 다시 쓸 수 있는지 확인한다. */
    protected abstract boolean isUsable(Path helper);

    /** 헬퍼를 실행하는 명령. */
    protected abstract List<String> command(Path helper, Path input, String kind);

    /** 명령을 쉘 없이 실행하고 표준 출력을 돌려준다. 출력은 파일로 받아 파이프가 막히지 않게 한다. */
    protected final String run(List<String> command, Duration limit) throws IOException {
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
                throw new OcrTimeoutException("OCR 처리 시간 초과");
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

    protected static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("OCR 임시 파일을 지우지 못했습니다 path={}", path.getFileName());
        }
    }

    private Path helper() {
        Path ready = helper;
        if (ready != null && isUsable(ready)) {
            return ready;
        }
        synchronized (prepareLock) {
            if (helper == null || !isUsable(helper)) {
                helper = prepare();
            }
            return helper;
        }
    }

    private Path prepare() {
        try {
            byte[] source = readSource();
            Path directory = Path.of(System.getProperty("java.io.tmpdir"), "contractcheck-ocr");
            Files.createDirectories(directory);
            return prepareHelper(directory, shortHash(source), source);
        } catch (IOException exception) {
            throw new OcrException(prepareFailureMessage(), exception);
        }
    }

    private byte[] readSource() throws IOException {
        try (InputStream stream = LocalProcessOcrClient.class.getResourceAsStream(sourceResource())) {
            if (stream == null) {
                throw new IOException("OCR 헬퍼 소스가 없습니다: " + sourceResource());
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
}
