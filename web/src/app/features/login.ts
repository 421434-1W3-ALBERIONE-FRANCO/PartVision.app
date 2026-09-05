import { Component, ElementRef, inject, signal, ViewChildren, QueryList } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ThreeBgComponent } from '../core/three-bg.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink, ThreeBgComponent],
  template: `
    <div class="relative min-h-screen flex items-center justify-center bg-dark overflow-hidden selection:bg-neon-purple selection:text-white px-4">
      <!-- 3D ThreeJS Particle Network Background -->
      <app-three-bg />

      <!-- Glowing backdrop ambient lights -->
      <div class="absolute top-1/4 left-1/3 w-96 h-96 bg-neon-purple/20 rounded-full blur-[120px] pointer-events-none animate-pulse-neon"></div>
      <div class="absolute bottom-1/4 right-1/3 w-96 h-96 bg-neon-cyan/20 rounded-full blur-[120px] pointer-events-none"></div>

      <!-- Main Login Card with Glassmorphism -->
      <div class="relative z-10 w-full max-w-md p-8 glass-panel rounded-2xl shadow-2xl border border-neon-purple/30 animate-fade-in">
        
        <!-- Header & Logo -->
        <div class="text-center mb-8">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-tr from-neon-purple via-indigo-600 to-neon-cyan p-0.5 shadow-neon mb-4">
            <div class="w-full h-full bg-dark-card rounded-[14px] flex items-center justify-center">
              <svg class="w-8 h-8 text-neon-cyan animate-pulse" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
            </div>
          </div>
          <h1 class="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-white via-purple-100 to-neon-cyan bg-clip-text text-transparent">
            PartVision
          </h1>
          <p class="text-sm text-gray-400 mt-1 font-mono tracking-wide">
            SISTEMA DE GESTION INTEGRA DE AUTOPARTES
          </p>
        </div>

        <!-- Form -->
        <div class="space-y-5">
          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-2">
              Usuario
            </label>
            <div class="relative">
              <input
                [(ngModel)]="username"
                (keyup.enter)="submit()"
                type="text"
                placeholder="Ingrese usuario"
                class="w-full px-4 py-3 bg-dark-surface/80 border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan focus:ring-1 focus:ring-neon-cyan transition-all text-sm font-medium"
              />
              <div class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-gray-500">
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-2">
              Contraseña
            </label>
            <div class="relative">
              <input
                [(ngModel)]="password"
                (keyup.enter)="submit()"
                [type]="mostrarPassword() ? 'text' : 'password'"
                placeholder="Ingrese contraseña"
                class="w-full pl-4 pr-12 py-3 bg-dark-surface/80 border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-purple focus:ring-1 focus:ring-neon-purple transition-all text-sm font-medium"
              />
              <button
                type="button"
                (click)="mostrarPassword.set(!mostrarPassword())"
                [attr.aria-label]="mostrarPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'"
                [attr.aria-pressed]="mostrarPassword()"
                class="absolute inset-y-0 right-0 pr-3 pl-2 flex items-center text-gray-500 hover:text-neon-cyan transition-colors cursor-pointer focus:outline-none"
              >
                @if (mostrarPassword()) {
                  <!-- ojo tachado: la contraseña está visible, clic para ocultar -->
                  <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                  </svg>
                } @else {
                  <!-- ojo: la contraseña está oculta, clic para mostrar -->
                  <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                  </svg>
                }
              </button>
            </div>
          </div>

          @if (requiere2fa()) {
            <div class="animate-fade-in">
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-2">
                Código de verificación (2FA)
              </label>
              <div class="flex justify-center gap-2">
                @for (i of otpIndices; track i) {
                  <input
                    #otpInput
                    type="text"
                    inputmode="numeric"
                    maxlength="1"
                    [value]="otpDigits()[i]"
                    (input)="onOtpInput($event, i)"
                    (keydown)="onOtpKeydown($event, i)"
                    (paste)="onOtpPaste($event)"
                    class="otp-cell w-11 h-12 text-center text-lg font-mono font-bold border border-neon-cyan/40 rounded-lg placeholder-gray-600 focus:outline-none focus:border-neon-cyan focus:ring-1 focus:ring-neon-cyan transition-all"
                  />
                }
              </div>
              <p class="text-[11px] text-gray-500 mt-2 text-center">
                Ingresá el código de tu app de autenticación (o un código de recuperación).
              </p>
              <div class="text-center">
                <a routerLink="/recuperar-2fa" class="inline-block mt-2 text-xs text-neon-cyan/70 hover:text-neon-cyan transition-colors cursor-pointer">
                  Perdí el acceso a mi autenticador
                </a>
              </div>
            </div>
          }

          @if (error()) {
            <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2 animate-shake">
              <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{{ error() }}</span>
            </div>
          }

          <div class="pt-2">
            <button
              [disabled]="cargando()"
              (click)="submit()"
              class="w-full py-3.5 px-6 rounded-xl font-semibold text-sm neon-button-primary flex items-center justify-center gap-2 disabled:opacity-50 cursor-pointer"
            >
              @if (cargando()) {
                <svg class="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>Ingresando al sistema...</span>
              } @else {
                <span>Iniciar Sesión</span>
                <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              }
            </button>
          </div>

          <div class="text-center mt-4">
            <a routerLink="/olvide-password" class="text-xs text-gray-400 hover:text-neon-cyan transition-colors cursor-pointer">
              ¿Olvidaste tu contraseña?
            </a>
          </div>
        </div>

        <!-- Footer Info -->
        <div class="mt-8 pt-6 border-t border-dark-border/50 text-center">
          <p class="text-xs text-gray-500 font-mono">
            PartVision v2.4
          </p>
        </div>
      </div>
    </div>
  `,
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);

  @ViewChildren('otpInput') otpInputs!: QueryList<ElementRef<HTMLInputElement>>;

  username = '';
  password = '';
  code = '';
  mostrarPassword = signal(false);
  requiere2fa = signal(false);
  error = signal<string | null>(null);
  cargando = signal(false);

  otpDigits = signal<string[]>(['', '', '', '', '', '']);
  readonly otpIndices = [0, 1, 2, 3, 4, 5];

  onOtpInput(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const val = input.value.replace(/\D/g, '');
    const digits = [...this.otpDigits()];
    digits[index] = val.slice(0, 1);
    this.otpDigits.set(digits);
    this.code = digits.join('');
    if (val && index < 5) {
      const inputs = this.otpInputs.toArray();
      inputs[index + 1]?.nativeElement.focus();
    }
    if (this.code.length === 6) this.submit();
  }

  onOtpKeydown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace' && !this.otpDigits()[index] && index > 0) {
      const inputs = this.otpInputs.toArray();
      const prev = inputs[index - 1]?.nativeElement;
      if (prev) {
        const digits = [...this.otpDigits()];
        digits[index - 1] = '';
        this.otpDigits.set(digits);
        this.code = digits.join('');
        prev.focus();
      }
    }
  }

  onOtpPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const paste = (event.clipboardData?.getData('text') ?? '').replace(/\D/g, '').slice(0, 6);
    const digits = Array.from({ length: 6 }, (_, i) => paste[i] ?? '');
    this.otpDigits.set(digits);
    this.code = digits.join('');
    const inputs = this.otpInputs.toArray();
    const focusIdx = Math.min(paste.length, 5);
    inputs[focusIdx]?.nativeElement.focus();
    if (this.code.length === 6) this.submit();
  }

  submit(): void {
    if (!this.username.trim() || !this.password) {
      this.error.set('Por favor ingresá usuario y contraseña');
      return;
    }
    if (this.requiere2fa() && !this.code.trim()) {
      this.error.set('Ingresá el código de verificación');
      return;
    }
    this.error.set(null);
    this.cargando.set(true);
    const code = this.requiere2fa() ? this.code.trim() : undefined;
    this.auth.login(this.username.trim(), this.password, code).subscribe({
      next: () => this.router.navigate(['/']),
      error: (e) => {
        this.cargando.set(false);
        // El backend pide el segundo factor: mostramos el campo del código (no es un error).
        if (e?.status === 401 && e?.error?.twoFactorRequired) {
          this.requiere2fa.set(true);
          this.error.set(null);
          setTimeout(() => this.otpInputs?.first?.nativeElement.focus());
          return;
        }
        if (e?.status === 401) {
          this.error.set(this.requiere2fa() ? 'Código o credenciales incorrectos' : 'Usuario o contraseña incorrectos');
        } else {
          this.error.set('No se pudo conectar con el servidor');
        }
      },
    });
  }
}
