import { getUserConfig, saveUserConfig } from '../api.js';
import { ICONS } from '../icons.js';

export function renderSettings() {
  return `
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title"><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.SETTINGS}</span> 系统设置</h2>
        <p class="page-subtitle">自定义您的 AI 引擎参数与 API 秘钥 (BYOK)</p>
      </div>

      <div class="settings-grid">
        <!-- API Key Section -->
        <div class="settings-card glass">
          <div class="card-header">
            <h3><span style="display:inline-flex;align-items:center;vertical-align:middle;margin-right:8px">${ICONS.LOCK}</span> 模型鉴权</h3>
          </div>
          <div class="card-body">
            <div class="form-group">
              <label>DashScope API Key</label>
              <input type="password" id="set-api-key" class="input" placeholder="sk-xxxx...">
              <p class="input-hint">填入您的阿里云百炼 API Key。留空则尝试使用系统公共 Key。</p>
            </div>
          </div>
        </div>

        <!-- RAG Parameters -->
        <div class="settings-card glass">
          <div class="card-header">
            <h3>文档切片策略</h3>
          </div>
          <div class="card-body">
            <div class="form-group">
              <label>切片大小 (Chunk Size): <span id="val-chunk-size">800</span></label>
              <input type="range" id="set-chunk-size" min="100" max="2000" step="50" class="slider">
              <p class="input-hint">单个文本块的最大字符数。值越小，上下文越精确但碎片化。</p>
            </div>
            <div class="form-group">
              <label>重叠度 (Overlap): <span id="val-chunk-overlap">100</span></label>
              <input type="range" id="set-chunk-overlap" min="0" max="500" step="10" class="slider">
              <p class="input-hint">相邻切片之间的重叠字数，用于保持语义连贯。</p>
            </div>
          </div>
        </div>

        <!-- Model Selection -->
        <div class="settings-card glass">
          <div class="card-header">
            <h3>模型选择</h3>
          </div>
          <div class="card-body">
            <div class="form-group">
              <label>对话模型 (Chat Model)</label>
              <input type="text" id="set-chat-model" class="input" list="chat-models" placeholder="例如: qwen-max">
              <datalist id="chat-models">
                <option value="qwen-turbo">通义千问 Turbo</option>
                <option value="qwen-plus">通义千问 Plus</option>
                <option value="qwen-max">通义千问 Max</option>
                <option value="qwen-long">通义千问 Long</option>
                <option value="qwen-vl-max">通义千问 多模态 Max</option>
              </datalist>
              <p class="input-hint">可手动输入任何 DashScope 支持的模型标识符。</p>
            </div>
            <div class="form-group">
              <label>向量模型 (Embedding Model)</label>
              <input type="text" id="set-embedding-model" class="input" list="embedding-models" placeholder="例如: text-embedding-v3">
              <datalist id="embedding-models">
                <option value="text-embedding-v1">Text Embedding V1</option>
                <option value="text-embedding-v2">Text Embedding V2</option>
                <option value="text-embedding-v3">Text Embedding V3</option>
              </datalist>
              <p class="input-hint">支持通义千问系列通用或增强型向量模型。</p>
            </div>
          </div>
        </div>
      </div>

      <div class="settings-actions">
        <button class="btn btn-primary" onclick="window.__saveSettings()">保存配置</button>
        <button class="btn btn-outline" onclick="window.__initSettings()">重置默认</button>
      </div>
    </div>
  `;
}

export async function initSettings() {
  try {
    const config = await getUserConfig();
    const apiKeyInput = document.getElementById('set-api-key');
    const chunkSizeInput = document.getElementById('set-chunk-size');
    const chunkOverlapInput = document.getElementById('set-chunk-overlap');
    const chatModelSelect = document.getElementById('set-chat-model');
    const embeddingModelSelect = document.getElementById('set-embedding-model');

    // 严格脱敏显示 API Key
    if (apiKeyInput) {
      const fieldKey = config.dashscopeApiKey;
      if (fieldKey && fieldKey.length > 8) {
        apiKeyInput.value = fieldKey.substring(0, 4) + '****************' + fieldKey.substring(fieldKey.length - 4);
      } else {
        apiKeyInput.value = fieldKey || '';
      }
      
      // 当输入框获得焦点时，如果是已有的脱敏 Key，清空它以便用户输入新 Key
      apiKeyInput.onfocus = () => {
        if (apiKeyInput.value.includes('****')) {
          apiKeyInput.value = '';
          apiKeyInput.placeholder = '请输入新的 API Key';
        }
      };
    }

    if (chunkSizeInput) chunkSizeInput.value = config.chunkSize || 800;
    if (chunkOverlapInput) chunkOverlapInput.value = config.chunkOverlap || 100;
    if (chatModelSelect) chatModelSelect.value = config.chatModel || 'qwen-turbo';
    if (embeddingModelSelect) embeddingModelSelect.value = config.embeddingModel || 'text-embedding-v2';
    
    // Update live labels
    if (chunkSizeInput) {
      document.getElementById('val-chunk-size').textContent = chunkSizeInput.value;
      chunkSizeInput.oninput = (e) => {
        document.getElementById('val-chunk-size').textContent = e.target.value;
      };
    }
    if (chunkOverlapInput) {
      document.getElementById('val-chunk-overlap').textContent = chunkOverlapInput.value;
      chunkOverlapInput.oninput = (e) => {
        document.getElementById('val-chunk-overlap').textContent = e.target.value;
      };
    }
  } catch (e) {
    window.__toast?.('获取配置失败: ' + e.message, 'error');
  }
}

export function destroySettings() {
  // Cleanup if needed
}

window.__saveSettings = async function() {
  const apiKeyInput = document.getElementById('set-api-key');
  const apiKeyRaw = apiKeyInput.value.trim();
  
  const config = {
    chunkSize: parseInt(document.getElementById('set-chunk-size').value),
    chunkOverlap: parseInt(document.getElementById('set-chunk-overlap').value),
    chatModel: document.getElementById('set-chat-model').value,
    embeddingModel: document.getElementById('set-embedding-model').value,
    topK: 5
  };

  // 仅当用户输入了非脱敏格式的新 Key 时才发送更新
  if (apiKeyRaw && !apiKeyRaw.includes('****')) {
    config.dashscopeApiKey = apiKeyRaw;
  }

  try {
    await saveUserConfig(config);
    window.__toast?.('设置已保存！', 'success');
    // 重新初始化以刷新脱敏显示
    await initSettings();
  } catch (e) {
    window.__toast?.('保存失败: ' + e.message, 'error');
  }
};
