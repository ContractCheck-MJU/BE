// Apple Vision으로 PDF·이미지에서 텍스트를 읽어 JSON({"pages": ["1쪽 텍스트", ...]})으로 표준 출력에 쓴다.
// 사용법: vision-ocr <파일 경로> <pdf|image>
// 실패하면 표준 오류에 사유를 쓰고 0이 아닌 코드로 끝난다. 계약서 내용은 표준 오류에 쓰지 않는다.
import CoreGraphics
import Foundation
import ImageIO
import PDFKit
import Vision

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

func recognize(_ image: CGImage) throws -> String {
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.usesLanguageCorrection = true
    let wanted = ["ko-KR", "en-US"]
    let supported = (try? request.supportedRecognitionLanguages()) ?? []
    let languages = wanted.filter { supported.contains($0) }
    if !languages.isEmpty {
        request.recognitionLanguages = languages
    }
    try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
    // Vision은 같은 줄의 글을 여러 글상자로 나눠 줄 때가 많다(예: "제 2 조"와 본문, 표의 라벨 칸과 값 칸).
    // 위에서 아래로 정렬한 뒤 세로 위치가 겹치는 글상자를 한 줄로 묶고, 줄 안에서는 왼쪽에서 오른쪽 순서로 잇는다.
    let boxes = (request.results ?? []).filter { $0.topCandidates(1).first != nil }
        .sorted { $0.boundingBox.midY > $1.boundingBox.midY }
    var rows: [[VNRecognizedTextObservation]] = []
    for box in boxes {
        if let first = rows.last?.first,
           abs(first.boundingBox.midY - box.boundingBox.midY) < 0.6 * min(first.boundingBox.height, box.boundingBox.height) {
            rows[rows.count - 1].append(box)
        } else {
            rows.append([box])
        }
    }
    return rows.map { row in
        row.sorted { $0.boundingBox.minX < $1.boundingBox.minX }
            .compactMap { $0.topCandidates(1).first?.string }
            .joined(separator: " ")
    }.joined(separator: "\n")
}

func render(_ page: PDFPage, scale: CGFloat) -> CGImage? {
    let bounds = page.bounds(for: .mediaBox)
    let width = Int(bounds.width * scale)
    let height = Int(bounds.height * scale)
    guard width > 0, height > 0,
          let context = CGContext(
              data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
              space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
    else { return nil }
    context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: width, height: height))
    context.scaleBy(x: scale, y: scale)
    page.draw(with: .mediaBox, to: context)
    return context.makeImage()
}

let arguments = CommandLine.arguments
guard arguments.count == 3 else { fail("usage: vision-ocr <path> <pdf|image>") }
let url = URL(fileURLWithPath: arguments[1])
var pages: [String] = []

do {
    switch arguments[2] {
    case "pdf":
        guard let document = PDFDocument(url: url), document.pageCount > 0 else { fail("PDF를 열 수 없습니다") }
        for index in 0..<document.pageCount {
            guard let page = document.page(at: index), let image = render(page, scale: 4) else {
                fail("PDF \(index + 1)쪽을 그릴 수 없습니다")
            }
            pages.append(try recognize(image))
        }
    case "image":
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
        else { fail("이미지를 열 수 없습니다") }
        pages.append(try recognize(image))
    default:
        fail("지원하지 않는 종류입니다: \(arguments[2])")
    }
    let data = try JSONSerialization.data(withJSONObject: ["pages": pages], options: [])
    FileHandle.standardOutput.write(data)
} catch {
    fail("인식에 실패했습니다: \(error.localizedDescription)")
}
