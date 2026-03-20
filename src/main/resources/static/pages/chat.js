// ============================================================
// Chat Page — AI Conversation with SSE Streaming + RAG
// ============================================================
import { chatStream, clearMemory, listDocuments, getUserId } from '../api.js';
import { ICONS } from '../icons.js';

let messages = [];
let isStreaming = false;
let selectedFileIds = [];
let selectedMcpIds = new Set();

/**
 * 获取基于用户 ID 的存储 Key，实现同浏览器多账号隔离
 */
function getStorageKey() {
  const uid = getUserId();
  return uid ? `mei_chat_messages_${uid}` : 'mei_chat_messages_guest';
}

export function renderChat() {
  return `
    <div class="chat-layout">
      <div class="chat-main">
        <div class="chat-messages" id="chat-messages">
          <div class="empty-state" id="chat-empty">
            <div class="empty-state-icon">${ICONS.MESSAGE}</div>
            <div class="empty-state-text" style="font-size:18px;font-weight:600;color:var(--text-primary);margin-bottom:4px">开始对话</div>
            <div class="empty-state-text">向 AI 助手提问，支持联网搜索和知识库检索。<br>在右侧勾选文件可激活 RAG 知识库增强。</div>
            <div style="margin-top:20px;padding:10px 15px;background:var(--bg-card);border-radius:8px;font-size:12px;color:var(--text-muted);border:1px dashed var(--border-light);max-width:400px">
              <span style="display:block;margin-bottom:4px;color:var(--accent-light);font-weight:600">💡 存储说明</span>
              为减轻服务器压力，对话记录仅保存在您的浏览器本地（sessionStorage）。更换浏览器或清理缓存后记录将消失。
            </div>
          </div>
        </div>
        <div class="chat-input-area">
          <div class="chat-input-wrap">
            <textarea id="chat-input" rows="1" placeholder="输入你的问题... (Shift+Enter 换行)" 
              onkeydown="if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();window.__sendMessage?.()}"></textarea>
            <button class="send-btn" id="send-btn" onclick="window.__sendMessage?.()">${ICONS.SEND}</button>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-top:8px">
            <div style="font-size:11px;color:var(--text-muted)" id="rag-status">普通对话模式</div>
            <button class="btn btn-ghost btn-sm" onclick="window.__clearChat?.()">${ICONS.TRASH} 清空对话</button>
          </div>
        </div>
      </div>
      <div class="chat-sidebar">
        <div class="chat-sidebar-header">
          <span style="display:inline-flex;align-items:center;gap:8px">${ICONS.PAPERCLIP} 附件 (RAG 增强)</span>
          <div style="font-size:11px;color:var(--text-muted);margin-top:4px">勾选文件后，AI 将优先参考其内容</div>
        </div>
        <div class="chat-sidebar-body" id="file-list-panel">
          <div style="padding:20px;text-align:center;color:var(--text-muted);font-size:13px">加载中...</div>
        </div>

        <div class="chat-sidebar-header" style="margin-top:16px;border-top:1px solid var(--border-light);padding-top:16px">
          <span style="display:inline-flex;align-items:center;gap:8px">${ICONS.PLUG} MCP 插件 (工具增强)</span>
          <div style="font-size:11px;color:var(--text-muted);margin-top:4px">选择对话中可调用的外部工具</div>
        </div>
        <div class="chat-sidebar-body" id="mcp-list-panel">
          <div style="padding:20px;text-align:center;color:var(--text-muted);font-size:13px">加载中...</div>
        </div>
      </div>
    </div>
  `;
}

export function initChat() {
  isStreaming = false;
  selectedFileIds = [];
  selectedMcpIds = new Set();
  
  // Persistence: Load messages from storage
  messages = loadMessages();

  window.__sendMessage = sendMessage;
  window.__clearChat = doClearChat;
  window.__toggleFile = toggleFile;
  window.__toggleMcp = toggleMcp;

  // Configure marked for Markdown
  if (typeof marked !== 'undefined') {
    marked.setOptions({
      breaks: true,
      gfm: true,
      headerIds: false,
      mangle: false
    });
  }

  loadFileList();
  loadMcpServers();
  renderMessages();
}

/**
 * Persistence: Load from SessionStorage
 */
function loadMessages() {
  const stored = sessionStorage.getItem(getStorageKey());
  if (stored) {
    try { return JSON.parse(stored); } catch(e) { return []; }
  }
  return [];
}

/**
 * Persistence: Save to SessionStorage
 */
function saveMessages() {
  sessionStorage.setItem(getStorageKey(), JSON.stringify(messages));
}

