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

// ---- annotation state ----
let annotations = [];
let lastLoadedAnnotationsJson = '';
let annotationEnabled = false;
let annotationTool = 'PEN';
let annotationColor = '#FF3B30';
let activeStroke = null;

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
  } catch (_) { /* bridge unavailable in desktop browser */ }
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
  annotations = [];
  activeStroke = null;
  documentPageCount = 0;
  currentPage = -1;
  pagesNode.replaceChildren();
  if (documentHandle && typeof documentHandle.free === 'function') documentHandle.free();
  documentHandle = null;
}

// ---------- annotation rendering ----------
function drawStrokeOnContext(ctx, stroke, width, height) {
  const pts = stroke.points || [];
  if (!pts.length) return;
  const tool = stroke.tool || 'PEN';
  const color = stroke.color || '#FF3B30';
  ctx.save();
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  // Scale stroke width with canvas size so screen and 1600px share output look identical.
  const strokeScale = width / 800;
  if (tool === 'HIGHLIGHT') {
    ctx.globalAlpha = 0.35;
    ctx.lineWidth = 20 * strokeScale;
  } else {
    ctx.globalAlpha = 1;
    ctx.lineWidth = 4 * strokeScale;
  }

  if (tool === 'HIGHLIGHT' || tool === 'PEN') {
    if (pts.length === 1) {
      ctx.beginPath();
      ctx.arc(pts[0][0] * width, pts[0][1] * height, ctx.lineWidth / 2, 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.beginPath();
      ctx.moveTo(pts[0][0] * width, pts[0][1] * height);
      for (let i = 1; i < pts.length; i += 1) ctx.lineTo(pts[i][0] * width, pts[i][1] * height);
      ctx.stroke();
    }
  }
  ctx.restore();
}

function redrawPage(pageIndex) {
  const page = document.getElementById(`page-${pageIndex}`);
  const canvas = page?.querySelector('canvas.annotation-layer');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  annotations.filter((a) => a.pageIndex === pageIndex).forEach((a) => drawStrokeOnContext(ctx, a, canvas.width, canvas.height));
  if (activeStroke && activeStroke.pageIndex === pageIndex) {
    drawStrokeOnContext(ctx, activeStroke, canvas.width, canvas.height);
  }
}

function resizeAnnotationCanvas(pageElement) {
  const canvas = pageElement.querySelector('canvas.annotation-layer');
  const img = pageElement.querySelector('img');
  if (!canvas || !img) return;
  const width = img.clientWidth || img.naturalWidth || 720;
  const height = img.clientHeight || img.naturalHeight || 900;
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
    redrawPage(Number(pageElement.dataset.pageIndex));
  }
}

function ensureAnnotationCanvas(pageElement) {
  let canvas = pageElement.querySelector('canvas.annotation-layer');
  if (canvas) return canvas;

  canvas = document.createElement('canvas');
  canvas.className = 'annotation-layer';
  pageElement.appendChild(canvas);

  canvas.addEventListener('pointerdown', (event) => {
    if (!annotationEnabled || annotationTool === 'NONE') return;
    event.preventDefault();
    canvas.setPointerCapture(event.pointerId);
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;
    activeStroke = {
      id: `local-${Date.now()}`,
      pageIndex: Number(pageElement.dataset.pageIndex),
      tool: annotationTool,
      color: annotationColor,
      points: [[x, y]],
    };
    redrawPage(activeStroke.pageIndex);
  });

  canvas.addEventListener('pointermove', (event) => {
    if (!activeStroke || !annotationEnabled) return;
    event.preventDefault();
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;
    activeStroke.points.push([x, y]);
    redrawPage(activeStroke.pageIndex);
  });

  const finishStroke = (event) => {
    if (!activeStroke) return;
    event?.preventDefault?.();
    const stroke = activeStroke;
    activeStroke = null;
    // Keep the stroke locally so it is not erased by the next redraw/load cycle.
    annotations.push({
      pageIndex: stroke.pageIndex,
      tool: stroke.tool,
      color: stroke.color,
      points: stroke.points,
    });
    lastLoadedAnnotationsJson = JSON.stringify(annotations.map((a) => ({
      pageIndex: a.pageIndex,
      tool: a.tool,
      color: a.color,
      points: a.points,
    })));
    redrawPage(stroke.pageIndex);
    if (stroke.points.length >= 2) {
      try {
        globalThis.AndroidBridge?.onStroke?.(
          stroke.pageIndex,
          stroke.tool,
          stroke.color,
          JSON.stringify(stroke.points),
        );
      } catch (_) { /* bridge unavailable */ }
    }
  };
  canvas.addEventListener('pointerup', finishStroke);
  canvas.addEventListener('pointercancel', finishStroke);
  canvas.addEventListener('pointerleave', (event) => { if (activeStroke) finishStroke(event); });

  resizeAnnotationCanvas(pageElement);
  return canvas;
}

