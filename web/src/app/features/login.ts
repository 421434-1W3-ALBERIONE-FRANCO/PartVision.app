import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="login-wrap">
      <div class="card login-box">
        <h1>PartVision</h1>
        <p class="muted">Panel de administración</p>
        <label>Usuario</label>
        <input [(ngModel)]="username" (keyup.enter)="submit()" />
        <label>Contraseña</label>
        <input type="password" [(ngModel)]="password" (keyup.enter)="submit()" />
        @if (error()) { <p class="error">{{ error() }}</p> }
        <div style="margin-top:16px">
          <button [disabled]="cargando()" (click)="submit()">
            {{ cargando() ? 'Ingresando…' : 'Ingresar' }}
          </button>
        </div>
      </div>
    </div>
  `,
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);

  username = '';
  password = '';
  error = signal<string | null>(null);
  cargando = signal(false);

  submit(): void {
    if (!this.username.trim() || !this.password) {
      this.error.set('Completá usuario y contraseña');
      return;
    }
    this.error.set(null);
    this.cargando.set(true);
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (e) => {
        this.error.set(e?.status === 401 ? 'Usuario o contraseña incorrectos' : 'No se pudo conectar con el servidor');
        this.cargando.set(false);
      },
    });
  }
}
