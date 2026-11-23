package ac.mju.contractcheck.client.mac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ac.mju.contractcheck.client.OcrException;
import ac.mju.contractcheck.domain.ContractFileType;
import ac.mju.contractcheck.domain.OcrResult;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 실제 macOS Vision으로 인식하는 테스트다. 외부 서비스가 아니라 이 컴퓨터에서 도는 프로세스라 mock하지 않는다.
 * macOS가 아니거나 swiftc(Xcode Command Line Tools)가 없으면 건너뛴다. 그래서 Linux CI에서는 실행되지 않는다.
 * 처음 실행할 때는 헬퍼 컴파일과 모델 로딩으로 1분 가까이 걸릴 수 있다.
 */
class AppleVisionOcrClientTest {

    private AppleVisionOcrClient client;

    @BeforeAll
    static void requireMacWithSwift() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("mac"), "macOS가 아니라 건너뜀");
        assumeTrue(swiftAvailable(), "swiftc가 없어 건너뜀");
    }

    @BeforeEach
    void createClient() {
        client = new AppleVisionOcrClient();
        ReflectionTestUtils.setField(client, "timeout", Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("한국어 계약서 이미지는 조항 줄바꿈을 유지한 채 텍스트로 읽힌다")
    void readsKoreanContractImage() throws IOException {
        byte[] png = image(List.of(
                "임대차 계약서",
                "제1조(목적) 이 계약은 임대차에 관한 것이다.",
                "제2조(보증금) 보증금은 1,000만원으로 한다."));

        OcrResult result = client.extract(png, ContractFileType.PNG);

        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.text().lines().toList()).anyMatch(line -> line.startsWith("제1조(목적)"));
        assertThat(result.text()).contains("임대차 계약서", "제2조(보증금)", "1,000만원");
        assertThat(result.empty()).isFalse();
    }

    @Test
    @DisplayName("JPEG 이미지도 읽는다")
    void readsJpegImage() throws IOException {
        byte[] jpeg = image(List.of("제1조(목적) 이 계약은 임대차에 관한 것이다."), "jpg");

        OcrResult result = client.extract(jpeg, ContractFileType.JPEG);

        assertThat(result.text()).contains("제1조(목적)");
    }

    @Test
    @DisplayName("PDF는 쪽마다 읽어 쪽 수를 세고 쪽 순서대로 이어 붙인다")
    void readsEveryPdfPageInOrder() {
        byte[] pdf = pdf(List.of("Lease Contract Page One", "Deposit Page Two"));

        OcrResult result = client.extract(pdf, ContractFileType.PDF);

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.text()).contains("Page One", "Page Two");
        assertThat(result.text().indexOf("Page One")).isLessThan(result.text().indexOf("Page Two"));
    }

    @Test
    @DisplayName("글자가 없는 이미지는 예외 없이 빈 텍스트를 돌려준다")
    void blankImageReturnsEmptyText() throws IOException {
        OcrResult result = client.extract(image(List.of()), ContractFileType.PNG);

        assertThat(result.empty()).isTrue();
        assertThat(result.pageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미지가 아닌 바이트는 OcrException으로 실패한다")
    void corruptedFileFails() {
        byte[] notAnImage = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        assertThatThrownBy(() -> client.extract(notAnImage, ContractFileType.PNG))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("OCR 처리에 실패했습니다");
    }

    @Test
    @DisplayName("제한 시간을 넘기면 시간 초과 OcrException을 던진다")
    void exceedingTimeoutFails() throws IOException {
        client.extract(image(List.of("준비 운동")), ContractFileType.PNG);
        ReflectionTestUtils.setField(client, "timeout", Duration.ofMillis(1));

        assertThatThrownBy(() -> client.extract(image(List.of("제1조(목적)")), ContractFileType.PNG))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("시간 초과");
    }

    @Test
    @DisplayName("끝나면 임시 입력 파일을 남기지 않는다")
    void leavesNoTemporaryInputFiles() throws IOException {
        long before = countTempFiles("ocr-input-");

        client.extract(image(List.of("제1조(목적)")), ContractFileType.PNG);

        assertThat(countTempFiles("ocr-input-")).isEqualTo(before);
    }

    private static long countTempFiles(String prefix) throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix)).count();
        }
    }

    private static boolean swiftAvailable() {
        try {
            Process process = new ProcessBuilder("swiftc", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            return false;
        }
    }

    private static byte[] image(List<String> lines) throws IOException {
        return image(lines, "png");
    }

    private static byte[] image(List<String> lines, String format) throws IOException {
        BufferedImage image = new BufferedImage(1000, 80 + lines.size() * 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Apple SD Gothic Neo", Font.PLAIN, 28));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(lines.get(i), 30, 70 + i * 80);
        }
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }

    /** 영문 텍스트만 있는 최소한의 PDF를 만든다. 쪽마다 한 줄이다. */
    private static byte[] pdf(List<String> pageTexts) {
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new java.util.ArrayList<>();
        int pageCount = pageTexts.size();
        int fontObject = 3 + pageCount * 2;
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(3 + i * 2).append(" 0 R ");
        }
        offsets.add(pdf.length());
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets.add(pdf.length());
        pdf.append("2 0 obj\n<< /Type /Pages /Kids [").append(kids).append("] /Count ").append(pageCount).append(" >>\nendobj\n");
        for (int i = 0; i < pageCount; i++) {
            int pageObject = 3 + i * 2;
            String stream = "BT /F1 24 Tf 50 750 Td (" + pageTexts.get(i) + ") Tj ET";
            offsets.add(pdf.length());
            pdf.append(pageObject).append(" 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents ")
                    .append(pageObject + 1).append(" 0 R /Resources << /Font << /F1 ").append(fontObject).append(" 0 R >> >> >>\nendobj\n");
            offsets.add(pdf.length());
            pdf.append(pageObject + 1).append(" 0 obj\n<< /Length ").append(stream.length()).append(" >>\nstream\n")
                    .append(stream).append("\nendstream\nendobj\n");
        }
        offsets.add(pdf.length());
        pdf.append(fontObject).append(" 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(offsets.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
