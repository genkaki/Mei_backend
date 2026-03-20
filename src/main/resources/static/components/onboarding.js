/**
 * Onboarding Component — 提供非机械化的新用户引导。
 * 处理项目初次使用的 BYOK 告知与配置指引。
 */
export function showOnboarding() {
  const modalData = `
    <div class="modal-overlay" id="onboarding-modal">
      <div class="modal-content glass onboarding-card animate-pop">
        <div class="onboarding-header">
          <div class="onboarding-icon">🎉</div>
          <h2>欢迎来到 MeiStudio！</h2>
          <p>您的 AI 探索之旅即将开启</p>
        </div>
        
        <div class="onboarding-body">
          <div class="onboarding-step">
            <div class="step-num">1</div>
            <div class="step-text">
              <h4>独立会话空间已就绪</h4>
              <p>您的邮箱账号已成功创建，数据已实现物理隔离。</p>
            </div>
          </div>
          
          <div class="onboarding-step highlight">
            <div class="step-num">2</div>
            <div class="step-text">
              <h4>配置您的私有 AI 引擎</h4>
              <p>为了保障隐私与成本，Web 端采用 <strong>BYOK (自带 Key)</strong> 模式。您需要填入自己的 DashScope API Key 才能启动大模型。</p>
            </div>
          </div>
        </div>

        <div class="onboarding-actions">
          <button class="btn btn-primary btn-block" onclick="window.__closeOnboarding('settings')">
            🚀 立即前往配置 API Key
          </button>
          <button class="btn btn-ghost btn-block" onclick="window.__closeOnboarding('later')">
            稍后设置
          </button>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalData);
}

window.__closeOnboarding = function(target) {
  const modal = document.getElementById('onboarding-modal');
  if (modal) modal.remove();
  
  if (target === 'settings') {
    window.location.hash = '#/settings';
  } else {
    window.location.hash = '#/chat';
  }
};
