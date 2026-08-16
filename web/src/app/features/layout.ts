import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ThreeLogoComponent } from '../core/three-logo.component';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ThreeLogoComponent],
  template: `
    <div class="flex h-screen bg-dark text-gray-100 overflow-hidden font-sans selection:bg-neon-purple selection:text-white">

      <!-- Backdrop (mobile) -->
      @if (sidebarOpen()) {
        <div class="fixed inset-0 bg-black/60 z-30 md:hidden" (click)="sidebarOpen.set(false)"></div>
      }

      <!-- Sidebar Nav (drawer en mobile, fijo en desktop) -->
      <aside
        class="fixed md:static inset-y-0 left-0 w-64 bg-dark-card border-r border-dark-border flex flex-col justify-between p-5 z-40 shrink-0 shadow-2xl transition-transform duration-200 md:translate-x-0"
        [class.-translate-x-full]="!sidebarOpen()"
      >
        <div>
          <!-- Logo & Brand Header -->
          <div class="flex items-center gap-3 px-2 py-3 mb-8 border-b border-dark-border/60">
            <app-three-logo />
            <div>
              <h1 class="text-xl font-bold tracking-tight bg-gradient-to-r from-white via-purple-200 to-neon-cyan bg-clip-text text-transparent leading-none">
                PartVision
              </h1>
              <span class="text-[10px] font-mono text-neon-cyan/80 tracking-widest uppercase">
                Enterprise Core
              </span>
            </div>
          </div>

          <!-- Navigation Links -->
          <nav class="space-y-1.5" (click)="sidebarOpen.set(false)">
            <a
              routerLink="/productos"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-cyan transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
              </svg>
              <span>Productos</span>
            </a>

            <a
              routerLink="/stock"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-cyan transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
              </svg>
              <span>Stock</span>
            </a>

            <a
              routerLink="/ubicaciones"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-cyan transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              <span>Ubicaciones</span>
            </a>

            <a
              routerLink="/carga-ia"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-pink transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span>Carga por IA</span>
              <span class="ml-auto text-[10px] font-mono uppercase bg-neon-pink/20 text-neon-pink border border-neon-pink/30 px-1.5 py-0.5 rounded-md">
                AI
              </span>
            </a>

            <a
              routerLink="/extracciones"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-pink transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
              <span>Extracciones IA</span>
              <span class="ml-auto text-[10px] font-mono uppercase bg-neon-purple/20 text-neon-purple border border-neon-purple/30 px-1.5 py-0.5 rounded-md">
                AI
              </span>
            </a>

            <a
              routerLink="/seguridad"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-cyan transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <span>Seguridad</span>
            </a>

            @if (esAdmin) {
            <a
              routerLink="/importacion"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-cyan transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
              </svg>
              <span>Importación CSV</span>
            </a>

            <a
              routerLink="/usuarios"
              routerLinkActive="bg-gradient-to-r from-neon-purple/20 to-indigo-900/10 text-white border-l-4 border-neon-purple shadow-neon font-semibold"
              class="flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-gray-400 hover:text-white hover:bg-dark-surface/60 transition-all text-sm group"
            >
              <svg class="w-5 h-5 text-gray-400 group-hover:text-neon-cyan transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <span>Usuarios</span>
            </a>
            }
          </nav>
        </div>

        <!-- User Info & Logout -->
        <div class="pt-4 border-t border-dark-border">
          <div class="flex items-center justify-between p-2.5 rounded-xl bg-dark-surface/50 border border-dark-border">
            <div class="flex items-center gap-3 overflow-hidden">
              <div class="w-9 h-9 rounded-lg bg-gradient-to-tr from-neon-purple to-neon-cyan flex items-center justify-center font-bold text-white text-xs shrink-0 shadow-neon">
                PV
              </div>
              <div class="truncate">
                <p class="text-xs font-semibold text-white truncate">Operador Admin</p>
                <p class="text-[10px] text-neon-cyan font-mono truncate">Sesión Activa</p>
              </div>
            </div>
            <button
              (click)="salir()"
              title="Cerrar Sesión"
              class="p-2 rounded-lg text-gray-400 hover:text-red-400 hover:bg-red-500/10 transition-colors shrink-0 cursor-pointer"
            >
              <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
            </button>
          </div>
        </div>
      </aside>

      <!-- Main Content Stage -->
      <div class="flex-1 flex flex-col overflow-hidden min-w-0">
        <!-- Barra superior (solo mobile) -->
        <header class="md:hidden flex items-center gap-3 px-4 py-3 border-b border-dark-border bg-dark-card z-20 shrink-0">
          <button
            (click)="sidebarOpen.set(true)"
            class="p-2 rounded-lg text-gray-300 hover:text-white hover:bg-dark-surface cursor-pointer"
            title="Menú"
          >
            <svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <span class="text-lg font-bold bg-gradient-to-r from-white to-neon-cyan bg-clip-text text-transparent">
            PartVision
          </span>
        </header>

        <main class="flex-1 overflow-y-auto bg-dark p-4 md:p-8 relative z-10">
          <!-- Ambient top light -->
          <div class="absolute top-0 right-1/4 w-96 h-32 bg-neon-purple/10 rounded-full blur-[90px] pointer-events-none"></div>
          <router-outlet />
        </main>
      </div>

    </div>
  `,
})
export class Layout {
  private auth = inject(AuthService);
  private router = inject(Router);

  sidebarOpen = signal(false);

  get esAdmin(): boolean {
    return this.auth.esAdmin;
  }

  salir(): void {
    // logout() es un Observable: hay que suscribirse para que dispare el POST /auth/logout
    // (borra la cookie y revoca el token del lado del server). Navegamos igual pase lo que pase.
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}
