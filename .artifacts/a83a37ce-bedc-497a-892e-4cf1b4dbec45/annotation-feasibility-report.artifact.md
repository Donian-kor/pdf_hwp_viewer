# 문서 확대·주석 기능 구현 가능성 검토

## 결론

구현 가능합니다. 다만 문서 형식(PDF / HWP)에 따라 난이도와 방식이 크게 다릅니다.

| 기능 | PDF | HWP/HWPX | 비고 |
|---|---|---|---|
| 확대 후 보기 | 이미 지원(더블탭 2배, 핀치 1~4배) | 이미 지원(WebView 줌) | 기존 코드 확인됨 |
| 동그라미·밑줄·글씨 | 비교적 쉬움 | 중간~어려움 | HWP는 좌표 기준이 불안정 |
| 빨강/초록/파랑 선택 | 쉬움 | 쉬움 | UI + 색상 상태 공통화 |
| 주석 저장/재표시 | 중간 | 중간~어려움 | Room DB 확장 필요 |
| 원문 PDF/HWP에 실제 저장 | 어려움 | 매우 어려움 | 별도 저장/병합 로직 필요 |

현재 앱은 **읽기 전용 뷰어**이므로, 주석은 문서 원본을 수정하지 않고 **별도 레이어 + Room DB**로 저장하는 방식이 현재 아키텍처에 가장 적합합니다.

## 현재 코드 기준 분석

### PDF 뷰어

- [PdfReaderView.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/reader/PdfReaderView.kt)
  - `PdfPage`가 페이지별 Bitmap을 `Image`로 표시합니다.
  - 더블탭 확대(2배), 핀치 확대(1~4배), 가로 스크롤이 이미 구현되어 있습니다.
  - 페이지는 `LazyColumn`으로 세로 스크롤됩니다.
- [PdfSession.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/reader/PdfSession.kt)
  - Android `PdfRenderer`로 Bitmap을 렌더링합니다.
  - 확대는 Bitmap 재렌더가 아니라 Compose 크기 변환(`imageWidth * zoom`)입니다.

**주석 구현 관점:** 이미지 위에 Compose `Canvas`를 겹쳐주면 됩니다. 터치 좌표를 Bitmap 기준 정규화 좌표(0~1)로 저장하면 확대/화면 크기 변화와 무관하게 다시 그릴 수 있습니다.

### HWP 뷰어

- [HwpReaderView.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/reader/HwpReaderView.kt)
  - 로컬 WebView에서 `hwpviewer/index.html`을 로드합니다.
  - `LocalHwpWebViewClient`가 선택된 문서와 번들 asset만 허용합니다.
  - WebView 줌(`setSupportZoom(true)`)이 켜져 있습니다.
- [main.js](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/web/src/main.js)
  - rhwp로 페이지를 SVG로 렌더링한 뒤 `<img>`로 표시합니다.
  - IntersectionObserver로 현재 페이지를 추적합니다.

**주석 구현 관점:** WebView 내부의 SVG 이미지 위에 HTML Canvas 오버레이를 두거나, WebView 바깥에 Compose 오버레이를 올려야 합니다. 그러나 HWP는 페이지가 WebView 스크롤 안에 있고 확대 배율도 WebView가 관리하므로, **정확한 좌표 추적은 PDF보다 복잡**합니다. 특히 WebView 줌 상태와 스크롤 오프셋을 JS→Android 브리지로 전달해야 정확한 저장/복원이 가능합니다.

### 공통 UI / 데이터

- [ReaderScreen.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/ui/ReaderScreen.kt)
  - PDF/HWP 분기는 여기서 처리합니다.
  - 주석 도구 UI(색상 선택, 도구 선택)를 이 화면의 툴바에 추가할 수 있습니다.
