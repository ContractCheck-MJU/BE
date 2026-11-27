# Windows 내장 OCR(Windows.Media.Ocr)로 PDF·이미지에서 텍스트를 읽어 JSON({"pages": ["1쪽 텍스트", ...]})으로 표준 출력에 쓴다.
# 사용법: powershell -File ocr.ps1 <파일 경로> <pdf|image>
# 실패하면 표준 오류에 사유를 쓰고 0이 아닌 코드로 끝난다. 계약서 내용은 표준 오류에 쓰지 않는다.
# Windows PowerShell 5.1(Windows 10 이상 기본 탑재)에서 돈다. 한국어 인식은 한국어 언어팩(OCR 포함)이 있어야 한다.
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Kind
)

$ErrorActionPreference = 'Stop'
# 표준 오류의 한글이 깨지지 않게 UTF-8로 쓴다(콘솔이 없으면 무시).
try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch { }

function Fail([string]$message) {
    [Console]::Error.WriteLine($message)
    exit 1
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
    $null = [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
    $null = [Windows.Media.Ocr.OcrEngine, Windows.Media.Ocr, ContentType = WindowsRuntime]
    $null = [Windows.Media.Ocr.OcrResult, Windows.Media.Ocr, ContentType = WindowsRuntime]
    $null = [Windows.Globalization.Language, Windows.Globalization, ContentType = WindowsRuntime]
    $null = [Windows.Data.Pdf.PdfDocument, Windows.Data.Pdf, ContentType = WindowsRuntime]
} catch {
    Fail 'Windows OCR API를 불러오지 못했습니다. Windows 10 이상의 Windows PowerShell 5.1이 필요합니다.'
}

$asTaskOperation = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
$asTaskAction = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
} | Select-Object -First 1

function Await([object]$operation, [Type]$resultType) {
    $task = $asTaskOperation.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

function AwaitAction([object]$action) {
    $task = $asTaskAction.Invoke($null, @($action))
    $task.Wait(-1) | Out-Null
}

# 한국어 엔진을 우선 쓰고, 없으면 사용자 프로필 언어로 만든다.
$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage([Windows.Globalization.Language]::new('ko'))
if ($null -eq $engine) {
    $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
}
if ($null -eq $engine) {
    Fail 'Windows OCR 엔진을 만들 수 없습니다. 설정에서 한국어 언어팩(OCR 포함)을 설치하세요.'
}
$maxDimension = [Windows.Media.Ocr.OcrEngine]::MaxImageDimension

# 스트림의 이미지를 디코딩해 인식한다. 엔진이 받는 최대 크기를 넘으면 비율을 유지해 줄인다.
function Recognize([object]$stream) {
    $decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $width = [uint32]$decoder.PixelWidth
    $height = [uint32]$decoder.PixelHeight
    $transform = [Windows.Graphics.Imaging.BitmapTransform]::new()
    $longest = [Math]::Max($width, $height)
    if ($longest -gt $maxDimension) {
        $ratio = $maxDimension / $longest
        $transform.ScaledWidth = [uint32][Math]::Max(1, [Math]::Floor($width * $ratio))
        $transform.ScaledHeight = [uint32][Math]::Max(1, [Math]::Floor($height * $ratio))
    }
    $bitmap = Await ($decoder.GetSoftwareBitmapAsync(
            [Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8,
            [Windows.Graphics.Imaging.BitmapAlphaMode]::Premultiplied,
            $transform,
            [Windows.Graphics.Imaging.ExifOrientationMode]::RespectExifOrientation,
            [Windows.Graphics.Imaging.ColorManagementMode]::DoNotColorManage)) ([Windows.Graphics.Imaging.SoftwareBitmap])
    $result = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
    $lines = @($result.Lines | ForEach-Object { $_.Text })
    return ($lines -join "`n")
}

$pages = New-Object System.Collections.Generic.List[string]
try {
    $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($Path)) ([Windows.Storage.StorageFile])
    switch ($Kind) {
        'image' {
            $stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
            try { $pages.Add((Recognize $stream)) } finally { $stream.Dispose() }
        }
        'pdf' {
            $document = Await ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($file)) ([Windows.Data.Pdf.PdfDocument])
            if ($document.PageCount -lt 1) { Fail 'PDF에 쪽이 없습니다' }
            for ($index = 0; $index -lt $document.PageCount; $index++) {
                $page = $document.GetPage($index)
                $memory = [Windows.Storage.Streams.InMemoryRandomAccessStream]::new()
                try {
                    $options = [Windows.Data.Pdf.PdfPageRenderOptions]::new()
                    # 쪽 크기(96dpi 기준)의 3배로 그리되 엔진 한도를 넘지 않게 한다. 투명 배경은 흰색으로 채운다.
                    $scale = [Math]::Min(3.0, $maxDimension / [Math]::Max($page.Size.Width, $page.Size.Height))
                    $options.DestinationWidth = [uint32][Math]::Max(1, [Math]::Floor($page.Size.Width * $scale))
                    $options.DestinationHeight = [uint32][Math]::Max(1, [Math]::Floor($page.Size.Height * $scale))
                    $white = [Activator]::CreateInstance($options.GetType().GetProperty('BackgroundColor').PropertyType)
                    $white.A = 255; $white.R = 255; $white.G = 255; $white.B = 255
                    $options.BackgroundColor = $white
                    AwaitAction ($page.RenderToStreamAsync($memory, $options))
                    $pages.Add((Recognize $memory))
                } finally {
                    $memory.Dispose()
                    $page.Dispose()
                }
            }
        }
        default { Fail "지원하지 않는 종류입니다: $Kind" }
    }
} catch {
    Fail ('인식에 실패했습니다: ' + $_.Exception.GetType().Name)
}

# BOM 없는 UTF-8로 쓴다. 한 쪽뿐이어도 배열로 직렬화되도록 pages를 명시적으로 배열로 둔다.
$json = ConvertTo-Json -InputObject @{ pages = $pages.ToArray() } -Compress
$bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($json)
$out = [Console]::OpenStandardOutput()
$out.Write($bytes, 0, $bytes.Length)
$out.Flush()
