// ============================================================
// MCP Plugin Management Page
// ============================================================
import { listMcpServers, addMcpServer, removeMcpServer, reconnectMcpServer, toggleMcpServer } from '../api.js';
import { ICONS } from '../icons.js';

let servers = [];

export function renderMcp() {
  return `
    <div class="page-header">
      <h1 class="page-title"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.PLUG}</span> MCP 插件管理</h1>
      <p class="page-desc">
        添加外部 MCP Server 后，AI 助手将自动发现并使用其工具能力（如 AI 绘图、数据库查询等）。
        支持 Streamable HTTP 和 SSE 两种传输协议。
      </p>
    </div>
    <div class="mcp-layout">
      <!-- Add Form -->
      <div class="mcp-add-form">
        <h3 style="font-size:15px;font-weight:600;margin-bottom:16px">添加 MCP Server</h3>
        <div class="form-row">
          <div class="input-group">
            <label>服务名称 *</label>
            <input class="input" id="mcp-name" placeholder="如：阿里云 AI 绘图">
          </div>
          <div class="input-group">
            <label>端点地址 *</label>
            <input class="input" id="mcp-url" placeholder="https://example.com/mcp">
          </div>
        </div>
        <div class="form-row">
          <div class="input-group">
            <label>API Key (可选)</label>
            <input class="input" id="mcp-apikey" placeholder="sk-xxx" type="password">
          </div>
          <div class="input-group">
            <label>描述 (可选)</label>
            <input class="input" id="mcp-desc" placeholder="服务功能简介">
          </div>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" id="mcp-add-btn" onclick="window.__addMcpServer?.()">
            ${ICONS.LINK} 连接并添加
          </button>
        </div>
      </div>

      <!-- Server List -->
      <h3 style="font-size:16px;font-weight:600;margin-bottom:16px">已配置的服务</h3>
      <div id="mcp-server-list">
        <div style="text-align:center;padding:30px;color:var(--text-muted)">
          <div class="spinner"></div>
          <div style="margin-top:12px;font-size:13px">加载中...</div>
        </div>
      </div>
    </div>
  `;
}

export function initMcp() {
  window.__addMcpServer = doAddServer;
  window.__removeMcpServer = doRemoveServer;
  window.__reconnectMcpServer = doReconnect;
  window.__toggleMcpServer = doToggle;

  loadServers();
}

async function loadServers() {
  try {
    servers = await listMcpServers() || [];
    renderServers();
    updateBadge();
  } catch(e) {
    document.getElementById('mcp-server-list').innerHTML =
      `<div style="padding:20px;text-align:center;color:var(--danger)">加载失败: ${e.message}</div>`;
  }
}

function renderServers() {
  const list = document.getElementById('mcp-server-list');
  if (!list) return;

  if (servers.length === 0) {
    list.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-icon" style="opacity:0.3;margin-bottom:20px">${ICONS.PLUG}</div>
        <div class="empty-state-text">还没有配置任何 MCP Server</div>
        <div class="empty-state-text" style="color:var(--text-muted)">添加后，AI 助手将自动获得新的工具能力</div>
      </div>
    `;
    return;
  }

  list.innerHTML = servers.map(s => {
    const online = s.status === 1;
    const active = s.active !== false;
    return `
      <div class="server-item" style="${!active ? 'opacity:0.5' : ''}">
        <div class="server-icon" style="display:flex;align-items:center;justify-content:center">
          <span class="status-dot ${online ? 'online' : 'offline'}" style="width:10px;height:10px"></span>
        </div>
        <div class="server-info">
          <div class="server-name">${escapeHtml(s.name)}</div>
          <div class="server-url">${escapeHtml(s.url)}</div>
          <div class="server-meta">
            <span class="badge ${online ? 'badge-success' : 'badge-danger'}">${online ? '在线' : '离线'}</span>
            <span class="badge badge-info"><span style="display:inline-flex;align-items:center;width:12px;height:12px;margin-right:4px">${ICONS.TERMINAL}</span> ${s.toolCount || 0} 个工具</span>
            <span class="badge badge-accent">${s.type || 'streamableHttp'}</span>
            ${!active ? '<span class="badge badge-warning">已禁用</span>' : ''}
          </div>
        </div>
        <div class="server-actions">
          <button class="btn btn-ghost btn-sm" onclick="window.__reconnectMcpServer?.('${s.id}')">🔄 重连</button>
          <button class="btn btn-ghost btn-sm" onclick="window.__toggleMcpServer?.('${s.id}', ${!active})">
            ${active ? '⏸ 禁用' : '▶ 启用'}
          </button>
          <button class="btn btn-danger btn-sm" onclick="window.__removeMcpServer?.('${s.id}')">${ICONS.TRASH} 删除</button>
        </div>
      </div>
    `;
  }).join('');
}

async function doAddServer() {
  const name = document.getElementById('mcp-name')?.value?.trim();
  const url = document.getElementById('mcp-url')?.value?.trim();
  const apiKey = document.getElementById('mcp-apikey')?.value?.trim();
  const description = document.getElementById('mcp-desc')?.value?.trim();

  if (!name || !url) {
    window.__toast?.('请填写服务名称和端点地址', 'error');
    return;
  }

  const btn = document.getElementById('mcp-add-btn');
  if (btn) { btn.disabled = true; btn.textContent = '⏳ 连接中...'; }

  try {
    await addMcpServer({ name, url, apiKey, description, type: 'streamableHttp' });
    window.__toast?.(`${name} 添加成功`, 'success');
    // Clear form
    ['mcp-name','mcp-url','mcp-apikey','mcp-desc'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.value = '';
    });
    await loadServers();
  } catch(e) {
    window.__toast?.(`添加失败: ${e.message}`, 'error');
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = '🔗 连接并添加'; }
  }
}

async function doRemoveServer(id) {
  if (!confirm('确认删除该 MCP Server？')) return;
  try {
    await removeMcpServer(id);
    window.__toast?.('已删除', 'success');
    await loadServers();
  } catch(e) {
    window.__toast?.(`删除失败: ${e.message}`, 'error');
  }
}

async function doReconnect(id) {
  try {
    window.__toast?.('正在重连...', 'success');
    await reconnectMcpServer(id);
    window.__toast?.('重连成功', 'success');
    await loadServers();
  } catch(e) {
    window.__toast?.(`重连失败: ${e.message}`, 'error');
  }
}

async function doToggle(id, active) {
  try {
    await toggleMcpServer(id, active);
    window.__toast?.(active ? '已启用' : '已禁用', 'success');
    await loadServers();
  } catch(e) {
    window.__toast?.(`操作失败: ${e.message}`, 'error');
  }
}

function updateBadge() {
  const badge = document.getElementById('badge-mcp');
  if (badge) badge.textContent = servers.length;
}

function escapeHtml(text) {
  const d = document.createElement('div');
  d.textContent = text || '';
  return d.innerHTML;
}

export function destroyMcp() {
  delete window.__addMcpServer;
  delete window.__removeMcpServer;
  delete window.__reconnectMcpServer;
  delete window.__toggleMcpServer;
}
