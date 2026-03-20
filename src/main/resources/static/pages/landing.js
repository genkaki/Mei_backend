// ============================================================
// Landing Page — Project Showcase + Login Form
// ============================================================
import { isLoggedIn } from '../api.js';
import { ICONS } from '../icons.js';

export function renderLanding() {
  const loggedIn = isLoggedIn();

  const loginSection = loggedIn ? `
    <div class="hero-actions">
      <a href="#/chat" class="btn btn-primary">${ICONS.MESSAGE} 体验 AI 对话</a>
      <a href="https://appgallery.huawei.com/app/detail?id=com.genkaki.meistudio&channelId=SHARE&source=appshare" target="_blank" class="btn btn-appgallery">
        <span style="font-size:16px;margin-right:8px">🛒</span> 华为应用市场下载
      </a>
      <a href="#/knowledge" class="btn btn-ghost">${ICONS.BOOK} 管理知识库</a>
    </div>
  ` : `
    <div class="login-card">
      <div class="login-card-inner">
        <div class="auth-tabs">
          <button class="auth-tab active" id="tab-login" onclick="window.__switchAuthMode('login')">登录</button>
          <button class="auth-tab" id="tab-register" onclick="window.__switchAuthMode('register')">注册</button>
        </div>
        
        <div class="auth-header">
          <h3 id="auth-title">${ICONS.LOCK} 欢迎回来</h3>
          <p id="auth-desc">请使用您的邮箱进行登录</p>
        </div>

        <div class="login-form">
          <div class="input-group">
            <input type="email" class="input" id="login-email" placeholder="邮箱地址 (example@meistudio.com)"
              onkeydown="if(event.key==='Enter') window.__doAuth?.()">
          </div>
          <div class="input-group">
            <input type="password" class="input" id="login-password" placeholder="请输入密码"
              onkeydown="if(event.key==='Enter') window.__doAuth?.()">
          </div>
          <div class="input-group" id="code-group" style="display: none; gap: 8px;">
            <input type="text" class="input" id="login-code" placeholder="6位验证码" style="flex: 1;">
            <button class="btn btn-outline" id="send-code-btn" onclick="window.__sendCode?.()" style="min-width: 100px; padding: 0 12px; font-size: 13px;">
              获取验证码
            </button>
          </div>
          <button class="btn btn-primary" id="auth-btn" onclick="window.__doAuth?.()">
            ${ICONS.ROCKET} 登录
          </button>
        </div>

        <div class="auth-footer">
          <p id="auth-switch-text">还没有账号？<a href="javascript:void(0)" onclick="window.__switchAuthMode('register')">立即注册</a></p>
        </div>

        <p class="login-hint">
          💡 <strong>独立空间</strong>：网页端采用邮箱认证体系，数据与鸿蒙端（设备 ID 模式）物理隔离，确保开发与演示互不干扰。
        </p>

        <div style="margin-top:20px; text-align:center">
          <a href="https://appgallery.huawei.com/app/detail?id=com.genkaki.meistudio&channelId=SHARE&source=appshare" target="_blank" class="btn btn-appgallery" style="width:100%">
            <span style="font-size:16px;margin-right:8px">🛒</span> 华为应用市场下载 (HarmonyOS)
          </a>
        </div>
      </div>
    </div>
  `;

  return `
    <div class="landing-hero reveal">
      <div class="hero-logo">
        <img src="/assets/logo.png" alt="MeiStudio Logo">
      </div>
      <div class="hero-badge">✦ Spring Boot + LangChain4j + MCP 全栈实战</div>
      <h1 class="hero-title">MeiStudio AI 智能助手平台</h1>
      <p class="hero-subtitle">
        一个面向 HarmonyOS NEXT 的 AI 助手应用后端。集成 RAG 知识库检索增强、
        MCP 协议插件扩展、联网搜索、流式对话等能力，后端约 3000+ 行 Java 代码。
      </p>
      ${loginSection}
    </div>

    <!-- Stats -->
    <div class="landing-stats reveal-stagger">
      <div class="stat-card card-shine">
        <div class="stat-value">3300+</div>
        <div class="stat-label">Java 后端代码行</div>
      </div>
      <div class="stat-card card-shine">
        <div class="stat-value">39</div>
        <div class="stat-label">核心源文件</div>
      </div>
      <div class="stat-card card-shine">
        <div class="stat-value">6</div>
        <div class="stat-label">MCP 协议方法</div>
      </div>
      <div class="stat-card card-shine">
        <div class="stat-value">5-8x</div>
        <div class="stat-label">处理加速比</div>
      </div>
    </div>

    <div class="landing-section reveal">
      <h2 class="section-title"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.TERMINAL}</span> 深度架构解析 (滚动手感探秘)</h2>
      
      <div class="scrollytelling-container">
        <!-- Sticky Visual Side -->
        <div class="scrolly-sticky">
          <div class="arch-flow-container card-shine highlight-hover" id="scrolly-visual">
            <svg class="arch-connectors" viewBox="0 0 900 300">
              <path class="arch-connector-line" data-node="client" d="M225,80 L450,150"></path>
              <path class="arch-connector-line" data-node="web" d="M675,80 L450,150"></path>
              <path class="arch-connector-line" data-node="mysql" d="M450,210 L300,280"></path>
              <path class="arch-connector-line" data-node="redis" d="M450,210 L450,280"></path>
              <path class="arch-connector-line" data-node="mcp" d="M450,210 L600,280"></path>
            </svg>

            <div class="arch-row">
              <div class="arch-node client ripple" id="node-client">
                <div class="arch-node-title">HarmonyOS Client</div>
                <div class="arch-node-desc">ArkTS + UI Lifecycle</div>
              </div>
              <div class="arch-node client ripple" id="node-web">
                <div class="arch-node-title">Web Dashboard</div>
                <div class="arch-node-desc">Vanilla JS + SPA</div>
              </div>
            </div>

            <div class="arch-row">
              <div class="arch-node backend ripple" id="node-core">
                <div class="arch-node-title">MeiAgent Core</div>
                <div class="arch-node-desc">Spring Boot + LangChain4j</div>
              </div>
            </div>

            <div class="arch-row">
              <div class="arch-node infra ripple" id="node-mysql">
                <div class="arch-node-title">MySQL 8.0</div>
                <div class="arch-node-desc">Persistence</div>
              </div>
              <div class="arch-node infra ripple" id="node-redis">
                <div class="arch-node-title">Redis Vector</div>
                <div class="arch-node-desc">MeiRAG Storage</div>
              </div>
              <div class="arch-node infra ripple" id="node-mcp">
                <div class="arch-node-title">External MCP</div>
                <div class="arch-node-desc">Tool Servers</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Scrolling Content Side -->
        <div class="scrolly-content">
          <div class="scrolly-step" data-step="client">
            <h3 class="step-title">HarmonyOS NEXT 终端</h3>
            <p class="step-desc">基于 <b>ArkTS</b> 开发，深度适配 HarmonyOS 原生生命周期。集成 AppGallery Connect SDK，实现高效的文件管理与身份验证。</p>
            <div class="step-tech-tags">
              <span>ArkUI</span><span>Hvigor</span><span>FileIO</span>
            </div>
          </div>
          
          <div class="scrolly-step" data-step="core">
            <h3 class="step-title">MeiAgent 后端大脑</h3>
            <p class="step-desc">中枢神经系统，由 Spring Boot 驱动。通过 <b>LangChain4j</b> 编排 LLM 任务，实时处理流式对话建议，支持 JSON-RPC 2.0 协议。</p>
            <div class="step-tech-tags">
              <span>Spring Boot 3</span><span>LangChain4j</span><span>Virtual Threads</span>
            </div>
          </div>

          <div class="scrolly-step" data-step="redis">
            <h3 class="step-title">MeiRAG 检索管道</h3>
            <p class="step-desc">集成 Redis Vector 向量存储，利用 <b>HNSW</b> 算法实现毫秒级语义检索。解决大规模知识库处理下的限流与并行化难题。</p>
            <div class="step-tech-tags">
              <span>HNSW Index</span><span>Cosine Similarity</span><span>Jedis</span>
            </div>
          </div>

          <div class="scrolly-step" data-step="mcp">
            <h3 class="step-title">MCP 插件生态</h3>
            <p class="step-desc">手写 <b>Model Context Protocol</b> 传输层，支持 Server/Client 双向热插拔。赋予 Agent 联网搜索、数据库操作等动态工具能力。</p>
            <div class="step-tech-tags">
              <span>SSE</span><span>JSON-RPC 2.0</span><span>Adapter Pattern</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Features -->
    <div class="landing-section">
      <h2 class="section-title reveal"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.PLUS}</span> 核心能力</h2>
      <div class="feature-grid reveal-stagger">
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.BRAIN}</div>
          <div class="feature-title">MeiRAG 检索增强</div>
          <div class="feature-desc">
            上传 PDF/Word/TXT → Apache Tika 解析 → 语义分块 → DashScope 向量化 → 
            余弦相似度检索 → Prompt 注入。已解决批量 API 限流、向量持久化等生产级问题。
          </div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.PLUG}</div>
          <div class="feature-title">MeiAgent MCP 引擎</div>
          <div class="feature-desc">
            从零手写 JSON-RPC 2.0 + SSE 传输层。Server 端暴露本系统的工具能力，
            同时也作为 Client 端支持热插拔外部 MCP Server。
          </div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.CPU}</div>
          <div class="feature-title">高性能并行处理</div>
          <div class="feature-desc">
            CompletableFuture 并行向量化 + 独立 @Async 线程池，
            2.5MB 文件处理从 112s 降至 20s（5-8x 提升）。
          </div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.TERMINAL}</div>
          <div class="feature-title">Agent ReAct 循环</div>
          <div class="feature-desc">
            基于 LangChain4j AiServices 动态代理，自动编排"推理 → Function Calling → 
            工具执行 → 再推理"循环。支持同步和 SSE 流式模式。
          </div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.SHIELD}</div>
          <div class="feature-title">多租户安全隔离</div>
          <div class="feature-desc">
            JWT 鉴权 + 自定义 @RateLimit 限流。ConcurrentHashMap 实现用户级
            向量库/Agent 会话/MCP 连接三重隔离。Snowflake 分布式 ID。
          </div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.GLOBE}</div>
          <div class="feature-title">联网搜索工具</div>
          <div class="feature-desc">
            基于 Jsoup 深度爬取。通过 @Tool 注解自动注册到 MeiAgent，
            大模型根据上下文自主决定调度，实时获取新鲜资讯。
          </div>
        </div>
      </div>
    </div>

    <!-- Tech Stack -->
    <div class="landing-section reveal">
      <h2 class="section-title">🛠 技术栈</h2>
      <div class="tech-tags reveal-stagger">
        <span class="tech-tag">Java 17</span>
        <span class="tech-tag">Spring Boot 3</span>
        <span class="tech-tag">LangChain4j 0.35</span>
        <span class="tech-tag">MyBatis-Plus</span>
        <span class="tech-tag">MySQL 8</span>
        <span class="tech-tag">Apache Tika</span>
        <span class="tech-tag">DashScope API</span>
        <span class="tech-tag">JSON-RPC 2.0</span>
        <span class="tech-tag">SSE (Server-Sent Events)</span>
        <span class="tech-tag">CompletableFuture</span>
        <span class="tech-tag">JWT</span>
        <span class="tech-tag">AOP 限流</span>
        <span class="tech-tag">Jsoup</span>
        <span class="tech-tag">Docker</span>
        <span class="tech-tag">GCP VM</span>
        <span class="tech-tag">HarmonyOS NEXT (ArkTS)</span>
      </div>
    </div>
  `;
}

