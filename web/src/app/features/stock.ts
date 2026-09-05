import { Component, DestroyRef, OnInit, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, interval, of, switchMap, tap } from 'rxjs';

import { Movimiento, ProductoListItem, StockLinea, StockResumen, Ubicacion } from '../core/models';
import { StockService } from '../core/stock.service';
import { ProductoService } from '../core/producto.service';
import { UbicacionService } from '../core/ubicacion.service';

type Vista = 'productos' | 'historial';
type OpTab = 'entrada' | 'salida' | 'transferencia' | 'ajuste';

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <div class="space-y-6 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="border-b border-dark-border pb-5">
        <h2 class="text-2xl md:text-3xl font-extrabold tracking-tight text-white flex flex-wrap items-center gap-3">
          <span>Control de Stock</span>
          <span class="text-xs font-mono bg-neon-purple/20 text-neon-purple border border-neon-purple/30 px-2 py-0.5 rounded-full uppercase">
            INVENTARIO
          </span>
        </h2>
        <p class="text-sm text-gray-400 mt-1 flex flex-wrap items-center gap-x-4 gap-y-1">
          <span>Gestioná entradas, salidas, transferencias y ajustes de inventario.</span>
          <span class="inline-flex items-center gap-1.5 text-xs text-gray-500">
            <span class="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse"></span>
            Se actualiza cada 1m 10s
            <button (click)="cargarLista()" class="text-neon-cyan hover:underline cursor-pointer ml-1">Actualizar ahora</button>
          </span>
        </p>
      </div>

      <!-- Tabs principales -->
      <div class="flex items-center gap-1 bg-dark-surface/60 rounded-xl p-1 w-fit overflow-x-auto">
        <button (click)="cambiarVista('productos')"
          [class]="vista() === 'productos'
            ? 'px-5 py-2 rounded-lg text-sm font-semibold bg-neon-green/15 text-neon-green border border-neon-green/30 transition-all cursor-pointer whitespace-nowrap'
            : 'px-5 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white transition-all cursor-pointer whitespace-nowrap'">
          Productos
        </button>
        <button (click)="cambiarVista('historial')"
          [class]="vista() === 'historial'
            ? 'px-5 py-2 rounded-lg text-sm font-semibold bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/40 transition-all cursor-pointer whitespace-nowrap'
            : 'px-5 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white transition-all cursor-pointer whitespace-nowrap'">
          Historial
        </button>
      </div>

      <!-- ==================== PRODUCTOS ==================== -->
      @if (vista() === 'productos') {
        <!-- Buscador -->
        <div class="flex flex-col sm:flex-row gap-3 items-end">
          <div class="flex-1">
            <input [(ngModel)]="filtroTexto" (keyup.enter)="buscarEnLista()" (input)="onFiltroInput()" type="text"
              placeholder="Buscar producto por nombre, SKU o marca..."
              class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-green text-sm" />
          </div>
          @if (filtroTexto) {
            <button (click)="limpiarFiltro()" class="px-4 py-2.5 rounded-xl text-xs font-semibold text-gray-400 bg-dark-surface border border-dark-border hover:text-white cursor-pointer transition-colors shrink-0">
              Limpiar
            </button>
          }
        </div>

        @if (listaError()) {
          <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-sm flex items-center gap-2">
            <svg class="w-5 h-5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
            </svg>
            <span>{{ listaError() }}</span>
            <button (click)="cargarLista()" class="ml-auto px-3 py-1 rounded-lg text-xs font-semibold neon-button-secondary cursor-pointer shrink-0">Reintentar</button>
          </div>
        }

        @if (cargandoLista()) {
          <div class="text-center py-12 text-gray-500 text-sm">Cargando productos...</div>
        } @else if (listaProductos().length === 0) {
          <div class="glass-panel rounded-2xl p-8 border border-dark-border text-center">
            @if (filtroTexto) {
              <p class="text-gray-400">No se encontraron productos para "{{ filtroTexto }}".</p>
            } @else {
              <p class="text-gray-400">No hay productos registrados en el catálogo.</p>
            }
          </div>
        } @else {
          <!-- Tabla unificada de productos -->
          <div class="glass-panel rounded-2xl border border-dark-border shadow-card overflow-hidden">
            <div class="overflow-x-auto">
              <table class="w-full text-left text-sm border-collapse min-w-[900px]">
                <thead>
                  <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                    <th class="py-3 px-4">SKU</th>
                    <th class="py-3 px-4">Descripción</th>
                    <th class="py-3 px-4">Marca</th>
                    <th class="py-3 px-4 text-right">Stock total</th>
                    <th class="py-3 px-4 text-right">P. Costo</th>
                    <th class="py-3 px-4 text-right">P. Venta</th>
                    <th class="py-3 px-4">Ubicaciones</th>
                    <th class="py-3 px-4 text-center">Acciones</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-dark-border/50">
                  @for (p of listaProductos(); track p.id) {
                    <tr class="hover:bg-dark-surface/40 transition-colors"
                        [class.bg-neon-cyan/5]="productoSel()?.id === p.id">
                      <td class="py-3 px-4 font-mono font-bold text-neon-cyan text-sm whitespace-nowrap">{{ p.sku || '—' }}</td>
                      <td class="py-3 px-4 text-white text-sm max-w-[380px] whitespace-normal break-words align-top">{{ p.descripcion }}</td>
                      <td class="py-3 px-4">
                        @if (p.marcaNombre) {
                          <span class="neon-badge-purple px-2 py-0.5 rounded text-[11px] font-mono">{{ p.marcaNombre }}</span>
                        } @else {
                          <span class="text-gray-500 text-xs">—</span>
                        }
                      </td>
                      <td class="py-3 px-4 font-mono font-extrabold text-right"
                          [class]="p.stockTotal > 0 ? 'text-neon-green' : 'text-gray-500'">
                        {{ p.stockTotal }} u.
                      </td>
                      <td class="py-3 px-4 font-mono text-right text-sm"
                          [class]="p.precioCosto ? 'text-gray-300' : 'text-gray-600'">
                        {{ p.precioCosto ? '$' + (p.precioCosto | number:'1.2-2') : '—' }}
                      </td>
                      <td class="py-3 px-4 font-mono font-bold text-right text-sm"
                          [class]="p.precioVenta ? 'text-amber-400' : 'text-gray-600'">
                        {{ p.precioVenta ? '$' + (p.precioVenta | number:'1.2-2') : '—' }}
                      </td>
                      <td class="py-3 px-4 text-xs text-gray-300">
                        @if (p.ubicaciones.length > 0) {
                          @for (u of p.ubicaciones; track u.codigo; let i = $index) {
                            @if (i < 3) {
                              <span class="font-mono text-neon-purple">{{ u.codigo }}</span><span class="text-gray-500">({{ u.cantidad }})</span>@if (i < p.ubicaciones.length - 1 && i < 2) {<span class="text-gray-600">, </span>}
                            }
                          }
                          @if (p.ubicaciones.length > 3) {
                            <span class="text-gray-500"> +{{ p.ubicaciones.length - 3 }}</span>
                          }
                        } @else {
                          <span class="text-gray-500">Sin ubicación</span>
                        }
                      </td>
                      <td class="py-3 px-4 text-center whitespace-nowrap">
                        <button (click)="seleccionar(p)" class="text-xs font-semibold cursor-pointer"
                          [class]="p.stockTotal > 0 ? 'text-neon-cyan hover:underline' : 'text-amber-400 hover:underline'">
                          {{ p.stockTotal > 0 ? 'Gestionar' : 'Cargar stock' }}
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            <!-- Paginación -->
            @if (totalPaginas() > 1) {
              <div class="flex items-center justify-between px-4 py-3 border-t border-dark-border bg-dark-surface/20">
                <span class="text-xs text-gray-500">
                  Página {{ paginaActual() + 1 }} de {{ totalPaginas() }} · {{ totalElementos() }} productos
                </span>
                <div class="flex gap-2">
                  <button [disabled]="paginaActual() === 0" (click)="irPagina(paginaActual() - 1)"
                    class="px-3 py-1.5 rounded-lg text-xs font-semibold bg-dark-surface border border-dark-border text-gray-300 hover:border-neon-cyan disabled:opacity-30 cursor-pointer transition-colors">
                    ← Anterior
                  </button>
                  <button [disabled]="paginaActual() >= totalPaginas() - 1" (click)="irPagina(paginaActual() + 1)"
                    class="px-3 py-1.5 rounded-lg text-xs font-semibold bg-dark-surface border border-dark-border text-gray-300 hover:border-neon-cyan disabled:opacity-30 cursor-pointer transition-colors">
                    Siguiente →
                  </button>
                </div>
              </div>
            }
          </div>
        }

        @if (productoSel(); as p) {
          <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-2 sm:p-4" (click)="cerrarModalCarga()">
            <div class="glass-panel w-full max-w-4xl max-h-[90vh] overflow-y-auto p-4 sm:p-6 rounded-2xl border border-neon-cyan/40 shadow-neon animate-fade-in space-y-5" (click)="$event.stopPropagation()">
              <!-- Header -->
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div class="space-y-1 flex-1 min-w-0">
                  <p class="text-xs uppercase tracking-wider text-gray-400">Gestionar stock</p>
                  <p class="text-lg font-bold text-white break-words">{{ p.descripcion }}</p>
                  <p class="text-sm font-mono text-neon-cyan">
                    SKU {{ p.sku || '—' }}
                    @if (p.marcaNombre) { <span class="text-gray-400"> · {{ p.marcaNombre }}</span> }
                  </p>
                </div>
                <div class="flex items-start gap-2 shrink-0">
                  @if (resumen(); as r) {
                    <div class="flex gap-2">
                      <div class="text-center px-3 py-1.5 rounded-xl bg-dark-surface/60 border border-dark-border">
                        <p class="text-lg font-extrabold text-neon-green font-mono">{{ r.total }}</p>
                        <p class="text-[10px] uppercase text-gray-400 font-semibold">Unidades</p>
                      </div>
                      <div class="text-center px-3 py-1.5 rounded-xl bg-dark-surface/60 border border-dark-border">
                        <p class="text-lg font-extrabold text-neon-purple font-mono">{{ r.ubicaciones.length }}</p>
                        <p class="text-[10px] uppercase text-gray-400 font-semibold">Ubicaciones</p>
                      </div>
                    </div>
                  }
                  <button (click)="cerrarModalCarga()"
                    class="text-gray-500 hover:text-white cursor-pointer p-1 transition-colors">
                    <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </div>

              <!-- Tabs de operaciones -->
              <div class="rounded-2xl border border-dark-border overflow-hidden">
                <div class="flex border-b border-dark-border overflow-x-auto">
                  @for (t of opTabs; track t.key) {
                    <button (click)="opTab.set(t.key)"
                      [class]="opTab() === t.key
                        ? 'flex items-center gap-2 px-5 py-3 text-sm font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ' + t.activeClass
                        : 'flex items-center gap-2 px-5 py-3 text-sm font-medium text-gray-400 hover:text-white border-b-2 border-transparent transition-all cursor-pointer whitespace-nowrap'">
                      <span>{{ t.icon }}</span>
                      {{ t.label }}
                    </button>
                  }
                </div>
                <p class="px-5 py-2 text-xs text-gray-500 bg-dark-surface/30 border-b border-dark-border">{{ opTabActual().desc }}</p>

              <div class="p-4 md:p-6 space-y-4">
                <!-- ENTRADA -->
                @if (opTab() === 'entrada') {
                  <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Ubicación destino</label>
                      <select [(ngModel)]="opUbicacionId"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm">
                        <option [ngValue]="null" disabled>Elegí ubicación…</option>
                        @for (u of ubicaciones(); track u.id) {
                          <option [ngValue]="u.id">{{ u.codigo }}{{ u.descripcion ? ' — ' + u.descripcion : '' }}</option>
                        }
                      </select>
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Cantidad</label>
                      <input [(ngModel)]="opCantidad" type="number" min="1" placeholder="0"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Motivo (opcional)</label>
                      <input [(ngModel)]="opMotivo" type="text" placeholder="Ej: ingreso de mercadería"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                  </div>
                }

                <!-- SALIDA -->
                @if (opTab() === 'salida') {
                  <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Ubicación origen</label>
                      <select [(ngModel)]="opUbicacionId"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm">
                        <option [ngValue]="null" disabled>Elegí ubicación…</option>
                        @for (u of ubicacionesConStock(); track u.ubicacionId) {
                          <option [ngValue]="u.ubicacionId">{{ u.ubicacionPath }} ({{ u.cantidad }} u.)</option>
                        }
                      </select>
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Cantidad</label>
                      <input [(ngModel)]="opCantidad" type="number" min="1" placeholder="0"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Motivo (opcional)</label>
                      <input [(ngModel)]="opMotivo" type="text" placeholder="Ej: venta, devolución"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                  </div>
                }

                <!-- TRANSFERENCIA -->
                @if (opTab() === 'transferencia') {
                  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Ubicación origen</label>
                      <select [(ngModel)]="opUbicacionId"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm">
                        <option [ngValue]="null" disabled>Origen…</option>
                        @for (u of ubicacionesConStock(); track u.ubicacionId) {
                          <option [ngValue]="u.ubicacionId">{{ u.ubicacionPath }} ({{ u.cantidad }} u.)</option>
                        }
                      </select>
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Ubicación destino</label>
                      <select [(ngModel)]="opUbicacionDestinoId"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm">
                        <option [ngValue]="null" disabled>Destino…</option>
                        @for (u of ubicaciones(); track u.id) {
                          <option [ngValue]="u.id">{{ u.codigo }}{{ u.descripcion ? ' — ' + u.descripcion : '' }}</option>
                        }
                      </select>
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Cantidad</label>
                      <input [(ngModel)]="opCantidad" type="number" min="1" placeholder="0"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Motivo (opcional)</label>
                      <input [(ngModel)]="opMotivo" type="text" placeholder="Ej: reubicación"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                  </div>
                }

                <!-- AJUSTE -->
                @if (opTab() === 'ajuste') {
                  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Ubicación</label>
                      <select [(ngModel)]="opUbicacionId"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm">
                        <option [ngValue]="null" disabled>Elegí ubicación…</option>
                        @for (u of ubicaciones(); track u.id) {
                          <option [ngValue]="u.id">{{ u.codigo }}{{ u.descripcion ? ' — ' + u.descripcion : '' }}</option>
                        }
                      </select>
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Tipo de ajuste</label>
                      <select [(ngModel)]="ajusteTipo"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm">
                        <option value="AJUSTE_POSITIVO">Ajuste positivo (+)</option>
                        <option value="AJUSTE_NEGATIVO">Ajuste negativo (−)</option>
                      </select>
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Cantidad</label>
                      <input [(ngModel)]="opCantidad" type="number" min="1" placeholder="0"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                    <div>
                      <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Motivo (obligatorio)</label>
                      <input [(ngModel)]="opMotivo" type="text" placeholder="Ej: rotura, merma"
                        class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm" />
                    </div>
                  </div>
                }

                @if (opError()) {
                  <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ opError() }}</div>
                }
                @if (opOk()) {
                  <div class="p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-xs">{{ opOk() }}</div>
                }

                <div class="flex justify-end">
                  <button [disabled]="guardandoOp()" (click)="ejecutarOperacion()"
                    class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50">
                    {{ guardandoOp() ? 'Procesando...' : opTabActual().action }}
                  </button>
                </div>
              </div>
            </div>

            <!-- Stock actual por ubicación -->
            @if (resumen(); as r) {
              <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card space-y-4">
                <div class="flex items-center justify-between border-b border-dark-border pb-3">
                  <h3 class="text-lg font-bold text-white">Stock por ubicación</h3>
                  <span class="text-xl font-extrabold text-neon-green font-mono">{{ r.total }} u.</span>
                </div>
                @if (r.ubicaciones.length === 0) {
                  <p class="text-xs text-gray-500 py-4 text-center">Sin existencias.</p>
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
                                <input [(ngModel)]="editCantidad" type="number" min="0"
                                  (keyup.enter)="guardarCantidad(l.ubicacionId)"
                                  class="w-24 px-2.5 py-1.5 bg-dark-surface border border-neon-cyan/50 rounded-lg text-white font-mono text-right text-sm focus:outline-none focus:border-neon-cyan" />
                              </td>
                              <td class="py-3 px-4 text-right whitespace-nowrap">
                                <button [disabled]="filaGuardando()" (click)="guardarCantidad(l.ubicacionId)"
                                  class="text-xs font-semibold text-neon-green hover:underline cursor-pointer disabled:opacity-50">Guardar</button>
                                <button (click)="cancelarEdicionCantidad()"
                                  class="ml-3 text-xs font-semibold text-gray-400 hover:underline cursor-pointer">Cancelar</button>
                              </td>
                            } @else {
                              <td class="py-3 px-4 font-mono font-bold text-neon-green text-right">{{ l.cantidad }} u.</td>
                              <td class="py-3 px-4 text-right whitespace-nowrap">
                                <button (click)="iniciarEdicionCantidad(l)"
                                  class="text-xs font-semibold text-neon-cyan hover:underline cursor-pointer">Editar</button>
                                <button [disabled]="filaGuardando()" (click)="pedirEliminarLinea(l)"
                                  class="ml-3 text-xs font-semibold text-red-400 hover:underline cursor-pointer disabled:opacity-50">Eliminar</button>
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

              <!-- Footer -->
              <div class="flex justify-end pt-2 border-t border-dark-border">
                <button (click)="cerrarModalCarga()"
                  class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">
                  Cerrar
                </button>
              </div>
            </div>
          </div>
        }
      }

      <!-- ==================== HISTORIAL ==================== -->
      @if (vista() === 'historial') {
        <!-- Buscador -->
        <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card space-y-4">
          <div class="flex flex-col sm:flex-row gap-3 items-end">
            <div class="flex-1">
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Producto</label>
              <input [(ngModel)]="q" (keyup.enter)="buscar()" (input)="onQueryInput()" type="text"
                placeholder="Buscá un producto para ver su historial..."
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-purple text-sm" />
            </div>
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Filtrar tipo</label>
              <select [ngModel]="filtroTipo()" (ngModelChange)="filtroTipo.set($event)"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-cyan text-sm">
                <option value="">Todos</option>
                <option value="ENTRADA">Entradas</option>
                <option value="SALIDA">Salidas</option>
                <option value="AJUSTE_POSITIVO">Ajuste +</option>
                <option value="AJUSTE_NEGATIVO">Ajuste −</option>
                <option value="TRANSFERENCIA">Transferencias</option>
              </select>
            </div>
            <button (click)="buscar()" class="px-5 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer shrink-0">
              Buscar
            </button>
          </div>

          @if (historialError()) {
            <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-sm flex items-center gap-2">
              <svg class="w-5 h-5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
              </svg>
              <span>{{ historialError() }}</span>
              <button (click)="buscar()" class="ml-auto px-3 py-1 rounded-lg text-xs font-semibold neon-button-secondary cursor-pointer shrink-0">Reintentar</button>
            </div>
          }

          @if (buscando()) {
            <p class="text-xs text-gray-500 font-mono">Buscando...</p>
          } @else if (resultados().length > 0 && !productoSel()) {
            <div class="divide-y divide-dark-border/50 border border-dark-border rounded-xl overflow-hidden">
              @for (p of resultados(); track p.id) {
                <button (click)="seleccionar(p)"
                  class="w-full text-left px-4 py-3 hover:bg-dark-surface/60 transition-colors cursor-pointer flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span class="font-mono font-bold text-neon-cyan text-sm">{{ p.sku || '—' }}</span>
                  <span class="text-gray-200 text-sm">{{ p.descripcion }}</span>
                  @if (p.marcaNombre) {
                    <span class="neon-badge-purple px-2 py-0.5 rounded text-[11px] font-mono">{{ p.marcaNombre }}</span>
                  }
                </button>
              }
            </div>
          }
        </div>

        @if (productoSel(); as p) {
          <div class="flex flex-wrap items-center justify-between gap-3 px-1">
            <div class="flex items-center gap-3">
              <p class="text-sm text-gray-400">Historial de</p>
              <span class="font-semibold text-white">{{ p.descripcion }}</span>
              <span class="font-mono text-neon-cyan text-sm">{{ p.sku || '' }}</span>
            </div>
            <button (click)="deseleccionar()" class="text-xs text-gray-400 hover:text-white cursor-pointer">
              ✕ Cambiar producto
            </button>
          </div>

          <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card space-y-4">
            @if (movimientosFiltrados().length === 0) {
              <div class="py-10 text-center text-gray-500 text-sm">
                @if (filtroTipo()) {
                  No hay movimientos de tipo "{{ tipoLabel(filtroTipo()) }}" para este producto.
                } @else {
                  Sin movimientos registrados para este producto.
                }
              </div>
            } @else {
              <div class="overflow-x-auto">
                <table class="w-full text-left text-sm border-collapse min-w-[700px]">
                  <thead>
                    <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                      <th class="py-3 px-4">Fecha</th>
                      <th class="py-3 px-4">Tipo</th>
                      <th class="py-3 px-4 text-right">Cantidad</th>
                      <th class="py-3 px-4">Ubicación</th>
                      <th class="py-3 px-4">Motivo</th>
                      <th class="py-3 px-4">Referencia</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-dark-border/50">
                    @for (m of movimientosFiltrados(); track m.id) {
                      <tr class="hover:bg-dark-surface/40 transition-colors">
                        <td class="py-3.5 px-4 font-mono text-gray-300 text-xs whitespace-nowrap">
                          {{ m.fecha ? (m.fecha | date:'dd/MM/yyyy HH:mm:ss') : '-' }}
                        </td>
                        <td class="py-3.5 px-4">
                          <span class="px-2.5 py-1 rounded-md text-[11px] font-mono font-semibold" [class]="tipoClass(m.tipo)">
                            {{ tipoLabel(m.tipo) }}
                          </span>
                        </td>
                        <td class="py-3.5 px-4 font-mono font-bold text-right" [class]="cantidadClass(m.tipo)">
                          {{ cantidadPrefix(m.tipo) }}{{ m.cantidad }}
                        </td>
                        <td class="py-3.5 px-4 text-xs text-gray-300">{{ ubicacionInfo(m) }}</td>
                        <td class="py-3.5 px-4 text-gray-400 text-xs">{{ m.motivo ?? '-' }}</td>
                        <td class="py-3.5 px-4 font-mono text-gray-500 text-xs">{{ m.referencia ?? '-' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>

              <div class="flex flex-wrap gap-4 pt-3 border-t border-dark-border">
                <div class="flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full bg-green-400"></span>
                  <span class="text-xs text-gray-400">Entradas: <span class="font-mono font-bold text-white">{{ contarPorTipo('ENTRADA') }}</span></span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full bg-red-400"></span>
                  <span class="text-xs text-gray-400">Salidas: <span class="font-mono font-bold text-white">{{ contarPorTipo('SALIDA') }}</span></span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full bg-cyan-400"></span>
                  <span class="text-xs text-gray-400">Transfer.: <span class="font-mono font-bold text-white">{{ contarPorTipo('TRANSFERENCIA') }}</span></span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full bg-amber-400"></span>
                  <span class="text-xs text-gray-400">Ajustes: <span class="font-mono font-bold text-white">{{ contarPorTipo('AJUSTE_POSITIVO') + contarPorTipo('AJUSTE_NEGATIVO') }}</span></span>
                </div>
              </div>
            }
          </div>
        }
      }
    </div>

    <!-- Modal: confirmar descarte de cambios -->
    @if (mostrarConfirmDescarte()) {
      <div class="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-4" (click)="cancelarDescarte()">
        <div class="glass-panel w-full max-w-sm p-6 rounded-2xl border border-amber-500/40 shadow-neon animate-fade-in" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            ¿Salir sin guardar?
          </h3>
          <p class="text-sm text-gray-300">
            Tenés datos cargados que no se guardaron. ¿Querés descartarlos?
          </p>
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="cancelarDescarte()"
              class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">
              Seguir editando
            </button>
            <button (click)="confirmarDescarte()"
              class="px-5 py-2.5 rounded-xl font-semibold text-sm bg-amber-600 hover:bg-amber-500 text-white transition-colors cursor-pointer">
              Descartar
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Modal: confirmación de eliminación -->
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
            Vas a eliminar la existencia en
            <span class="font-mono font-semibold text-neon-purple">{{ l.ubicacionPath }}</span>
            (<span class="font-mono font-semibold text-neon-green">{{ l.cantidad }} u.</span>).
            ¿Seguro?
          </p>
          @if (stockError()) {
            <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ stockError() }}</div>
          }
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="cancelarEliminarLinea()"
              class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">
              Cancelar
            </button>
            <button [disabled]="filaGuardando()" (click)="confirmarEliminarLinea(l)"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-600 hover:bg-red-500 text-white transition-colors cursor-pointer disabled:opacity-50">
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
  private destroyRef = inject(DestroyRef);
  private queryChanged = new Subject<string>();

  vista = signal<Vista>('productos');

  // Filtro de texto
  filtroTexto = '';
  private filtroChanged = new Subject<string>();

  // Lista paginada unificada
  listaProductos = signal<ProductoListItem[]>([]);
  cargandoLista = signal(false);
  paginaActual = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  // Errores de búsqueda
  listaError = signal<string | null>(null);
  historialError = signal<string | null>(null);

  // Búsqueda (historial)
  q = '';
  resultados = signal<ProductoListItem[]>([]);
  buscando = signal(false);
  buscoSinResultados = signal(false);

  // Selección
  productoSel = signal<ProductoListItem | null>(null);
  resumen = signal<StockResumen | null>(null);
  movimientos = signal<Movimiento[]>([]);

  // Ubicaciones
  ubicaciones = signal<Ubicacion[]>([]);

  // Operaciones
  opTab = signal<OpTab>('entrada');
  opUbicacionId: number | null = null;
  opUbicacionDestinoId: number | null = null;
  opCantidad: number | null = null;
  opMotivo = '';
  ajusteTipo: 'AJUSTE_POSITIVO' | 'AJUSTE_NEGATIVO' = 'AJUSTE_POSITIVO';
  guardandoOp = signal(false);
  opError = signal<string | null>(null);
  opOk = signal<string | null>(null);

  // Editar / eliminar filas
  editandoUbicacionId = signal<number | null>(null);
  editCantidad: number | null = null;
  filaGuardando = signal(false);
  stockError = signal<string | null>(null);
  aEliminarLinea = signal<StockLinea | null>(null);
  mostrarConfirmDescarte = signal(false);

  // Historial
  filtroTipo = signal('');

  readonly opTabs: { key: OpTab; label: string; icon: string; activeClass: string; action: string; desc: string }[] = [
    { key: 'entrada', label: 'Entrada', icon: '↓', activeClass: 'text-green-400 border-green-400', action: 'Registrar entrada', desc: 'Agregar unidades (recibiste mercadería nueva)' },
    { key: 'salida', label: 'Salida', icon: '↑', activeClass: 'text-red-400 border-red-400', action: 'Registrar salida', desc: 'Retirar unidades (venta, devolución)' },
    { key: 'transferencia', label: 'Transferencia', icon: '⇄', activeClass: 'text-neon-cyan border-neon-cyan', action: 'Transferir', desc: 'Mover unidades de una ubicación a otra' },
    { key: 'ajuste', label: 'Ajuste', icon: '±', activeClass: 'text-amber-400 border-amber-400', action: 'Registrar ajuste', desc: 'Corregir stock real (rotura, merma, error de conteo)' },
  ];

  readonly ubicacionesConStock = computed(() => this.resumen()?.ubicaciones ?? []);
  readonly opTabActual = computed(() => this.opTabs.find(t => t.key === this.opTab())!);

  readonly movimientosFiltrados = computed(() => {
    const tipo = this.filtroTipo();
    if (!tipo) return this.movimientos();
    return this.movimientos().filter(m => m.tipo === tipo);
  });

  ngOnInit(): void {
    this.ubicacionService.listar().subscribe({
      next: (list) => this.ubicaciones.set(list),
      error: () => this.ubicaciones.set([]),
    });
    this.cargarLista();

    interval(70000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.vista() === 'productos' && !this.productoSel()) {
          this.cargarLista();
        }
      });

    this.filtroChanged
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.paginaActual.set(0);
        this.cargarLista();
      });

    this.queryChanged
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        tap(() => this.buscando.set(true)),
        switchMap((term) => this.productos.buscarTexto(term, 0, 10).pipe(
          catchError(() => {
            this.historialError.set('No se pudo buscar. Verificá tu conexión e intentá de nuevo.');
            return of(null);
          }),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        if (page) {
          this.resultados.set(page.content);
          this.buscoSinResultados.set(page.content.length === 0);
          this.historialError.set(null);
        }
        this.buscando.set(false);
      });
  }

  cambiarVista(v: Vista): void {
    this.vista.set(v);
    this.deseleccionar();
    this.filtroTexto = '';
    this.paginaActual.set(0);
    this.listaError.set(null);
    this.historialError.set(null);
    this.cargarLista();
  }

  cargarLista(): void {
    if (this.vista() === 'historial') return;
    this.cargandoLista.set(true);
    const q = this.filtroTexto.trim() || undefined;
    this.productos.listar(this.paginaActual(), 20, undefined, q).subscribe({
      next: (page) => {
        this.listaProductos.set(page.content);
        this.totalPaginas.set(page.totalPages);
        this.totalElementos.set(page.totalElements);
        this.listaError.set(null);
        this.cargandoLista.set(false);
      },
      error: () => {
        this.listaError.set('No se pudieron cargar los productos. Intentá de nuevo.');
        this.cargandoLista.set(false);
      },
    });
  }

  irPagina(p: number): void {
    this.paginaActual.set(p);
    this.cargarLista();
  }

  buscarEnLista(): void {
    this.paginaActual.set(0);
    this.cargarLista();
  }

  onFiltroInput(): void {
    const term = this.filtroTexto.trim();
    if (term.length === 1) return;
    this.filtroChanged.next(term);
  }

  limpiarFiltro(): void {
    this.filtroTexto = '';
    this.paginaActual.set(0);
    this.listaError.set(null);
    this.cargarLista();
  }

  onQueryInput(): void {
    const term = this.q.trim();
    if (term.length === 1) return;
    if (!term) {
      this.resultados.set([]);
      this.buscoSinResultados.set(false);
      return;
    }
    this.queryChanged.next(term);
  }

  buscar(): void {
    const q = this.q.trim();
    if (!q) return;
    this.buscando.set(true);
    this.buscoSinResultados.set(false);
    this.historialError.set(null);
    this.productos.buscarTexto(q, 0, 10).subscribe({
      next: (page) => {
        this.resultados.set(page.content);
        this.buscoSinResultados.set(page.content.length === 0);
        this.historialError.set(null);
        this.buscando.set(false);
      },
      error: () => {
        this.resultados.set([]);
        this.historialError.set('No se pudo buscar. Verificá tu conexión e intentá de nuevo.');
        this.buscando.set(false);
      },
    });
  }

  seleccionar(p: ProductoListItem): void {
    this.productoSel.set(p);
    this.resultados.set([]);
    this.opOk.set(null);
    this.opError.set(null);
    this.opUbicacionId = null;
    this.opUbicacionDestinoId = null;
    this.opCantidad = null;
    this.opMotivo = '';
    this.opTab.set('entrada');
    this.cancelarEdicionCantidad();
    this.refrescarStock(p.id);
  }

  deseleccionar(): void {
    this.productoSel.set(null);
    this.resumen.set(null);
    this.movimientos.set([]);
    this.opOk.set(null);
    this.opError.set(null);
    this.mostrarConfirmDescarte.set(false);
  }

  cerrarModalCarga(): void {
    if (this.formCargaDirty()) {
      this.mostrarConfirmDescarte.set(true);
    } else {
      this.deseleccionar();
    }
  }

  confirmarDescarte(): void {
    this.mostrarConfirmDescarte.set(false);
    this.deseleccionar();
  }

  cancelarDescarte(): void {
    this.mostrarConfirmDescarte.set(false);
  }

  private formCargaDirty(): boolean {
    return (this.opCantidad != null && this.opCantidad > 0)
      || this.opMotivo.trim() !== ''
      || this.editandoUbicacionId() !== null;
  }

  ejecutarOperacion(): void {
    const p = this.productoSel();
    if (!p) return;
    this.opError.set(null);
    this.opOk.set(null);
    const tab = this.opTab();

    if (tab === 'entrada') {
      if (!this.opUbicacionId) { this.opError.set('Elegí una ubicación.'); return; }
      if (!this.opCantidad || this.opCantidad < 1) { this.opError.set('La cantidad debe ser ≥ 1.'); return; }
      this.guardandoOp.set(true);
      this.service.entrada({
        productoId: p.id, ubicacionId: this.opUbicacionId, cantidad: this.opCantidad,
        motivo: this.opMotivo.trim() || undefined,
      }).subscribe({
        next: () => this.onOpExito(`+${this.opCantidad} u. registradas como entrada.`, p.id),
        error: (e) => this.onOpError(e),
      });
    }

    if (tab === 'salida') {
      if (!this.opUbicacionId) { this.opError.set('Elegí la ubicación de origen.'); return; }
      if (!this.opCantidad || this.opCantidad < 1) { this.opError.set('La cantidad debe ser ≥ 1.'); return; }
      this.guardandoOp.set(true);
      this.service.salida({
        productoId: p.id, ubicacionId: this.opUbicacionId, cantidad: this.opCantidad,
        motivo: this.opMotivo.trim() || undefined,
      }).subscribe({
        next: () => this.onOpExito(`−${this.opCantidad} u. registradas como salida.`, p.id),
        error: (e) => this.onOpError(e),
      });
    }

    if (tab === 'transferencia') {
      if (!this.opUbicacionId) { this.opError.set('Elegí la ubicación de origen.'); return; }
      if (!this.opUbicacionDestinoId) { this.opError.set('Elegí la ubicación de destino.'); return; }
      if (this.opUbicacionId === this.opUbicacionDestinoId) { this.opError.set('Origen y destino no pueden ser iguales.'); return; }
      if (!this.opCantidad || this.opCantidad < 1) { this.opError.set('La cantidad debe ser ≥ 1.'); return; }
      this.guardandoOp.set(true);
      this.service.transferencia({
        productoId: p.id, ubicacionOrigenId: this.opUbicacionId,
        ubicacionDestinoId: this.opUbicacionDestinoId, cantidad: this.opCantidad,
        motivo: this.opMotivo.trim() || undefined,
      }).subscribe({
        next: () => this.onOpExito(`${this.opCantidad} u. transferidas correctamente.`, p.id),
        error: (e) => this.onOpError(e),
      });
    }

    if (tab === 'ajuste') {
      if (!this.opUbicacionId) { this.opError.set('Elegí una ubicación.'); return; }
      if (!this.opCantidad || this.opCantidad < 1) { this.opError.set('La cantidad debe ser ≥ 1.'); return; }
      if (!this.opMotivo.trim()) { this.opError.set('El motivo es obligatorio para ajustes.'); return; }
      this.guardandoOp.set(true);
      this.service.ajuste({
        productoId: p.id, ubicacionId: this.opUbicacionId, tipo: this.ajusteTipo,
        cantidad: this.opCantidad, motivo: this.opMotivo.trim(),
      }).subscribe({
        next: () => this.onOpExito(`Ajuste de ${this.ajusteTipo === 'AJUSTE_POSITIVO' ? '+' : '−'}${this.opCantidad} u. registrado.`, p.id),
        error: (e) => this.onOpError(e),
      });
    }
  }

  // -- Edición inline de stock --

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
    this.service.conteo({ productoId: p.id, ubicacionId, cantidadReal: this.editCantidad, motivo: 'Ajuste manual de stock' }).subscribe({
      next: () => {
        this.filaGuardando.set(false);
        this.cancelarEdicionCantidad();
        this.refrescarStock(p.id);
        this.cargarLista();
      },
      error: (e) => {
        this.stockError.set(e?.error?.message ?? 'No se pudo actualizar.');
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
        this.cargarLista();
      },
      error: (e) => {
        this.stockError.set(e?.error?.message ?? 'No se pudo eliminar.');
        this.filaGuardando.set(false);
      },
    });
  }

  // -- Helpers de display --

  tipoClass(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA': return 'neon-badge-green';
      case 'SALIDA': return 'bg-red-500/15 text-red-400 border border-red-500/30';
      case 'TRANSFERENCIA': return 'neon-badge-cyan';
      case 'AJUSTE_POSITIVO': return 'bg-amber-500/15 text-amber-400 border border-amber-500/30';
      case 'AJUSTE_NEGATIVO': return 'bg-orange-500/15 text-orange-400 border border-orange-500/30';
      default: return 'neon-badge-purple';
    }
  }

  tipoLabel(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA': return 'Entrada';
      case 'SALIDA': return 'Salida';
      case 'TRANSFERENCIA': return 'Transfer.';
      case 'AJUSTE_POSITIVO': return 'Ajuste +';
      case 'AJUSTE_NEGATIVO': return 'Ajuste −';
      default: return tipo;
    }
  }

  cantidadClass(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA':
      case 'AJUSTE_POSITIVO': return 'text-green-400';
      case 'SALIDA':
      case 'AJUSTE_NEGATIVO': return 'text-red-400';
      default: return 'text-white';
    }
  }

  cantidadPrefix(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA':
      case 'AJUSTE_POSITIVO': return '+';
      case 'SALIDA':
      case 'AJUSTE_NEGATIVO': return '−';
      default: return '';
    }
  }

  ubicacionInfo(m: Movimiento): string {
    const ubicMap = new Map(this.ubicaciones().map(u => [u.id, u.codigo]));
    if (m.tipo === 'TRANSFERENCIA') {
      const origen = m.ubicacionOrigenId ? ubicMap.get(m.ubicacionOrigenId) ?? `#${m.ubicacionOrigenId}` : '?';
      const destino = m.ubicacionDestinoId ? ubicMap.get(m.ubicacionDestinoId) ?? `#${m.ubicacionDestinoId}` : '?';
      return `${origen} → ${destino}`;
    }
    const ubId = m.ubicacionDestinoId ?? m.ubicacionOrigenId;
    if (ubId) return ubicMap.get(ubId) ?? `#${ubId}`;
    return '-';
  }

  contarPorTipo(tipo: string): number {
    return this.movimientos().filter(m => m.tipo === tipo).length;
  }

  private onOpExito(msg: string, productoId: number): void {
    this.opOk.set(msg);
    this.opCantidad = null;
    this.opMotivo = '';
    this.guardandoOp.set(false);
    this.refrescarStock(productoId);
    this.cargarLista();
  }

  private onOpError(e: any): void {
    this.opError.set(e?.error?.message ?? 'No se pudo completar la operación.');
    this.guardandoOp.set(false);
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
