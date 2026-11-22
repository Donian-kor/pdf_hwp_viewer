# 페이지 주석·표기 및 공유 기능 구현 가능성 검토

## 사용자 요구사항 (확정)

- 원문 PDF/HWP 파일 자체를 편집하거나 주석을 병합하지 않는다.
- 사용자가 문서를 열어 원하는 부분을 확대한 뒤, 해당 페이지에 동그라미·밑줄·글씨·자유 표기를 한다.
- 빨강/초록/파랑 3색을 사용자가 선택해 사용한다.
- 완료 후 **보내기** 버튼을 누르면, 주석이 표시된 **해당 페이지 전체**를 이미지로 저장/생성한다.
- 생성된 이미지를 카카오톡, 다른 공유 앱, 메일, 문자(MMS) 등 Android 공유 시트로 보낼 수 있어야 한다.
- 사용자가 보내기 대상을 직접 선택한다. 앱이 특정 앱/전화번호를 자동 지정하지 않는다.

## 결론

**구현 가능합니다.** 현재 앱 구조와 잘 맞는 방식은 다음과 같습니다.

> 주석을 원문에 저장하지 않고, **현재 페이지 전체를 이미지로 렌더링한 뒤 그 위에 주석을 그려 PNG로 만들고, Android `ACTION_SEND` 공유 시트로 전달**합니다.

이 방식은 PDF/HWP 모두에 적용할 수 있고, 원문 파일을 건드리지 않으며, 카카오톡·메일·문자 등은 Android 공유 인텐트가 처리하므로 앱이 각 서비스별 API를 따로 연동할 필요가 없습니다.

## 요구사항에 맞춘 동작 흐름

1. 사용자가 문서를 열고 페이지를 이동한다.
2. 확대/이동으로 원하는 부분을 확인한다.
3. 주석 도구를 선택한다.
   - 도구: 자유선/동그라미/밑줄/글씨/지우개
   - 색상: 빨강/초록/파랑
4. 화면에 주석을 그린다.
5. **보내기** 버튼을 누른다.
6. 앱이 현재 페이지 전체를 고해상도 이미지로 만든다.
   - PDF: `PdfRenderer`로 현재 페이지를 다시 렌더링해 Bitmap 생성
   - HWP: WebView에서 `/page` 또는 JS API로 현재 페이지 SVG/이미지를 추출
7. 그 Bitmap/SVG 위에 저장된 주석 좌표를 그려 최종 PNG를 만든다.
8. PNG을 앱 내부 캐시에 저장하고 `FileProvider` URI로 `ACTION_SEND` 인텐트를 실행한다.
9. 사용자가 카카오톡/메일/문자/기타 앱을 선택해 전송한다.

## 현재 코드 기준 분석

### PDF

- [PdfReaderView.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/reader/PdfReaderView.kt)
  - 이미 페이지별 Bitmap 렌더링, 더블탭/핀치 확대, 가로 스크롤을 지원합니다.
  - 주석은 `Image` 위에 Compose `Canvas`를 겹치는 방식으로 추가할 수 있습니다.
- [PdfSession.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/reader/PdfSession.kt)
  - `renderPage(pageIndex, requestedWidthPx)`가 있어, 공유용 고해상도 페이지 이미지를 다시 만들 수 있습니다.
  - 현재 최대 폭 2048px, 최대 400만 픽셀 제한이 있어 공유용 이미지도 이 범위에서 생성하면 안전합니다.

**PDF 공유 이미지 생성:** 현재 화면의 확대 상태와 무관하게, 페이지 원본 기준 고해상도 Bitmap을 새로 렌더링한 뒤 주석을 정규화 좌표로 그리면 됩니다. 이렇게 하면 사용자가 확대해서 본 위치와 상관없이 **페이지 전체**가 공유됩니다.

### HWP/HWPX

- [HwpReaderView.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/reader/HwpReaderView.kt)
  - WebView는 선택된 문서와 번들 리소스만 허용하는 샌드박스입니다.
  - `AndroidBridge`로 `onDocumentLoaded`, `onPageChanged`, `onError`만 주고받습니다.