// ==================== Interactive Architecture Logic ====================

const TECH_DETAILS = {
  client: {
    title: "HarmonyOS NEXT 移动端实现",
    body: `
      <p>基于 <b>ArkTS</b> 开发，深度适配 HarmonyOS 原生生命周期与 UI 范式。</p>
      <div class="tech-spec-grid">
        <div class="tech-spec-item"><div class="tech-spec-label">核心框架</div><div class="tech-spec-value">ArkUI / Hvigor</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">网络通信</div><div class="tech-spec-value">@ohos.net.http / SSE</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">文件管理</div><div class="tech-spec-value">Picker + FileIO</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">性能优化</div><div class="tech-spec-value">LazyForEach 列表</div></div>
      </div>
      <p style="margin-top:15px">集成官方 <b>AppGallery Connect</b> SDK，支持云存储与 Auth 服务对接。</p>
    `
  },
  web: {
    title: "Web 演示中心 (Dashboard)",
    body: `
      <p>轻量级单页应用 (SPA)，用于跨端特性演示与 API 测试。</p>
      <div class="tech-spec-grid">
        <div class="tech-spec-item"><div class="tech-spec-label">前端驱动</div><div class="tech-spec-value">Vanilla JS + CSS 3</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">状态管理</div><div class="tech-spec-value">LocalStorage / URL Hash</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">资产管理</div><div class="tech-spec-value">SVG + Canvas</div></div>
      </div>
    `
  },
  core: {
    title: "MeiAgent 后端核心 (Java)",
    body: `
      <p>整个系统的中枢，负责大模型编排、MCP 协议解析与 RAG 管道调度。</p>
      <div class="tech-spec-grid">
        <div class="tech-spec-item"><div class="tech-spec-label">微服务架构</div><div class="tech-spec-value">Spring Boot 3.3.x</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">AI 引擎</div><div class="tech-spec-value">LangChain4j 0.35</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">消息协议</div><div class="tech-spec-value">SSE / JSON-RPC 2.0</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">并发模型</div><div class="tech-spec-value">Virtual Threads / CF</div></div>
      </div>
    `
  },
  mysql: {
    title: "MySQL 业务持久层",
    body: `
      <p>处理用户信息、文档元数据、Agent 配置与对话历史。</p>
      <div class="tech-spec-grid">
        <div class="tech-spec-item"><div class="tech-spec-label">版本</div><div class="tech-spec-value">MySQL 8.0.x</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">ORM</div><div class="tech-spec-value">MyBatis-Plus</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">连接池</div><div class="tech-spec-value">HikariCP</div></div>
      </div>
    `
  },
  redis: {
    title: "MeiRAG 向量存储 (Redis)",
    body: `
      <p>存储 1536/768 维语义向量，实现高效的 Top-K 检索。</p>
      <div class="tech-spec-grid">
        <div class="tech-spec-item"><div class="tech-spec-label">向量引擎</div><div class="tech-spec-value">RedisVector (Jedis)</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">索引算法</div><div class="tech-spec-value">HNSW (Cosine)</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">数据隔离</div><div class="tech-spec-value">User-Scoped Indexes</div></div>
      </div>
    `
  },
  mcp: {
    title: "外部 MCP 扩展层",
    body: `
      <p>支持动态连接第三方 MCP Server（如 Brave Search, SQLite SDK），实现 Agent 工具热插拔。</p>
      <div class="tech-spec-grid">
        <div class="tech-spec-item"><div class="tech-spec-label">协议兼容</div><div class="tech-spec-value">MCP v0.1.0 (SSE)</div></div>
        <div class="tech-spec-item"><div class="tech-spec-label">安全性</div><div class="tech-spec-value">Encapsulated Environment</div></div>
      </div>
    `
  }
};

window.__showTechDetail = function(id) {
  const detail = TECH_DETAILS[id];
  if (!detail) return;
  
  const modal = document.getElementById('tech-modal');
  const title = document.getElementById('modal-title');
  const body = document.getElementById('modal-body');
  
  if (modal && title && body) {
    title.textContent = detail.title;
    body.innerHTML = detail.body;
    modal.style.display = 'flex';
  }
};

window.__closeTechModal = function() {
  const modal = document.getElementById('tech-modal');
  if (modal) modal.style.display = 'none';
};

// Global click listener for modal backdrop
if (!window.__modalInitialized) {
  window.addEventListener('click', (e) => {
    const modal = document.getElementById('tech-modal');
    if (e.target === modal) window.__closeTechModal();
  });
  window.__modalInitialized = true;
}
