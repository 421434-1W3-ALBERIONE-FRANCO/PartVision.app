import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { UsuarioService } from '../core/usuario.service';

@Component({
  selector: 'app-usuarios',
  imports: [FormsModule],
  template: `
    <h2>Usuarios</h2>

    <div class="card">
      <h3>Alta de usuario</h3>
      <p class="muted">Se crea con rol OPERARIO (endpoint /auth/register del backend).</p>
      <div class="row">
        <div><label>Usuario *</label><input [(ngModel)]="username" /></div>
        <div><label>Nombre *</label><input [(ngModel)]="nombre" /></div>
        <div><label>Contraseña *</label><input type="password" [(ngModel)]="password" /></div>
        <div style="flex:0 0 auto"><button [disabled]="guardando()" (click)="crear()">Crear</button></div>
      </div>
      @if (error()) { <p class="error">{{ error() }}</p> }
      @if (ok()) { <p style="color:#16a34a">Usuario creado.</p> }
    </div>

    <div class="card">
      <p class="muted">
        El listado y la edición de usuarios todavía no están disponibles en el backend
        (no hay endpoint). Se agregarán en una fase posterior.
      </p>
    </div>
  `,
})
export class Usuarios {
  private service = inject(UsuarioService);

  username = '';
  nombre = '';
  password = '';
  guardando = signal(false);
  error = signal<string | null>(null);
  ok = signal(false);

  crear(): void {
    if (!this.username.trim() || !this.nombre.trim() || this.password.length < 8) {
      this.error.set('Completá los campos (contraseña de al menos 8 caracteres)');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.ok.set(false);
    this.service
      .registrar({ username: this.username.trim(), nombre: this.nombre.trim(), password: this.password })
      .subscribe({
        next: () => {
          this.username = '';
          this.nombre = '';
          this.password = '';
          this.guardando.set(false);
          this.ok.set(true);
        },
        error: (e) => {
          this.error.set(e?.error?.message ?? 'No se pudo crear el usuario');
          this.guardando.set(false);
        },
      });
  }
}
