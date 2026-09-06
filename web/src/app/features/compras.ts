import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Compra, CompraLinea, Ubicacion } from '../core/models';
import { CompraService, LineaUbicacionAsignacion } from '../core/compra.service';
import { UbicacionService } from '../core/ubicacion.service';

type TabEstado = 'TODAS' | 'EN_TRANSITO' | 'INGRESADA';

@Component({
  selector: 'app-compras',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="space-y-6 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="border-b border-dark-border pb-5">
        <h2 class="text-2xl md:text-3xl font-extrabold tracking-tight text-white flex flex-wrap items-center gap-3">
          <svg class="w-7 h-7 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          <span>Compras</span>
          <span class="text-xs font-mono bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/30 px-2 py-0.5 rounded-full uppercase">
            ADMIN
          </span>
        </h2>
        <p class="text-sm text-gray-400 mt-1">
          Facturas de compra recibidas desde Power Automate. Marcá como ingresada para cargar stock automáticamente.
        </p>
      </div>

      <!-- Tabs de estado -->
      <div class="flex items-center gap-1 bg-dark-surface/60 rounded-xl p-1 w-fit overflow-x-auto">
        <button (click)="cambiarTab('TODAS')"
          [class]="tab() === 'TODAS'
            ? 'px-5 py-2 rounded-lg text-sm font-semibold bg-white/10 text-white border border-white/20 transition-all cursor-pointer whitespace-nowrap'
            : 'px-5 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white transition-all cursor-pointer whitespace-nowrap'">
          Todas
          @if (totalCompras() > 0) {
            <span class="ml-1.5 text-xs text-gray-500">({{ totalCompras() }})</span>
          }
        </button>
        <button (click)="cambiarTab('EN_TRANSITO')"
          [class]="tab() === 'EN_TRANSITO'
            ? 'px-5 py-2 rounded-lg text-sm font-semibold bg-amber-500/15 text-amber-400 border border-amber-500/30 transition-all cursor-pointer whitespace-nowrap'
            : 'px-5 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white transition-all cursor-pointer whitespace-nowrap'">
          En Tránsito
        </button>
        <button (click)="cambiarTab('INGRESADA')"
          [class]="tab() === 'INGRESADA'
            ? 'px-5 py-2 rounded-lg text-sm font-semibold bg-neon-green/15 text-neon-green border border-neon-green/30 transition-all cursor-pointer whitespace-nowrap'
            : 'px-5 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white transition-all cursor-pointer whitespace-nowrap'">
          Ingresadas
        </button>
      </div>

      <!-- Tabla -->
      @if (cargando()) {
        <div class="py-12 text-center text-gray-400 font-mono">Cargando compras...</div>
      } @else if (compras().length === 0) {
        <div class="py-12 text-center">
          <svg class="w-12 h-12 mx-auto text-gray-600 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          <p class="text-gray-500 text-sm">No hay compras registradas.</p>
        </div>
      } @else {
        <div class="glass-panel rounded-2xl border border-dark-border shadow-card overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-dark-border bg-dark-surface/40">
                  <th class="px-4 py-3 text-left text-xs font-semibold text-gray-400 uppercase tracking-wider">Factura</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold text-gray-400 uppercase tracking-wider hidden sm:table-cell">Fecha</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold text-gray-400 uppercase tracking-wider hidden md:table-cell">Proveedor</th>
                  <th class="px-4 py-3 text-center text-xs font-semibold text-gray-400 uppercase tracking-wider">Estado</th>
                  <th class="px-4 py-3 text-center text-xs font-semibold text-gray-400 uppercase tracking-wider hidden sm:table-cell">Líneas</th>
                  <th class="px-4 py-3 text-center text-xs font-semibold text-gray-400 uppercase tracking-wider hidden sm:table-cell">Uds.</th>
                  <th class="px-4 py-3 text-center text-xs font-semibold text-gray-400 uppercase tracking-wider hidden md:table-cell">Match</th>
                  <th class="px-4 py-3 text-center"></th>
                </tr>
              </thead>
              <tbody>
                @for (c of compras(); track c.id) {
                  <tr class="border-b border-dark-border/50 hover:bg-dark-surface/30 transition-colors">
                    <td class="px-4 py-3 font-mono text-white font-semibold text-xs">{{ c.numeroFactura }}</td>
                    <td class="px-4 py-3 text-gray-300 hidden sm:table-cell">{{ c.fechaFactura | date:'dd/MM/yyyy' }}</td>
                    <td class="px-4 py-3 text-gray-300 hidden md:table-cell">{{ c.proveedor || '—' }}</td>
                    <td class="px-4 py-3 text-center">
                      @if (c.estado === 'EN_TRANSITO') {
                        <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-400">
                          <span class="w-2 h-2 rounded-full bg-amber-400"></span>
                          Tránsito
                        </span>
                      } @else {
                        <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-neon-green">
                          <span class="w-2 h-2 rounded-full bg-neon-green"></span>
                          Ingresada
                        </span>
                      }
                    </td>
                    <td class="px-4 py-3 text-center text-gray-300 hidden sm:table-cell">{{ c.totalLineas }}</td>
                    <td class="px-4 py-3 text-center text-gray-300 hidden sm:table-cell">{{ c.totalUnidades }}</td>
                    <td class="px-4 py-3 text-center hidden md:table-cell">
                      <span class="text-xs font-mono" [class]="c.lineasMatcheadas === c.totalLineas ? 'text-neon-green' : 'text-amber-400'">
                        {{ c.lineasMatcheadas }}/{{ c.totalLineas }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-center">
                      <button (click)="verDetalle(c.id)"
                        class="p-1.5 rounded-lg text-gray-400 hover:text-neon-cyan hover:bg-neon-cyan/10 transition-colors cursor-pointer"
                        title="Ver detalle">
                        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Paginación -->
          @if (totalPages() > 1) {
            <div class="flex items-center justify-between px-4 py-3 border-t border-dark-border bg-dark-surface/20">
              <span class="text-xs text-gray-500">{{ totalCompras() }} compras</span>
              <div class="flex items-center gap-1">
                <button (click)="irPagina(page() - 1)" [disabled]="page() === 0"
                  class="px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default text-gray-300 hover:bg-dark-surface">
                  Ant.
                </button>
                <span class="px-3 py-1.5 text-xs text-gray-400">{{ page() + 1 }} / {{ totalPages() }}</span>
                <button (click)="irPagina(page() + 1)" [disabled]="page() >= totalPages() - 1"
                  class="px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default text-gray-300 hover:bg-dark-surface">
                  Sig.
                </button>
              </div>
            </div>
          }
        </div>
      }

      <!-- Modal detalle -->
      @if (detalleCompra()) {
        <div class="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-4" (click)="cerrarDetalle()">
          <div class="glass-panel w-full max-w-4xl max-h-[90vh] rounded-2xl border border-dark-border shadow-neon flex flex-col" (click)="$event.stopPropagation()">
            <!-- Header modal -->
            <div class="flex items-center justify-between p-5 border-b border-dark-border shrink-0">
              <div>
                <h3 class="text-lg font-bold text-white flex items-center gap-2">
                  Factura {{ detalleCompra()!.numeroFactura }}
                  @if (detalleCompra()!.estado === 'EN_TRANSITO') {
                    <span class="inline-flex items-center gap-1 text-xs font-semibold text-amber-400 bg-amber-400/10 px-2 py-0.5 rounded-full">
                      <span class="w-1.5 h-1.5 rounded-full bg-amber-400"></span>En Tránsito
                    </span>
                  } @else {
                    <span class="inline-flex items-center gap-1 text-xs font-semibold text-neon-green bg-neon-green/10 px-2 py-0.5 rounded-full">
                      <span class="w-1.5 h-1.5 rounded-full bg-neon-green"></span>Ingresada
                    </span>
                  }
                </h3>
                <p class="text-xs text-gray-400 mt-1">
                  {{ detalleCompra()!.fechaFactura | date:'dd/MM/yyyy' }}
                  @if (detalleCompra()!.proveedor) { · {{ detalleCompra()!.proveedor }} }
                  · {{ detalleCompra()!.totalLineas }} líneas · {{ detalleCompra()!.totalUnidades }} unidades
                </p>
              </div>
              <button (click)="cerrarDetalle()" class="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-dark-surface transition-colors cursor-pointer">
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <!-- Tabla de líneas -->
            <div class="flex-1 overflow-y-auto p-5">
              @if (detalleCompra()!.estado === 'INGRESADA') {
                <div class="mb-4 flex items-center gap-2 text-xs text-neon-green bg-neon-green/5 border border-neon-green/20 rounded-xl px-4 py-2.5">
                  <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                  </svg>
                  <span>Stock cargado — ubicación asignada por línea</span>
                </div>
              }

              @if (detalleCompra()!.estado === 'EN_TRANSITO') {
                <!-- Bulk assign -->
                <div class="mb-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-2 bg-dark-surface/40 border border-dark-border rounded-xl px-4 py-3">
                  <span class="text-xs text-gray-400 shrink-0">Asignar a todas:</span>
                  <select (change)="asignarTodas($event)"
                    class="flex-1 px-2 py-1.5 bg-dark-surface border border-dark-border rounded-lg text-white text-xs focus:outline-none focus:border-neon-cyan">
                    <option value="">— seleccionar —</option>
                    @for (u of ubicaciones(); track u.id) {
                      <option [value]="u.id">{{ u.path || u.codigo }}</option>
                    }
                  </select>
                </div>
              }

              <input [(ngModel)]="filtroLineas" type="text"
                placeholder="Filtrar por código o descripción..."
                class="w-full mb-3 px-3 py-2 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm" />

              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-dark-border">
                      <th class="px-3 py-2 text-left text-xs font-semibold text-gray-400 uppercase">Código</th>
                      <th class="px-3 py-2 text-left text-xs font-semibold text-gray-400 uppercase">Descripción</th>
                      <th class="px-3 py-2 text-center text-xs font-semibold text-gray-400 uppercase">Cant.</th>
                      <th class="px-3 py-2 text-center text-xs font-semibold text-gray-400 uppercase">Match</th>
                      <th class="px-3 py-2 text-left text-xs font-semibold text-gray-400 uppercase">Ubicación</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (l of filtrarLineas(detalleCompra()!.lineas); track l.id; let i = $index) {
                      <tr class="border-b border-dark-border/30">
                        <td class="px-3 py-2 font-mono text-white text-xs">{{ l.codigo }}</td>
                        <td class="px-3 py-2 text-gray-300 text-xs whitespace-normal break-words max-w-xs">{{ l.descripcion }}</td>
                        <td class="px-3 py-2 text-center text-white font-semibold">{{ l.cantidad }}</td>
                        <td class="px-3 py-2 text-center">
                          @if (l.productoId) {
                            <span class="text-neon-green text-xs" [title]="(l.productoMarca ? l.productoMarca + ' — ' : '') + (l.productoDescripcion || '')">
                              <svg class="w-4 h-4 inline" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                              </svg>
                            </span>
                          } @else {
                            <span class="text-gray-600 text-xs" title="Sin match en catálogo">—</span>
                          }
                        </td>
                        <td class="px-3 py-2">
                          @if (detalleCompra()!.estado === 'EN_TRANSITO') {
                            <select [value]="ubicacionPorLinea[l.id] || ''"
                              (change)="setUbicacionLinea(l.id, $event)"
                              class="w-full min-w-[140px] px-2 py-1.5 bg-dark-surface border rounded-lg text-xs focus:outline-none focus:border-neon-cyan"
                              [class]="ubicacionPorLinea[l.id]
                                ? 'w-full min-w-[140px] px-2 py-1.5 bg-dark-surface border border-dark-border rounded-lg text-white text-xs focus:outline-none focus:border-neon-cyan'
                                : 'w-full min-w-[140px] px-2 py-1.5 bg-dark-surface border border-amber-500/40 rounded-lg text-gray-400 text-xs focus:outline-none focus:border-neon-cyan'">
                              <option value="">— sin asignar —</option>
                              @for (u of ubicaciones(); track u.id) {
                                <option [value]="u.id">{{ u.path || u.codigo }}</option>
                              }
                            </select>
                            @if (l.ubicacionSugeridaCodigo && !ubicacionPorLinea[l.id]) {
                              <span class="text-[10px] text-neon-cyan/60 mt-0.5 block">
                                Sugerida: {{ l.ubicacionSugeridaCodigo }}
                              </span>
                            }
                          } @else {
                            @if (l.ubicacionIngresoCodigo) {
                              <span class="text-xs text-neon-green font-mono">{{ l.ubicacionIngresoCodigo }}</span>
                            } @else if (detalleCompra()!.ubicacionIngresoCodigo) {
                              <span class="text-xs text-gray-400 font-mono">{{ detalleCompra()!.ubicacionIngresoCodigo }}</span>
                            } @else {
                              <span class="text-xs text-gray-600">—</span>
                            }
                          }
                        </td>
                      </tr>
                    } @empty {
                      <tr>
                        <td colspan="5" class="py-4 text-center text-gray-500 text-xs">
                          Sin coincidencias para "{{ filtroLineas }}".
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Footer modal: acción de ingreso -->
            @if (detalleCompra()!.estado === 'EN_TRANSITO') {
              <div class="p-5 border-t border-dark-border shrink-0">
                @if (errorIngreso()) {
                  <div class="mb-3 text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded-lg px-3 py-2">
                    {{ errorIngreso() }}
                  </div>
                }
                <div class="flex items-center justify-between gap-3">
                  <div class="text-xs text-gray-400">
                    {{ lineasAsignadas() }}/{{ detalleCompra()!.lineas.length }} líneas con ubicación
                  </div>
                  <button (click)="confirmarIngreso()" [disabled]="lineasAsignadas() === 0 || ingresando()"
                    class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-neon-green/20 text-neon-green border border-neon-green/40 hover:bg-neon-green/30 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-default whitespace-nowrap">
                    @if (ingresando()) {
                      <span class="inline-flex items-center gap-2">
                        <svg class="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none">
                          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" class="opacity-25"></circle>
                          <path d="M4 12a8 8 0 018-8" stroke="currentColor" stroke-width="3" stroke-linecap="round" class="opacity-75"></path>
                        </svg>
                        Cargando stock...
                      </span>
                    } @else {
                      Marcar como Ingresada
                    }
                  </button>
                </div>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class Compras implements OnInit {
  private compraService = inject(CompraService);
  private ubicacionService = inject(UbicacionService);

  tab = signal<TabEstado>('TODAS');
  compras = signal<Compra[]>([]);
  cargando = signal(false);
  page = signal(0);
  totalCompras = signal(0);
  totalPages = signal(0);

  detalleCompra = signal<Compra | null>(null);
  cargandoDetalle = signal(false);
  filtroLineas = '';
  ubicaciones = signal<Ubicacion[]>([]);
  ubicacionPorLinea: Record<number, number> = {};
  ingresando = signal(false);
  errorIngreso = signal('');

  ngOnInit(): void {
    this.cargar();
    this.ubicacionService.listar().subscribe(u => this.ubicaciones.set(u));
  }

  cambiarTab(t: TabEstado): void {
    this.tab.set(t);
    this.page.set(0);
    this.cargar();
  }

  irPagina(p: number): void {
    this.page.set(p);
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    const estado = this.tab() === 'TODAS' ? undefined : this.tab();
    this.compraService.listar(this.page(), 20, estado).subscribe({
      next: (res) => {
        this.compras.set(res.content);
        this.totalCompras.set(res.totalElements);
        this.totalPages.set(res.totalPages);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  verDetalle(id: number): void {
    this.cargandoDetalle.set(true);
    this.errorIngreso.set('');
    this.filtroLineas = '';
    this.ubicacionPorLinea = {};
    this.compraService.detalle(id).subscribe({
      next: (c) => {
        this.detalleCompra.set(c);
        this.cargandoDetalle.set(false);
        this.inicializarUbicaciones(c);
      },
      error: () => this.cargandoDetalle.set(false),
    });
  }

  cerrarDetalle(): void {
    this.detalleCompra.set(null);
    this.filtroLineas = '';
  }

  filtrarLineas(lineas: CompraLinea[]): CompraLinea[] {
    const f = this.filtroLineas.toLowerCase().trim();
    if (!f) return lineas;
    return lineas.filter(l =>
      l.codigo.toLowerCase().includes(f) ||
      l.descripcion.toLowerCase().includes(f)
    );
  }

  lineasAsignadas(): number {
    return Object.keys(this.ubicacionPorLinea).length;
  }

  setUbicacionLinea(lineaId: number, event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    if (val) {
      this.ubicacionPorLinea[lineaId] = +val;
    } else {
      delete this.ubicacionPorLinea[lineaId];
    }
  }

  asignarTodas(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    if (!val) return;
    const ubicId = +val;
    const compra = this.detalleCompra();
    if (!compra) return;
    this.ubicacionPorLinea = {};
    for (const l of compra.lineas) {
      this.ubicacionPorLinea[l.id] = ubicId;
    }
  }

  confirmarIngreso(): void {
    const compra = this.detalleCompra();
    if (!compra) return;

    const asignaciones: LineaUbicacionAsignacion[] = Object.entries(this.ubicacionPorLinea)
      .map(([lineaId, ubicacionId]) => ({ lineaId: +lineaId, ubicacionId }));

    if (asignaciones.length === 0) return;

    this.ingresando.set(true);
    this.errorIngreso.set('');
    this.compraService.marcarIngresada(compra.id, asignaciones).subscribe({
      next: (updated) => {
        this.detalleCompra.set(updated);
        this.ingresando.set(false);
        this.cargar();
      },
      error: (err) => {
        this.errorIngreso.set(err.error?.message || err.error?.error || 'Error al marcar como ingresada');
        this.ingresando.set(false);
      },
    });
  }

  private inicializarUbicaciones(compra: Compra): void {
    if (compra.estado !== 'EN_TRANSITO') return;
    this.ubicacionPorLinea = {};
    for (const l of compra.lineas) {
      if (l.ubicacionSugeridaId) {
        this.ubicacionPorLinea[l.id] = l.ubicacionSugeridaId;
      }
    }
  }
}
