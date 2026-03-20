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
      <h2 class="section-title"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.TERMINAL}</span> 系统架构</h2>
      
      <div class="arch-flow-container card-shine">
        <svg class="arch-connectors" viewBox="0 0 900 300">
          <!-- Connector Lines -->
          <path class="arch-connector-line" d="M225,80 L450,150"></path>
          <path class="arch-connector-line" d="M675,80 L450,150"></path>
          <path class="arch-connector-line" d="M450,210 L300,280"></path>
          <path class="arch-connector-line" d="M450,210 L450,280"></path>
          <path class="arch-connector-line" d="M450,210 L600,280"></path>
        </svg>

        <!-- Top Row -->
        <div class="arch-row">
          <div class="arch-node client">
            <div class="arch-node-title">HarmonyOS Client</div>
            <div class="arch-node-desc">ArkTS + UI Lifecycle</div>
          </div>
          <div class="arch-node client">
            <div class="arch-node-title">Web Dashboard</div>
            <div class="arch-node-desc">Vanilla JS + SPA</div>
          </div>
        </div>

        <!-- Middle Row (Core) -->
        <div class="arch-row">
          <div class="arch-node backend">
            <div class="arch-node-title">MeiAgent Core</div>
            <div class="arch-node-desc">Spring Boot + LangChain4j</div>
          </div>
        </div>

        <!-- Bottom Row (Infra) -->
        <div class="arch-row">
          <div class="arch-node infra">
            <div class="arch-node-title">MySQL 8.0</div>
            <div class="arch-node-desc">Relational Data</div>
          </div>
          <div class="arch-node infra">
            <div class="arch-node-title">Redis Vector</div>
            <div class="arch-node-desc">MeiRAG Storage</div>
          </div>
          <div class="arch-node infra">
            <div class="arch-node-title">External MCP</div>
            <div class="arch-node-desc">Tool Servers</div>
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
            从零手写 JSON-RPC 2.0 + SSE 传输层。Server 端暴露本系统工具，
            Client 端支持热插拔外部 MCP Server。适配器模式桥接 LangChain4j。
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
            工具执行 → 再推理"循环。支持同步和 SSE 流式两种模式。
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
