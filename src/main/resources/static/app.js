// ============================================================
// MeiStudio Web Demo — App Router with Login Gate
// ============================================================
import { login, isLoggedIn, logout, restoreSession, sendCode } from './api.js';
import { renderLanding } from './pages/landing.js';
import { renderSettings, initSettings, destroySettings } from './pages/settings.js';
import { renderChat, initChat, destroyChat } from './pages/chat.js';
import { renderKnowledge, initKnowledge, destroyKnowledge } from './pages/knowledge.js';
import { renderMcp, initMcp, destroyMcp } from './pages/mcp.js';
import { showOnboarding } from './components/onboarding.js';

let currentPage = null;

// ==================== Routes ====================

// Public routes (no login required)
const publicRoutes = {
  '/': { render: renderLanding, init: null, destroy: null, navKey: 'landing' },
};

// Protected routes (login required)
const protectedRoutes = {
  '/chat':      { render: renderChat, init: initChat, destroy: destroyChat, navKey: 'chat' },
  '/knowledge': { render: renderKnowledge, init: initKnowledge, destroy: destroyKnowledge, navKey: 'knowledge' },
  '/settings':  { render: renderSettings, init: initSettings, destroy: destroySettings, navKey: 'settings' },
  '/mcp':       { render: renderMcp, init: initMcp, destroy: destroyMcp, navKey: 'mcp' },
};

function navigate(hash) {
  const path = hash.replace('#', '') || '/';

  // Check if route requires login
  if (protectedRoutes[path] && !isLoggedIn()) {
    window.__toast?.('请先登录后再使用该功能', 'error');
    window.location.hash = '#/';
    return;
  }

  const route = protectedRoutes[path] || publicRoutes[path] || publicRoutes['/'];

  // Destroy previous page
  if (currentPage?.destroy) currentPage.destroy();

  // Render new page
  const container = document.getElementById('page-container');
  container.innerHTML = route.render();

  // Init new page
  if (route.init) route.init();

  // Update nav active state
  document.querySelectorAll('.nav-item').forEach(item => {
    item.classList.toggle('active', item.dataset.page === route.navKey);
  });

  // Update sidebar visibility
  updateSidebarState();

  // Setup reveal after render (Delayed to ensure DOM is ready)
  setTimeout(() => {
    if (window.__setupScrollReveal) window.__setupScrollReveal();
  }, 100);

  currentPage = route;

  // Mobile: Auto close sidebar on navigate
  if (window.innerWidth <= 768) {
    const sidebar = document.getElementById('sidebar');
    if (sidebar?.classList.contains('mobile-open')) {
      window.__toggleSidebar?.();
    }
  }
}

// ==================== Login / Logout ====================

// ==================== Login / Register / Logout ====================

let authMode = 'login'; // 'login' or 'register'

window.__switchAuthMode = function(mode) {
  authMode = mode;
  const isLogin = mode === 'login';
  
  // Update Tabs
  document.getElementById('tab-login').classList.toggle('active', isLogin);
  document.getElementById('tab-register').classList.toggle('active', !isLogin);
  
  // Update Header
  document.getElementById('auth-title').textContent = isLogin ? '🔑 欢迎回来' : '✨ 建立新账号';
  document.getElementById('auth-desc').textContent = isLogin ? '请使用您的邮箱进行登录' : '创建一个 MeiStudio 独立空间账号';
  
  // Update Button & Footer
  document.getElementById('auth-btn').textContent = isLogin ? '🚀 登录' : '🌱 立即注册';
  document.getElementById('auth-switch-text').innerHTML = isLogin 
    ? `还没有账号？<a href="javascript:void(0)" onclick="window.__switchAuthMode('register')">立即注册</a>`
    : `已有账号？<a href="javascript:void(0)" onclick="window.__switchAuthMode('login')">返回登录</a>`;

  // Update Code Group Visibility
  const codeGroup = document.getElementById('code-group');
  if (codeGroup) {
    codeGroup.style.display = isLogin ? 'none' : 'flex';
  }
};

window.__sendCode = async function() {
  const emailInput = document.getElementById('login-email');
  const email = emailInput?.value?.trim();
  const btn = document.getElementById('send-code-btn');

  if (!email || !email.includes('@')) {
    window.__toast?.('请先输入有效的邮箱地址', 'error');
    return;
  }

  if (btn) { btn.disabled = true; btn.textContent = '⏳ 发送中...'; }

  try {
    await sendCode(email);
    window.__toast?.('验证码已发送至您的邮箱 (请同时检查垃圾箱)', 'success');
    
    // Countdown
    let count = 60;
    const timer = setInterval(() => {
      count--;
      if (count > 0) {
        btn.textContent = `${count}s 后重发`;
      } else {
        clearInterval(timer);
        btn.disabled = false;
        btn.textContent = '获取验证码';
      }
    }, 1000);
  } catch (e) {
    window.__toast?.('发送失败: ' + e.message, 'error');
    if (btn) { btn.disabled = false; btn.textContent = '获取验证码'; }
  }
};