function applyAnnotationState() {
  document.querySelectorAll('.page').forEach((page) => {
    const canvas = ensureAnnotationCanvas(page);
    canvas.style.pointerEvents = annotationEnabled && annotationTool !== 'NONE' ? 'auto' : 'none';
    resizeAnnotationCanvas(page);
  });
}

window.setAnnotationState = (enabled, tool, colorHex) => {
  annotationEnabled = !!enabled;
  annotationTool = tool || 'PEN';
  annotationColor = colorHex || '#FF3B30';
  applyAnnotationState();
};

window.loadAnnotations = (json) => {
  const raw = json || '[]';
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (_) {
    parsed = [];
  }
  // Structural comparison avoids replacing a just-drawn stroke with the exact
  // same snapshot from Room (number formatting may differ between JS and Kotlin).
  const same = parsed.length === annotations.length && parsed.every((a, i) => {
    const b = annotations[i];
    if (!b || a.pageIndex !== b.pageIndex || a.tool !== b.tool || a.color?.toUpperCase() !== b.color?.toUpperCase()) return false;
    if (!Array.isArray(a.points) || !Array.isArray(b.points) || a.points.length !== b.points.length) return false;
    return a.points.every((p, j) => Math.abs(p[0] - b.points[j][0]) < 1e-4 && Math.abs(p[1] - b.points[j][1]) < 1e-4);
  });
  if (same) return;
  lastLoadedAnnotationsJson = raw;
  annotations = parsed;
  annotations.forEach((a) => ensureAnnotationCanvas(document.getElementById(`page-${a.pageIndex}`)));
  for (let i = 0; i < documentPageCount; i += 1) redrawPage(i);
};

window.undoLastAnnotation = (pageIndex) => {
  let removed = false;
  for (let i = annotations.length - 1; i >= 0; i -= 1) {
    if (annotations[i].pageIndex === pageIndex) {
      annotations.splice(i, 1);
      removed = true;
      break;
    }
  }
  redrawPage(pageIndex);
  return removed;
};

window.clearPageAnnotations = (pageIndex) => {
  annotations = annotations.filter((a) => a.pageIndex !== pageIndex);
  redrawPage(pageIndex);
};

// Clears every annotation in the document and repaints all visible pages.
window.clearAllAnnotations = () => {
  annotations = [];
  activeStroke = null;
  lastLoadedAnnotationsJson = '[]';
  for (let i = 0; i < documentPageCount; i += 1) redrawPage(i);
  document.querySelectorAll('.page').forEach((page) => redrawPage(Number(page.dataset.pageIndex)));
};

