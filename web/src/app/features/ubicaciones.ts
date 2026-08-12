import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Ubicacion } from '../core/models';
import { UbicacionService } from '../core/ubicacion.service';

@Component({
  selector: 'app-ubicaciones',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-dark-border pb-6">
        <div>
          <h2 class="text-2xl md:text-3xl font-extrabold tracking-tight text-white flex flex-wrap items-center gap-3">
            <span>Ubicaciones Físicas</span>
            <span class="text-xs font-mono bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/30 px-2 py-0.5 rounded-full uppercase">
              ESTRUCTURA DE DEPOSITO
            </span>
          </h2>
          <p class="text-sm text-gray-400 mt-1">
            Define <span class="text-gray-200">dónde</span> se guarda físicamente el stock en el depósito.
          </p>
        </div>

        <button
          (click)="abrirNueva()"
          class="px-5 py-2.5 rounded-xl font-semibold text-sm neon-button-primary flex items-center gap-2 self-start md:self-auto cursor-pointer"
        >
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          <span>{{ mostrarForm() ? 'Ocultar Formulario' : 'Nueva Ubicación' }}</span>
        </button>
      </div>

      <!-- Leyenda -->
      <div class="glass-panel p-4 rounded-2xl border border-dark-border text-sm text-gray-300 space-y-2">
        <p>
          <span class="text-neon-cyan font-semibold">¿Para qué sirve?</span>
          Cada producto en Stock se guarda en una <span class="text-white">ubicación</span> (una posición física del galpón).
          Usá <span class="text-white">tu propia nomenclatura</span> en el código (ej.
          <span class="font-mono text-white">010306</span>, <span class="font-mono text-white">P1-E2</span>, como te sirva).
        </p>
        <p class="text-xs text-gray-500">
          La <span class="text-gray-300">descripción</span> es opcional: te sirve para dejar una nota (color, referencia, un tipo, etc.).
        </p>
      </div>

      <!-- Form: Crear / Editar -->
      @if (mostrarForm()) {
        <div class="glass-panel p-6 rounded-2xl border border-neon-purple/40 shadow-neon animate-slide-in">
          <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            {{ editandoId() ? 'Editar Ubicación' : 'Crear Ubicación' }}
          </h3>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Código de Ubicación *
              </label>
              <input
                [(ngModel)]="codigo"
                type="text"
                placeholder="Ej: 010306 o P1-E2"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
              <p class="mt-1 text-[11px] text-gray-500">Tu identificador de la posición.</p>
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Descripción (opcional)
              </label>
              <input
                [(ngModel)]="descripcion"
                type="text"
                placeholder="Ej: Estante rojo, fondo del galpón"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
              <p class="mt-1 text-[11px] text-gray-500">Nota libre (color, referencia, tipo…).</p>
            </div>
          </div>

          @if (error()) {
            <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2">
              <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{{ error() }}</span>
            </div>
          }

          <div class="mt-5 flex flex-wrap justify-end gap-3">
            <button
              (click)="cancelar()"
              class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer"
            >
              Cancelar
            </button>
            <button
              [disabled]="guardando()"
              (click)="guardar()"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50"
            >
              {{ guardando() ? 'Guardando...' : (editandoId() ? 'Actualizar' : 'Guardar Ubicación') }}
            </button>
          </div>
        </div>
      }

      <!-- Tabla -->
      <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card">
        <div class="flex items-center justify-between mb-6">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
            Ubicaciones
          </h3>
          <button
            (click)="cargar()"
            class="px-3 py-1.5 rounded-lg text-xs font-medium text-gray-400 hover:text-white bg-dark-surface border border-dark-border hover:border-neon-cyan transition-colors flex items-center gap-1.5 cursor-pointer"
          >
            Actualizar
          </button>
        </div>

        @if (cargando()) {
          <div class="py-12 text-center text-gray-400 font-mono">
            <svg class="animate-spin h-8 w-8 text-neon-cyan mx-auto mb-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            Cargando ubicaciones...
          </div>
        } @else if (ubicaciones().length === 0) {
          <div class="py-12 text-center text-gray-500">
            No hay ubicaciones registradas. Creá la primera con "Nueva Ubicación".
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm border-collapse min-w-[520px]">
              <thead>
                <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                  <th class="py-3 px-4">Código</th>
                  <th class="py-3 px-4">Descripción</th>
                  <th class="py-3 px-4">Estado</th>
                  <th class="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border/50">
                @for (u of ubicaciones(); track u.id) {
                  <tr class="hover:bg-dark-surface/40 transition-colors">
                    <td class="py-3.5 px-4 font-mono font-bold text-neon-cyan">{{ u.codigo }}</td>
                    <td class="py-3.5 px-4 text-gray-300">{{ u.descripcion || '—' }}</td>
                    <td class="py-3.5 px-4">
                      <span class="neon-badge-green px-2.5 py-1 rounded-md text-xs font-semibold">
                        {{ u.activo ? 'Activa' : 'Inactiva' }}
                      </span>
                    </td>
                    <td class="py-3.5 px-4">
                      <div class="flex items-center justify-end gap-3">
                        <button
                          (click)="editar(u)"
                          class="text-xs font-semibold text-neon-cyan hover:text-white transition-colors cursor-pointer"
                        >
                          Editar
                        </button>
                        <button
                          (click)="pedirEliminar(u)"
                          class="text-xs font-semibold text-red-400 hover:text-red-300 transition-colors cursor-pointer"
                        >
                          Eliminar
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>

    <!-- Modal de confirmación de borrado -->
    @if (aEliminar(); as u) {
      <div
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
        (click)="cancelarEliminar()"
      >
        <div
          class="glass-panel w-full max-w-md p-6 rounded-2xl border border-red-500/40 shadow-neon"
          (click)="$event.stopPropagation()"
        >
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            Eliminar ubicación
          </h3>
          <p class="text-sm text-gray-300">
            Estás por eliminar la ubicación <span class="font-mono font-bold text-neon-cyan">{{ u.codigo }}</span>.
            Esta acción no se puede deshacer. ¿Seguro?
          </p>

          @if (error()) {
            <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">
              {{ error() }}
            </div>
          }

          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button
              (click)="cancelarEliminar()"
              class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer"
            >
              Cancelar
            </button>
            <button
              [disabled]="eliminando()"
              (click)="eliminarConfirmado(u)"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-600 hover:bg-red-500 text-white transition-colors cursor-pointer disabled:opacity-50"
            >
              {{ eliminando() ? 'Eliminando...' : 'Eliminar' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class Ubicaciones implements OnInit {
  private service = inject(UbicacionService);

  ubicaciones = signal<Ubicacion[]>([]);
  cargando = signal(true);

  mostrarForm = signal(false);
  editandoId = signal<number | null>(null);
  codigo = '';
  descripcion = '';
  guardando = signal(false);
  error = signal<string | null>(null);

  aEliminar = signal<Ubicacion | null>(null);
  eliminando = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.service.listar().subscribe({
      next: (list) => {
        this.ubicaciones.set(list);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  abrirNueva(): void {
    if (this.mostrarForm() && !this.editandoId()) {
      this.mostrarForm.set(false);
      return;
    }
    this.limpiarForm();
    this.mostrarForm.set(true);
  }

  editar(u: Ubicacion): void {
    this.editandoId.set(u.id);
    this.codigo = u.codigo;
    this.descripcion = u.descripcion ?? '';
    this.error.set(null);
    this.mostrarForm.set(true);
  }

  cancelar(): void {
    this.mostrarForm.set(false);
    this.limpiarForm();
  }

  guardar(): void {
    if (!this.codigo.trim()) {
      this.error.set('El código de ubicación es obligatorio');
      return;
    }
    this.error.set(null);
    this.guardando.set(true);

    const req = { codigo: this.codigo.trim(), descripcion: this.descripcion.trim() || null };
    const id = this.editandoId();
    const op = id ? this.service.actualizar(id, req) : this.service.crear(req);

    op.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarForm.set(false);
        this.limpiarForm();
        this.cargar();
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'No se pudo guardar la ubicación');
        this.guardando.set(false);
      },
    });
  }

  pedirEliminar(u: Ubicacion): void {
    this.error.set(null);
    this.aEliminar.set(u);
  }

  cancelarEliminar(): void {
    this.aEliminar.set(null);
    this.error.set(null);
  }

  eliminarConfirmado(u: Ubicacion): void {
    this.eliminando.set(true);
    this.service.eliminar(u.id).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.aEliminar.set(null);
        this.cargar();
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'No se pudo eliminar la ubicación');
        this.eliminando.set(false);
      },
    });
  }

  private limpiarForm(): void {
    this.editandoId.set(null);
    this.codigo = '';
    this.descripcion = '';
    this.error.set(null);
  }
}
