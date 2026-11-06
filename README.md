# 문서뷰어 (DocuView)

Android PDF / HWP / HWPX 읽기 전용 통합 뷰어의 초기 MVP 프로젝트입니다.

## 구현된 범위

- PDF 열람: Android `PdfRenderer`로 페이지 단위 렌더링, 세로 스크롤, 핀치 확대, 더블 탭 확대
- HWP / HWPX 열람: MIT 라이선스 `@rhwp/core` 0.8.7 WebAssembly 렌더러를 Android WebView에 로컬 내장
- Android 시스템 파일 선택기로 문서 가져오기
- 가져온 원본 사본을 앱 내부 저장소에 보관
- 최근 문서 목록, 마지막 페이지 복원
- 문서별 페이지 북마크 추가 / 삭제 / 이동
- Room 데이터베이스에 문서 메타데이터와 북마크 저장
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
3. Android Studio에서 이 프로젝트의 루트 폴더 `DocuView`를 엽니다.
4. Gradle 동기화가 끝나면 `app` 구성을 선택합니다.
5. Android 9(API 28) 이상 기기 또는 에뮬레이터에서 실행합니다.

이 전달본에는 Gradle Wrapper 실행 JAR와 Android SDK가 포함되지 않았습니다. 현재 작업 환경에도 Android SDK / Gradle 실행 파일이 없어 이 세션에서 APK를 컴파일하거나 기기 테스트하지 못했습니다. Android Studio에서 프로젝트를 연 뒤 Wrapper를 생성하거나 로컬 Gradle을 지정해야 합니다. `gradle/wrapper/gradle-wrapper.properties`는 Gradle 8.13을 가리킵니다.

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

현재 전달본에는 Gradle Wrapper 실행 스크립트와 Wrapper JAR가 포함되어 있지 않습니다. Android Studio에서 프로젝트를 열기 전에 Gradle 8.13을 설치한 환경에서 프로젝트 루트에서 다음 명령으로 Wrapper를 생성할 수 있습니다.

```bash
gradle wrapper --gradle-version 8.13
```

이후 생성된 `gradlew`(Windows는 `gradlew.bat`)로 테스트를 실행합니다.

```bash
./gradlew test
```

`app/src/test`에는 파일 시그니처 판별 테스트가 포함되어 있습니다. 이 전달본은 전체 Android Gradle 빌드 및 기기 테스트가 완료된 바이너리가 아니라 소스 프로젝트입니다.

## 제한 사항

- 읽기 전용: 문서 편집, 주석, PDF 변환은 구현하지 않았습니다.
- PDF 암호 입력 UI는 아직 구현하지 않았습니다.
- PDF는 Android 기본 렌더러를 사용하며 기기별 Android PDF 구현 차이가 있을 수 있습니다.
- HWP 렌더링은 rhwp 오픈소스 엔진의 지원 범위와 브라우저 폰트에 영향을 받습니다. 한컴 오피스와 픽셀 단위로 동일한 출력은 보장하지 않습니다.
- HWP 문서 데이터는 파싱을 위해 WebAssembly 메모리에 로드됩니다. 64 MiB 제한은 완전한 저메모리 스트리밍을 의미하지 않습니다.

## License

앱 소스와 제3자 구성요소의 라이선스 개요는 [LICENSES.md](LICENSES.md)를 참고하세요.
