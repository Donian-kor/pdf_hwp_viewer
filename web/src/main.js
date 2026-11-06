import init, { HwpDocument } from '@rhwp/core';
import './style.css';

const statusNode = document.getElementById('status');
const pagesNode = document.getElementById('pages');
const params = new URLSearchParams(window.location.search);
const startPage = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);

let documentHandle = null;
let currentDocumentId = '';
const pageObjectUrls = new Map();
let currentPage = -1;
let pageObserver = null;
let renderObserver = null;
let reportTimer = 0;

function setStatus(message, isError = false) {
  statusNode.textContent = message;
  statusNode.classList.toggle('error', isError);
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
  if (pageIndex === currentPage) return;
  currentPage = pageIndex;
  try {
    globalThis.AndroidBridge?.onPageChanged?.(pageIndex, pageCount);
  } catch (_) {
    // The Android bridge may not be present when this page is opened in a desktop browser.
  }
}

function cleanupPreviousDocument() {
  renderObserver?.disconnect();
  pageObserver?.disconnect();
  renderObserver = null;
  pageObserver = null;
  for (const url of pageObjectUrls.values()) URL.revokeObjectURL(url);
  pageObjectUrls.clear();
  pagesNode.replaceChildren();
  if (documentHandle && typeof documentHandle.free === 'function') {
    documentHandle.free();
  }
  documentHandle = null;
}

function renderPage(index, pageElement) {
  if (pageElement.dataset.rendered === 'true' || !documentHandle) return;
  pageElement.dataset.rendered = 'true';
  try {
    const svg = documentHandle.renderPageSvg(index);
    const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    pageObjectUrls.set(index, objectUrl);
    const image = document.createElement('img');
    image.alt = `문서 ${index + 1}페이지`;
    image.loading = 'lazy';
    image.src = objectUrl;
    pageElement.replaceChildren(image);
  } catch (error) {
    pageElement.textContent = `페이지 ${index + 1} 렌더링 실패: ${error?.message ?? error}`;
    pageElement.classList.add('error');
  }
}

function buildPagePlaceholders(pageCount) {
  const fragment = document.createDocumentFragment();
  for (let index = 0; index < pageCount; index += 1) {
    const page = document.createElement('section');
    page.className = 'page';
    page.id = `page-${index}`;
    page.dataset.pageIndex = String(index);
    page.setAttribute('aria-label', `${index + 1}페이지`);
    fragment.append(page);
  }
  pagesNode.replaceChildren(fragment);

  renderObserver = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      const index = Number(entry.target.dataset.pageIndex);
      if (entry.isIntersecting) {
        renderPage(index, entry.target);
      } else {
        // Drop rendered SVG blobs once pages move well outside the prefetch window.
        const oldUrl = pageObjectUrls.get(index);
        if (oldUrl) URL.revokeObjectURL(oldUrl);
        pageObjectUrls.delete(index);
        entry.target.replaceChildren();
        entry.target.dataset.rendered = 'false';
      }
    }
  }, { rootMargin: '900px 0px' });

  pageObserver = new IntersectionObserver((entries) => {
    const visible = entries
      .filter((entry) => entry.isIntersecting)
      .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
    if (visible) reportPage(Number(visible.target.dataset.pageIndex), pageCount);
  }, { threshold: [0.25, 0.5, 0.75] });

  for (const page of pagesNode.children) {
    renderObserver.observe(page);
    pageObserver.observe(page);
  }
}

window.scrollToPage = (pageIndex) => {
  const safeIndex = Math.max(0, Math.min(Number(pageIndex) || 0, pagesNode.children.length - 1));
  document.getElementById(`page-${safeIndex}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  reportPage(safeIndex, pagesNode.children.length);
};

async function openDocument() {
  currentDocumentId = params.get('documentId') ?? '';
  if (!currentDocumentId) throw new Error('문서 식별 정보가 없습니다.');

  registerTextWidthMeasurement();
  setStatus('한글 문서 엔진을 불러오는 중입니다.');
  await init({ module_or_path: new URL('rhwp_bg.wasm', document.baseURI).toString() });

  setStatus('문서를 읽는 중입니다.');
  const response = await fetch(new URL('document', document.baseURI), { method: 'GET' });
  if (!response.ok) throw new Error(`문서 읽기에 실패했습니다. (${response.status})`);
  const bytes = new Uint8Array(await response.arrayBuffer());
  documentHandle = new HwpDocument(bytes);

  const pageCount = documentHandle.pageCount();
  if (!Number.isFinite(pageCount) || pageCount < 1) throw new Error('문서에서 페이지를 찾을 수 없습니다.');
  if (pageCount > 5000) throw new Error('페이지가 5,000장을 초과하는 문서는 초기 버전에서 지원하지 않습니다.');

  buildPagePlaceholders(pageCount);
  setStatus(`${pageCount}페이지 · 오프라인 문서 열람`);
  globalThis.AndroidBridge?.onDocumentLoaded?.(pageCount);
  window.scrollToPage(startPage);
}

window.addEventListener('pagehide', cleanupPreviousDocument);

openDocument().catch((error) => {
  cleanupPreviousDocument();
  const message = String(error?.message ?? error);
  setStatus(`문서를 열 수 없습니다.\n${message}`, true);
  try { globalThis.AndroidBridge?.onError?.(message); } catch (_) { /* bridge unavailable */ }
});
