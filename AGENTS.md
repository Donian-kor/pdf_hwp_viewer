# AGENTS.md

## Project overview

This repository is an Android document viewer for PDF and HWP/HWPX files. The app is organized as a Jetpack Compose single-activity app with a Room database for metadata and a bundled WebView-based HWP renderer.

- Android app source: `app/src/main/java/kr/docuview/app`
- UI layer: `app/src/main/java/kr/docuview/app/ui`
- Data layer: `app/src/main/java/kr/docuview/app/data`
- Import + file-copy flow: `app/src/main/java/kr/docuview/app/importer`
- Reader implementations: `app/src/main/java/kr/docuview/app/reader`
- WebAssembly HWP renderer: `web/src/main.js` and `web/`

## Architecture and key integration points

### 1) App entry and navigation

`MainActivity.kt` is the app entry. It creates the Room database and `SettingsManager`, then hosts `DocumentViewerApp` inside the Compose theme.

- Navigation is handled in `MainActivity.kt` with a 3-tab flow: home, file browser, settings.
- `HomeScreen`, `FileBrowserScreen`, `ReaderScreen`, and `SettingsScreen` are the Compose screens.
- `ReaderScreen` is the shared reader UI for both PDF and HWP documents and keeps page progress state in sync with `DocumentDao`.

### 2) Data model and persistence

`DocumentDatabase.kt` is the Room database. The schema currently includes:

- `DocumentEntity`: document metadata, path, size, and page info
- `BookmarkEntity`: document-scoped page bookmarks with `ON DELETE CASCADE`

Key examples:

- `app/src/main/java/kr/docuview/app/data/DocumentEntity.kt`
- `app/src/main/java/kr/docuview/app/data/DocumentDao.kt`
- `app/src/main/java/kr/docuview/app/data/SettingsManager.kt`

Important conventions:

- Page indexes are stored as 0-based values in the database and UI state.
- The displayed page label is converted to 1-based for the user (`currentPage + 1`).
- User preferences are stored in `SharedPreferences` by `SettingsManager` and surfaced as a `StateFlow`.

### 3) Import flow and document lifecycle

`DocumentImporter.kt` is the standard entry point for opening a document from Android's file picker.

- It copies the selected file into `context.filesDir/documents/`.
- It computes a SHA-256 hash and deduplicates identical content by ID.
- It validates the file type and enforces conservative limits:
  - PDF: max 256 MiB
  - HWP/HWPX: max 64 MiB
- It records metadata back into Room via `dao.upsertDocument(...)`.

This is the project's canonical way to add new documents; do not bypass it when creating or modifying file import behavior.

### 4) HWP renderer integration

The HWP viewer is not a native Android document parser. It runs in a local WebView with a bundled JavaScript renderer powered by `@rhwp/core`.

- Viewer source: `web/src/main.js`
- Web assets: `web/public/`
- Android side: `app/src/main/java/kr/docuview/app/reader/HwpReaderView.kt`

Key behaviors to preserve:

- `HwpReaderView` loads `app/src/main/assets/hwpviewer/index.html` from the bundled asset directory.
- `LocalHwpWebViewClient` permits only two kinds of requests:
  - `/document` for the selected file
  - bundled assets under `/assets/` and `/rhwp_bg.wasm`
- All other URLs are rejected to keep the renderer sandboxed.
- JavaScript calls back through `AndroidBridge` with `onDocumentLoaded`, `onPageChanged`, and `onError`.
- `web/main.js` synchronizes the current page using `IntersectionObserver` and reports page changes back to Android.

Do not hand-edit generated HWP viewer asset output in `app/src/main/assets/hwpviewer`; rebuild via the `web` project.

### 5) PDF rendering

PDF rendering is handled separately from HWP rendering:

- `app/src/main/java/kr/docuview/app/reader/PdfReaderView.kt`
- `app/src/main/java/kr/docuview/app/reader/PdfSession.kt`

The app follows the same reader contract as HWP: page count, current page, and user-requested page transitions are surfaced via the Compose reader layer.

## Build and validation workflow

### Required setup before Android build

From the project root:

```bash
cd web
npm install
npm run build
```

This produces the HWP viewer bundle under:

```text
app/src/main/assets/hwpviewer/
```

Without the Web bundle, the HWP/HWPX viewer will not render.

### Android build commands

```bash
./gradlew.bat assembleDebug
```

```bash
./gradlew test
```

The repository's current setup expects JDK 17 and the Android SDK. The README records successful validation with the web build and the Android debug assemble task.

## Project conventions that matter for AI agents

### Keep code in the existing package structure

The app uses package boundaries that match responsibility:

- `kr.docuview.app.data` for persistence and settings
- `kr.docuview.app.importer` for file-copy/import logic
- `kr.docuview.app.reader` for PDF/HWP view implementations
- `kr.docuview.app.ui` for Compose screens and reusable UI

When adding code, prefer placing it in the corresponding package instead of introducing a new top-level layer.

### Reuse the existing app state patterns

The Compose UI stores state in `remember`/`mutableStateOf` and uses Kotlin flows (`collectAsState`) for data-driven updates. Follow this pattern when extending screens or reader state.

Example patterns:

- `MainActivity.kt` reads settings via `settingsManager.settings.collectAsState()`
- `ReaderScreen.kt` updates `currentPage`, `requestedPage`, and `pageCount` as state, then persists progress to Room via DAO

### Preserve the document contract

When editing reader logic, keep the same contract across PDF and HWP:

- `onPageChanged(pageIndex, pageCount)`
- `onPageCount(pageCount)`
- `requestedPage` navigation updates
- last-page persistence in `DocumentEntity.lastPage`

### Respect the current limits and security model

This project intentionally keeps a small and explicit security boundary:

- HWP documents are served only from the selected file and bundled assets
- `LocalHwpWebViewClient` denies all other network access
- document imports are file-size capped and type-checked before being saved

Do not relax these boundaries without updating the corresponding security logic and docs.

## Files to check before making changes

These are the best starting points for understanding this project:

- `README.md` — build, setup, and project status
- `app/src/main/java/kr/docuview/app/MainActivity.kt` — app flow and navigation
- `app/src/main/java/kr/docuview/app/ui/ReaderScreen.kt` — document reading UX and page-state handling
- `app/src/main/java/kr/docuview/app/importer/DocumentImporter.kt` — file import and deduplication rules
- `app/src/main/java/kr/docuview/app/reader/HwpReaderView.kt` — WebView HWP bridge and asset sandbox
- `web/src/main.js` — HWP page rendering and JavaScript-to-Android sync
- `app/src/main/java/kr/docuview/app/data/DocumentDatabase.kt` — database schema and persistence root

## Minimal change guidance for agents

- Prefer small, local edits in the relevant screen, reader, or data file.
- Preserve the current Compose + Room + WebView architecture rather than introducing alternative stacks.
- If a change affects HWP rendering, check both the Kotlin bridge (`HwpReaderView.kt`) and the JavaScript renderer (`web/src/main.js`).
- If a change affects imported documents, check `DocumentImporter.kt` and the `DocumentEntity`/DAO flow together.
- If a change affects settings or bookmarks, update `SettingsManager` and `ReaderScreen` together.
- If a change affects asset generation, update the `web` build process and the generated asset path expectations.

## What not to do

- Do not rewrite the app around a different architecture (e.g., switching to a new framework or a different document engine) without updating the surrounding integration points.
- Do not bypass `DocumentImporter.kt` when adding documents.
- Do not edit generated HWP asset files directly; rebuild them from `web`.
- Do not widen HWP/PDF size limits without checking the relevant importer, viewer, and documentation assumptions.

