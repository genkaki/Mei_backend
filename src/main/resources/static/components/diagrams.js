// ============================================================
// Technical Diagrams Component — Animated SVGs
// ============================================================

export const DIAGRAMS = {
  /**
   * MeiAgent Reasoning Topology
   * Shows LLM core interacting with external tools
   */
  agentTopology: () => `
    <div class="diagram-container agent-topology">
      <svg viewBox="0 0 400 300" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- Connecting Lines (Animated) -->
        <path class="flow-path" d="M200 150 L100 80" stroke="var(--accent-light)" stroke-width="2" stroke-dasharray="4 4" />
        <path class="flow-path" d="M200 150 L300 80" stroke="var(--accent-light)" stroke-width="2" stroke-dasharray="4 4" />
        <path class="flow-path" d="M200 150 L100 220" stroke="var(--accent-light)" stroke-width="2" stroke-dasharray="4 4" />
        <path class="flow-path" d="M200 150 L300 220" stroke="var(--accent-light)" stroke-width="2" stroke-dasharray="4 4" />

        <!-- Nodes -->
        <!-- Center: LLM -->
        <g class="node node-central">
          <circle cx="200" cy="150" r="40" fill="var(--bg-card)" stroke="var(--accent-light)" stroke-width="2" />
          <text x="200" y="155" text-anchor="middle" fill="var(--text-primary)" style="font-size:12px;font-weight:bold">LLM Core</text>
          <circle cx="200" cy="150" r="45" class="node-pulse" stroke="var(--accent-light)" stroke-opacity="0.3" />
        </g>

        <!-- Satellites: Tools -->
        <g class="node">
          <circle cx="100" cy="80" r="25" fill="var(--bg-card)" stroke="var(--border-light)" stroke-width="1.5" />
          <text x="100" y="85" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">Search</text>
        </g>
        <g class="node">
          <circle cx="300" cy="80" r="25" fill="var(--bg-card)" stroke="var(--border-light)" stroke-width="1.5" />
          <text x="300" y="85" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">Knowledge</text>
        </g>
        <g class="node">
          <circle cx="100" cy="220" r="25" fill="var(--bg-card)" stroke="var(--border-light)" stroke-width="1.5" />
          <text x="100" y="225" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">MCP Tools</text>
        </g>
        <g class="node">
          <circle cx="300" cy="220" r="25" fill="var(--bg-card)" stroke="var(--border-light)" stroke-width="1.5" />
          <text x="300" y="225" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">Memory</text>
        </g>
      </svg>
      <div class="diagram-caption">ReAct 推理循环：感知 → 决策 → 调用 → 观察</div>
    </div>
  `,

  /**
   * RAG Retrieval Chain
   * Shows the document-to-answer pipeline
   */
  ragChain: () => `
    <div class="diagram-container rag-chain">
      <svg viewBox="0 0 500 200" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- Main Line -->
        <path d="M50 100 H450" stroke="var(--border-light)" stroke-width="2" stroke-dasharray="8 8" />
        
        <!-- Steps -->
        <g class="step-group">
          <rect x="30" y="70" width="60" height="60" rx="8" fill="var(--bg-card)" stroke="var(--accent-light)" />
          <text x="60" y="150" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">用户问句</text>
        </g>
        
        <path d="M100 100 L140 100" stroke="var(--accent-light)" stroke-width="2" class="arrow-path" />
        
        <g class="step-group">
          <rect x="150" y="70" width="60" height="60" rx="8" fill="var(--bg-card)" stroke="var(--accent-light)" />
          <text x="180" y="150" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">语义索引</text>
        </g>

        <path d="M220 100 L260 100" stroke="var(--accent-light)" stroke-width="2" class="arrow-path" />

        <g class="step-group">
          <rect x="270" y="70" width="60" height="60" rx="8" fill="var(--bg-card)" stroke="var(--accent-light)" />
          <text x="300" y="150" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">知识召回</text>
        </g>

        <path d="M340 100 L380 100" stroke="var(--accent-light)" stroke-width="2" class="arrow-path" />

        <g class="step-group">
          <rect x="390" y="70" width="60" height="60" rx="8" fill="var(--bg-card)" stroke="var(--accent-light)" />
          <text x="420" y="150" text-anchor="middle" fill="var(--text-muted)" style="font-size:10px">增强回答</text>
        </g>
        
        <!-- Animated Scanner on Recall -->
        <rect x="275" y="75" width="50" height="2" fill="var(--accent-light)" class="scan-line" />
      </svg>
      <div class="diagram-caption">RAG 链路：语义切片 → 向量化 → HNSW 检索 → 提示词注入</div>
    </div>
  `,

  /**
   * MCP Handshake Sequence
   * Shows JSON-RPC handshake steps
   */
  mcpSequence: () => `
    <div class="diagram-container mcp-sequence">
      <div class="seq-list">
        <div class="seq-item active">
          <div class="seq-dot">1</div>
          <div class="seq-label">SSE 端点连接 (Establish SSE)</div>
        </div>
        <div class="seq-item active" style="animation-delay: 0.5s">
          <div class="seq-dot">2</div>
          <div class="seq-label">Initialize 握手 (Handshake)</div>
        </div>
        <div class="seq-item" style="animation-delay: 1s">
          <div class="seq-dot">3</div>
          <div class="seq-label">资源/工具发现 (Discovery)</div>
        </div>
        <div class="seq-item" style="animation-delay: 1.5s">
          <div class="seq-dot">4</div>
          <div class="seq-label">Tool Call 动态路由</div>
        </div>
      </div>
      <div class="diagram-caption">MCP 协议：标准化工具上下文发现与调用流程</div>
    </div>
  `
};
