// ============================================================
// Particles.js — Neural Network Background (Performance Optimized)
// ============================================================

class ParticleNetwork {
  constructor(canvasId) {
    this.canvas = document.getElementById(canvasId);
    if (!this.canvas) return;
    this.ctx = this.canvas.getContext('2d');
    this.particles = [];
    this.particleCount = 80;
    this.mouse = { x: null, y: null, radius: 150 };
    
    // Theme-based color
    this.updateThemeColor();
    window.addEventListener('themeChanged', () => this.updateThemeColor());
    
    this.init();
    this.animate();

    window.addEventListener('mousemove', (e) => {
      this.mouse.x = e.x;
      this.mouse.y = e.y;
      document.body.style.setProperty('--mouse-x', `${(e.x / window.innerWidth) * 100}%`);
      document.body.style.setProperty('--mouse-y', `${(e.y / window.innerHeight) * 100}%`);
    });

    window.addEventListener('resize', () => {
      this.canvas.width = window.innerWidth;
      this.canvas.height = window.innerHeight;
      this.init();
    });
  }

  updateThemeColor() {
    const isLight = document.body.getAttribute('data-theme') === 'light';
    this.particleColor = isLight ? '236, 72, 153' : '139, 92, 246'; // RGB strings
  }

  init() {
    this.canvas.width = window.innerWidth;
    this.canvas.height = window.innerHeight;
    this.particles = [];
    for (let i = 0; i < this.particleCount; i++) {
      this.particles.push({
        x: Math.random() * this.canvas.width,
        y: Math.random() * this.canvas.height,
        size: Math.random() * 2 + 0.5,
        speedX: Math.random() * 1 - 0.5,
        speedY: Math.random() * 1 - 0.5,
        opacity: Math.random() * 0.5 + 0.2
      });
    }
  }

  draw() {
    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    for (let i = 0; i < this.particles.length; i++) {
      let p = this.particles[i];
      
      // Move
      p.x += p.speedX;
      p.y += p.speedY;

      // Wrap
      if (p.x < 0) p.x = this.canvas.width;
      if (p.x > this.canvas.width) p.x = 0;
      if (p.y < 0) p.y = this.canvas.height;
      if (p.y > this.canvas.height) p.y = 0;

      // Mouse repulsion
      let dx = this.mouse.x - p.x;
      let dy = this.mouse.y - p.y;
      let distance = Math.sqrt(dx * dx + dy * dy);
      if (distance < this.mouse.radius) {
        const force = (this.mouse.radius - distance) / this.mouse.radius;
        p.x -= dx * force * 0.02;
        p.y -= dy * force * 0.02;
      }

      // Draw particle
      this.ctx.fillStyle = `rgba(${this.particleColor}, ${p.opacity})`;
      this.ctx.beginPath();
      this.ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      this.ctx.fill();

      // Connections
      for (let j = i + 1; j < this.particles.length; j++) {
        let p2 = this.particles[j];
        let dx2 = p.x - p2.x;
        let dy2 = p.y - p2.y;
        let dist2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);

        if (dist2 < 120) {
          this.ctx.strokeStyle = `rgba(${this.particleColor}, ${0.1 * (1 - dist2/120)})`;
          this.ctx.lineWidth = 0.5;
          this.ctx.beginPath();
          this.ctx.moveTo(p.x, p.y);
          this.ctx.lineTo(p2.x, p2.y);
          this.ctx.stroke();
        }
      }
    }
  }

  animate() {
    this.draw();
    requestAnimationFrame(() => this.animate());
  }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  new ParticleNetwork('particles-canvas');
});
