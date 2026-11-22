# DocuView (문서뷰어)

Android용 PDF / HWP / HWPX 통합 문서 뷰어입니다. 최근 문서 목록, 파일 탐색, 외부 파일 열기, 독자 모드, 북마크, 주석(자유 그리기·형광펜) 및 페이지 공유 기능을 제공합니다.

## 최신 버전

**1.4.0** (`versionCode = 5`)

---

## 주요 기능

### 문서 열람
- **PDF**: Android `PdfRenderer` 기반 페이지 렌더링, 세로 스크롤, 핀치 줌, 더블탭 확대
- **HWP/HWPX**: `@rhwp/core` WebAssembly 렌더러를 Android WebView에 내장
- 페이지 동기화: 스크롤 시 현재 페이지 자동 추적 및 표시
- 마지막 읽은 페이지 자동 복원

### 주석 (Annotation)
- **도구**: 자유 그리기(PEN), 형광펜(HIGHLIGHT)
- **5가지 색상**: 빨강, 주황, 노랑, 초록, 파랑
- **저장**: Room DB에 자동 저장되어 앱 재실행 후에도 유지
- **되돌리기**: 현재 페이지의 마지막 주석을 삭제
- **초기화**: 문서 전체의 모든 주석을 한 번에 삭제
- **공유**: 주석이 포함된 페이지를 PNG 이미지로 생성하여 Android 공유 시트(카카오톡, 메일, 메시지 등)로 전송
  - 주석이 있는 **모든 페이지**를 각각 PNG로 만들어 한 번에 공유 (`ACTION_SEND_MULTIPLE`)

### 문서 관리
- Android 시스템 파일 선택기(`ACTION_OPEN_DOCUMENT`)로 가져오기
- 원본 파일 사본을 앱 내부 저장소(`filesDir/documents/`)에 보관
- SHA-256 해시 기반 중복 제거
- 파일 크기 제한:
  - PDF: 최대 256 MiB
  - HWP/HWPX: 최대 64 MiB
- 최근 문서 목록 및 문서 찾기 (검색어 자동완성, 캐싱)
- 문서별 북마크 (페이지 추가/삭제/이동)
- 외부 앱에서 PDF/HWP/HWPX 파일을 열 때 DocuView로 연결

### 보안 및 프라이버시
- **인터넷 권한 없음**: WebView는 로컬 문서와 앱 내부 리소스만 로드
- WebView 새로고침·외부 링크 차단으로 보안 강화
- 앱 내부 개인정보처리방침 화면 제공

### UI/UX
- Jetpack Compose 기반 Material 3 디자인
- 라이트/다크 테마 지원
- 반응형 레이아웃 (좁은 화면에서 주석 툴바가 아이콘 모드로 전환)
- 삭제 확인 다이얼로그에 경고 색상 및 명확한 안내
- 시스템 하단 네비게이션바 항상 표시

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| **언어** | Kotlin |
| **UI** | Jetpack Compose, Material 3 |
| **최소 SDK** | 28 (Android 9) |
| **타겟 SDK** | 36 |
| **컴파일 SDK** | 37 |
| **DB** | Room 2.8.5 |
| **PDF** | Android `PdfRenderer` |
| **HWP/HWPX** | `@rhwp/core` 0.8.7 (MIT) + WebAssembly + WebView |
| **빌드 도구** | JDK 17, Android Gradle Plugin 8.13.2, Gradle 8.13 |
| **웹 빌드** | Node.js 20.19+ 또는 22.12+, npm, Vite 7 |

---

## 시작하기

### 1. HWP/HWPX 웹 리소스 빌드 (필수)

HWP 뷰어를 사용하려면 먼저 WebView용 JavaScript/WASM 번들을 빌드해야 합니다.

```bash
cd web
npm install
npm run build
```

빌드 결과가 자동으로 `app/src/main/assets/hwpviewer/`에 생성됩니다.

> **주의**: 이 단계를 건너뛰면 HWP/HWPX 뷰어가 동작하지 않습니다. PDF 뷰어와 문서 관리 기능은 별도로 사용 가능합니다.

### 2. Android 앱 빌드

