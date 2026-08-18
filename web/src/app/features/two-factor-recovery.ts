import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ThreeBgComponent } from '../core/three-bg.component';

@Component({
  selector: 'app-two-factor-recovery',
  standalone: true,
  imports: [FormsModule, RouterLink, ThreeBgComponent],
  template: `
    <div class="relative min-h-screen flex items-center justify-center bg-dark overflow-hidden selection:bg-neon-purple selection:text-white px-4">
      <app-three-bg />
      <div class="absolute top-1/4 left-1/3 w-96 h-96 bg-neon-purple/20 rounded-full blur-[120px] pointer-events-none animate-pulse-neon"></div>
      <div class="absolute bottom-1/4 right-1/3 w-96 h-96 bg-neon-cyan/20 rounded-full blur-[120px] pointer-events-none"></div>

      <div class="relative z-10 w-full max-w-md p-8 glass-panel rounded-2xl shadow-2xl border border-neon-purple/30 animate-fade-in">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-tr from-neon-purple via-indigo-600 to-neon-cyan p-0.5 shadow-neon mb-4">
            <div class="w-full h-full bg-dark-card rounded-[14px] flex items-center justify-center">
              <svg class="w-7 h-7 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
          </div>
          <h1 class="text-2xl font-extrabold tracking-tight bg-gradient-to-r from-white via-purple-100 to-neon-cyan bg-clip-text text-transparent">
            Recuperar acceso 2FA
          </h1>
          <p class="text-sm text-gray-400 mt-1">
            Si perdiste el acceso a tu app de autenticación, podemos enviarte un código por email para desactivar el 2FA.
          </p>
        </div>

        @if (paso() === 'email') {
          <div class="space-y-5">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-2">
                Email de tu cuenta
              </label>
              <input
                [(ngModel)]="email"
                (keyup.enter)="solicitarCodigo()"
                type="email"
                placeholder="tu-email@ejemplo.com"
                class="w-full px-4 py-3 bg-dark-surface/80 border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan focus:ring-1 focus:ring-neon-cyan transition-all text-sm font-medium"
              />
            </div>

            @if (error()) {
              <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2 animate-shake">
                <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{{ error() }}</span>
              </div>
            }

            <button
              [disabled]="cargando()"
              (click)="solicitarCodigo()"
              class="w-full py-3.5 px-6 rounded-xl font-semibold text-sm neon-button-primary flex items-center justify-center gap-2 disabled:opacity-50 cursor-pointer"
            >
              @if (cargando()) {
                <svg class="animate-spin h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>Enviando...</span>
              } @else {
                <span>Enviar código de recuperación</span>
              }
            </button>
          </div>
        }

        @if (paso() === 'codigo') {
          <div class="space-y-5 animate-fade-in">
            <div class="p-3 bg-neon-cyan/5 border border-neon-cyan/20 rounded-xl text-xs text-gray-300">
              Se envió un código de 6 dígitos a <span class="text-neon-cyan font-semibold">{{ email }}</span>. Tenés 15 minutos para ingresarlo.
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-2">
                Código de recuperación
              </label>
              <input
                [(ngModel)]="codigo"
                (keyup.enter)="confirmar()"
                type="text"
                inputmode="numeric"
                placeholder="Código"
                maxlength="6"
                class="w-full px-4 py-3 bg-dark-surface/80 border border-neon-cyan/40 rounded-xl text-white placeholder-gray-500 tracking-[0.3em] text-center font-mono text-lg focus:outline-none focus:border-neon-cyan focus:ring-1 focus:ring-neon-cyan transition-all"
              />
            </div>

            @if (error()) {
              <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2 animate-shake">
                <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{{ error() }}</span>
              </div>
            }

            <button
              [disabled]="cargando()"
              (click)="confirmar()"
              class="w-full py-3.5 px-6 rounded-xl font-semibold text-sm neon-button-primary flex items-center justify-center gap-2 disabled:opacity-50 cursor-pointer"
            >
              @if (cargando()) {
                <svg class="animate-spin h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>Verificando...</span>
              } @else {
                <span>Confirmar y desactivar 2FA</span>
              }
            </button>

            <button
              (click)="paso.set('email'); error.set(null)"
              class="w-full text-xs text-gray-400 hover:text-white transition-colors cursor-pointer"
            >
              Reenviar código
            </button>
          </div>
        }

        @if (paso() === 'exito') {
          <div class="p-4 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-sm text-center animate-fade-in">
            <svg class="w-8 h-8 mx-auto mb-2 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <p class="font-semibold">2FA desactivado correctamente</p>
            <p class="text-xs text-gray-400 mt-1">
              Ya podés iniciar sesión normalmente. Si querés, podés volver a activar el 2FA desde la sección Seguridad.
            </p>
          </div>
        }

        <div class="mt-6 text-center">
          <a routerLink="/login" class="text-xs text-gray-400 hover:text-neon-cyan transition-colors cursor-pointer">
            Volver al inicio de sesión
          </a>
        </div>
      </div>
    </div>
  `,
})
export class TwoFactorRecovery {
  private auth = inject(AuthService);

  email = '';
  codigo = '';
  paso = signal<'email' | 'codigo' | 'exito'>('email');
  error = signal<string | null>(null);
  cargando = signal(false);

  solicitarCodigo(): void {
    if (!this.email.trim()) {
      this.error.set('Ingresá tu email');
      return;
    }
    this.error.set(null);
    this.cargando.set(true);

    this.auth.twoFactorRecoverRequest(this.email.trim()).subscribe({
      next: () => {
        this.cargando.set(false);
        this.paso.set('codigo');
      },
      error: () => {
        this.cargando.set(false);
        this.paso.set('codigo');
      },
    });
  }

  confirmar(): void {
    if (!this.codigo.trim()) {
      this.error.set('Ingresá el código de 6 dígitos');
      return;
    }
    this.error.set(null);
    this.cargando.set(true);

    this.auth.twoFactorRecoverConfirm(this.email.trim(), this.codigo.trim()).subscribe({
      next: () => {
        this.cargando.set(false);
        this.paso.set('exito');
      },
      error: (e) => {
        this.cargando.set(false);
        this.error.set(e?.error?.message ?? 'Código inválido o expirado');
      },
    });
  }
}
