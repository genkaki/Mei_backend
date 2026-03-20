/**
 * MeiStudio — Dynamic Particle System (Canvas 2D)
 * Optimized for high-end tech-giant aesthetic.
 */

const canvas = document.getElementById('particles-canvas');
if (canvas) {
  const ctx = canvas.getContext('2d');
  let particles = [];
  let w, h;

  const mouse = { x: null, y: null, radius: 150 };

  window.addEventListener('mousemove', (e) => {
    mouse.x = e.x;
    mouse.y = e.y;
    document.body.style.setProperty('--mouse-x', `${e.x}px`);
    document.body.style.setProperty('--mouse-y', `${e.y}px`);
  });

  function resize() {
    w = canvas.width = window.innerWidth;
    h = canvas.height = window.innerHeight;
  }

  window.addEventListener('resize', resize);
  resize();

  class Particle {
    constructor() {
      this.x = Math.random() * w;
      this.y = Math.random() * h;
      this.size = Math.random() * 2 + 0.5;
      this.speedX = Math.random() * 0.5 - 0.25;
      this.speedY = Math.random() * 0.5 - 0.25;
      this.baseOpacity = Math.random() * 0.5 + 0.1;
    }

    update() {
      this.x += this.speedX;
      this.y += this.speedY;

      if (this.x > w) this.x = 0;
      else if (this.x < 0) this.x = w;
      if (this.y > h) this.y = 0;
      else if (this.y < 0) this.y = h;

      // Mouse interaction
      const dx = mouse.x - this.x;
      const dy = mouse.y - this.y;
      const dist = Math.sqrt(dx*dx + dy*dy);
      if (dist < mouse.radius) {
        const force = (mouse.radius - dist) / mouse.radius;
        this.x -= dx * force * 0.02;
        this.y -= dy * force * 0.02;
      }
    }

    draw() {
      ctx.fillStyle = `rgba(168, 85, 247, ${this.baseOpacity})`;
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function init() {
    particles = [];
    const count = Math.floor((w * h) / 12000); // Responsive density
    for (let i = 0; i < count; i++) {
      particles.push(new Particle());
    }
  }

  function connect() {
    for (let i = 0; i < particles.length; i++) {
      for (let j = i; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x;
        const dy = particles[i].y - particles[j].y;
        const dist = Math.sqrt(dx*dx + dy*dy);

        if (dist < 120) {
          const opacity = 1 - (dist / 120);
          ctx.strokeStyle = `rgba(139, 92, 246, ${opacity * 0.15})`;
          ctx.lineWidth = 0.5;
          ctx.beginPath();
          ctx.moveTo(particles[i].x, particles[i].y);
          ctx.lineTo(particles[j].x, particles[j].y);
          ctx.stroke();
        }
      }
    }
  }

  function animate() {
    ctx.clearRect(0, 0, w, h);
    particles.forEach(p => {
      p.update();
      p.draw();
    });
    connect();
    requestAnimationFrame(animate);
  }

  init();
  animate();
}