window.__doAuth = async function() {
  const emailInput = document.getElementById('login-email');
  const passwordInput = document.getElementById('login-password');
  const btn = document.getElementById('auth-btn');
  
  const email = emailInput?.value?.trim();
  const password = passwordInput?.value?.trim();

  if (!email || !password) {
    window.__toast?.('请输入邮箱和密码', 'error');
    return;
  }

  // Basic email validation
  if (!email.includes('@')) {
    window.__toast?.('请输入有效的邮箱地址', 'error');
    return;
  }

  if (btn) { btn.disabled = true; btn.textContent = '⏳ 处理中...'; }

  try {
    if (authMode === 'login') {
      await login(email, password);
      window.__toast?.('登录成功！', 'success');
      updateSidebarState();
      updateStatus(true);
      window.location.hash = '#/chat';
    } else {
      const code = document.getElementById('login-code')?.value?.trim();
      if (!code) {
        window.__toast?.('请输入验证码', 'error');
        if (btn) { btn.disabled = false; btn.textContent = '🌱 立即注册'; }
        return;
      }
      await import('./api.js').then(api => api.register(email, password, code));
      // 注册成功后自动登录，提升职业感 (Pro UX)
      await login(email, password);
      updateSidebarState();
      updateStatus(true);
      showOnboarding();
    }
  } catch(e) {
    window.__toast?.(`${authMode === 'login' ? '登录' : '注册'}失败: ${e.message}`, 'error');
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = authMode === 'login' ? '🚀 登录' : '🌱 立即注册';
    }
  }
};

window.__doLogout = function() {
  logout();
  updateSidebarState();
  updateStatus(false);
  window.__toast?.('已退出登录', 'success');
  window.location.hash = '#/';
};

// ==================== Sidebar State ====================

function updateSidebarState() {
  const loggedIn = isLoggedIn();
  // Show/hide protected nav items
  document.querySelectorAll('.nav-item[data-protected]').forEach(item => {
    item.style.display = loggedIn ? '' : 'none';
  });
  // Show/hide logout btn
  const logoutBtn = document.getElementById('logout-btn');
  if (logoutBtn) logoutBtn.style.display = loggedIn ? '' : 'none';
  // Update user display
  const userDisplay = document.getElementById('user-display');
  if (userDisplay) userDisplay.style.display = loggedIn ? '' : 'none';
}

// ==================== Toast System ====================

function setupToast() {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  window.__toast = (msg, type = 'success') => {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = msg;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(20px)';
      toast.style.transition = 'all 0.3s';
      setTimeout(() => toast.remove(), 300);
    }, 3000);
  };
}

// ==================== Connection Status ====================

function updateStatus(online) {
  const dot = document.getElementById('status-dot');
  const text = document.getElementById('status-text');
  if (dot) dot.className = `status-dot ${online ? 'online' : 'offline'}`;
  if (text) text.textContent = online ? '已连接' : '未登录';
}

// Global reveal setup (called on page load and navigation)
window.__setupScrollReveal = function() {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
      }
    });
  }, { threshold: 0.1 });

  document.querySelectorAll('.reveal, .reveal-stagger').forEach(el => {
    observer.observe(el);
  });
};

// ==================== Theme System ====================

window.__toggleTheme = function() {
  const isLight = document.body.getAttribute('data-theme') === 'light';
  const newTheme = isLight ? 'dark' : 'light';
  
  document.body.setAttribute('data-theme', newTheme);
  localStorage.setItem('meistudio-theme', newTheme);
  
  const icon = document.getElementById('theme-icon');
  if (icon) icon.textContent = newTheme === 'light' ? '☀️' : '🌙';
  
  // Notify other components (like particles)
  window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme: newTheme } }));
};

function initTheme() {
  const savedTheme = localStorage.getItem('meistudio-theme') || 'dark';
  document.body.setAttribute('data-theme', savedTheme);
  const icon = document.getElementById('theme-icon');
  if (icon) icon.textContent = savedTheme === 'light' ? '☀️' : '🌙';
}

// ==================== Sidebar Mobile Toggle ====================

window.__toggleSidebar = function() {
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('sidebar-overlay');
  const body = document.body;
  if (sidebar) {
    const isOpen = sidebar.classList.toggle('mobile-open');
    if (overlay) overlay.classList.toggle('active', isOpen);
    body.classList.toggle('lock-scroll', isOpen);
  }
};

// ==================== App Init ====================

async function init() {
  initTheme();
  setupToast();
  // ... rest of init

  // Try to restore session
  const restored = restoreSession();
  if (restored) {
    updateStatus(true);
  } else {
    updateStatus(false);
  }

  updateSidebarState();

  // Hash routing
  window.addEventListener('hashchange', () => navigate(location.hash));
  navigate(location.hash || '#/');
}

// Start
init();