window.requestSharedPage = async (pageIndex) => {
  try {
    if (!documentHandle) throw new Error('document not loaded');

    // Determine the page actually visible in the viewport (WebView zoom/scroll aware).
    // Fall back to the supplied pageIndex when nothing is measurable.
    let targetPage = Number.isFinite(pageIndex) ? pageIndex : currentPage;
    const centerY = window.innerHeight / 2;
    const hit = document.elementFromPoint(window.innerWidth / 2, centerY);
    const hitPage = hit?.closest?.('.page');
    if (hitPage && pagesNode.contains(hitPage)) {
      targetPage = Number(hitPage.dataset.pageIndex);
    } else {
      for (const candidate of pagesNode.children) {
        if (candidate.getBoundingClientRect().bottom >= centerY) {
          targetPage = Number(candidate.dataset.pageIndex);
          break;
        }
      }
    }

    const svg = documentHandle.renderPageSvg(targetPage);
    const svgUrl = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    const image = new Image();
    await new Promise((resolve, reject) => {
      image.onload = resolve;
      image.onerror = reject;
      image.src = svgUrl;
    });

    const width = 1600;
    const height = Math.round(width * ((image.naturalHeight / image.naturalWidth) || 1.3));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(image, 0, 0, width, height);
    annotations.filter((a) => a.pageIndex === targetPage)
      .forEach((a) => drawStrokeOnContext(ctx, a, width, height));
    const dataUrl = canvas.toDataURL('image/png');
    globalThis.AndroidBridge?.onSharedPage?.(dataUrl.substring(dataUrl.indexOf(',') + 1));
  } catch (error) {
    try { globalThis.AndroidBridge?.onSharedPage?.(''); } catch (_) { }
  }
};

// Share multiple pages. Each page is sent separately because a single JSON array
// of base64 PNGs would exceed the Binder transaction limit (~1MB) and fail silently.
window.requestSharedPages = async (pageIndexes) => {
  let sent = 0;
  try {
    if (!documentHandle) throw new Error('document not loaded');
    // Prefer pages that actually carry annotations in this viewer instance,
    // so sharing never depends on native DB state being in sync.
    const annotatedPages = Array.from(new Set(annotations.map((a) => a.pageIndex))).sort((a, b) => a - b);
    const pages = Array.isArray(pageIndexes) && pageIndexes.length
      ? pageIndexes
      : (annotatedPages.length ? annotatedPages : [currentPage]);
    console.log('HwpShare js pages=' + JSON.stringify(pages));
    for (const rawPage of pages) {
      const targetPage = Math.max(0, Math.min(Number(rawPage) || 0, documentPageCount - 1));
      try {
        const svg = documentHandle.renderPageSvg(targetPage);
        const svgUrl = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
        const image = new Image();
        await new Promise((resolve, reject) => {
          image.onload = resolve;
          image.onerror = reject;
          image.src = svgUrl;
        });
        const width = 1600;
        const height = Math.round(width * ((image.naturalHeight / image.naturalWidth) || 1.3));
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, width, height);
        ctx.drawImage(image, 0, 0, width, height);
        annotations.filter((a) => a.pageIndex === targetPage)
          .forEach((a) => drawStrokeOnContext(ctx, a, width, height));
        const dataUrl = canvas.toDataURL('image/png');
        // One PNG per bridge call keeps each payload under the Binder limit.
        globalThis.AndroidBridge?.onSharedPage?.(dataUrl.substring(dataUrl.indexOf(',') + 1));
        sent += 1;
      } catch (pageError) {
        // Skip pages that fail to render, but keep the ones that succeeded.
        try { globalThis.AndroidBridge?.onError?.(`share page ${targetPage}: ${pageError?.message ?? pageError}`); } catch (_) { }
      }
    }
  } catch (error) {
    try { globalThis.AndroidBridge?.onError?.(`share: ${error?.message ?? error}`); } catch (_) { }
  } finally {
    // Tell Android the batch finished so it can share whatever was collected.
    globalThis.AndroidBridge?.onSharedPageBatchDone?.(sent);
  }
};

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
    image.addEventListener('load', () => {
      ensureAnnotationCanvas(pageElement);
      resizeAnnotationCanvas(pageElement);
      redrawPage(index);
    }, { once: true });
    pageElement.replaceChildren(image);
    ensureAnnotationCanvas(pageElement);
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

  const pages = pagesNode.children;
  for (let index = 0; index < pages.length; index += 1) {
    renderObserver.observe(pages[index]);
    if ((index + 1) % 500 === 0) await nextFrame();
  }

  window.addEventListener('scroll', schedulePageSync, { passive: true });
  window.addEventListener('resize', schedulePageSync);
  window.addEventListener('resize', applyAnnotationState);
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
