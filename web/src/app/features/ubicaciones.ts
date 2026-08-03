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
          <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
            <span>Ubicaciones Físicas</span>
            <span class="text-xs font-mono bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/30 px-2 py-0.5 rounded-full uppercase">
              ESTRUCTURA DE DEPOSITO
            </span>
          </h2>
          <p class="text-sm text-gray-400 mt-1">
            Gestión jerárquica de depósitos, pasillos, estantes, cajones y pallets.
          </p>
        </div>

        <button
          (click)="mostrarForm.set(!mostrarForm())"
          class="px-5 py-2.5 rounded-xl font-semibold text-sm neon-button-primary flex items-center gap-2 self-start md:self-auto cursor-pointer"
        >
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          <span>{{ mostrarForm() ? 'Ocultar Formulario' : 'Nueva Ubicación' }}</span>
        </button>
      </div>

      <!-- Form: Crear Ubicación -->
      @if (mostrarForm()) {
        <div class="glass-panel p-6 rounded-2xl border border-neon-purple/40 shadow-neon animate-slide-in">
          <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            Crear Ubicación
          </h3>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Código de Ubicación *
              </label>
              <input
                [(ngModel)]="codigo"
                type="text"
                placeholder="Ej: A-01-E3"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Tipo de Ubicación *
              </label>
              <select
                [(ngModel)]="tipo"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm"
              >
                @for (t of tipos; track t) {
                  <option [value]="t">{{ t }}</option>
                }
              </select>
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                ID Padre (Opcional)
              </label>
              <input
                [(ngModel)]="parentId"
                type="number"
                placeholder="ID de ubicación contenedora"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
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

          <div class="mt-5 flex justify-end">
            <button
              [disabled]="guardando()"
              (click)="crear()"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50"
            >
              {{ guardando() ? 'Guardando...' : 'Guardar Ubicación' }}
            </button>
          </div>
        </div>
      }

      <!-- Ubicaciones Table -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <div class="flex items-center justify-between mb-6">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
            Ubicaciones Principales
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
            No hay ubicaciones raíz registradas en el sistema.
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm border-collapse">
              <thead>
                <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                  <th class="py-3 px-4">ID</th>
                  <th class="py-3 px-4">Código</th>
                  <th class="py-3 px-4">Tipo</th>
                  <th class="py-3 px-4">Ruta Jerárquica</th>
                  <th class="py-3 px-4">Estado</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border/50">
                @for (u of ubicaciones(); track u.id) {
                  <tr class="hover:bg-dark-surface/40 transition-colors">
                    <td class="py-3.5 px-4 font-mono text-xs text-gray-400">#{{ u.id }}</td>
                    <td class="py-3.5 px-4 font-mono font-bold text-neon-cyan">
                      {{ u.codigo }}
                    </td>
                    <td class="py-3.5 px-4">
                      <span class="neon-badge-purple px-2.5 py-1 rounded-md text-xs font-mono">
                        {{ u.tipo }}
                      </span>
                    </td>
                    <td class="py-3.5 px-4 text-gray-300 font-mono text-xs">
                      {{ u.path }}
                    </td>
                    <td class="py-3.5 px-4">
                      <span class="neon-badge-green px-2.5 py-1 rounded-md text-xs font-semibold">
                        {{ u.activo ? 'Activa' : 'Inactiva' }}
                      </span>
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
export class Ubicaciones implements OnInit {
  private service = inject(UbicacionService);

  ubicaciones = signal<Ubicacion[]>([]);
  tipos = ['DEPOSITO', 'PASILLO', 'ESTANTE', 'CAJON', 'PALLET', 'OTRO'];
  cargando = signal(true);

  mostrarForm = signal(false);
  codigo = '';
  tipo = 'ESTANTE';
  parentId: number | null = null;
  guardando = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.service.raices().subscribe({
      next: (list) => {
        this.ubicaciones.set(list);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  crear(): void {
    if (!this.codigo.trim()) {
      this.error.set('El código de ubicación es obligatorio');
      return;
    }
    this.error.set(null);
    this.guardando.set(true);

    this.service
      .crear({
        codigo: this.codigo.trim(),
        tipo: this.tipo,
        parentId: this.parentId || undefined,
      })
      .subscribe({
        next: () => {
          this.codigo = '';
          this.parentId = null;
          this.guardando.set(false);
          this.mostrarForm.set(false);
          this.cargar();
        },
        error: (e) => {
          this.error.set(e?.error?.message ?? 'No se pudo crear la ubicación');
          this.guardando.set(false);
        },
      });
  }
}
