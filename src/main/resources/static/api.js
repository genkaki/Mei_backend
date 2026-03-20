// ============================================================
// MeiStudio Web Demo — API Layer
// Fixed: Bearer prefix for Authorization header
// ============================================================

const API_BASE = window.location.origin;

let authToken = null;
let currentUserId = null;

// ==================== Auth ====================

export async function register(email, password, code) {
  const res = await fetch(`${API_BASE}/api/user/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, code }),
  });
  const data = await res.json();
  if (data.code !== 200) throw new Error(data.msg || '注册失败');
  return data.data;
}

export async function sendCode(email) {
  const res = await fetch(`${API_BASE}/api/user/send-code?email=${encodeURIComponent(email)}`);
  const data = await res.json();
  if (data.code !== 200) throw new Error(data.msg || '发送验证码失败');
  return data.data;
}

export async function login(email, password) {
  const res = await fetch(`${API_BASE}/api/user/login-email`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  if (data.code === 200) {
    authToken = data.data.token;
    currentUserId = data.data.userId;
    // Persist to sessionStorage
    sessionStorage.setItem('mei_token', authToken);
    sessionStorage.setItem('mei_userId', String(currentUserId));
    sessionStorage.setItem('mei_userEmail', data.data.email);
    return data.data;
  }
  throw new Error(data.msg || '登录失败');
}

export function logout() {
  authToken = null;
  currentUserId = null;
  sessionStorage.removeItem('mei_token');
  sessionStorage.removeItem('mei_userId');
}

export function restoreSession() {
  const t = sessionStorage.getItem('mei_token');
  const u = sessionStorage.getItem('mei_userId');
  if (t && u) {
    authToken = t;
    currentUserId = u;
    return true;
  }
  return false;
}

function headers(extra = {}) {
  return {
    'Content-Type': 'application/json',
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    ...extra,
  };
}

async function request(path, opts = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    ...opts,
    headers: headers(opts.headers || {}),
  });
  const data = await res.json();
  if (data.code === 401) {
    logout();
    window.location.hash = '#/';
    throw new Error('登录已过期，请重新登录');
  }
  if (data.code !== 200) throw new Error(data.msg || '请求失败');
  return data.data;
}

// ==================== User Config ====================

export async function getUserConfig() {
  const token = sessionStorage.getItem('mei_token');
  const res = await fetch(`${API_BASE}/api/user/config`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  const data = await res.json();
  if (data.code !== 200) throw new Error(data.msg || '获取配置失败');
  return data.data;
}

export async function saveUserConfig(config) {
  const token = sessionStorage.getItem('mei_token');
  const res = await fetch(`${API_BASE}/api/user/config`, {
    method: 'POST',
    headers: { 
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(config),
  });
  const data = await res.json();
  if (data.code !== 200) throw new Error(data.msg || '保存配置失败');
  return data.data;
}

// ==================== Agent / Chat ====================

export async function chatStream(message, fileIds, mcpServerIds, onToken, onDone, onError) {
  try {
    const res = await fetch(`${API_BASE}/api/agent/chat-stream`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({ 
        message, 
        fileIds, 
        mcpServerIds,
        modelName: '', 
        temperature: 0.7 
      }),
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(errText || `HTTP ${res.status}`);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const payload = line.substring(5).trim();
          if (payload === 'done' || payload === '[DONE]') {
            onDone?.();
            return;
          }
          onToken?.(payload);
        } else if (line.startsWith('event:complete')) {
          onDone?.();
          return;
        }
      }
    }
    onDone?.();
  } catch (e) {
    onError?.(e);
  }
}

export async function clearMemory() {
  return request('/api/agent/clear', { method: 'POST' });
}

// ==================== Knowledge Base ====================

export async function uploadFile(file) {
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${API_BASE}/api/kb/upload`, {
    method: 'POST',
    headers: { ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}) },
    body: formData,
  });
  const data = await res.json();
  if (data.code !== 200) throw new Error(data.msg || '上传失败');
  return data.data;
}

export async function listDocuments() {
  return request('/api/kb/documents');
}

export async function deleteDocument(id) {
  return request(`/api/kb/documents/${id}`, { method: 'DELETE' });
}

// ==================== MCP Servers ====================

export async function listMcpServers() {
  return request('/api/mcp/servers');
}

export async function addMcpServer(serverData) {
  return request('/api/mcp/servers', {
    method: 'POST',
    body: JSON.stringify(serverData),
  });
}

export async function removeMcpServer(id) {
  return request(`/api/mcp/servers/${id}`, { method: 'DELETE' });
}

export async function reconnectMcpServer(id) {
  return request(`/api/mcp/servers/${id}/reconnect`, { method: 'POST' });
}

export async function toggleMcpServer(id, active) {
  return request(`/api/mcp/servers/${id}/toggle`, {
    method: 'POST',
    body: JSON.stringify({ active }),
  });
}

// ==================== Utils ====================

export function getToken() { return authToken; }
export function getUserId() { return currentUserId; }
export function isLoggedIn() { return !!authToken; }
