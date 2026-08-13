import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Usuario } from '../core/models';
import { UsuarioService } from '../core/usuario.service';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      
      <!-- Page Header -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-dark-border pb-6">
        <div>
          <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
            <span>Gestión de Usuarios</span>
            <span class="text-xs font-mono bg-neon-purple/20 text-neon-purple border border-neon-purple/30 px-2 py-0.5 rounded-full uppercase">
              ADMIN CONTROL
            </span>
          </h2>
          <p class="text-sm text-gray-400 mt-1">
            Administración de cuentas de acceso, roles y estados de actividad de operadores del sistema.
          </p>
        </div>

        <button
          (click)="mostrarForm.set(!mostrarForm())"
          class="px-5 py-2.5 rounded-xl font-semibold text-sm neon-button-primary flex items-center gap-2 self-start md:self-auto cursor-pointer"
        >
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
          </svg>
          <span>{{ mostrarForm() ? 'Ocultar Formulario' : 'Nuevo Usuario' }}</span>
        </button>
      </div>

      <!-- Form Card: Alta de Usuario -->
      @if (mostrarForm()) {
        <div class="glass-panel p-6 rounded-2xl border border-neon-purple/40 shadow-neon animate-slide-in">
          <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
            </svg>
            Alta de Operador / Usuario
          </h3>

          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Usuario *
              </label>
              <input
                [(ngModel)]="username"
                type="text"
                placeholder="Ej: operario1"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Rol *
              </label>
              <select
                [(ngModel)]="rol"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm"
              >
                <option value="OPERARIO">Empleado</option>
                <option value="ADMIN">Administrador</option>
              </select>
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Nombre Completo *
              </label>
              <input
                [(ngModel)]="nombre"
                type="text"
                placeholder="Ej: Juan Pérez"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Contraseña *
              </label>
              <input
                [(ngModel)]="password"
                type="password"
                placeholder="Mínimo 8 caracteres"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-purple text-sm"
              />
            </div>
          </div>

          @if (error()) {
            <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2">
              <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{{ error() }}</span>
            </div>
          }

          @if (ok()) {
            <div class="mt-4 p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-xs flex items-center gap-2">
              <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
              </svg>
              <span>Usuario creado correctamente con rol {{ rolCreado() }}.</span>
            </div>
          }

          <div class="mt-5 flex justify-end">
            <button
              [disabled]="guardando()"
              (click)="crear()"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50"
            >
              {{ guardando() ? 'Creando...' : 'Guardar Usuario' }}
            </button>
          </div>
        </div>
      }

      <!-- User List Table Card -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <div class="flex items-center justify-between mb-6">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
            Usuarios Registrados
          </h3>

          <button
            (click)="cargarUsuarios()"
            class="px-3 py-1.5 rounded-lg text-xs font-medium text-gray-400 hover:text-white bg-dark-surface border border-dark-border hover:border-neon-cyan transition-colors flex items-center gap-1.5 cursor-pointer"
          >
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            <span>Actualizar</span>
          </button>
        </div>

        @if (cargandoLista()) {
          <div class="py-12 text-center text-gray-400">
            <svg class="animate-spin h-8 w-8 text-neon-cyan mx-auto mb-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <p class="text-sm font-mono">Cargando usuarios desde el backend...</p>
          </div>
        } @else if (usuarios().length === 0) {
          <div class="py-12 text-center text-gray-500">
            No se encontraron usuarios en el sistema.
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm border-collapse">
              <thead>
                <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                  <th class="py-3 px-4">ID</th>
                  <th class="py-3 px-4">Usuario</th>
                  <th class="py-3 px-4">Nombre</th>
                  <th class="py-3 px-4">Roles</th>
                  <th class="py-3 px-4">Estado</th>
                  <th class="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border/50">
                @for (u of usuarios(); track u.id) {
                  <tr class="hover:bg-dark-surface/40 transition-colors">
                    <td class="py-3.5 px-4 font-mono text-gray-400 text-xs">#{{ u.id }}</td>
                    <td class="py-3.5 px-4 font-semibold text-white">
                      {{ u.username }}
                    </td>
                    <td class="py-3.5 px-4 text-gray-300">
                      {{ u.nombre }}
                    </td>
                    <td class="py-3.5 px-4">
                      @if (editandoRolId() === u.id) {
                        <div class="flex items-center gap-2">
                          <select
                            [(ngModel)]="rolEdit"
                            class="px-2.5 py-1.5 bg-dark-surface border border-neon-cyan/50 rounded-lg text-white text-xs focus:outline-none focus:border-neon-cyan"
                          >
                            <option value="OPERARIO">Empleado</option>
                            <option value="ADMIN">Administrador</option>
                          </select>
                          <button [disabled]="rolGuardando()" (click)="guardarRol(u.id)" class="text-xs font-semibold text-neon-green hover:underline cursor-pointer disabled:opacity-50">Guardar</button>
                          <button (click)="cancelarEdicionRol()" class="text-xs font-semibold text-gray-400 hover:underline cursor-pointer">Cancelar</button>
                        </div>
                      } @else {
                        <div class="flex gap-1.5 flex-wrap">
                          @for (rol of u.roles; track rol) {
                            <span class="text-[10px] font-mono px-2 py-0.5 rounded-md"
                                  [class]="rol === 'ADMIN' ? 'neon-badge-purple' : 'neon-badge-cyan'">
                              {{ rol }}
                            </span>
                          }
                        </div>
                      }
                    </td>
                    <td class="py-3.5 px-4">
                      @if (u.activo) {
                        <span class="inline-flex items-center gap-1.5 text-xs font-medium text-green-400 bg-green-500/10 border border-green-500/20 px-2.5 py-1 rounded-full">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse"></span>
                          Activo
                        </span>
                      } @else {
                        <span class="inline-flex items-center gap-1.5 text-xs font-medium text-gray-400 bg-gray-500/10 border border-gray-500/20 px-2.5 py-1 rounded-full">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>
                          Inactivo
                        </span>
                      }
                    </td>
                    <td class="py-3.5 px-4 text-right whitespace-nowrap">
                      <button
                        (click)="iniciarEdicionRol(u)"
                        class="px-3 py-1.5 mr-2 rounded-lg text-xs font-medium cursor-pointer transition-colors bg-dark-surface border border-dark-border hover:border-neon-cyan text-neon-cyan"
                      >
                        Cambiar rol
                      </button>
                      <button
                        (click)="toggleActivo(u)"
                        class="px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer transition-colors"
                        [class]="u.activo ? 'neon-button-danger' : 'neon-button-secondary'"
                      >
                        {{ u.activo ? 'Desactivar' : 'Activar' }}
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

    </div>
  `,
})
export class Usuarios implements OnInit {
  private service = inject(UsuarioService);

  usuarios = signal<Usuario[]>([]);
  cargandoLista = signal(true);

  mostrarForm = signal(false);
  username = '';
  nombre = '';
  password = '';
  rol = 'OPERARIO';
  guardando = signal(false);
  error = signal<string | null>(null);
  ok = signal(false);
  rolCreado = signal('OPERARIO');

  // Edición de rol de un usuario existente
  editandoRolId = signal<number | null>(null);
  rolEdit = 'OPERARIO';
  rolGuardando = signal(false);

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  cargarUsuarios(): void {
    this.cargandoLista.set(true);
    this.service.listar().subscribe({
      next: (lista) => {
        this.usuarios.set(lista);
        this.cargandoLista.set(false);
      },
      error: () => {
        this.cargandoLista.set(false);
      },
    });
  }

  crear(): void {
    if (!this.username.trim() || !this.nombre.trim() || this.password.length < 8) {
      this.error.set('Completá todos los campos (contraseña de al menos 8 caracteres)');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.ok.set(false);

    this.service
      .crear({ username: this.username.trim(), nombre: this.nombre.trim(), password: this.password, rol: this.rol })
      .subscribe({
        next: () => {
          this.rolCreado.set(this.rol === 'ADMIN' ? 'ADMIN' : 'OPERARIO');
          this.username = '';
          this.nombre = '';
          this.password = '';
          this.rol = 'OPERARIO';
          this.guardando.set(false);
          this.ok.set(true);
          this.cargarUsuarios();
        },
        error: (e) => {
          this.error.set(e?.error?.message ?? 'No se pudo crear el usuario');
          this.guardando.set(false);
        },
      });
  }

  toggleActivo(u: Usuario): void {
    this.service.toggleActivo(u.id).subscribe({
      next: () => this.cargarUsuarios(),
      error: (e) => alert(e?.error?.message ?? 'No se pudo modificar el estado del usuario'),
    });
  }

  iniciarEdicionRol(u: Usuario): void {
    this.editandoRolId.set(u.id);
    this.rolEdit = u.roles.includes('ADMIN') ? 'ADMIN' : 'OPERARIO';
  }

  cancelarEdicionRol(): void {
    this.editandoRolId.set(null);
  }

  guardarRol(id: number): void {
    this.rolGuardando.set(true);
    this.service.cambiarRol(id, this.rolEdit).subscribe({
      next: () => {
        this.rolGuardando.set(false);
        this.editandoRolId.set(null);
        this.cargarUsuarios();
      },
      error: (e) => {
        this.rolGuardando.set(false);
        alert(e?.error?.message ?? 'No se pudo cambiar el rol del usuario');
      },
    });
  }
}
