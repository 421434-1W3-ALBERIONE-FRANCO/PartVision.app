import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Marca, ProductoListItem } from '../core/models';
import { MarcaService } from '../core/marca.service';
import { ProductoService } from '../core/producto.service';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-dark-border pb-6">
        <div>
          <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
            <span>Catálogo de Productos</span>
            <span class="text-xs font-mono bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/30 px-2 py-0.5 rounded-full uppercase">
              CATALOGO MASTER
            </span>
          </h2>
          <p class="text-sm text-gray-400 mt-1">
            Gestión de repuestos, códigos SKU, marcas asociadas y categorización técnica.
          </p>
        </div>

        <div class="flex gap-2 self-start md:self-auto">
          <button
            (click)="toggleMarcas()"
            class="px-5 py-2.5 rounded-xl font-semibold text-sm bg-dark-surface border border-dark-border hover:border-neon-cyan text-gray-200 flex items-center gap-2 cursor-pointer"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 7h.01M7 3h5a1.99 1.99 0 011.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.99 1.99 0 013 12V7a4 4 0 014-4z" />
            </svg>
            <span>{{ mostrarMarcas() ? 'Cerrar Marcas' : 'Marcas' }}</span>
          </button>

          <button
            (click)="mostrarNuevo.set(!mostrarNuevo())"
            class="px-5 py-2.5 rounded-xl font-semibold text-sm neon-button-primary flex items-center gap-2 cursor-pointer"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            <span>{{ mostrarNuevo() ? 'Cerrar Formulario' : 'Nuevo Producto' }}</span>
          </button>
        </div>
      </div>

      <!-- Panel: Gestión de Marcas -->
      @if (mostrarMarcas()) {
        <div class="glass-panel p-6 rounded-2xl border border-neon-purple/40 shadow-neon animate-slide-in">
          <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 7h.01M7 3h5a1.99 1.99 0 011.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.99 1.99 0 013 12V7a4 4 0 014-4z" />
            </svg>
            Gestión de Marcas
          </h3>

          <!-- Alta rápida -->
          <div class="flex gap-2 mb-5">
            <input
              [(ngModel)]="nuevaMarcaNombre"
              type="text"
              placeholder="Nueva marca..."
              (keyup.enter)="crearMarca()"
              class="flex-1 min-w-0 px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-purple text-sm"
            />
            <button
              [disabled]="marcaGuardando()"
              (click)="crearMarca()"
              class="px-5 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer disabled:opacity-50 whitespace-nowrap"
            >
              Agregar
            </button>
          </div>

          @if (marcaError()) {
            <div class="mb-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">
              {{ marcaError() }}
            </div>
          }

          @if (marcas().length === 0) {
            <p class="text-gray-500 text-sm py-4 text-center">No hay marcas cargadas todavía.</p>
          } @else {
            <ul class="divide-y divide-dark-border/50 max-h-80 overflow-y-auto">
              @for (m of marcas(); track m.id) {
                <li class="flex items-center gap-3 py-2.5">
                  @if (editandoMarcaId() === m.id) {
                    <input
                      [(ngModel)]="editandoMarcaNombre"
                      type="text"
                      (keyup.enter)="guardarEdicionMarca(m.id)"
                      class="flex-1 min-w-0 px-3 py-1.5 bg-dark-surface border border-neon-cyan/50 rounded-lg text-white text-sm focus:outline-none focus:border-neon-cyan"
                    />
                    <button (click)="guardarEdicionMarca(m.id)" class="text-xs font-semibold text-neon-green hover:underline cursor-pointer">Guardar</button>
                    <button (click)="cancelarEdicionMarca()" class="text-xs font-semibold text-gray-400 hover:underline cursor-pointer">Cancelar</button>
                  } @else {
                    <span class="flex-1 min-w-0 truncate text-white text-sm">{{ m.nombre }}</span>
                    <button (click)="iniciarEdicionMarca(m)" class="text-xs font-semibold text-neon-cyan hover:underline cursor-pointer">Editar</button>
                    <button (click)="eliminarMarca(m)" class="text-xs font-semibold text-red-400 hover:underline cursor-pointer">Eliminar</button>
                  }
                </li>
              }
            </ul>
          }
        </div>
      }

      <!-- Form: Crear Producto -->
      @if (mostrarNuevo()) {
        <div class="glass-panel p-6 rounded-2xl border border-neon-cyan/40 shadow-neon-cyan animate-slide-in">
          <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
            </svg>
            Alta Manual de Producto
          </h3>

          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Código SKU
              </label>
              <input
                [(ngModel)]="sku"
                type="text"
                placeholder="Ej: BPR6ES"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>

            <div>
              <div class="flex items-center justify-between mb-1">
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300">
                  Marca
                </label>
                <button
                  type="button"
                  (click)="mostrarNuevaMarca.set(!mostrarNuevaMarca())"
                  class="text-xs font-semibold text-neon-cyan hover:underline cursor-pointer"
                >
                  {{ mostrarNuevaMarca() ? 'Cancelar' : '+ Nueva marca' }}
                </button>
              </div>
              @if (mostrarNuevaMarca()) {
                <div class="flex gap-2">
                  <input
                    [(ngModel)]="nuevaMarcaNombre"
                    type="text"
                    placeholder="Nombre de la marca"
                    (keyup.enter)="crearMarca()"
                    class="flex-1 min-w-0 px-3.5 py-2.5 bg-dark-surface border border-neon-cyan/50 rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
                  />
                  <button
                    type="button"
                    [disabled]="marcaGuardando()"
                    (click)="crearMarca()"
                    class="px-3 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer disabled:opacity-50 whitespace-nowrap"
                  >
                    Crear
                  </button>
                </div>
              } @else {
                <select
                  [(ngModel)]="marcaId"
                  class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm"
                >
                  <option [ngValue]="null">Seleccionar marca...</option>
                  @for (m of marcas(); track m.id) {
                    <option [ngValue]="m.id">{{ m.nombre }}</option>
                  }
                </select>
              }
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Código de Barras
              </label>
              <input
                [(ngModel)]="codigoBarras"
                type="text"
                placeholder="Ej: 77900011122"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                Descripción *
              </label>
              <input
                [(ngModel)]="descripcion"
                type="text"
                placeholder="Ej: Bujía de Encendido"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
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
              {{ guardando() ? 'Guardando...' : 'Guardar Producto' }}
            </button>
          </div>
        </div>
      }

      <!-- Products Table -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        @if (cargando()) {
          <div class="py-12 text-center text-gray-400 font-mono">
            <svg class="animate-spin h-8 w-8 text-neon-purple mx-auto mb-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            Cargando catálogo...
          </div>
        } @else if (productos().length === 0) {
          <div class="py-12 text-center text-gray-500">
            No se encontraron productos registrados.
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm border-collapse">
              <thead>
                <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                  <th class="py-3 px-4">ID</th>
                  <th class="py-3 px-4">SKU</th>
                  <th class="py-3 px-4">Descripción</th>
                  <th class="py-3 px-4">Marca</th>
                  <th class="py-3 px-4">Categoría</th>
                  <th class="py-3 px-4">Estado</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border/50">
                @for (p of productos(); track p.id) {
                  <tr class="hover:bg-dark-surface/40 transition-colors">
                    <td class="py-3.5 px-4 font-mono text-gray-400 text-xs">#{{ p.id }}</td>
                    <td class="py-3.5 px-4 font-mono font-bold text-neon-purple">
                      {{ p.sku ?? '-' }}
                    </td>
                    <td class="py-3.5 px-4 font-medium text-white">
                      {{ p.descripcion }}
                    </td>
                    <td class="py-3.5 px-4">
                      @if (p.marcaNombre) {
                        <span class="neon-badge-cyan px-2.5 py-1 rounded-md text-xs font-semibold">
                          {{ p.marcaNombre }}
                        </span>
                      } @else {
                        <span class="text-gray-500 text-xs">-</span>
                      }
                    </td>
                    <td class="py-3.5 px-4 text-gray-400 text-xs">
                      {{ p.categoriaNombre ?? '-' }}
                    </td>
                    <td class="py-3.5 px-4">
                      <span class="neon-badge-green px-2.5 py-1 rounded-md text-xs font-semibold">
                        {{ p.estado }}
                      </span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Pagination -->
          <div class="flex items-center justify-between border-t border-dark-border pt-4 mt-4">
            <span class="text-xs font-mono text-gray-400">
              Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElements() }} productos)
            </span>
            <div class="flex gap-2">
              <button
                [disabled]="pagina() === 0"
                (click)="cambiarPagina(-1)"
                class="px-3 py-1.5 rounded-lg text-xs font-medium bg-dark-surface border border-dark-border hover:border-neon-purple text-gray-300 disabled:opacity-40 cursor-pointer"
              >
                Anterior
              </button>
              <button
                [disabled]="pagina() >= totalPaginas() - 1"
                (click)="cambiarPagina(1)"
                class="px-3 py-1.5 rounded-lg text-xs font-medium bg-dark-surface border border-dark-border hover:border-neon-purple text-gray-300 disabled:opacity-40 cursor-pointer"
              >
                Siguiente
              </button>
            </div>
          </div>
        }
      </div>
    </div>
  `,
})
export class Productos implements OnInit {
  private service = inject(ProductoService);
  private marcaService = inject(MarcaService);

  productos = signal<ProductoListItem[]>([]);
  marcas = signal<Marca[]>([]);
  pagina = signal(0);
  totalPaginas = signal(1);
  totalElements = signal(0);
  cargando = signal(true);

  mostrarNuevo = signal(false);
  sku = '';
  marcaId: number | null = null;
  codigoBarras = '';
  descripcion = '';
  guardando = signal(false);
  error = signal<string | null>(null);

  // Gestión de marcas
  mostrarMarcas = signal(false);
  mostrarNuevaMarca = signal(false);
  nuevaMarcaNombre = '';
  marcaGuardando = signal(false);
  marcaError = signal<string | null>(null);
  editandoMarcaId = signal<number | null>(null);
  editandoMarcaNombre = '';

  ngOnInit(): void {
    this.cargar();
    this.cargarMarcas();
  }

  cargarMarcas(): void {
    this.marcaService.listar().subscribe((list) => this.marcas.set(list));
  }

  toggleMarcas(): void {
    this.mostrarMarcas.update((v) => !v);
    this.marcaError.set(null);
    this.editandoMarcaId.set(null);
  }

  crearMarca(): void {
    const nombre = this.nuevaMarcaNombre.trim();
    if (!nombre) {
      this.marcaError.set('El nombre de la marca es obligatorio');
      return;
    }
    this.marcaError.set(null);
    this.marcaGuardando.set(true);
    this.marcaService.crear(nombre).subscribe({
      next: (marca) => {
        this.nuevaMarcaNombre = '';
        this.marcaGuardando.set(false);
        this.mostrarNuevaMarca.set(false);
        this.cargarMarcas();
        // Si venía del formulario de producto, la deja seleccionada.
        this.marcaId = marca.id;
      },
      error: (e) => {
        this.marcaError.set(e?.error?.message ?? 'No se pudo crear la marca');
        this.marcaGuardando.set(false);
      },
    });
  }

  iniciarEdicionMarca(m: Marca): void {
    this.editandoMarcaId.set(m.id);
    this.editandoMarcaNombre = m.nombre;
    this.marcaError.set(null);
  }

  cancelarEdicionMarca(): void {
    this.editandoMarcaId.set(null);
    this.editandoMarcaNombre = '';
  }

  guardarEdicionMarca(id: number): void {
    const nombre = this.editandoMarcaNombre.trim();
    if (!nombre) {
      this.marcaError.set('El nombre de la marca es obligatorio');
      return;
    }
    this.marcaService.editar(id, nombre).subscribe({
      next: () => {
        this.cancelarEdicionMarca();
        this.cargarMarcas();
      },
      error: (e) => this.marcaError.set(e?.error?.message ?? 'No se pudo actualizar la marca'),
    });
  }

  eliminarMarca(m: Marca): void {
    if (!confirm(`¿Eliminar la marca "${m.nombre}"?`)) return;
    this.marcaError.set(null);
    this.marcaService.eliminar(m.id).subscribe({
      next: () => this.cargarMarcas(),
      error: (e) => this.marcaError.set(e?.error?.message ?? 'No se pudo eliminar la marca'),
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.service.listar(this.pagina()).subscribe({
      next: (res) => {
        this.productos.set(res.content);
        this.totalPaginas.set(res.totalPages);
        this.totalElements.set(res.totalElements);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  cambiarPagina(delta: number): void {
    this.pagina.update((p) => Math.max(0, p + delta));
    this.cargar();
  }

  crear(): void {
    if (!this.descripcion.trim()) {
      this.error.set('La descripción es obligatoria');
      return;
    }
    this.error.set(null);
    this.guardando.set(true);

    const codigos = this.codigoBarras.trim()
      ? [{ codigo: this.codigoBarras.trim(), tipo: 'BARRA' }]
      : undefined;

    this.service
      .crear({
        sku: this.sku.trim() || undefined,
        marcaId: this.marcaId || undefined,
        descripcion: this.descripcion.trim(),
        codigos,
      })
      .subscribe({
        next: () => {
          this.sku = '';
          this.marcaId = null;
          this.codigoBarras = '';
          this.descripcion = '';
          this.guardando.set(false);
          this.mostrarNuevo.set(false);
          this.cargar();
        },
        error: (e) => {
          this.error.set(e?.error?.message ?? 'No se pudo crear el producto');
          this.guardando.set(false);
        },
      });
  }
}
