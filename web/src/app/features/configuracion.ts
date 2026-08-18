import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';

@Component({
  selector: 'app-configuracion',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="space-y-8 animate-fade-in max-w-2xl">

      <div class="border-b border-dark-border pb-6">
        <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
          Configuración
        </h2>
        <p class="text-sm text-gray-400 mt-1">
          Ajustes de tu cuenta y preferencias de la interfaz.
        </p>
      </div>

      <!-- Cambiar contraseña -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
          </svg>
          Cambiar contraseña
        </h3>

        @if (!passwordOk()) {
          <div class="space-y-4">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Contraseña actual *
              </label>
              <input
                [(ngModel)]="passwordActual"
                type="password"
                placeholder="Tu contraseña actual"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-purple text-sm"
              />
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                  Nueva contraseña *
                </label>
                <input
                  [(ngModel)]="nuevaPassword"
                  type="password"
                  placeholder="Mínimo 8 caracteres"
                  class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
                />
              </div>
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                  Confirmar nueva contraseña *
                </label>
                <input
                  [(ngModel)]="confirmPassword"
                  type="password"
                  placeholder="Repetí la nueva contraseña"
                  class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
                />
              </div>
            </div>

            @if (tiene2fa()) {
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                  Código 2FA *
                </label>
                <input
                  [(ngModel)]="code2fa"
                  type="text"
                  inputmode="numeric"
                  placeholder="123456"
                  maxlength="6"
                  class="w-48 px-3.5 py-2.5 bg-dark-surface border border-neon-cyan/40 rounded-xl text-white placeholder-gray-500 text-center font-mono tracking-[0.2em] focus:outline-none focus:border-neon-cyan text-sm"
                />
                <p class="text-[11px] text-gray-500 mt-1">Código de tu app de autenticación.</p>
              </div>
            }

            @if (passwordError()) {
              <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2">
                <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{{ passwordError() }}</span>
              </div>
            }

            <div class="flex justify-end">
              <button
                [disabled]="guardando()"
                (click)="cambiarPassword()"
                class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50"
              >
                {{ guardando() ? 'Guardando...' : 'Cambiar contraseña' }}
              </button>
            </div>
          </div>
        } @else {
          <div class="p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-sm flex items-center gap-2">
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <span>Contraseña actualizada correctamente.</span>
          </div>
        }
      </div>

      <!-- Tema de la interfaz -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
          </svg>
          Tema de la interfaz
        </h3>

        <div class="flex gap-4">
          <button
            (click)="themeService.set('dark')"
            class="flex-1 p-4 rounded-xl border-2 cursor-pointer transition-all"
            [class]="themeService.theme() === 'dark'
              ? 'border-neon-purple bg-dark-surface shadow-neon'
              : 'border-dark-border bg-dark-surface/50 hover:border-gray-500'"
          >
            <div class="flex items-center gap-3 mb-2">
              <div class="w-8 h-8 rounded-lg bg-[#0a0a0f] border border-[#1e1e2e] flex items-center justify-center">
                <svg class="w-4 h-4 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
                </svg>
              </div>
              <span class="text-sm font-semibold text-white">Oscuro</span>
            </div>
            <p class="text-xs text-gray-400">Tema por defecto, ideal para ambientes con poca luz.</p>
          </button>

          <button
            (click)="themeService.set('light')"
            class="flex-1 p-4 rounded-xl border-2 cursor-pointer transition-all"
            [class]="themeService.theme() === 'light'
              ? 'border-neon-purple bg-dark-surface shadow-neon'
              : 'border-dark-border bg-dark-surface/50 hover:border-gray-500'"
          >
            <div class="flex items-center gap-3 mb-2">
              <div class="w-8 h-8 rounded-lg bg-[#f8fafc] border border-[#e2e8f0] flex items-center justify-center">
                <svg class="w-4 h-4 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                </svg>
              </div>
              <span class="text-sm font-semibold text-white">Claro</span>
            </div>
            <p class="text-xs text-gray-400">Tema luminoso, mejor visibilidad con luz natural.</p>
          </button>
        </div>
      </div>

    </div>
  `,
})
export class Configuracion {
  private auth = inject(AuthService);
  themeService = inject(ThemeService);

  passwordActual = '';
  nuevaPassword = '';
  confirmPassword = '';
  code2fa = '';
  tiene2fa = signal(false);
  guardando = signal(false);
  passwordError = signal<string | null>(null);
  passwordOk = signal(false);

  constructor() {
    this.auth.estado2fa().subscribe({
      next: (s) => this.tiene2fa.set(s.enabled),
      error: () => this.tiene2fa.set(false),
    });
  }

  cambiarPassword(): void {
    if (!this.passwordActual) {
      this.passwordError.set('Ingresá tu contraseña actual');
      return;
    }
    if (this.nuevaPassword.length < 8) {
      this.passwordError.set('La nueva contraseña debe tener al menos 8 caracteres');
      return;
    }
    if (this.nuevaPassword !== this.confirmPassword) {
      this.passwordError.set('Las contraseñas no coinciden');
      return;
    }
    if (this.tiene2fa() && !this.code2fa.trim()) {
      this.passwordError.set('Ingresá el código 2FA');
      return;
    }
    this.passwordError.set(null);
    this.guardando.set(true);

    const code = this.tiene2fa() ? this.code2fa.trim() : undefined;
    this.auth.cambiarPassword(this.passwordActual, this.nuevaPassword, code).subscribe({
      next: () => {
        this.guardando.set(false);
        this.passwordOk.set(true);
        this.passwordActual = '';
        this.nuevaPassword = '';
        this.confirmPassword = '';
        this.code2fa = '';
      },
      error: (e) => {
        this.guardando.set(false);
        this.passwordError.set(e?.error?.message ?? 'No se pudo cambiar la contraseña');
      },
    });
  }
}
