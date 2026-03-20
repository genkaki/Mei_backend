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

    <!-- Architecture Bento Grid -->
    <div class="landing-section reveal">
      <h2 class="section-title"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.TERMINAL}</span> 系统全景架构 (Bento Showcase)</h2>
      <p class="section-desc">点击磁贴开启深度技术拆解，探索从端侧 UI 到向量引擎的每一层。 </p>
      
      <div class="bento-grid">
        <div class="bento-card bento-card-large card-shine" onclick="window.__openBento('client')">
          <div class="bento-tag">Mobile Frontend</div>
          <div class="bento-main">
            <h3>HarmonyOS NEXT 终端</h3>
            <p>基于 ArkTS 构建的原生 AI 界面，深度适配鸿蒙系统生命周期与流转特性。</p>
          </div>
          <div class="bento-footer">
            <span class="tech-tag">ArkTS</span><span class="tech-tag">ArkUI</span><span class="tech-tag">AppGallery SDK</span>
          </div>
          <div class="bento-action">深度拆解 ${ICONS.CHEVRON_RIGHT}</div>
        </div>

        <div class="bento-card card-shine" onclick="window.__openBento('core')">
          <div class="bento-tag">AI Backend</div>
          <div class="bento-main">
            <h3>MeiAgent 推理引擎</h3>
            <p>基于 Spring Boot 3 & LangChain4j。构建 ReAct 智能循环，驱动复杂任务理解。</p>
          </div>
          <div class="bento-footer">
            <span class="tech-tag">Spring Boot 3</span><span class="tech-tag">LangChain4j</span>
          </div>
          <div class="bento-action">${ICONS.PLUS}</div>
        </div>

        <div class="bento-card card-shine" onclick="window.__openBento('rag')">
          <div class="bento-tag">Persistence / RAG</div>
          <div class="bento-main">
            <h3>MeiRAG 检索管道</h3>
            <p>Redis Vector 语义存储。支持海量文档的并行向量化与毫秒级余弦检索。</p>
          </div>
          <div class="bento-footer">
            <span class="tech-tag">Redis Vector</span><span class="tech-tag">MySQL 8</span>
          </div>
          <div class="bento-action">${ICONS.PLUS}</div>
        </div>

        <div class="bento-card bento-card-wide card-shine" onclick="window.__openBento('mcp')">
          <div class="bento-tag">Tool Ecosystem</div>
          <div class="bento-main">
            <h3>MCP 插件协议层</h3>
            <p>自研 Model Context Protocol 适配层。支持跨语言、跨平台的工具能力热插拔与实时调度。</p>
          </div>
          <div class="bento-footer">
            <span class="tech-tag">JSON-RPC 2.0</span><span class="tech-tag">SSE</span><span class="tech-tag">Adapter Pattern</span>
          </div>
          <div class="bento-action">查看协议方案 ${ICONS.CHEVRON_RIGHT}</div>
        </div>
      </div>
    </div>

    <!-- Core Capacities (Simplified) -->
    <div class="landing-section">
      <h2 class="section-title reveal"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.PLUS}</span> 核心技术特性</h2>
      <div class="feature-grid reveal-stagger">
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.CPU}</div>
          <div class="feature-title">虚拟线程并行处理</div>
          <div class="feature-desc">Java 21 虚拟线程支持，在高并发向量化任务下性能提升显著。</div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.SHIELD}</div>
          <div class="feature-title">多租户安全隔离</div>
          <div class="feature-desc">基于 JWT 和分布式 ID 的严格数据物理隔离与限流保障。</div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.GLOBE}</div>
          <div class="feature-title">实时联网插件</div>
          <div class="feature-desc">MeiAgent 自主调度搜素工具，突破大模型时效性瓶颈。</div>
        </div>
      </div>
    </div>

    <!-- Tech Stack Tags -->
    <div class="landing-section reveal">
      <h2 class="section-title">🛠 技术栈概览</h2>
      <div class="tech-tags reveal-stagger">
        <span class="tech-tag">Java 17</span><span class="tech-tag">Spring Boot 3</span>
        <span class="tech-tag">LangChain4j</span><span class="tech-tag">Redis</span>
        <span class="tech-tag">MySQL</span><span class="tech-tag">Docker</span>
        <span class="tech-tag">ArkTS</span><span class="tech-tag">MCP Protocol</span>
      </div>
    </div>
  `;
}

// ==================== Bento Expansion Logic ====================

const BENTO_DETAILS = {
  client: {
    title: "HarmonyOS NEXT 原生端实现",
    subtitle: "ArkTS | ArkUI | AppGallery Connect",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>基于 HarmonyOS NEXT 的 <b>ArkTS</b> 开发，通过声明式 UI 实现流畅的交互体验。</p>
          <ul>
            <li><b>生命周期感知</b>：深度集成 ohos.app.ability，优化后台长连接稳定性。</li>
            <li><b>高效通信</b>：自定义 SSE 客户端处理来自 MeiAgent 的流式令牌响应。</li>
            <li><b>数据持久化</b>：通过 Preferences 和 KV-Store 实现本地配置与消息缓存。</li>
          </ul>
          <div class="expansion-code">
            // ArkTS SSE 核心逻辑示例
            let source = new EventSource(API_URL);
            source.on('message', (res) => {
              this.streamText += res.data;
              this.scrollToBottom();
            });
          </div>
        </div>
        <div class="expansion-visual">
          <div class="visual-placeholder">【UI 生命周期/流转逻辑图】</div>
        </div>
      </div>
    `
  },
  core: {
    title: "MeiAgent 推理引擎中枢",
    subtitle: "Spring Boot 3 | LangChain4j | ReAct",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>系统的中枢神经，负责将用户意图转化为可执行的工具调用与深度逻辑推理。</p>
          <ul>
            <li><b>AiServices 编排</b>：利用 LangChain4j 的声明式 AI 服务模型简化提示词工程。</li>
            <li><b>ReAct 智能循环</b>：自动实现 “思考-行动-观测” 闭环，直到获取最终答案。</li>
            <li><b>并行向量化</b>：CompletableFuture 驱动的扫描、切片与向量注桩并行流。</li>
          </ul>
        </div>
        <div class="expansion-visual">
          <div class="visual-placeholder">【Agent 推理状态机图】</div>
        </div>
      </div>
    `
  },
  rag: {
    title: "MeiRAG 检索增强系统",
    subtitle: "Redis Vector | HNSW Index | Apache Tika",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>赋予平衡了大模型通用知识与个人/企业私有知识的“第二大脑”。</p>
          <ul>
            <li><b>多格式解析</b>：通过 Apache Tika 自动提取 PDF, DOCX, MD 等文档中的文本。</li>
            <li><b>语义化切片</b>：基于字符或 Token 数落的语义重叠切片技术，确保上下文完整性。</li>
            <li><b>高效检索引擎</b>：RedisVector (HNSW) 支持高维空间下的 Top-K 余弦相似度检索。</li>
          </ul>
        </div>
        <div class="expansion-visual">
          <div class="visual-placeholder">【RAG 检索增强链路图】</div>
        </div>
      </div>
    `
  },
  mcp: {
    title: "MCP 插件协议解析层",
    subtitle: "Model Context Protocol | JSON-RPC 2.0 | SSE",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>打破 AI 孤岛，实现 Model 与外界工具（Search, Shell, DB）的标准化对话。</p>
          <ul>
            <li><b>自定义传输层</b>：手动实现 MCP 协议的 SSE 传输逻辑，支持热插拔外部服务。</li>
            <li><b>安全沙箱</b>：所有外部工具调用经过 AOP 拦截器进行权限与频率评估。</li>
            <li><b>Schema 映射</b>：将 Java 方法动态注解映射为符合 MCP 标准的 JSON Schema。</li>
          </ul>
        </div>
        <div class="expansion-visual">
          <div class="visual-placeholder">【MCP 协议握手与调用序列图】</div>
        </div>
      </div>
    `
  }
};

window.__openBento = function(id) {
  const detail = BENTO_DETAILS[id];
  if (!detail) return;

  const overlay = document.getElementById('bento-overlay');
  const body = document.getElementById('bento-detail-body');
  
  if (overlay && body) {
    body.innerHTML = `
      <div class="expansion-header">
        <div class="expansion-title-group">
          <h2>${detail.title}</h2>
          <p>${detail.subtitle}</p>
        </div>
      </div>
      <div class="expansion-body">
        ${detail.content}
      </div>
    `;
    overlay.style.display = 'flex';
    document.body.style.overflow = 'hidden'; // Lock background
    setTimeout(() => overlay.classList.add('active'), 10);
  }
};

window.__closeBento = function() {
  const overlay = document.getElementById('bento-overlay');
  if (overlay) {
    overlay.classList.remove('active');
    document.body.style.overflow = '';
    setTimeout(() => {
      overlay.style.display = 'none';
    }, 400);
  }
};
