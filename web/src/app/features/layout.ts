import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="nav">
        <h1>PartVision</h1>
        <a routerLink="/productos" routerLinkActive="active">Productos</a>
        <a routerLink="/stock" routerLinkActive="active">Stock</a>
        <a routerLink="/ubicaciones" routerLinkActive="active">Ubicaciones</a>
        <a routerLink="/extracciones" routerLinkActive="active">Extracciones IA</a>
        <a routerLink="/importacion" routerLinkActive="active">Importación</a>
        <a routerLink="/usuarios" routerLinkActive="active">Usuarios</a>
        <div class="spacer"></div>
        <button class="secondary" (click)="salir()">Salir</button>
      </aside>
      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
})
export class Layout {
  private auth = inject(AuthService);
  private router = inject(Router);

  salir(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