async function loadFileList() {
  const panel = document.getElementById('file-list-panel');
  try {
    const docs = await listDocuments();
    if (!docs || docs.length === 0) {
      panel.innerHTML = `<div class="empty-state" style="padding:30px">
        <div style="margin-bottom:12px;opacity:0.5">${ICONS.FILE}</div>
        <div style="font-size:12px">暂无文件<br><a href="#/knowledge" style="color:var(--accent-light)">去上传</a></div>
      </div>`;
      return;
    }
    const completedDocs = docs.filter(d => d.status === 1);
    panel.innerHTML = completedDocs.map(doc => `
      <label class="file-check-item">
        <input type="checkbox" value="${doc.id}" onchange="window.__toggleFile?.('${doc.id}', this.checked)">
        <span class="file-check-name">${doc.fileName}</span>
      </label>
    `).join('') || '<div style="padding:20px;text-align:center;color:var(--text-muted);font-size:12px">无已完成文件</div>';
  } catch(e) {
    panel.innerHTML = `<div style="padding:20px;text-align:center;color:var(--danger);font-size:12px">加载失败</div>`;
  }
}

function toggleFile(id, checked) {
  if (checked) {
    if (!selectedFileIds.includes(id)) selectedFileIds.push(id);
  } else {
    selectedFileIds = selectedFileIds.filter(f => f !== id);
  }
  updateRagStatus();
}

async function loadMcpServers() {
  const panel = document.getElementById('mcp-list-panel');
  if (!panel) return;
  try {
    const { listMcpServers } = await import('../api.js');
    const servers = await listMcpServers();
    const activeServers = servers.filter(s => s.active);
    
    if (activeServers.length === 0) {
      panel.innerHTML = `<div style="padding:20px;text-align:center;color:var(--text-muted);font-size:12px">暂无可用插件</div>`;
      return;
    }

    panel.innerHTML = activeServers.map(s => {
      // 默认选中所有已连接的插件
      if (s.status === 1) selectedMcpIds.add(s.id);
      return `
        <label class="file-check-item">
          <input type="checkbox" value="${s.id}" ${s.status === 1 ? 'checked' : ''} 
            onchange="window.__toggleMcp?.('${s.id}', this.checked)">
          <div style="display:flex;flex-direction:column">
            <span class="file-check-name">${s.name}</span>
            <span style="font-size:10px;color:${s.status === 1 ? 'var(--success)' : 'var(--danger)'}">
              ${s.status === 1 ? '● 已连接' : '○ 离线'} (${s.toolCount || 0} 工具)
            </span>
          </div>
        </label>
      `;
    }).join('');
  } catch(e) {
    panel.innerHTML = `<div style="padding:20px;text-align:center;color:var(--danger);font-size:12px">加载失败</div>`;
  }
}

function toggleMcp(id, checked) {
  if (checked) {
    selectedMcpIds.add(id);
  } else {
    selectedMcpIds.delete(id);
  }
}

function updateRagStatus() {
  const el = document.getElementById('rag-status');
  const badge = document.getElementById('badge-rag');
  if (selectedFileIds.length > 0) {
    el.textContent = `📎 RAG 模式 — 已选 ${selectedFileIds.length} 个文件`;
    el.style.color = 'var(--accent-light)';
    badge.style.display = '';
  } else {
    el.textContent = '普通对话模式';
    el.style.color = 'var(--text-muted)';
    badge.style.display = 'none';
  }
}

function renderMessages() {
  const container = document.getElementById('chat-messages');
  const empty = document.getElementById('chat-empty');
  if (!container) return;

  if (messages.length === 0) {
    if (empty) empty.style.display = '';
    container.innerHTML = '';
    return;
  }
  if (empty) empty.style.display = 'none';

  // Optimization: If counts match, only update the last bubble content
  const existingCount = container.querySelectorAll('.message').length;
  if (existingCount === messages.length && existingCount > 0) {
    const last = messages[messages.length - 1];
    if (last.role === 'assistant') {
      const lastEl = container.querySelector('.message:last-child .message-bubble');
      if (lastEl) {
        lastEl.innerHTML = parseMarkdown(last.content);
        // Add a typing cursor if still streaming
        if (isStreaming) {
          lastEl.innerHTML += '<span class="typing-cursor">|</span>';
        }
      }
    }
    return;
  }

  let html = '';
  for (const msg of messages) {
    const contentHtml = msg.role === 'assistant' ? parseMarkdown(msg.content) : escapeHtml(msg.content);
    html += `
      <div class="message ${msg.role}">
        <div class="message-bubble">${contentHtml}</div>
      </div>
    `;
  }
  container.innerHTML = html;
  container.scrollTop = container.scrollHeight;
}

