import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ThreeBgComponent } from '../core/three-bg.component';

@Component({
  selector: 'app-forgot-password',
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
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
          </div>
          <h1 class="text-2xl font-extrabold tracking-tight bg-gradient-to-r from-white via-purple-100 to-neon-cyan bg-clip-text text-transparent">
            Recuperar Contraseña
          </h1>
          <p class="text-sm text-gray-400 mt-1">
            Ingresá tu email y te enviaremos un enlace para restablecer tu contraseña.
          </p>
        </div>

        @if (!enviado()) {
          <div class="space-y-5">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-2">
                Email
              </label>
              <input
                [(ngModel)]="email"
                (keyup.enter)="enviar()"
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
              (click)="enviar()"
              class="w-full py-3.5 px-6 rounded-xl font-semibold text-sm neon-button-primary flex items-center justify-center gap-2 disabled:opacity-50 cursor-pointer"
            >
              @if (cargando()) {
                <svg class="animate-spin h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>Enviando...</span>
              } @else {
                <span>Enviar enlace de recuperación</span>
              }
            </button>
          </div>
        } @else {
          <div class="p-4 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-sm text-center">
            <svg class="w-8 h-8 mx-auto mb-2 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <p class="font-semibold">Revisá tu casilla de correo</p>
            <p class="text-xs text-gray-400 mt-1">
              Si el email está registrado en el sistema, recibirás un enlace para restablecer tu contraseña.
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
export class ForgotPassword {
  private auth = inject(AuthService);

  email = '';
  error = signal<string | null>(null);
  cargando = signal(false);
  enviado = signal(false);

  enviar(): void {
    if (!this.email.trim()) {
      this.error.set('Ingresá tu email');
      return;
    }
    this.error.set(null);
    this.cargando.set(true);

    this.auth.forgotPassword(this.email.trim()).subscribe({
      next: () => {
        this.cargando.set(false);
        this.enviado.set(true);
      },
      error: () => {
        this.cargando.set(false);
        this.enviado.set(true);
      },
    });
  }
}
