# 문서뷰어 (DocuView)

Android PDF / HWP / HWPX 읽기 전용 통합 뷰어 프로젝트입니다. 앱은 최근 문서 목록, 문서 탐색, 외부 파일 열기, PDF/HWP 뷰어, 파일 관리 UX를 함께 제공합니다.

## 구현된 범위

- PDF 열람: Android `PdfRenderer`로 페이지 단위 렌더링, 세로 스크롤, 핀치 확대, 더블 탭 확대
- HWP / HWPX 열람: MIT 라이선스 `@rhwp/core` WebAssembly 렌더러를 Android WebView에 로컬 내장
- 스크롤/페이지 이동 시 현재 페이지 표기와 페이지 위치 동기화
- Android 시스템 파일 선택기로 문서 가져오기
- 가져온 원본 사본을 앱 내부 저장소에 보관
- 최근 문서 목록, 마지막 페이지 복원
- 문서별 페이지 북마크 추가 / 삭제 / 이동
- Room 데이터베이스에 문서 메타데이터와 북마크 저장
- 문서 찾기 화면에서 길게 누르기 메뉴로 열기 / 삭제 동작 지원
- 삭제 확인 다이얼로그에서 경고 문구와 경고 색상을 표시해 실수 삭제 방지
- 다른 앱에서 PDF/HWP/HWPX 파일을 선택하면 앱으로 바로 열리도록 외부 열기 연동
- 앱 자체 인터넷 권한 없음. WebView는 패키지 내부 리소스와 선택한 문서만 제공

## 기술 구성

- Android: Kotlin, Jetpack Compose, minSdk 28 (Android 9)
- Database: Room 2.8.5
- PDF: Android 플랫폼 `PdfRenderer`
- HWP/HWPX: `@rhwp/core` 0.8.7 (MIT) + WebAssembly + WebView
- HWP 웹 리소스 빌드: Node.js 20.19+ 또는 22.12+, npm, Vite 7
- Android build: JDK 17, Android SDK 36, Android Gradle Plugin 8.13.2, Gradle 8.13

## 먼저 HWP 렌더러 리소스 만들기

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
cd web
npm install
npm run build
```

빌드 결과는 자동으로 다음 폴더에 생성됩니다.

```text
app/src/main/assets/hwpviewer/
```

이 과정은 개발/빌드 시점에만 인터넷 연결이 필요합니다. 설치된 앱은 WASM, JavaScript, CSS를 APK 안에서 읽고 문서를 앱 내부에서 처리합니다.

> `npm install`을 하지 않고 Android 앱만 빌드하면 HWP/HWPX 렌더링 자산이 없으므로 HWP 뷰어는 동작하지 않습니다. PDF 뷰어와 문서 관리 기능은 별도로 사용할 수 있습니다.

## Android Studio에서 실행

1. Android Studio 최신 버전을 설치하고 JDK 17 및 Android SDK Platform 36을 설치합니다.
2. `web` 폴더에서 HWP 리소스 빌드를 완료합니다.
3. Android Studio에서 이 프로젝트의 루트 폴더를 엽니다.
4. Gradle 동기화가 끝나면 `app` 구성을 선택합니다.
5. Android 9(API 28) 이상 기기 또는 에뮬레이터에서 실행합니다.

## 파일 가져오기 / 저장 방식

- 시스템 `ACTION_OPEN_DOCUMENT` 파일 선택기를 사용합니다.
- 선택한 파일은 `filesDir/documents/`에 복사되므로 원본 파일을 수정하지 않습니다.
- SHA-256 해시로 같은 내용의 파일을 중복 보관하지 않습니다.
- PDF 최대 파일 크기: 256 MiB
- HWP/HWPX 최대 파일 크기: 64 MiB (WASM 메모리 사용량 보호를 위한 초기 제한)
- HWP/HWPX의 지원 범위는 rhwp가 처리할 수 있는 HWP 5.0 바이너리 및 HWPX 문서입니다. 구형 HWP 3.x, 암호화/손상 문서, 특수 글꼴이나 복잡한 개체는 별도 호환성 테스트가 필요합니다.

## 데이터 위치

- 문서 사본: 앱 내부 `filesDir/documents/`
- DB: `docuview.db`
- 북마크 페이지는 0부터 시작하는 내부 인덱스로 저장하고 화면에는 1부터 표시합니다.

앱을 삭제하면 앱 내부 문서 사본과 DB도 함께 삭제됩니다. 원본은 그대로 유지됩니다.

## 테스트

```bash
cd web
npm install
npm run build
```

그다음 Android Gradle로 빌드합니다.

```bash
./gradlew.bat assembleDebug
```

또는 `./gradlew test`로 로컬 테스트를 실행할 수 있습니다.

## 제한 사항

- 읽기 전용: 문서 편집, 주석, PDF 변환은 구현하지 않았습니다.
- PDF 암호 입력 UI는 아직 구현하지 않았습니다.
- PDF는 Android 기본 렌더러를 사용하며 기기별 Android PDF 구현 차이가 있을 수 있습니다.
- HWP 렌더링은 rhwp 오픈소스 엔진의 지원 범위와 브라우저 폰트에 영향을 받습니다. 한컴 오피스와 픽셀 단위로 동일한 출력은 보장하지 않습니다.
- HWP 문서 데이터는 파싱을 위해 WebAssembly 메모리에 로드됩니다. 64 MiB 제한은 완전한 저메모리 스트리밍을 의미하지 않습니다.

## License

앱 소스와 제3자 구성요소의 라이선스 개요는 [LICENSES.md](LICENSES.md)를 참고하세요.

## 현재 상태(프로젝트 실제 기준)

- 이 프로젝트는 실제 Android 소스 프로젝트로 동작합니다.
- WebView 기반 HWP 렌더러 빌드는 `web` 폴더에서 별도로 수행해야 합니다.
- `npm run build`를 실행하면 `app/src/main/assets/hwpviewer` 번들이 생성되고, Android 앱에서 HWP 문서 렌더링이 활성화됩니다.
- 문서 찾기 화면은 길게 누르기 메뉴를 통해 열기와 삭제를 제공합니다.
- 삭제 확인 다이얼로그는 경고 문구를 강조하고 실수 삭제를 막는 UX로 구성되어 있습니다.
- 다른 앱에서 PDF/HWP/HWPX 파일을 열면 앱으로 바로 연결됩니다.
- `./gradlew.bat assembleDebug` 빌드는 성공적으로 검증되었습니다.

## 빠른 실행 절차

```bash
cd web
npm install
npm run build
```

그다음 Android Studio 또는 Gradle로 앱을 실행합니다.

```bash
./gradlew.bat assembleDebug
```

## 빌드 검증

현재 이 환경에서 아래를 실제로 확인했습니다.

- `cd web && npm install && npm run build` → 성공
- `./gradlew.bat assembleDebug` → 성공 (`BUILD SUCCESSFUL`)

이 정보는 README에 반영해 두어, 이후 작업자가 바로 동일한 절차로 검증할 수 있게 했습니다.
