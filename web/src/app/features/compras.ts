import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Compra, CompraLinea, ImportadoPendiente, ProductoListItem, Ubicacion } from '../core/models';
import { CompraService, LineaUbicacionAsignacion } from '../core/compra.service';
import { UbicacionService } from '../core/ubicacion.service';
import { ProductoService } from '../core/producto.service';

type TabEstado = 'TODAS' | 'EN_TRANSITO' | 'POR_UBICAR' | 'INGRESADA';

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
          Facturas de compra que llegan de la planilla del cliente. Cuando la planilla las marca INGRESADA,
          quedan por ubicar: asigná una ubicación a cada línea para cargar el stock.
        </p>
      </div>

      <!-- Tabs de estado + importados -->
      <div class="flex flex-wrap items-center justify-between gap-3">
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
        <button (click)="cambiarTab('POR_UBICAR')"
          [class]="tab() === 'POR_UBICAR'
            ? 'px-5 py-2 rounded-lg text-sm font-semibold bg-neon-cyan/15 text-neon-cyan border border-neon-cyan/30 transition-all cursor-pointer whitespace-nowrap'
            : 'px-5 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white transition-all cursor-pointer whitespace-nowrap'">
          Por ubicar
        </button>
        <button (click)="cambiarTab('INGRESADA')"
          [class]="tab() === 'INGRESADA'
            ? 'px-5 py-2 rounded-lg text-sm font-semibold bg-neon-green/15 text-neon-green border border-neon-green/30 transition-all cursor-pointer whitespace-nowrap'
            : 'px-5 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white transition-all cursor-pointer whitespace-nowrap'">
          Ingresadas
        </button>
      </div>
        <button (click)="abrirImportados()"
          class="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold border border-neon-purple-light/60 text-white bg-neon-purple/35 hover:bg-neon-purple/50 shadow-neon transition-colors cursor-pointer whitespace-nowrap"
          title="Piezas que llegaron sin código en la planilla">
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
          </svg>
          Importados
          @if (importadosTotal() > 0) {
            <span class="min-w-[1.25rem] px-1.5 py-0.5 rounded-full text-[11px] font-mono text-center bg-neon-purple-light text-dark font-bold">{{ importadosTotal() }}</span>
          }
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
                      } @else if (c.estado === 'POR_UBICAR') {
                        <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-neon-cyan">
                          <span class="w-2 h-2 rounded-full bg-neon-cyan"></span>
                          Por ubicar
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
                  } @else if (detalleCompra()!.estado === 'POR_UBICAR') {
                    <span class="inline-flex items-center gap-1 text-xs font-semibold text-neon-cyan bg-neon-cyan/10 px-2 py-0.5 rounded-full">
                      <span class="w-1.5 h-1.5 rounded-full bg-neon-cyan"></span>Por ubicar
                    </span>
                  } @else {
                    <span class="inline-flex items-center gap-1 text-xs font-semibold text-neon-green bg-neon-green/10 px-2 py-0.5 rounded-full">
                      <span class="w-1.5 h-1.5 rounded-full bg-neon-green"></span>Ingresada
                    </span>
                  }
                </h3>
                <p class="text-xs text-gray-400 mt-1">
                  @if (detalleCompra()!.estadoOrigen === 'PANEL') {
                    <span class="text-neon-cyan" title="Lo cambiamos desde el panel, no la planilla">estado puesto a mano</span> ·
                  }
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
                <div class="mb-4 flex items-center gap-2 text-xs text-amber-400 bg-amber-400/5 border border-amber-400/20 rounded-xl px-4 py-2.5">
                  <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span>La planilla todavía la marca EN TRÁNSITO. Si la mercadería ya está en el depósito podés
                    ubicarla e ingresarla igual: queda anotado que el estado lo pusimos nosotros, y la planilla no
                    lo va a marcar como conflicto después.</span>
                </div>
              }

              @if (puedeUbicar()) {
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
                          @if (puedeUbicar()) {
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

            <!-- Footer modal: ingresar, revertir, o corregir el estado a mano -->
            <div class="p-5 border-t border-dark-border shrink-0">
              @if (errorIngreso()) {
                <div class="mb-3 text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded-lg px-3 py-2">
                  {{ errorIngreso() }}
                </div>
              }
              <div class="flex flex-wrap items-center justify-between gap-3">
                @if (detalleCompra()!.estado === 'INGRESADA') {
                  <div class="text-xs text-gray-400 max-w-md">
                    El stock de esta compra ya está cargado. Revertirlo lo descuenta de las mismas ubicaciones
                    en las que entró; si esa mercadería ya no está, no se revierte nada.
                  </div>
                  <button (click)="revertirIngreso()" [disabled]="cambiandoEstado()"
                    class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-500/15 text-red-400 border border-red-500/40 hover:bg-red-500/25 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-default whitespace-nowrap">
                    {{ cambiandoEstado() ? 'Revirtiendo...' : 'Revertir ingreso' }}
                  </button>
                } @else {
                  <div class="text-xs text-gray-400">
                    {{ lineasAsignadas() }}/{{ detalleCompra()!.lineas.length }} líneas con ubicación
                  </div>
                  <div class="flex flex-wrap items-center gap-2">
                    @if (detalleCompra()!.estado === 'EN_TRANSITO') {
                      <button (click)="cambiarEstado('POR_UBICAR')" [disabled]="cambiandoEstado()"
                        class="px-4 py-2.5 rounded-xl font-medium text-sm text-gray-300 border border-dark-border hover:bg-dark-surface transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-default whitespace-nowrap"
                        title="Marcarla como llegada sin esperar a que la planilla lo diga">
                        Marcar como llegada
                      </button>
                    } @else {
                      <button (click)="cambiarEstado('EN_TRANSITO')" [disabled]="cambiandoEstado()"
                        class="px-4 py-2.5 rounded-xl font-medium text-sm text-gray-300 border border-dark-border hover:bg-dark-surface transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-default whitespace-nowrap"
                        title="Volverla a EN TRÁNSITO: no toca el stock, porque todavía no se cargó">
                        Volver a EN TRÁNSITO
                      </button>
                    }
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
                        Ingresar al stock
                      }
                    </button>
                  </div>
                }
              </div>
            </div>
          </div>
        </div>
      }

      <!-- Modal importados: lineas que llegaron sin codigo -->
      @if (importadosAbierto()) {
        <div class="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-4" (click)="cerrarImportados()">
          <div class="glass-panel w-full max-w-4xl max-h-[90vh] rounded-2xl border border-dark-border shadow-neon flex flex-col" (click)="$event.stopPropagation()">
            <div class="flex items-start justify-between gap-4 p-5 border-b border-dark-border shrink-0">
              <div>
                <h3 class="text-lg font-bold text-white">Importados sin catalogar</h3>
                <p class="text-xs text-gray-400 mt-1 max-w-2xl">
                  Piezas que llegaron sin código en la planilla: pedidos puntuales de clientes, que no cargan stock.
                  Si se van a volver a pedir, dalas de alta en el catálogo o asocialas a una que ya diste de alta.
                </p>
              </div>
              <button (click)="cerrarImportados()" aria-label="Cerrar"
                class="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-dark-surface transition-colors cursor-pointer">
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div class="flex-1 overflow-y-auto p-5 space-y-3">
              @if (avisoImportado()) {
                <div class="flex items-center gap-2 text-xs text-neon-green bg-neon-green/5 border border-neon-green/20 rounded-xl px-4 py-2.5">
                  <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                  </svg>
                  <span>{{ avisoImportado() }}</span>
                </div>
              }

              @if (cargandoImportados()) {
                <div class="py-10 text-center text-gray-400 font-mono text-sm">Cargando importados...</div>
              } @else if (importados().length === 0) {
                <div class="py-10 text-center text-gray-500 text-sm">No hay importados pendientes.</div>
              } @else {
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="border-b border-dark-border">
                        <th class="px-3 py-2 text-left text-xs font-semibold text-gray-400 uppercase">Factura</th>
                        <th class="px-3 py-2 text-left text-xs font-semibold text-gray-400 uppercase hidden sm:table-cell">Fecha</th>
                        <th class="px-3 py-2 text-left text-xs font-semibold text-gray-400 uppercase">Descripción</th>
                        <th class="px-3 py-2 text-center text-xs font-semibold text-gray-400 uppercase">Cant.</th>
                        <th class="px-3 py-2 text-center text-xs font-semibold text-gray-400 uppercase">Compra</th>
                        <th class="px-3 py-2"></th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (i of importados(); track i.lineaId) {
                        <tr [class]="resolviendo()?.lineaId === i.lineaId ? 'bg-neon-purple/10' : 'border-b border-dark-border/30'">
                          <td class="px-3 py-2 font-mono text-white text-xs">{{ i.factura }}</td>
                          <td class="px-3 py-2 text-gray-300 text-xs hidden sm:table-cell">{{ i.fechaFactura | date:'dd/MM/yyyy' }}</td>
                          <td class="px-3 py-2 text-gray-200 text-xs whitespace-normal break-words max-w-xs">{{ i.descripcion || '—' }}</td>
                          <td class="px-3 py-2 text-center text-white font-semibold">{{ i.cantidad }}</td>
                          <td class="px-3 py-2 text-center">
                            @if (i.estadoCompra === 'EN_TRANSITO') {
                              <span class="inline-flex items-center gap-1.5 text-xs text-amber-400" title="En tránsito">
                                <span class="w-2 h-2 rounded-full bg-amber-400"></span>Tránsito
                              </span>
                            } @else if (i.estadoCompra === 'POR_UBICAR') {
                              <span class="inline-flex items-center gap-1.5 text-xs text-neon-cyan" title="Por ubicar">
                                <span class="w-2 h-2 rounded-full bg-neon-cyan"></span>Por ubicar
                              </span>
                            } @else {
                              <span class="inline-flex items-center gap-1.5 text-xs text-neon-green" title="Ingresada">
                                <span class="w-2 h-2 rounded-full bg-neon-green"></span>Ingresada
                              </span>
                            }
                          </td>
                          <td class="px-3 py-2 text-right">
                            @if (resolviendo()?.lineaId !== i.lineaId) {
                              <button (click)="empezarResolver(i)"
                                class="px-3 py-1.5 rounded-lg text-xs font-semibold text-neon-purple-light border border-neon-purple/40 hover:bg-neon-purple/15 transition-colors cursor-pointer whitespace-nowrap">
                                Resolver
                              </button>
                            }
                          </td>
                        </tr>
                        @if (resolviendo()?.lineaId === i.lineaId) {
                          <tr class="bg-neon-purple/10 border-b border-dark-border/30">
                            <td colspan="6" class="px-3 pb-4 pt-1">
                              <div class="space-y-3">
                                <div class="flex items-center gap-1 bg-dark-surface/60 rounded-lg p-1 w-fit">
                                  <button (click)="cambiarModo('CREAR')"
                                    [class]="modoResolver() === 'CREAR'
                                      ? 'px-3 py-1.5 rounded-md text-xs font-semibold bg-white/10 text-white cursor-pointer'
                                      : 'px-3 py-1.5 rounded-md text-xs text-gray-400 hover:text-white cursor-pointer'">
                                    Crear producto nuevo
                                  </button>
                                  <button (click)="cambiarModo('VINCULAR')"
                                    [class]="modoResolver() === 'VINCULAR'
                                      ? 'px-3 py-1.5 rounded-md text-xs font-semibold bg-white/10 text-white cursor-pointer'
                                      : 'px-3 py-1.5 rounded-md text-xs text-gray-400 hover:text-white cursor-pointer'">
                                    Ya está en el catálogo
                                  </button>
                                </div>

                                @if (modoResolver() === 'CREAR') {
                                  <div class="grid gap-3 sm:grid-cols-[190px_1fr]">
                                    <label class="flex flex-col gap-1">
                                      <span class="text-[11px] uppercase tracking-wider text-gray-400">SKU</span>
                                      <input [(ngModel)]="skuNuevo" placeholder="IMP-00001"
                                        class="px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white font-mono text-sm uppercase focus:outline-none focus:border-neon-purple" />
                                      <span class="text-[10px] text-gray-500">Propuesto: no lo usa ningún otro producto.</span>
                                    </label>
                                    <label class="flex flex-col gap-1">
                                      <span class="text-[11px] uppercase tracking-wider text-gray-400">Descripción</span>
                                      <input [(ngModel)]="descripcionNueva"
                                        class="px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-purple" />
                                    </label>
                                  </div>
                                } @else {
                                  <div class="space-y-2">
                                    <div class="flex gap-2">
                                      <input [(ngModel)]="busquedaProducto" (keydown.enter)="buscarProducto()"
                                        placeholder="Buscar por SKU o descripción (por ejemplo IMP- o biela)"
                                        class="flex-1 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm placeholder-gray-500 focus:outline-none focus:border-neon-purple" />
                                      <button (click)="buscarProducto()"
                                        class="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-white/10 hover:bg-white/15 transition-colors cursor-pointer">
                                        Buscar
                                      </button>
                                    </div>
                                    @if (buscandoProducto()) {
                                      <div class="text-xs text-gray-400 font-mono">Buscando...</div>
                                    } @else if (resultadosProducto().length > 0) {
                                      <div class="max-h-48 overflow-y-auto rounded-lg border border-dark-border divide-y divide-dark-border/40">
                                        @for (p of resultadosProducto(); track p.id) {
                                          <button (click)="productoElegido.set(p)"
                                            [class]="productoElegido()?.id === p.id
                                              ? 'w-full text-left px-3 py-2 flex items-baseline gap-3 bg-neon-purple/20 cursor-pointer'
                                              : 'w-full text-left px-3 py-2 flex items-baseline gap-3 hover:bg-dark-surface cursor-pointer'">
                                            <span class="font-mono text-xs text-white shrink-0">{{ p.sku || 'sin SKU' }}</span>
                                            <span class="text-xs text-gray-300 truncate">{{ p.descripcion }}</span>
                                            @if (p.marcaNombre) {
                                              <span class="text-[10px] text-gray-500 shrink-0">{{ p.marcaNombre }}</span>
                                            }
                                          </button>
                                        }
                                      </div>
                                    } @else if (buscoProducto()) {
                                      <div class="text-xs text-gray-500">No hay productos que coincidan.</div>
                                    }
                                  </div>
                                }

                                @if (resolviendo()!.estadoCompra === 'INGRESADA') {
                                  <label class="flex flex-col gap-1 max-w-sm">
                                    <span class="text-[11px] uppercase tracking-wider text-gray-400">Ubicación</span>
                                    <select [(ngModel)]="ubicacionImportado"
                                      class="px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-purple">
                                      <option [ngValue]="null">— elegí dónde queda —</option>
                                      @for (u of ubicaciones(); track u.id) {
                                        <option [ngValue]="u.id">{{ u.path || u.codigo }}</option>
                                      }
                                    </select>
                                    <span class="text-[10px] text-gray-500">La compra ya ingresó: el stock de esta línea se carga ahora.</span>
                                  </label>
                                } @else {
                                  <p class="text-[11px] text-gray-500">
                                    El stock entra cuando se ingrese la compra, con la ubicación que elijas ahí.
                                  </p>
                                }

                                @if (errorImportado()) {
                                  <div class="text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded-lg px-3 py-2">
                                    {{ errorImportado() }}
                                  </div>
                                }

                                <div class="flex items-center justify-end gap-2">
                                  <button (click)="cancelarResolver()"
                                    class="px-4 py-2 rounded-lg text-xs text-gray-300 hover:bg-dark-surface transition-colors cursor-pointer">
                                    Cancelar
                                  </button>
                                  <button (click)="guardarImportado()" [disabled]="!puedeGuardarImportado() || guardandoImportado()"
                                    class="px-5 py-2 rounded-lg text-xs font-semibold bg-neon-purple/25 text-neon-purple-light border border-neon-purple/50 hover:bg-neon-purple/35 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-default">
                                    {{ guardandoImportado() ? 'Guardando...' : textoBotonImportado() }}
                                  </button>
                                </div>
                              </div>
                            </td>
                          </tr>
                        }
                      }
                    </tbody>
                  </table>
                </div>

                @if (importadosPaginas() > 1) {
                  <div class="flex items-center justify-between pt-1">
                    <span class="text-xs text-gray-500">{{ importadosTotal() }} pendientes</span>
                    <div class="flex items-center gap-1">
                      <button (click)="cargarImportados(importadosPagina() - 1)" [disabled]="importadosPagina() === 0"
                        class="px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default text-gray-300 hover:bg-dark-surface">
                        Ant.
                      </button>
                      <span class="px-3 py-1.5 text-xs text-gray-400">{{ importadosPagina() + 1 }} / {{ importadosPaginas() }}</span>
                      <button (click)="cargarImportados(importadosPagina() + 1)" [disabled]="importadosPagina() >= importadosPaginas() - 1"
                        class="px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default text-gray-300 hover:bg-dark-surface">
                        Sig.
                      </button>
                    </div>
                  </div>
                }
              }
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class Compras implements OnInit {
  private compraService = inject(CompraService);
  private ubicacionService = inject(UbicacionService);
  private productoService = inject(ProductoService);

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
  cambiandoEstado = signal(false);
  errorIngreso = signal('');

  // --- Importados: lineas que llegaron sin codigo (pedidos puntuales) ---
  importadosAbierto = signal(false);
  importados = signal<ImportadoPendiente[]>([]);
  importadosTotal = signal(0);
  importadosPagina = signal(0);
  importadosPaginas = signal(0);
  cargandoImportados = signal(false);
  resolviendo = signal<ImportadoPendiente | null>(null);
  modoResolver = signal<'CREAR' | 'VINCULAR'>('CREAR');
  skuNuevo = '';
  descripcionNueva = '';
  ubicacionImportado: number | null = null;
  busquedaProducto = '';
  resultadosProducto = signal<ProductoListItem[]>([]);
  buscandoProducto = signal(false);
  buscoProducto = signal(false);
  productoElegido = signal<ProductoListItem | null>(null);
  guardandoImportado = signal(false);
  errorImportado = signal('');
  avisoImportado = signal('');

  ngOnInit(): void {
    this.cargar();
    this.contarImportados();
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

  /** Se puede asignar ubicacion mientras el stock no este cargado, diga lo que diga la planilla. */
  puedeUbicar(): boolean {
    const compra = this.detalleCompra();
    return !!compra && compra.estado !== 'INGRESADA';
  }

  /**
   * Cambia el estado sin esperar a la planilla. El backend anota que salio del panel, asi que
   * el proximo envio del flujo no lo trata como una pelea con la planilla.
   */
  cambiarEstado(destino: 'EN_TRANSITO' | 'POR_UBICAR'): void {
    const compra = this.detalleCompra();
    if (!compra || this.cambiandoEstado()) return;

    this.cambiandoEstado.set(true);
    this.errorIngreso.set('');
    this.compraService.cambiarEstado(compra.id, destino).subscribe({
      next: (actualizada) => {
        this.detalleCompra.set(actualizada);
        this.ubicacionPorLinea = {};
        this.cambiandoEstado.set(false);
        this.cargar();
      },
      error: (err) => {
        this.errorIngreso.set(err.error?.message || err.error?.error || 'No se pudo cambiar el estado');
        this.cambiandoEstado.set(false);
      },
    });
  }

  /** Deshace un ingreso: descuenta el stock que cargo. Se pregunta antes porque toca stock. */
  revertirIngreso(): void {
    const compra = this.detalleCompra();
    if (!compra) return;
    const ok = confirm(
      `Revertir el ingreso de la factura ${compra.numeroFactura} descuenta de las ubicaciones `
      + 'todo lo que esta compra cargó. ¿Seguir?');
    if (ok) this.cambiarEstado('POR_UBICAR');
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
        this.errorIngreso.set(err.error?.message || err.error?.error || 'No se pudo ingresar al stock');
        this.ingresando.set(false);
      },
    });
  }

  // --- Importados ---

  /** Solo el total, para el contador del boton. */
  contarImportados(): void {
    this.compraService.importados(0, 1).subscribe({
      next: (res) => this.importadosTotal.set(res.totalElements),
      error: () => this.importadosTotal.set(0),
    });
  }

  abrirImportados(): void {
    this.importadosAbierto.set(true);
    this.avisoImportado.set('');
    this.cancelarResolver();
    this.cargarImportados(0);
  }

  cerrarImportados(): void {
    this.importadosAbierto.set(false);
    this.cancelarResolver();
  }

  cargarImportados(pagina: number): void {
    this.cargandoImportados.set(true);
    this.compraService.importados(pagina, 20).subscribe({
      next: (res) => {
        this.importados.set(res.content);
        this.importadosTotal.set(res.totalElements);
        this.importadosPaginas.set(res.totalPages);
        this.importadosPagina.set(pagina);
        this.cargandoImportados.set(false);
      },
      error: () => this.cargandoImportados.set(false),
    });
  }

  empezarResolver(importado: ImportadoPendiente): void {
    this.resolviendo.set(importado);
    this.modoResolver.set('CREAR');
    this.descripcionNueva = importado.descripcion ?? '';
    this.skuNuevo = '';
    this.ubicacionImportado = null;
    this.busquedaProducto = importado.descripcion ?? '';
    this.resultadosProducto.set([]);
    this.buscoProducto.set(false);
    this.productoElegido.set(null);
    this.errorImportado.set('');
    this.avisoImportado.set('');
    // El SKU propuesto es el proximo IMP- libre; se puede cambiar, y el backend vuelve a
    // controlar que no lo use ningun otro producto al guardar.
    this.compraService.skuSugerido().subscribe({
      next: (res) => {
        if (!this.skuNuevo) this.skuNuevo = res.sku;
      },
    });
  }

  cancelarResolver(): void {
    this.resolviendo.set(null);
    this.errorImportado.set('');
  }

  cambiarModo(modo: 'CREAR' | 'VINCULAR'): void {
    this.modoResolver.set(modo);
    this.errorImportado.set('');
  }

  buscarProducto(): void {
    const q = this.busquedaProducto.trim();
    if (q.length < 2) return;
    this.buscandoProducto.set(true);
    this.productoElegido.set(null);
    this.productoService.buscarTexto(q, 0, 8).subscribe({
      next: (res) => {
        this.resultadosProducto.set(res.content);
        this.buscoProducto.set(true);
        this.buscandoProducto.set(false);
      },
      error: () => this.buscandoProducto.set(false),
    });
  }

  puedeGuardarImportado(): boolean {
    const importado = this.resolviendo();
    if (!importado) return false;
    if (importado.estadoCompra === 'INGRESADA' && this.ubicacionImportado == null) return false;
    return this.modoResolver() === 'CREAR'
      ? this.skuNuevo.trim().length > 0 && this.descripcionNueva.trim().length > 0
      : this.productoElegido() != null;
  }

  textoBotonImportado(): string {
    const cargaAhora = this.resolviendo()?.estadoCompra === 'INGRESADA';
    if (this.modoResolver() === 'CREAR') {
      return cargaAhora ? 'Crear y cargar stock' : 'Crear producto';
    }
    return cargaAhora ? 'Asociar y cargar stock' : 'Asociar';
  }

  guardarImportado(): void {
    const importado = this.resolviendo();
    if (!importado || !this.puedeGuardarImportado()) return;

    this.guardandoImportado.set(true);
    this.errorImportado.set('');
    const llamada = this.modoResolver() === 'CREAR'
      ? this.compraService.darDeAltaImportado(importado.lineaId, this.skuNuevo.trim(),
          this.descripcionNueva.trim(), this.ubicacionImportado)
      : this.compraService.vincularImportado(importado.lineaId, this.productoElegido()!.id,
          this.ubicacionImportado);

    llamada.subscribe({
      next: (res) => {
        this.guardandoImportado.set(false);
        this.avisoImportado.set(res.mensaje);
        this.resolviendo.set(null);
        this.cargarImportados(this.importadosPagina());
        this.cargar();
      },
      error: (err) => {
        this.guardandoImportado.set(false);
        this.errorImportado.set(err.error?.message || err.error?.error || 'No se pudo guardar');
      },
    });
  }

  private inicializarUbicaciones(compra: Compra): void {
    if (compra.estado !== 'POR_UBICAR') return;
    this.ubicacionPorLinea = {};
    for (const l of compra.lineas) {
      if (l.ubicacionSugeridaId) {
        this.ubicacionPorLinea[l.id] = l.ubicacionSugeridaId;
      }
    }
  }
}
