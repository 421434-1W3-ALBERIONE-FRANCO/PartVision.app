import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import * as THREE from 'three';

@Component({
  selector: 'app-three-logo',
  standalone: true,
  template: `
    <div #canvasContainer class="w-10 h-10 relative flex items-center justify-center"></div>
  `,
})
export class ThreeLogoComponent implements OnInit, OnDestroy {
  @ViewChild('canvasContainer', { static: true }) containerRef!: ElementRef<HTMLDivElement>;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private mesh!: THREE.Mesh;
  private innerMesh!: THREE.Mesh;
  private animId: number | null = null;

  ngOnInit(): void {
    const container = this.containerRef.nativeElement;

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(50, 1, 0.1, 100);
    this.camera.position.z = 3.5;

    // Outer Octahedron
    const geometry = new THREE.OctahedronGeometry(1.1, 0);
    const wireframe = new THREE.WireframeGeometry(geometry);
    const lineMaterial = new THREE.LineBasicMaterial({
      color: 0x22d3ee,
      linewidth: 2,
    });
    const lineSegments = new THREE.LineSegments(wireframe, lineMaterial);
    this.scene.add(lineSegments);

    // Inner Glowing Core Icosahedron
    const innerGeo = new THREE.IcosahedronGeometry(0.5, 0);
    const innerMat = new THREE.MeshBasicMaterial({
      color: 0xa855f7,
      wireframe: true,
      transparent: true,
      opacity: 0.8,
    });
    this.innerMesh = new THREE.Mesh(innerGeo, innerMat);
    this.scene.add(this.innerMesh);

    this.mesh = lineSegments as any;

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setSize(40, 40);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(this.renderer.domElement);

    let time = 0;
    const animate = () => {
      time += 0.02;
      this.mesh.rotation.y = time * 0.8;
      this.mesh.rotation.x = time * 0.4;

      this.innerMesh.rotation.y = -time * 1.2;
      this.innerMesh.rotation.z = time * 0.6;

      this.renderer.render(this.scene, this.camera);
      this.animId = requestAnimationFrame(animate);
    };

    animate();
  }

  ngOnDestroy(): void {
    if (this.animId !== null) {
      cancelAnimationFrame(this.animId);
    }
    if (this.renderer) {
      this.renderer.dispose();
    }
  }
}
