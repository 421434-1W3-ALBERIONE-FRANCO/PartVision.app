import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import * as THREE from 'three';

@Component({
  selector: 'app-three-bg',
  standalone: true,
  template: `
    <div #container class="fixed inset-0 pointer-events-none z-0 overflow-hidden opacity-60"></div>
  `,
})
export class ThreeBgComponent implements OnInit, OnDestroy {
  @ViewChild('container', { static: true }) containerRef!: ElementRef<HTMLDivElement>;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private particleSystem!: THREE.Points;
  private linesMesh!: THREE.LineSegments;
  private animationFrameId: number | null = null;

  ngOnInit(): void {
    this.initThree();
    window.addEventListener('resize', this.onWindowResize);
  }

  ngOnDestroy(): void {
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
    }
    window.removeEventListener('resize', this.onWindowResize);
    if (this.renderer) {
      this.renderer.dispose();
    }
  }

  private initThree(): void {
    const container = this.containerRef.nativeElement;
    const width = window.innerWidth;
    const height = window.innerHeight;

    // Scene
    this.scene = new THREE.Scene();

    // Camera
    this.camera = new THREE.PerspectiveCamera(60, width / height, 1, 1000);
    this.camera.position.z = 400;

    // Particles Data
    const particleCount = 120;
    const positions = new Float32Array(particleCount * 3);
    const colors = new Float32Array(particleCount * 3);
    const velocities: { x: number; y: number; z: number }[] = [];

    const colorPurple = new THREE.Color(0x7c3aed);
    const colorCyan = new THREE.Color(0x06b6d4);
    const colorPink = new THREE.Color(0xf0abfc);

    for (let i = 0; i < particleCount; i++) {
      positions[i * 3] = (Math.random() - 0.5) * 800;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 800;
      positions[i * 3 + 2] = (Math.random() - 0.5) * 600;

      velocities.push({
        x: (Math.random() - 0.5) * 0.4,
        y: (Math.random() - 0.5) * 0.4,
        z: (Math.random() - 0.5) * 0.4,
      });

      const randColor = Math.random();
      const mixedColor = randColor > 0.6 ? colorPurple : randColor > 0.2 ? colorCyan : colorPink;
      colors[i * 3] = mixedColor.r;
      colors[i * 3 + 1] = mixedColor.g;
      colors[i * 3 + 2] = mixedColor.b;
    }

    // Geometry
    const particleGeometry = new THREE.BufferGeometry();
    particleGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    particleGeometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));

    // Particle Texture Canvas Generator
    const canvas = document.createElement('canvas');
    canvas.width = 32;
    canvas.height = 32;
    const ctx = canvas.getContext('2d')!;
    const gradient = ctx.createRadialGradient(16, 16, 0, 16, 16, 16);
    gradient.addColorStop(0, 'rgba(255,255,255,1)');
    gradient.addColorStop(0.3, 'rgba(168,85,247,0.8)');
    gradient.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, 32, 32);

    const texture = new THREE.CanvasTexture(canvas);

    // Particle Material
    const particleMaterial = new THREE.PointsMaterial({
      size: 14,
      vertexColors: true,
      map: texture,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });

    this.particleSystem = new THREE.Points(particleGeometry, particleMaterial);
    this.scene.add(this.particleSystem);

    // Network lines
    const linesGeometry = new THREE.BufferGeometry();
    const linePositions = new Float32Array(particleCount * particleCount * 6);
    linesGeometry.setAttribute('position', new THREE.BufferAttribute(linePositions, 3));

    const linesMaterial = new THREE.LineBasicMaterial({
      color: 0x7c3aed,
      transparent: true,
      opacity: 0.15,
      blending: THREE.AdditiveBlending,
    });

    this.linesMesh = new THREE.LineSegments(linesGeometry, linesMaterial);
    this.scene.add(this.linesMesh);

    // Renderer
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(this.renderer.domElement);

    // Animation Loop
    let time = 0;
    const animate = () => {
      time += 0.005;
      const posAttr = particleGeometry.attributes['position'] as THREE.BufferAttribute;
      const posArr = posAttr.array as Float32Array;

      // Update positions
      for (let i = 0; i < particleCount; i++) {
        posArr[i * 3] += velocities[i].x;
        posArr[i * 3 + 1] += velocities[i].y;
        posArr[i * 3 + 2] += velocities[i].z;

        // Bounce boundaries
        if (Math.abs(posArr[i * 3]) > 400) velocities[i].x *= -1;
        if (Math.abs(posArr[i * 3 + 1]) > 400) velocities[i].y *= -1;
        if (Math.abs(posArr[i * 3 + 2]) > 300) velocities[i].z *= -1;
      }
      posAttr.needsUpdate = true;

      // Update lines
      let vertexIndex = 0;
      const linePosAttr = linesGeometry.attributes['position'] as THREE.BufferAttribute;
      const linePosArr = linePosAttr.array as Float32Array;
      const maxDistance = 140;

      for (let i = 0; i < particleCount; i++) {
        for (let j = i + 1; j < particleCount; j++) {
          const dx = posArr[i * 3] - posArr[j * 3];
          const dy = posArr[i * 3 + 1] - posArr[j * 3 + 1];
          const dz = posArr[i * 3 + 2] - posArr[j * 3 + 2];
          const dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

          if (dist < maxDistance) {
            linePosArr[vertexIndex++] = posArr[i * 3];
            linePosArr[vertexIndex++] = posArr[i * 3 + 1];
            linePosArr[vertexIndex++] = posArr[i * 3 + 2];

            linePosArr[vertexIndex++] = posArr[j * 3];
            linePosArr[vertexIndex++] = posArr[j * 3 + 1];
            linePosArr[vertexIndex++] = posArr[j * 3 + 2];
          }
        }
      }
      linesGeometry.setDrawRange(0, vertexIndex / 3);
      linePosAttr.needsUpdate = true;

      // Gentle camera oscillation
      this.camera.position.x = Math.sin(time * 0.5) * 30;
      this.camera.position.y = Math.cos(time * 0.3) * 20;
      this.camera.lookAt(0, 0, 0);

      this.particleSystem.rotation.y = time * 0.08;

      this.renderer.render(this.scene, this.camera);
      this.animationFrameId = requestAnimationFrame(animate);
    };

    animate();
  }

  private onWindowResize = () => {
    if (!this.renderer || !this.camera) return;
    const width = window.innerWidth;
    const height = window.innerHeight;
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  };
}
