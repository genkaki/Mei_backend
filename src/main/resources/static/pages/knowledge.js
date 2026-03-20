// ============================================================
// Knowledge Base Page — File Upload + Document Management
// ============================================================
import { uploadFile, listDocuments, deleteDocument } from '../api.js';
import { ICONS } from '../icons.js';

let docs = [];
let pollTimer = null;

export function renderKnowledge() {
  return `
    <div class="page-header">
      <h1 class="page-title"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.BOOK}</span> 知识库管理</h1>
      <p class="page-desc">上传文档后，AI 对话中可勾选文件激活 RAG 检索增强。支持 TXT / PDF / Word / Markdown 格式，单文件最大 5MB。</p>
    </div>
    <div class="knowledge-layout">
      <div class="upload-zone" id="upload-zone">
        <input type="file" id="file-input" accept=".txt,.md,.pdf,.doc,.docx,.json"
          onchange="window.__handleUpload?.(this.files)">
        <div class="upload-zone-icon" style="opacity:0.6;margin-bottom:16px">${ICONS.UPLOAD}</div>
        <div class="upload-zone-title">拖拽文件到此处，或点击选择</div>
        <div class="upload-zone-desc">支持 TXT / MD / PDF / Word / JSON · 最大 5MB</div>
      </div>

      <h3 style="font-size:16px;font-weight:600;margin-bottom:16px">已上传文档</h3>
      <div class="doc-list" id="doc-list">
        <div style="text-align:center;padding:30px;color:var(--text-muted)">
          <div class="spinner"></div>
          <div style="margin-top:12px;font-size:13px">加载中...</div>
        </div>
      </div>
    </div>
  `;
}

export function initKnowledge() {
  window.__handleUpload = handleUpload;
  window.__deleteDoc = deleteDoc;

  const zone = document.getElementById('upload-zone');
  if (zone) {
    zone.addEventListener('dragover', e => { e.preventDefault(); zone.classList.add('dragover'); });
    zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
    zone.addEventListener('drop', e => {
      e.preventDefault();
      zone.classList.remove('dragover');
      if (e.dataTransfer.files.length) handleUpload(e.dataTransfer.files);
    });
  }

  loadDocs();
}

async function loadDocs() {
  try {
    docs = await listDocuments() || [];
    renderDocs();
    updateBadge();
    startPoll();
  } catch(e) {
    document.getElementById('doc-list').innerHTML = `<div style="padding:20px;text-align:center;color:var(--danger)">加载失败: ${e.message}</div>`;
  }
}

function renderDocs() {
  const list = document.getElementById('doc-list');
  if (!list) return;

  if (docs.length === 0) {
    list.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-icon" style="opacity:0.3;margin-bottom:20px">${ICONS.FILE}</div>
        <div class="empty-state-text">还没有上传任何文档</div>
        <div class="empty-state-text" style="color:var(--text-muted)">上传文件后，AI 对话将获得知识库增强能力</div>
      </div>
    `;
    return;
  }

  list.innerHTML = docs.map(doc => {
    const ext = (doc.fileName || '').split('.').pop().toLowerCase();
    const iconClass = ext === 'pdf' ? 'pdf' : ext === 'md' ? 'md' : ext === 'doc' || ext === 'docx' ? 'doc' : 'txt';
    const iconEmoji = ext === 'pdf' ? '📕' : ext === 'md' ? '📝' : ext === 'doc' || ext === 'docx' ? '📘' : '📄';
    const sizeStr = doc.fileSize > 1024*1024 
      ? (doc.fileSize / 1024 / 1024).toFixed(1) + ' MB' 
      : (doc.fileSize / 1024).toFixed(0) + ' KB';
    const statusBadge = doc.status === 0
      ? '<span class="badge badge-warning"><span class="spinner" style="width:12px;height:12px;border-width:1.5px"></span> 处理中</span>'
      : doc.status === 1
      ? '<span class="badge badge-success">✓ 已完成</span>'
      : '<span class="badge badge-danger">✗ 失败</span>';

    return `
      <div class="doc-item">
        <div class="doc-icon ${iconClass}">${ICONS.FILE}</div>
        <div class="doc-info">
          <div class="doc-name">${escapeHtml(doc.fileName)}</div>
          <div class="doc-meta">${sizeStr} · ${formatTime(doc.createTime)}</div>
        </div>
        <div class="doc-actions">
          ${statusBadge}
          <button class="btn btn-danger btn-sm" onclick="window.__deleteDoc?.('${doc.id}')">${ICONS.TRASH} 删除</button>
        </div>
      </div>
    `;
  }).join('');
}

async function handleUpload(files) {
  for (const file of files) {
    try {
      window.__toast?.(`正在上传 ${file.name}...`, 'success');
      await uploadFile(file);
      window.__toast?.(`${file.name} 上传成功，后台处理中`, 'success');
    } catch(e) {
      window.__toast?.(`上传失败: ${e.message}`, 'error');
    }
  }
  // Reset file input
  const input = document.getElementById('file-input');
  if (input) input.value = '';
  // Reload
  await loadDocs();
}

async function deleteDoc(id) {
  if (!confirm('确认删除该文档？关联的向量数据也会被清除。')) return;
  try {
    await deleteDocument(id);
    window.__toast?.('文档已删除', 'success');
    await loadDocs();
  } catch(e) {
    window.__toast?.(`删除失败: ${e.message}`, 'error');
  }
}

function startPoll() {
  stopPoll();
  // If any doc is processing, poll every 3s
  if (docs.some(d => d.status === 0)) {
    pollTimer = setInterval(async () => {
      try {
        docs = await listDocuments() || [];
        renderDocs();
        updateBadge();
        if (!docs.some(d => d.status === 0)) stopPoll();
      } catch(e) { stopPoll(); }
    }, 3000);
  }
}

function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

function updateBadge() {
  const badge = document.getElementById('badge-docs');
  if (badge) badge.textContent = docs.length;
}

function formatTime(t) {
  if (!t) return '';
  try {
    const d = new Date(t);
    return `${d.getMonth()+1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2,'0')}`;
  } catch { return ''; }
}

function escapeHtml(text) {
  const d = document.createElement('div');
  d.textContent = text || '';
  return d.innerHTML;
}

export function destroyKnowledge() {
  stopPoll();
  delete window.__handleUpload;
  delete window.__deleteDoc;
}