- [main.js](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/web/src/main.js)
  - rhwp가 `renderPageSvg(index)`로 페이지 SVG를 만들고 `<img>`로 표시합니다.
  - 현재 페이지는 IntersectionObserver로 추적합니다.

**HWP 공유 이미지 생성:** WebView 안에서 현재 페이지의 SVG 문자열을 얻는 JS 함수를 추가하고, AndroidBridge로 전달하거나, WebView 전체/페이지元素을 Canvas로 캡처하는 방법이 있습니다. SVG를 문자열로 받아 Android에서 Bitmap으로 변환하는 방식이 품질과 안정성 면에서 유리합니다.

### 공유 인텐트

- 현재 앱에는 `FileProvider`가 없습니다. 공유용 이미지 URI를 안전하게 전달하려면 추가가 필요합니다.
- Android 7.0(API 24) 이상에서는 `file://` URI를 외부 앱에 넘기면 `FileUriExposedException`이 발생하므로, `FileProvider` + `content://` URI가 필수입니다.
- AndroidManifest에 provider 등록, `file_paths.xml` 추가, `grantUriPermissions=true` 설정이 필요합니다.

```kotlin
val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    sharedImageFile,
)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(intent, "주석 페이지 공유"))
```

이 인텐트 카카오톡, Gmail, 기본 메일 앱, 문자(MMS), 드라이브 등이 공유 대상으로 표시됩니다. 앱은 사용자가 선택한 대상으로만 전송합니다.

## 구현 방식 비교

| 항목 | PDF | HWP/HWPX |
|---|---|---|
| 주석 입력 | Compose Canvas 오버레이 (쉬움) | WebView 내부 Canvas/SVG 오버레이 또는 Compose 오버레이 (중간) |
| 공유용 원본 페이지 | `PdfRenderer`로 High-res Bitmap 재생성 (쉼) | rhwp `renderPageSvg()`를 JS로 추출 (중간) |
| 주석 합성 | Android Canvas로 Bitmap에 그리기 (쉼) | SVG에 주석 SVG를 삽입하거나 Bitmap 위에 그리기 (중간) |
| 공유 | `ACTION_SEND` + FileProvider (공통, 쉼) | 동일 |

## 권장 설계

### 1) 주석 좌표 저장

```kotlin
@Entity(tableName = "page_annotations", foreignKeys = [/* documentId CASCADE */])
data class PageAnnotationEntity(
    val id: String,
    val documentId: String,
    val pageIndex: Int,          // 0-based
    val tool: String,            // FREEHAND / CIRCLE / UNDERLINE / TEXT
    val color: String,           // RED / GREEN / BLUE
    val points: String,          // JSON: [[x,y], ...], 0~1 정규화 좌표
    val text: String? = null,
    val createdAt: Long,
)
```

- 좌표는 **페이지 전체 기준 0~1 정규화 좌표**로 저장합니다.
- 이렇게 하면 화면 확대/축소와 무관하게 공유 이미지에 동일하게 그릴 수 있습니다.

### 2) 색상

```kotlin
enum class AnnotationColor(val hex: String, val argb: Int) {
    RED("#FF3B30", 0xFFFF3B30.toInt()),
    GREEN("#34C759", 0xFF34C759.toInt()),
    BLUE("#007AFF", 0xFF007AFF.toInt()),
}
```

### 3) 공유 이미지 생성

```kotlin
fun buildSharedPageImage(
    basePageImage: Bitmap,
    annotations: List<PageAnnotation>,
    pageWidth: Int,
    pageHeight: Int,
): Bitmap {
    val output = basePageImage.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(output)
    annotations.forEach { annotation ->
        // 정규화 좌표 * pageWidth/Height 로 변환해 그린다.
    }
    return output
}
```

- PDF는 `basePageImage = session.renderPage(pageIndex, 1600~2048px)`.
- HWP는 JS에서 받은 SVG를 Android Bitmap으로 변환한 뒤 사용합니다.