async function sendMessage() {
  if (isStreaming) return;
  const input = document.getElementById('chat-input');
  const text = input.value.trim();
  if (!text) return;

  input.value = '';
  messages.push({ role: 'user', content: text });
  messages.push({ role: 'assistant', content: '' });
  saveMessages(); // Save instantly
  renderMessages();

  isStreaming = true;
  const sendBtn = document.getElementById('send-btn');
  if (sendBtn) {
    sendBtn.disabled = true;
    sendBtn.innerHTML = '<div class="spinner spinner-sm"></div>';
  }

  const assistantIdx = messages.length - 1;

  await chatStream(
    text,
    selectedFileIds.length > 0 ? selectedFileIds : null,
    selectedMcpIds.size > 0 ? Array.from(selectedMcpIds) : null,
    (token) => {
      messages[assistantIdx].content += token;
      // We don't save to storage on every token for performance, only on complete
      // But we render it
      renderMessages();
      const container = document.getElementById('chat-messages');
      if (container) container.scrollTop = container.scrollHeight;
    },
    () => {
      isStreaming = false;
      saveMessages(); // Save final response
      if (sendBtn) {
        sendBtn.disabled = false;
        sendBtn.innerHTML = ICONS.SEND;
      }
      renderMessages();
    },
    (err) => {
      messages[assistantIdx].content = `⚠️ 请求失败: ${err.message}`;
      isStreaming = false;
      if (sendBtn) {
        sendBtn.disabled = false;
        sendBtn.innerHTML = ICONS.SEND;
      }
      renderMessages();
    }
  );
}

async function doClearChat() {
  if (!confirm('确定清空当前对话记录吗？此操作不可撤销。')) return;
  try {
    await clearMemory();
  } catch(e) { /* ignore */ }
  messages = [];
  sessionStorage.removeItem(getStorageKey());
  renderMessages();
  window.__toast?.('对话已清空', 'success');
}

function parseMarkdown(text) {
  try {
    // 1. 先处理 MCP 特殊标记（转换为 HTML）
    let html = renderMcpRichContent(text || '');
    
    // 2. 使用 marked 解析剩余的 Markdown 语法
    if (typeof marked !== 'undefined') {
      return marked.parse(html);
    }
    return html.replace(/\n/g, '<br>');
  } catch(e) {
    console.error('Markdown parse error:', e);
    return escapeHtml(text).replace(/\n/g, '<br>');
  }
}

/**
 * 将后端生成的 [MCP_XXX] 标记转换为 Web 渲染的 HTML
 */
function renderMcpRichContent(text) {
  if (!text) return '';

  // 1. [MCP_IMAGE:url]
  text = text.replace(/\[MCP_IMAGE:(.+?)\]/g, (match, url) => {
    return `<div class="mcp-rich-block mcp-image"><img src="${url}" alt="MCP Image" loading="lazy"></div>`;
  });

  // 2. [MCP_IMAGE_BASE64:mime:data]
  text = text.replace(/\[MCP_IMAGE_BASE64:(.+?):(.+?)\]/g, (match, mime, data) => {
    return `<div class="mcp-rich-block mcp-image"><img src="data:${mime};base64,${data}" alt="MCP Image"></div>`;
  });

  // 3. [MCP_VIDEO:url]
  text = text.replace(/\[MCP_VIDEO:(.+?)\]/g, (match, url) => {
    return `<div class="mcp-rich-block mcp-video"><video src="${url}" controls preload="metadata"></video></div>`;
  });

  // 4. [MCP_AUDIO:url]
  text = text.replace(/\[MCP_AUDIO:(.+?)\]/g, (match, url) => {
    return `<div class="mcp-rich-block mcp-audio"><audio src="${url}" controls preload="metadata"></audio></div>`;
  });

  // 5. [MCP_FILE:name|url]
  text = text.replace(/\[MCP_FILE:(.+?)\|(.+?)\]/g, (match, name, url) => {
    return `
      <div class="mcp-rich-block mcp-file-card">
        <div class="mcp-file-icon">📄</div>
        <div class="mcp-file-info">
          <div class="mcp-file-name">${name}</div>
          <div class="mcp-file-meta">MCP 资源文件</div>
        </div>
        <a href="${url}" target="_blank" class="mcp-file-btn">打开</a>
      </div>
    `;
  });

  // 6. [MCP_LINK:title|url]
  text = text.replace(/\[MCP_LINK:(.+?)\|(.+?)\]/g, (match, title, url) => {
    return `
      <a href="${url}" target="_blank" class="mcp-rich-block mcp-link-card">
        <div class="mcp-link-icon">🔗</div>
        <div class="mcp-link-content">
          <div class="mcp-link-title">${title}</div>
          <div class="mcp-link-url">${url}</div>
        </div>
      </a>
    `;
  });

  return text;
}

function escapeHtml(text) {
  const d = document.createElement('div');
  d.textContent = text;
  return d.innerHTML;
}

export function destroyChat() {
  delete window.__sendMessage;
  delete window.__clearChat;
  delete window.__toggleFile;
}
