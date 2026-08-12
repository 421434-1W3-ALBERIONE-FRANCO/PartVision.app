import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Movimiento, ProductoListItem, StockLinea, StockResumen, Ubicacion } from '../core/models';
import { StockService } from '../core/stock.service';
import { ProductoService } from '../core/producto.service';
import { UbicacionService } from '../core/ubicacion.service';

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="border-b border-dark-border pb-6">
        <h2 class="text-2xl md:text-3xl font-extrabold tracking-tight text-white flex flex-wrap items-center gap-3">
          <span>Control de Stock e Inventario</span>
          <span class="text-xs font-mono bg-neon-purple/20 text-neon-purple border border-neon-purple/30 px-2 py-0.5 rounded-full uppercase">
            DEPOSITO REAL
          </span>
        </h2>
        <p class="text-sm text-gray-400 mt-1">
          Buscá un producto, mirá dónde está y cuánto hay, y cargá stock en una ubicación.
        </p>
      </div>

      <!-- Buscador de producto -->
      <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card space-y-4">
        <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300">Buscar producto</label>
        <div class="flex flex-col sm:flex-row gap-3">
          <input
            [(ngModel)]="q"
            (keyup.enter)="buscar()"
            type="text"
            placeholder="SKU, descripción o marca..."
            class="flex-1 px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-purple text-sm"
          />
          <button (click)="buscar()" class="px-5 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer">
            Buscar
          </button>
        </div>

        @if (buscando()) {
          <p class="text-xs text-gray-500 font-mono">Buscando...</p>
        } @else if (resultados().length > 0) {
          <div class="divide-y divide-dark-border/50 border border-dark-border rounded-xl overflow-hidden">
            @for (p of resultados(); track p.id) {
              <button
                (click)="seleccionar(p)"
                class="w-full text-left px-4 py-3 hover:bg-dark-surface/60 transition-colors cursor-pointer flex flex-wrap items-center gap-x-3 gap-y-1"
              >
                <span class="font-mono font-bold text-neon-cyan text-sm">{{ p.sku || '—' }}</span>
                <span class="text-gray-200 text-sm">{{ p.descripcion }}</span>
                @if (p.marcaNombre) {
                  <span class="neon-badge-purple px-2 py-0.5 rounded text-[11px] font-mono">{{ p.marcaNombre }}</span>
                }
              </button>
            }
          </div>
        } @else if (buscoSinResultados()) {
          <p class="text-xs text-gray-500">No se encontraron productos para "{{ q }}".</p>
        }
      </div>

      @if (productoSel(); as p) {
        <!-- Producto seleccionado -->
        <div class="glass-panel rounded-2xl p-4 md:p-6 border border-neon-cyan/40 shadow-neon-cyan space-y-1">
          <p class="text-xs uppercase tracking-wider text-gray-400">Producto seleccionado</p>
          <p class="text-lg font-bold text-white">{{ p.descripcion }}</p>
          <p class="text-sm font-mono text-neon-cyan">
            SKU {{ p.sku || '—' }}
            @if (p.marcaNombre) { <span class="text-gray-400"> · {{ p.marcaNombre }}</span> }
          </p>
        </div>

        <!-- Cargar stock -->
        <div class="glass-panel rounded-2xl p-4 md:p-6 border border-neon-purple/40 shadow-neon space-y-4">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Cargar stock
          </h3>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div class="md:col-span-1">
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Ubicación</label>
              <select
                [(ngModel)]="entradaUbicacionId"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm"
              >
                <option [ngValue]="null" disabled>Elegí una ubicación…</option>
                @for (u of ubicaciones(); track u.id) {
                  <option [ngValue]="u.id">{{ u.codigo }}{{ u.descripcion ? ' — ' + u.descripcion : '' }}</option>
                }
              </select>
              @if (ubicaciones().length === 0) {
                <p class="mt-1 text-[11px] text-amber-400">No hay ubicaciones. Creá una en la sección Ubicaciones.</p>
              }
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Cantidad</label>
              <input
                [(ngModel)]="entradaCantidad"
                type="number"
                min="1"
                placeholder="0"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>

            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Motivo (opcional)</label>
              <input
                [(ngModel)]="entradaMotivo"
                type="text"
                placeholder="Ej: ingreso de mercadería"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>
          </div>

          @if (entradaError()) {
            <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ entradaError() }}</div>
          }
          @if (entradaOk()) {
            <div class="p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-xs">{{ entradaOk() }}</div>
          }

          <div class="flex justify-end">
            <button [disabled]="guardandoEntrada()" (click)="cargarStock()"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50">
              {{ guardandoEntrada() ? 'Guardando...' : 'Cargar stock' }}
            </button>
          </div>
        </div>

        <!-- Stock actual -->
        @if (resumen(); as r) {
          <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card space-y-4">
            <div class="flex items-center justify-between border-b border-dark-border pb-3">
              <h3 class="text-lg font-bold text-white">Stock actual</h3>
              <span class="text-xl font-extrabold text-neon-green font-mono">Total: {{ r.total }} u.</span>
            </div>
            @if (r.ubicaciones.length === 0) {
              <p class="text-xs text-gray-500 py-4 text-center">Este producto todavía no tiene existencias en ninguna ubicación.</p>
            } @else {
              @if (stockError()) {
                <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ stockError() }}</div>
              }
              <div class="overflow-x-auto">
                <table class="w-full text-left text-sm border-collapse min-w-[440px]">
                  <thead>
                    <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                      <th class="py-3 px-4">Ubicación</th>
                      <th class="py-3 px-4 text-right">Cantidad</th>
                      <th class="py-3 px-4 text-right">Acciones</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-dark-border/50">
                    @for (l of r.ubicaciones; track l.ubicacionId) {
                      <tr class="hover:bg-dark-surface/40 transition-colors">
                        <td class="py-3 px-4 font-mono font-semibold text-neon-purple">{{ l.ubicacionPath }}</td>
                        @if (editandoUbicacionId() === l.ubicacionId) {
                          <td class="py-3 px-4 text-right">
                            <input
                              [(ngModel)]="editCantidad"
                              type="number"
                              min="0"
                              (keyup.enter)="guardarCantidad(l.ubicacionId)"
                              class="w-24 px-2.5 py-1.5 bg-dark-surface border border-neon-cyan/50 rounded-lg text-white font-mono text-right text-sm focus:outline-none focus:border-neon-cyan"
                            />
                          </td>
                          <td class="py-3 px-4 text-right whitespace-nowrap">
                            <button [disabled]="filaGuardando()" (click)="guardarCantidad(l.ubicacionId)" class="text-xs font-semibold text-neon-green hover:underline cursor-pointer disabled:opacity-50">Guardar</button>
                            <button (click)="cancelarEdicionCantidad()" class="ml-3 text-xs font-semibold text-gray-400 hover:underline cursor-pointer">Cancelar</button>
                          </td>
                        } @else {
                          <td class="py-3 px-4 font-mono font-bold text-neon-green text-right">{{ l.cantidad }} u.</td>
                          <td class="py-3 px-4 text-right whitespace-nowrap">
                            <button (click)="iniciarEdicionCantidad(l)" class="text-xs font-semibold text-neon-cyan hover:underline cursor-pointer">Editar</button>
                            <button [disabled]="filaGuardando()" (click)="pedirEliminarLinea(l)" class="ml-3 text-xs font-semibold text-red-400 hover:underline cursor-pointer disabled:opacity-50">Eliminar</button>
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }

        <!-- Historial -->
        <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card space-y-4">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Historial de Movimientos
          </h3>
          @if (movimientos().length === 0) {
            <div class="py-8 text-center text-gray-500 text-sm">Sin movimientos registrados para este producto.</div>
          } @else {
            <div class="overflow-x-auto">
              <table class="w-full text-left text-sm border-collapse min-w-[520px]">
                <thead>
                  <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                    <th class="py-3 px-4">Fecha</th>
                    <th class="py-3 px-4">Tipo</th>
                    <th class="py-3 px-4">Cantidad</th>
                    <th class="py-3 px-4">Motivo</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-dark-border/50">
                  @for (m of movimientos(); track m.id) {
                    <tr class="hover:bg-dark-surface/40 transition-colors">
                      <td class="py-3.5 px-4 font-mono text-gray-400 text-xs">
                        {{ m.fecha ? (m.fecha | date:'dd/MM/yyyy HH:mm') : '-' }}
                      </td>
                      <td class="py-3.5 px-4 font-semibold">
                        <span class="px-2.5 py-1 rounded-md text-xs font-mono"
                              [class]="m.tipo === 'ENTRADA' ? 'neon-badge-green' : m.tipo === 'SALIDA' ? 'bg-red-500/15 text-red-400 border border-red-500/30' : 'neon-badge-cyan'">
                          {{ m.tipo }}
                        </span>
                      </td>
                      <td class="py-3.5 px-4 font-mono font-bold text-white">{{ m.cantidad }} u.</td>
                      <td class="py-3.5 px-4 text-gray-400 text-xs">{{ m.motivo ?? '-' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }
    </div>

    <!-- Confirmación de eliminación de existencia -->
    @if (aEliminarLinea(); as l) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="cancelarEliminarLinea()">
        <div class="glass-panel w-full max-w-md p-6 rounded-2xl border border-red-500/40 shadow-neon" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            Eliminar existencia
          </h3>
          <p class="text-sm text-gray-300">
            Vas a eliminar la existencia de este producto en
            <span class="font-mono font-semibold text-neon-purple">{{ l.ubicacionPath }}</span>
            (<span class="font-mono font-semibold text-neon-green">{{ l.cantidad }} u.</span>).
            Esta ubicación dejará de figurar para el producto. ¿Seguro?
          </p>
          @if (stockError()) {
            <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ stockError() }}</div>
          }
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="cancelarEliminarLinea()" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">Cancelar</button>
            <button [disabled]="filaGuardando()" (click)="confirmarEliminarLinea(l)" class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-600 hover:bg-red-500 text-white transition-colors cursor-pointer disabled:opacity-50">
              {{ filaGuardando() ? 'Eliminando...' : 'Eliminar' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class Stock implements OnInit {
  private service = inject(StockService);
  private productos = inject(ProductoService);
  private ubicacionService = inject(UbicacionService);

  // Búsqueda
  q = '';
  resultados = signal<ProductoListItem[]>([]);
  buscando = signal(false);
  buscoSinResultados = signal(false);

  // Selección
  productoSel = signal<ProductoListItem | null>(null);
  resumen = signal<StockResumen | null>(null);
  movimientos = signal<Movimiento[]>([]);

  // Ubicaciones (para el desplegable)
  ubicaciones = signal<Ubicacion[]>([]);

  // Cargar stock
  entradaUbicacionId: number | null = null;
  entradaCantidad: number | null = null;
  entradaMotivo = '';
  guardandoEntrada = signal(false);
  entradaError = signal<string | null>(null);
  entradaOk = signal<string | null>(null);

  // Editar / eliminar filas de stock actual
  editandoUbicacionId = signal<number | null>(null);
  editCantidad: number | null = null;
  filaGuardando = signal(false);
  stockError = signal<string | null>(null);
  aEliminarLinea = signal<StockLinea | null>(null);

  ngOnInit(): void {
    this.ubicacionService.listar().subscribe({
      next: (list) => this.ubicaciones.set(list),
      error: () => this.ubicaciones.set([]),
    });
  }

  buscar(): void {
    const q = this.q.trim();
    if (!q) return;
    this.buscando.set(true);
    this.buscoSinResultados.set(false);
    this.productos.buscarTexto(q, 0, 10).subscribe({
      next: (page) => {
        this.resultados.set(page.content);
        this.buscoSinResultados.set(page.content.length === 0);
        this.buscando.set(false);
      },
      error: () => {
        this.resultados.set([]);
        this.buscando.set(false);
      },
    });
  }

  seleccionar(p: ProductoListItem): void {
    this.productoSel.set(p);
    this.resultados.set([]);
    this.entradaOk.set(null);
    this.entradaError.set(null);
    this.refrescarStock(p.id);
  }

  cargarStock(): void {
    const p = this.productoSel();
    if (!p) return;
    this.entradaError.set(null);
    this.entradaOk.set(null);

    if (this.entradaUbicacionId == null) {
      this.entradaError.set('Elegí una ubicación.');
      return;
    }
    if (this.entradaCantidad == null || this.entradaCantidad < 1) {
      this.entradaError.set('La cantidad debe ser mayor o igual a 1.');
      return;
    }

    this.guardandoEntrada.set(true);
    this.service
      .entrada({
        productoId: p.id,
        ubicacionId: this.entradaUbicacionId,
        cantidad: this.entradaCantidad,
        motivo: this.entradaMotivo.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.entradaOk.set(`Se cargaron ${this.entradaCantidad} u. correctamente.`);
          this.entradaCantidad = null;
          this.entradaMotivo = '';
          this.guardandoEntrada.set(false);
          this.refrescarStock(p.id);
        },
        error: (e) => {
          this.entradaError.set(e?.error?.message ?? 'No se pudo cargar el stock.');
          this.guardandoEntrada.set(false);
        },
      });
  }

  iniciarEdicionCantidad(l: StockLinea): void {
    this.stockError.set(null);
    this.editandoUbicacionId.set(l.ubicacionId);
    this.editCantidad = l.cantidad;
  }

  cancelarEdicionCantidad(): void {
    this.editandoUbicacionId.set(null);
    this.editCantidad = null;
  }

  guardarCantidad(ubicacionId: number): void {
    const p = this.productoSel();
    if (!p) return;
    if (this.editCantidad == null || this.editCantidad < 0) {
      this.stockError.set('La cantidad debe ser 0 o mayor.');
      return;
    }
    this.stockError.set(null);
    this.filaGuardando.set(true);
    this.service
      .conteo({ productoId: p.id, ubicacionId, cantidadReal: this.editCantidad, motivo: 'Ajuste manual de stock' })
      .subscribe({
        next: () => {
          this.filaGuardando.set(false);
          this.cancelarEdicionCantidad();
          this.refrescarStock(p.id);
        },
        error: (e) => {
          this.stockError.set(e?.error?.message ?? 'No se pudo actualizar la cantidad.');
          this.filaGuardando.set(false);
        },
      });
  }

  pedirEliminarLinea(l: StockLinea): void {
    this.stockError.set(null);
    this.cancelarEdicionCantidad();
    this.aEliminarLinea.set(l);
  }

  cancelarEliminarLinea(): void {
    this.aEliminarLinea.set(null);
    this.stockError.set(null);
  }

  confirmarEliminarLinea(l: StockLinea): void {
    const p = this.productoSel();
    if (!p) return;
    this.stockError.set(null);
    this.filaGuardando.set(true);
    this.service.eliminar(p.id, l.ubicacionId).subscribe({
      next: () => {
        this.filaGuardando.set(false);
        this.aEliminarLinea.set(null);
        this.refrescarStock(p.id);
      },
      error: (e) => {
        this.stockError.set(e?.error?.message ?? 'No se pudo eliminar la existencia.');
        this.filaGuardando.set(false);
      },
    });
  }

  private refrescarStock(productoId: number): void {
    this.service.resumen(productoId).subscribe({
      next: (res) => this.resumen.set(res),
      error: () => this.resumen.set(null),
    });
    this.service.movimientos(productoId).subscribe({
      next: (page) => this.movimientos.set(page.content),
      error: () => this.movimientos.set([]),
    });
  }
}