### 4) 공유 파일 저장 위치

- `cacheDir/shared/` 또는 `filesDir/shared/`에 `docuview-page-<timestamp>.png`으로 저장합니다.
- 공유 후에는 삭제하지 않아도 됩니다. 앱 캐시 비우기/삭제 시 정리됩니다.
- `FileProvider`는 이 디렉터리만 외부 앱에 노출합니다.

## 필요한 변경 파일

| 파일/영역 | 변경 내용 |
|---|---|
| `app/src/main/AndroidManifest.xml` | FileProvider 등록 |
| `app/src/main/res/xml/file_paths.xml` (신규) | 공유 이미지 캐시 경로 설정 |
| `data/PageAnnotationEntity.kt` (신규) | 주석 엔티티 |
| `data/DocumentDao.kt` | 주석 조회/저장/삭제 쿼리 |
| `data/DocumentDatabase.kt` | DB version 2 + migration |
| `ui/ReaderScreen.kt` | 주석 툴바, 색상 선택, 보내기 버튼 |
| `reader/PdfReaderView.kt` | 주석 Canvas 오버레이, 현재 페이지/줌 상태 전달 |
| `reader/HwpReaderView.kt` | WebView 주석 오버레이 및 공유용 SVG 추출 브리지 |
| `web/src/main.js` | HWP 페이지 SVG 추출/주석 레이어 함수 |
| `util/PageShareHelper.kt` (신규) | 이미지 저장 + ACTION_SEND 공유 |
| 개인정보처리방침 | 주석/페이지 이미지 공유 기능 설명 추가 |

## 단계별 구현 권장안

### 1차: PDF 페이지 주석 + 공유

- PDF 현재 페이지를 고해상도 Bitmap으로 재렌더
- 자유선/동그라미/밑줄/글씨 + 3색
- 주석 저장/복원(현재 세션 또는 Room)
- `ACTION_SEND`로 PNG 공유

### 2차: HWP 페이지 주석 + 공유

- `web/src/main.js`에 현재 페이지 SVG 추출 함수 추가
- AndroidBridge 확장
- SVG→Bitmap 변환 후 주석 합성
- WebView 제스처와 주석 제스처 충돌 처리

### 3차: 고도화

- 실행 취소/다시 실행
- 주석만 지우기 / 페이지 초기화
- 공유 이미지 해상도 선택
- 공유 전 미리보기

## 주의사항

- 공유는 **사용자가 보내기 버튼을 누른 뒤 Android 공유 시트에서 대상을 선택**하는 방식입니다. 카카오톡/메일/문자 앱을 앱이 자동 실행하거나 전화번호를 자동 입력하지 않습니다.
- MMS 문자로 이미지를 보내면 통신사/기기 정책에 따라 압축되거나 전송 제한이 있을 수 있습니다. 앱은 공유 인텐트까지만 책임집니다.
- HWP는 rhwp가 생성한 SVG를 사용하므로, 폰트/벡터 처리 차이로 공유 이미지가 화면과 완전히 동일하지 않을 수 있습니다.
- 주석은 원문에 병합되지 않고 **페이지 이미지에만 표시**됩니다. 받는 사람은 PDF/HWP 원문이 아니라 주석이 포함된 **페이지 이미지(PNG)**를 받습니다.
- 기존 AGENTS.md에 따라 HWP 렌더러 변경은 `web/src/main.js`와 Kotlin 브리지를 함께 수정하고 `web` 빌드를 다시 수행해야 합니다.

## 최종 판단

요청한 기능은 **구현 가능**하며, 원문 수정 없이 **현재 페이지 전체 이미지 + 주석 + Android 공유 시트**라는 명확한 범위로 설계할 수 있습니다. PDF는 기존 렌더링 구조 덕분에 비교적 빠르게 구현할 수 있고, HWP는 SVG 추출 및 WebView 제스처 처리 때문에 추가 작업이 필요합니다.