- [DocumentDao.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/data/DocumentDao.kt), [DocumentEntity.kt](file:///C:/Users/donian/Desktop/android_app/pdf_hwp_viewer/app/src/main/java/kr/docuview/app/data/DocumentEntity.kt)
  - 현재 documents, bookmarks 테이블만 존재합니다.
  - 주석용 엔티티/DAO 추가 필요합니다.

## 제안 구현 방식

### 1) 주석 데이터 모델

```kotlin
@Entity(tableName = "annotations", foreignKeys = [/* documentId CASCADE */])
data class AnnotationEntity(
    val id: String,              // UUID
    val documentId: String,
    val pageIndex: Int,          // 0-based
    val tool: String,            // CIRCLE / UNDERLINE / TEXT
    val color: String,           // RED / GREEN / BLUE 또는 hex
    val points: String,          // JSON: 정규화 좌표 목록
    val text: String? = null,    // TEXT 도구용
    val createdAt: Long,
)
```

- 좌표는 화면 픽셀이 아니라 **페이지 내부 정규화 좌표(0~1)**로 저장합니다.
- 이렇게 하면 확대 배율, 기기 해상도, 회전 후에도 동일 위치에 표시됩니다.

### 2) 도구 UI

- ReaderScreen 상단 또는 하단에 주석 툴바 추가:
  - 도구: 선택/동그라미/밑줄/글씨/지우개
  - 색상: 빨강/초록/파랑 3종 버튼
  - 실행 취소/저장/주석 보기 토글

### 3) PDF 구현 흐름

1. `PdfPage`의 `Image` 위에 `Canvas(modifier = Modifier.pointerInput(...))`을 겹친다.
2. 사용자가 드래그하면 시작/끝 좌표를 페이지 크기로 나눠 정규화한다.
3. `AnnotationEntity`를 Room에 저장한다.
4. 페이지 렌더 시 해당 페이지의 주석을 읽어 같은 좌표에 다시 그린다.
5. 동그라미: `drawEllipse`, 밑줄: `drawLine`, 글씨: `drawText` 또는 텍스트 입력 다이얼로그 후 배치.

### 4) HWP 구현 흐름

두 가지 선택지가 있습니다.

#### A. WebView 내부 오버레이 (권장)

- `web/src/main.js`에 주석 레이어를 추가하고, rhwp가 만든 각 `.page` 위에 절대 위치 `<canvas>` 또는 `<svg>`를 겹친다.
- AndroidBridge에 다음 메서드 추가:
  - `getAnnotations(documentId, pageIndex)`
  - `saveAnnotation(...)`
  - `getViewState()` (현재 줌/스크롤/페이지 정보)
- `web`을 다시 빌드해 `app/src/main/assets/hwpviewer`를 생성해야 합니다. 기존 AGENTS.md 규칙에 따라 생성 자산은 직접 수정하지 않습니다.

#### B. Compose 오버레이

- WebView 위에 투명 Compose Canvas를 올린다.
- WebView 내부 좌표를 알기 어렵고 제스처 충돌 가능성이 있어, 단순 구현에는 적합하지만 정확도는 떨어집니다.

권장안은 **A번(WebView 내부 오버레이)**입니다.

### 5) 색상 처리

```kotlin
enum class AnnotationColor(val argb: Long) {
    RED(0xFFFF3B30),
    GREEN(0xFF34C759),
    BLUE(0xFF007AFF),
}
```

UI에서는 3개 색상 버튼으로 선택하고, 선택 색상을 `ReaderScreen` 상태로 보관한 뒤 각 뷰어로 전달합니다.

## 난이도 및 공수 추정

| 단계 | 설명 | 상대 공수 |
|---|---|---|
| 1 | 주석 엔티티/DAO + DB 마이그레이션 | 작음 |
| 2 | 공통 툴바(도구/색상) UI | 작음~중간 |
| 3 | PDF 주석 그리기/저장/복원 | 중간 |
| 4 | PDF 지우개/되돌리기 | 중간 |
| 5 | HWP 주석 오버레이 + 브리지 확장 | 큼 |
| 6 | 두 문서 형식 모두 테스트/디버깅 | 중간~큼 |

전체 기능을 한 번에 구현한다면 **중간~큰 변경**입니다. 특히 HWP 쪽이 변수가 많습니다.

## 주의사항 / 제약

- 이 기능은 “문서 내용 위에 표시”하는 방식이지, PDF/HWP 파일 자체에 주석을 병합하는 기능은 아닙니다. 실제 파일 병합까지 원한다면 PDF는 별도 라이브러리, HWP는 이 rhwp 엔진이 쓰기 API를 제공하지 않으므로 사실상 불가능에 가깝습니다.
- 기존 AGENTS.md에 따라 HWP 렌더러 관련 변경은 Kotlin 브리지와 `web/src/main.js`를 함께 수정하고, `web`에서 빌드해야 합니다.
- Room schema가 version 1이므로 주석 테이블 추가 시 migration(`version = 2`)이 필요합니다. 현재는 `exportSchema = true`이므로 마이그레이션 처리도 함께 해야 합니다.
- 개인정보처리방침에 “읽기 전용, 주석 미제공”이라고 명시된 부분이 있다면, 기능 구현 후 문서도 함께 갱신해야 합니다.

## 단계별 구현 권장안

1. **1차 범위: PDF만 구현**
   - 동그라미/밑줄/글씨 + 3색 + 저장/복원/삭제
   - HWP는 기존대로 두고 PDF에서 UX를 검증
2. **2차 범위: HWP 구현**
   - WebView 내부 오버레이 + AndroidBridge 확장
   - 문서 찾기/페이지 이동/줌과의 충돌 테스트
3. **3차 범위: 고도화**
   - 되돌리기/다시 실행, 주석 목록, 색상별 필터, 내보내기/이미지 저장

## 최종 판단

요청한 기능은 **충분히 구현 가능**합니다. 현재 앱이 이미 페이지 단위 렌더링과 확대를 지원하고 있어 PDF는 비교적 빠르게 추가할 수 있습니다. HWP는 WebView 좌표/줌 처리 때문에 더 많은 작업이 필요하지만, rhwp 렌더 결과가 페이지 단위 SVG 이미지라는 점을 활용하면 오버레이 방식으로 구현할 수 있습니다.
