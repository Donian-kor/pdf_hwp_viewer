import init, { HwpDocument } from '@rhwp/core';
import './style.css';

const pagesNode = document.getElementById('pages');
const params = new URLSearchParams(window.location.search);
const startPage = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);

let documentHandle = null;
let currentDocumentId = '';
const pageObjectUrls = new Map();
let currentPage = -1;
let documentPageCount = 0;
let renderObserver = null;
let reportTimer = 0;

const MAX_CACHED_PAGES = 24;
const PLACEHOLDER_CHUNK = 250;

function nextFrame() {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

function schedulePageSync() {
  window.clearTimeout(reportTimer);
  reportTimer = window.setTimeout(() => {
    if (documentPageCount <= 0) return;

    const centerY = window.innerHeight / 2;
    const hit = document.elementFromPoint(window.innerWidth / 2, centerY);
    const page = hit?.closest?.('.page');
    if (page && pagesNode.contains(page)) {
      reportPage(Number(page.dataset.pageIndex), documentPageCount);
      return;
    }

    // Fallback for gaps between pages: the first page reaching the viewport center.
    for (const candidate of pagesNode.children) {
      if (candidate.getBoundingClientRect().bottom >= centerY) {
        reportPage(Number(candidate.dataset.pageIndex), documentPageCount);
        return;
      }
    }
  }, 80);
}

function registerTextWidthMeasurement() {
  let context = null;
  let previousFont = '';
  globalThis.measureTextWidth = (font, text) => {
    if (!context) context = document.createElement('canvas').getContext('2d');
    if (!context) return 0;
    if (font !== previousFont) {
      context.font = font;
      previousFont = font;
    }
    return context.measureText(text).width;
  };
}

function reportPage(pageIndex, pageCount) {
  const safeIndex = Number.isFinite(pageIndex) ? Math.max(0, Math.min(pageIndex, pageCount - 1)) : 0;
  if (safeIndex === currentPage && pageCount > 0) return;
  currentPage = safeIndex;
  try {
    globalThis.AndroidBridge?.onPageChanged?.(safeIndex, pageCount);
  } catch (_) {
    // The Android bridge may not be present when this page is opened in a desktop browser.
  }
}

function trimRenderedPages() {
  while (pageObjectUrls.size > MAX_CACHED_PAGES) {
    const oldest = pageObjectUrls.keys().next().value;
    URL.revokeObjectURL(pageObjectUrls.get(oldest));
    pageObjectUrls.delete(oldest);
    const element = document.getElementById(`page-${oldest}`);
    if (element) {
      element.replaceChildren();
      element.dataset.rendered = 'false';
    }
  }
}

function cleanupPreviousDocument() {
  window.clearTimeout(reportTimer);
  renderObserver?.disconnect();
  renderObserver = null;
  for (const url of pageObjectUrls.values()) URL.revokeObjectURL(url);
  pageObjectUrls.clear();
  documentPageCount = 0;
  currentPage = -1;
  pagesNode.replaceChildren();
  if (documentHandle && typeof documentHandle.free === 'function') {
    documentHandle.free();
  }
  documentHandle = null;
}

function renderPage(index, pageElement) {
  if (pageObjectUrls.has(index) || !documentHandle) return;
  try {
    const svg = documentHandle.renderPageSvg(index);
    const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    pageObjectUrls.set(index, objectUrl);
    pageElement.dataset.rendered = 'true';
    const image = document.createElement('img');
    image.alt = `문서 ${index + 1}페이지`;
    image.loading = 'lazy';
    image.src = objectUrl;
    pageElement.replaceChildren(image);
    trimRenderedPages();
  } catch (error) {
    pageElement.dataset.rendered = 'false';
    pageElement.textContent = `페이지 ${index + 1} 렌더링 실패: ${error?.message ?? error}`;
    pageElement.classList.add('error');
  }
}

async function buildPagePlaceholders(pageCount) {
  const fragment = document.createDocumentFragment();
  pagesNode.replaceChildren();

  for (let index = 0; index < pageCount; index += 1) {
    const page = document.createElement('section');
    page.className = 'page';
    page.id = `page-${index}`;
    page.dataset.pageIndex = String(index);
    page.setAttribute('aria-label', `${index + 1}페이지`);
    fragment.append(page);
    if ((index + 1) % PLACEHOLDER_CHUNK === 0) {
      pagesNode.append(fragment);
      await nextFrame();
    }
  }
  pagesNode.append(fragment);

  renderObserver = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        renderPage(Number(entry.target.dataset.pageIndex), entry.target);
      }
    }
  }, { rootMargin: '900px 0px' });

  // Observe in chunks so a very long document does not block one animation frame.
  const pages = pagesNode.children;
  for (let index = 0; index < pages.length; index += 1) {
    renderObserver.observe(pages[index]);
    if ((index + 1) % 500 === 0) await nextFrame();
  }

  window.addEventListener('scroll', schedulePageSync, { passive: true });
  window.addEventListener('resize', schedulePageSync);
  schedulePageSync();
}

window.scrollToPage = (pageIndex) => {
  const safeIndex = Math.max(0, Math.min(Number(pageIndex) || 0, pagesNode.children.length - 1));
  document.getElementById(`page-${safeIndex}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  reportPage(safeIndex, documentPageCount || pagesNode.children.length);
};

async function openDocument() {
  currentDocumentId = params.get('documentId') ?? '';
  if (!currentDocumentId) throw new Error('문서 식별 정보가 없습니다.');

  registerTextWidthMeasurement();
  await init({ module_or_path: new URL('rhwp_bg.wasm', document.baseURI).toString() });

  const response = await fetch(new URL('document', document.baseURI), { method: 'GET' });
  if (!response.ok) throw new Error(`문서 읽기에 실패했습니다. (${response.status})`);
  const bytes = new Uint8Array(await response.arrayBuffer());
  documentHandle = new HwpDocument(bytes);

  const pageCount = documentHandle.pageCount();
  if (!Number.isFinite(pageCount) || pageCount < 1) throw new Error('문서에서 페이지를 찾을 수 없습니다.');
  if (pageCount > 5000) throw new Error('페이지가 5,000장을 초과하는 문서는 초기 버전에서 지원하지 않습니다.');

  documentPageCount = pageCount;
  // Report the count before building the DOM so the native reader can leave its loading state.
  globalThis.AndroidBridge?.onDocumentLoaded?.(pageCount);

  await buildPagePlaceholders(pageCount);
  window.scrollToPage(startPage);
}

window.addEventListener('pagehide', cleanupPreviousDocument);

openDocument().catch((error) => {
  cleanupPreviousDocument();
  const message = String(error?.message ?? error);
  try { globalThis.AndroidBridge?.onError?.(message); } catch (_) { /* bridge unavailable */ }
});
