import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toDataURL } from 'qrcode';

import { AuthService } from '../core/auth.service';

type Estado = 'cargando' | 'off' | 'setup' | 'recovery' | 'on' | 'desactivar';

@Component({
  selector: 'app-seguridad',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="max-w-2xl">
      <h1 class="text-2xl font-extrabold text-white mb-1">Seguridad de la cuenta</h1>
      <p class="text-sm text-gray-400 mb-6">Verificación en dos pasos (2FA) con una app de autenticación.</p>

      <div class="glass-panel rounded-2xl border border-dark-border p-6">
        @if (estado() === 'cargando') {
          <p class="text-gray-400 text-sm">Cargando…</p>
        }

        <!-- Sin 2FA -->
        @if (estado() === 'off') {
          <div class="flex items-start justify-between gap-4">
            <div>
              <h2 class="text-white font-semibold">Verificación en dos pasos</h2>
              <p class="text-sm text-gray-400 mt-1">
                Está <span class="text-red-400 font-semibold">desactivada</span>. Activala para pedir un código
                de tu teléfono además de la contraseña.
              </p>
            </div>
            <button (click)="iniciar()" [disabled]="procesando()"
              class="shrink-0 py-2.5 px-5 rounded-xl font-semibold text-sm neon-button-primary disabled:opacity-50 cursor-pointer">
              Activar 2FA
            </button>
          </div>
        }

        <!-- Enrolamiento: QR + código -->
        @if (estado() === 'setup') {
          <h2 class="text-white font-semibold mb-3">1. Escaneá el código QR</h2>
          <p class="text-sm text-gray-400 mb-4">
            Con Google Authenticator, Authy o similar. Si no podés escanear, cargá esta clave a mano:
            <code class="block mt-1 text-neon-cyan font-mono text-xs break-all bg-dark-surface/60 p-2 rounded-lg">{{ secret() }}</code>
          </p>
          @if (qr()) {
            <img [src]="qr()!" alt="QR 2FA" class="w-52 h-52 rounded-xl bg-white p-2 mb-5" />
          }
          <h2 class="text-white font-semibold mb-2">2. Ingresá el código que muestra la app</h2>
          <div class="flex items-center gap-3">
            <input [(ngModel)]="code" (keyup.enter)="confirmar()" inputmode="numeric" placeholder="123 456" maxlength="6"
              class="w-40 px-4 py-2.5 bg-dark-surface/80 border border-neon-cyan/40 rounded-xl text-white text-center font-mono tracking-[0.3em] focus:outline-none focus:border-neon-cyan" />
            <button (click)="confirmar()" [disabled]="procesando()"
              class="py-2.5 px-5 rounded-xl font-semibold text-sm neon-button-primary disabled:opacity-50 cursor-pointer">
              Confirmar y activar
            </button>
            <button (click)="cargar()" class="text-xs text-gray-500 hover:text-gray-300 cursor-pointer">Cancelar</button>
          </div>
        }

        <!-- Códigos de recuperación (una sola vez) -->
        @if (estado() === 'recovery') {
          <h2 class="text-white font-semibold mb-2">✅ 2FA activado</h2>
          <p class="text-sm text-gray-400 mb-3">
            Guardá estos <b class="text-white">códigos de recuperación</b> en un lugar seguro. Cada uno sirve
            <b>una sola vez</b> si perdés el teléfono. <span class="text-yellow-400">No se vuelven a mostrar.</span>
          </p>
          <div class="grid grid-cols-2 gap-2 font-mono text-sm bg-dark-surface/60 p-4 rounded-xl mb-4">
            @for (rc of recoveryCodes(); track rc) {
              <span class="text-neon-cyan">{{ rc }}</span>
            }
          </div>
          <button (click)="finalizarRecovery()"
            class="py-2.5 px-5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer">
            Ya los guardé
          </button>
        }

        <!-- 2FA activo -->
        @if (estado() === 'on') {
          <div class="flex items-start justify-between gap-4">
            <div>
              <h2 class="text-white font-semibold">Verificación en dos pasos</h2>
              <p class="text-sm text-gray-400 mt-1">
                Está <span class="text-neon-cyan font-semibold">activa</span>. Se te pedirá un código al iniciar sesión.
              </p>
            </div>
            <button (click)="pedirDesactivar()"
              class="shrink-0 py-2.5 px-5 rounded-xl font-semibold text-sm text-red-400 border border-red-500/40 hover:bg-red-500/10 cursor-pointer">
              Desactivar
            </button>
          </div>
        }

        <!-- Desactivar: pedir código -->
        @if (estado() === 'desactivar') {
          <h2 class="text-white font-semibold mb-2">Desactivar 2FA</h2>
          <p class="text-sm text-gray-400 mb-3">Ingresá un código de tu app (o uno de recuperación) para confirmar.</p>
          <div class="flex items-center gap-3">
            <input [(ngModel)]="code" (keyup.enter)="desactivar()" inputmode="numeric" placeholder="123 456" maxlength="12"
              class="w-40 px-4 py-2.5 bg-dark-surface/80 border border-red-500/40 rounded-xl text-white text-center font-mono tracking-[0.2em] focus:outline-none focus:border-red-400" />
            <button (click)="desactivar()" [disabled]="procesando()"
              class="py-2.5 px-5 rounded-xl font-semibold text-sm text-red-400 border border-red-500/40 hover:bg-red-500/10 disabled:opacity-50 cursor-pointer">
              Confirmar desactivación
            </button>
            <button (click)="cargar()" class="text-xs text-gray-500 hover:text-gray-300 cursor-pointer">Cancelar</button>
          </div>
        }

        @if (error()) {
          <p class="mt-4 text-sm text-red-400">{{ error() }}</p>
        }
      </div>
    </div>
  `,
})
export class Seguridad {
  private auth = inject(AuthService);

  estado = signal<Estado>('cargando');
  qr = signal<string | null>(null);
  secret = signal('');
  recoveryCodes = signal<string[]>([]);
  code = '';
  error = signal<string | null>(null);
  procesando = signal(false);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.error.set(null);
    this.code = '';
    this.estado.set('cargando');
    this.auth.estado2fa().subscribe({
      next: (s) => this.estado.set(s.enabled ? 'on' : 'off'),
      error: () => this.estado.set('off'),
    });
  }

  iniciar(): void {
    this.error.set(null);
    this.procesando.set(true);
    this.auth.setup2fa().subscribe({
      next: (r) => {
        this.secret.set(r.secret);
        toDataURL(r.otpauthUri, { margin: 1, width: 220 }).then((url) => this.qr.set(url));
        this.code = '';
        this.estado.set('setup');
        this.procesando.set(false);
      },
      error: () => {
        this.error.set('No se pudo iniciar la configuración');
        this.procesando.set(false);
      },
    });
  }

  confirmar(): void {
    if (!this.code.trim()) {
      this.error.set('Ingresá el código de la app');
      return;
    }
    this.error.set(null);
    this.procesando.set(true);
    this.auth.activar2fa(this.code.trim()).subscribe({
      next: (r) => {
        this.recoveryCodes.set(r.recoveryCodes);
        this.code = '';
        this.estado.set('recovery');
        this.procesando.set(false);
      },
      error: (e) => {
        this.error.set(e?.status === 422 || e?.status === 400 ? 'Código inválido, probá de nuevo' : 'No se pudo activar');
        this.procesando.set(false);
      },
    });
  }

  pedirDesactivar(): void {
    this.error.set(null);
    this.code = '';
    this.estado.set('desactivar');
  }

  desactivar(): void {
    if (!this.code.trim()) {
      this.error.set('Ingresá un código');
      return;
    }
    this.error.set(null);
    this.procesando.set(true);
    this.auth.desactivar2fa(this.code.trim()).subscribe({
      next: () => {
        this.qr.set(null);
        this.estado.set('off');
        this.procesando.set(false);
      },
      error: (e) => {
        this.error.set(e?.status === 422 || e?.status === 400 ? 'Código inválido' : 'No se pudo desactivar');
        this.procesando.set(false);
      },
    });
  }

  finalizarRecovery(): void {
    this.qr.set(null);
    this.recoveryCodes.set([]);
    this.estado.set('on');
  }
}