#### Android Studio 사용
1. Android Studio 최신 버전 설치 (JDK 17, Android SDK 37 포함)
2. 프로젝트 루트 폴더를 엽니다
3. Gradle 동기화 완료 후 `app` 모듈 선택
4. Android 9(API 28) 이상 기기 또는 에뮬레이터에서 실행

#### 커맨드라인 사용
```bash
./gradlew.bat assembleDebug
```

APK 생성 위치: `app/build/outputs/apk/debug/app-debug.apk`

---

## 프로젝트 구조

```
pdf_hwp_viewer/
├── app/                          # Android 앱 모듈
│   ├── src/main/java/kr/docuview/app/
│   │   ├── MainActivity.kt       # 앱 진입점, 탐색
│   │   ├── data/                 # Room DB, 설정, 데이터 모델
│   │   ├── importer/             # 파일 가져오기, 스캔, 캐싱
│   │   ├── reader/               # PDF/HWP 뷰어, 주석 렌더링
│   │   └── ui/                   # Compose 화면, 테마, 툴바
│   ├── src/main/assets/
│   │   └── hwpviewer/            # WebView용 HWP 렌더러 (빌드 생성물)
│   └── build.gradle.kts          # Android 모듈 설정
├── web/                          # HWP WebView 렌더러 소스
│   ├── src/main.js               # HWP 뷰어 JavaScript
│   ├── package.json              # @rhwp/core, Vite 의존성
│   └── vite.config.js
├── build.gradle.kts              # 루트 Gradle 설정
├── settings.gradle.kts
├── AGENTS.md                     # AI 에이전트용 프로젝트 가이드
└── README.md

```

---

## 데이터 저장

| 데이터 | 위치 | 비고 |
|--------|------|------|
| 문서 사본 | `filesDir/documents/` | 원본 파일의 SHA-256 해시 기반 중복 제거 |
| Room DB | `filesDir/docuview.db` | 문서 메타데이터, 북마크, 주석 |
| 설정 | `SharedPreferences` | 테마, 뷰어 설정 |

앱 삭제 시 내부 문서 사본과 DB가 함께 삭제됩니다. 원본 파일은 변경되지 않습니다.

---

## 파일 형식 제한 및 지원 범위

### PDF
- Android `PdfRenderer`로 렌더링
- 암호 입력 UI는 현재 미구현

### HWP/HWPX
- **지원**: HWP 5.0 바이너리, HWPX (rhwp가 처리 가능한 범위)
- **제한**: 64 MiB (WASM 메모리 보호)
- **참고**: 구형 HWP 3.x, 암호화된 문서, 복잡한 개체/글꼴은 별도 호환성 검증 필요
- **출력 차이**: 한컴오피스와 픽셀 단위로 동일한 출력을 보장하지 않습니다

---

## 테스트

```bash
# 단위 테스트
./gradlew test

# 빌드 검증
./gradlew.bat assembleDebug
```

---

## 라이선스

앱 소스 코드 및 제3자 구성요소 라이선스는 [LICENSES.md](LICENSES.md)를 참고하세요.

주요 오픈소스:
- `@rhwp/core` (MIT) - HWP/HWPX 파싱 및 렌더링
- AndroidX, Jetpack Compose (Apache 2.0)

---

## 개발 참고사항

### AI 에이전트 협업
이 프로젝트는 AI 에이전트와의 협업을 고려한 구조를 가지고 있습니다:
- [AGENTS.md](AGENTS.md)에 프로젝트 아키텍처, 규칙, 금지 사항 상세 기술
- 변경 사항이 HWP 렌더링에 영향을 주면 `HwpReaderView.kt`와 `web/src/main.js`를 함께 확인
- `DocumentImporter.kt`를 우회하지 않고 모든 파일 가져오기 처리

### 빌드 검증 이력
- `cd web && npm install && npm run build` ✅
- `./gradlew.bat assembleDebug` ✅ (`BUILD SUCCESSFUL`)

---

## 문의 및 피드백

프로젝트 이슈는 GitHub Issues에 등록해 주세요.

---

**마지막 업데이트**: 2026년 1월