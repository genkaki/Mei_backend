// ============================================================
// Landing Page — Project Showcase + Login Form
// ============================================================
import { isLoggedIn } from '../api.js';
import { ICONS } from '../icons.js';
import { DIAGRAMS } from '../components/diagrams.js';

export function renderLanding() {
  const loggedIn = isLoggedIn();

  const loginSection = loggedIn ? `
    <div class="hero-actions">
      <a href="#/chat" class="btn btn-primary">${ICONS.MESSAGE} 体验 AI 对话</a>
      <a href="#/knowledge" class="btn btn-ghost">${ICONS.BOOK} 管理知识库</a>
    </div>
  ` : `
    <div class="login-card reveal">
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
          💡 <strong>数据隔离</strong>：网页端采用邮箱认证体系，与鸿蒙端（设备 ID 模式）在数据逻辑层深度解耦，确保开发与演示互不干扰。
        </p>
      </div>
    </div>
  `;

  return `
    <div class="landing-hero reveal">
      <div class="hero-logo">
        <img src="/assets/logo.png" alt="MeiStudio Logo">
      </div>
      <div class="hero-badge">✦ Spring Boot 3 + LangChain4j + MCP 全栈实战</div>
      <h1 class="hero-title">MeiStudio AI 智能助手平台</h1>
      <p class="hero-subtitle">
        面向 HarmonyOS NEXT 的 AI 助手核心引擎。集成 RAG 知识库检索增强、
        MCP 协议插件扩展、实时联网搜索等能力，构建标准化的端云协同 AI 闭环。
      </p>
      ${loginSection}
    </div>

    <!-- Stats -->
    <div class="landing-stats reveal-stagger">
      <div class="stat-card card-shine">
        <div class="stat-value">Clean</div>
        <div class="stat-label">Architecture 架构实现</div>
      </div>
      <div class="stat-card card-shine">
        <div class="stat-value">39</div>
        <div class="stat-label">核心组件源文件</div>
      </div>
      <div class="stat-card card-shine">
        <div class="stat-value">6</div>
        <div class="stat-label">MCP 协议方法</div>
      </div>
      <div class="stat-card card-shine">
        <div class="stat-value">3-5x</div>
        <div class="stat-label">向量化并发提速</div>
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
            <span class="tech-tag">ArkTS</span><span class="tech-tag">ArkUI</span><span class="tech-tag">Native Modules</span>
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
          <div class="feature-title">高性能并发处理</div>
          <div class="feature-desc">基于 Spring Boot 3 异步架构优化，在 RAG 向量化任务下展现卓越吞吐性能。</div>
        </div>
        <div class="feature-card card-shine">
          <div class="feature-icon">${ICONS.SHIELD}</div>
          <div class="feature-title">数据逻辑隔离</div>
          <div class="feature-desc">基于 JWT 和分布式 ID 的严格隔离，确保不同租户与终端数据安全解耦。</div>
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
    title: "HarmonyOS NEXT 原生端核心方案",
    subtitle: "ArkTS |声明式 UI | 系统级协同",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>HarmonyOS 端侧采用 <b>ArkTS</b> 进行开发，通过声明式 UI 与 native 能力的深度融合，实现了极高的交互响应速度。</p>
          <div class="expansion-specs">
            <div class="spec-item">
              <h4>UI 系统</h4>
              <p>基于 ArkUI 框架，通过 Flex 布局与自定义组件构建了具备 MeiStudio 家族语言的精致界面。集成 <b>LazyForEach</b> 优化超长对话列表的内存占用。</p>
            </div>
            <div class="spec-item">
              <h4>双端流转 (Planned)</h4>
              <p>预留了分布式软总线接口，未来将支持手机端 AI 对话在平板、PC 间的无缝衔接与上下文实时拉取。</p>
            </div>
            <div class="spec-item">
              <h4>文件安全</h4>
              <p>通过鸿蒙系统的 <b>Security Access</b> 机制获取文档权限，确保 RAG 知识库上传过程中的隐私安全。</p>
            </div>
          </div>
        </div>
        <div class="expansion-visual">
          <div class="code-window">
            <div class="code-header"><span></span><span></span><span></span></div>
            <pre><code>// ArkTS 消息流处理片段
@Component
struct ChatList {
  @Link messages: Message[]
  build() {
    List() {
      LazyForEach(this.messages, (item) => {
        MessageItem({ data: item })
      })
    }.cachedCount(5)
  }
}</code></pre>
          </div>
        </div>
      </div>
    `
  },
  core: {
    title: "MeiAgent 大模型编排中枢",
    subtitle: "Spring Boot 3.3 | LangChain4j | Java 21 Virtual Threads",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>MeiAgent 是整个系统的“大脑”，负责解析复杂指令并调度底层能力。</p>
          <div class="expansion-specs">
            <div class="spec-item">
              <h4>ReAct 智能链路</h4>
              <p>集成 LangChain4j 的 <b>AiServices</b>，通过动态代理将 Java 接口抽象为大模型可理解的 API。支持 "Thought → Action → Observation" 的自主逻辑闭环。</p>
            </div>
            <div class="spec-item">
              <h4>高并发架构</h4>
              <p>核心业务逻辑运行在 <b>Java 21 虚拟线程</b> 环境下，相比传统线程池，在处理大量 SSE 流式长连接时能显著降低损耗并提升吞吐量。</p>
            </div>
          </div>
          <div class="expansion-code">
            // Agent 核心定义
            @AiService
            public interface MeiAssistant {
               @SystemMessage("You are a helpful HarmonyOS developer...")
               TokenStream chat(String message);
            }
          </div>
        </div>
        <div class="expansion-visual">
           ${DIAGRAMS.agentTopology()}
        </div>
      </div>
    `
  },
  rag: {
    title: "MeiRAG 检索增强管道",
    subtitle: "Redis Vector | HNSW Index | CompletableFuture Parallelism",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>解决了大模型“幻觉”问题，通过企业级私有知识库为对话提供事实支撑。</p>
          <div class="expansion-specs">
            <div class="spec-item">
              <h4>向量索引选型</h4>
              <p>利用 Redis 的 <b>RediSearch</b> 插件实现 1536 维语义向量存储。采用 <b>HNSW</b> (Hierarchical Navigable Small World) 索引算法，在大规模数据下保持高性能检索。</p>
            </div>
            <div class="spec-item">
              <h4>流水线性能优化</h4>
              <p>文档向量化过程采用了分布式 Snowflake ID 确保 ID 唯一性，并通过 <b>CompletableFuture</b> 实现“分块-入库-索引刷新”的无锁异步并发流水线。</p>
            </div>
          </div>
        </div>
        <div class="expansion-visual">
           ${DIAGRAMS.ragChain()}
        </div>
      </div>
    `
  },
  mcp: {
    title: "MCP 标准插件协议层",
    subtitle: "JSON-RPC 2.0 | SSE | 动态工具发现",
    content: `
      <div class="expansion-layout">
        <div class="expansion-text">
          <p>打破后端与外部能力的壁垒。MeiStudio 完整实现了 <b>Model Context Protocol</b> 传输层规范。</p>
          <div class="expansion-specs">
            <div class="spec-item">
              <h4>标准互操作性</h4>
              <p>原生支持 JSON-RPC 2.0 协议。这意味着任何符合 MCP 标准的三方服务（如 Google Search, SQL SDK）只需一行配置即可接入 MeiStudio 供 Agent 使用。</p>
            </div>
            <div class="spec-item">
              <h4>传输层实现</h4>
              <p>采用 <b>SSE (Server-Sent Events)</b> 作为双向传输通道，实现了工具的实时自注册与动态调用路由。</p>
            </div>
          </div>
        </div>
        <div class="expansion-visual">
           ${DIAGRAMS.mcpSequence()}
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
    document.body.style.overflow = 'hidden'; 
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
