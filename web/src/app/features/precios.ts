import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ConfiguracionPrecio, PrecioBatch, PrecioImportPreview, PrecioImportProgreso, PrecioImportResult, PrecioPreviewFila } from '../core/models';
import { ProductoService } from '../core/producto.service';

@Component({
  selector: 'app-precios',
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe],
  template: `
    <div class="space-y-8 animate-fade-in max-w-5xl mx-auto">
      <!-- Header -->
      <div class="border-b border-dark-border pb-6">
        <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
          <svg class="w-8 h-8 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>Precios</span>
          <span class="text-xs font-mono bg-amber-500/20 text-amber-400 border border-amber-500/30 px-2 py-0.5 rounded-full uppercase">
            ADMIN
          </span>
        </h2>
        <p class="text-sm text-gray-400 mt-1">
          Configuración de márgenes, importación de listas y rollback de precios.
        </p>
      </div>

      <!-- === SECCIÓN 1: Márgenes por proveedor === -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <h3 class="text-lg font-bold text-white mb-5 flex items-center gap-2">
          <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
          Márgenes por Proveedor
        </h3>

        @if (cargando()) {
          <div class="py-8 text-center text-gray-400 font-mono">Cargando configuración...</div>
        } @else {
          <div class="space-y-4">
            @for (c of configs(); track c.id) {
              <div class="flex flex-col sm:flex-row sm:items-center gap-4 p-4 rounded-xl bg-dark-surface/50 border border-dark-border">
                <div class="flex-1 min-w-0">
                  <p class="font-semibold text-white text-sm">{{ c.proveedor }}</p>
                  <p class="text-[11px] text-gray-500 font-mono mt-0.5">
                    Multiplicador: x{{ (1 + c.margen / 100) | number:'1.5-5' }}
                  </p>
                </div>
                <div class="flex items-center gap-3">
                  <div class="flex items-center gap-1.5">
                    <label class="text-xs text-gray-400 font-semibold whitespace-nowrap">Margen %</label>
                    <input type="number" step="0.001" min="0" [ngModel]="c.margen" (ngModelChange)="onMargenChange(c, $event)"
                      class="w-28 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white font-mono text-sm focus:outline-none focus:border-amber-400 text-right" />
                  </div>
                  <label class="flex items-center gap-2 cursor-pointer select-none">
                    <input type="checkbox" [checked]="c.activo" (change)="onActivoChange(c, $event)" class="w-4 h-4 rounded accent-neon-green cursor-pointer" />
                    <span class="text-xs font-semibold" [class]="c.activo ? 'text-neon-green' : 'text-gray-500'">{{ c.activo ? 'Activo' : 'Inactivo' }}</span>
                  </label>
                  <button [disabled]="guardandoId() === c.id" (click)="guardar(c)"
                    class="px-4 py-2 rounded-lg text-xs font-semibold neon-button-primary cursor-pointer disabled:opacity-50 whitespace-nowrap">
                    {{ guardandoId() === c.id ? 'Guardando...' : 'Guardar' }}
                  </button>
                </div>
              </div>
            }
          </div>

          <!-- Agregar nuevo proveedor -->
          <div class="mt-5 p-4 rounded-xl bg-dark-surface/40 border border-dashed border-dark-border">
            <p class="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-3">Agregar nuevo proveedor</p>
            <div class="flex flex-col sm:flex-row items-start sm:items-end gap-3">
              <div>
                <label class="block text-[11px] text-gray-500 mb-1">Nombre</label>
                <input type="text" [(ngModel)]="nuevoProvNombre" placeholder="Ej: EGSA"
                  class="w-44 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-cyan" />
              </div>
              <div>
                <label class="block text-[11px] text-gray-500 mb-1">Margen %</label>
                <input type="number" step="0.01" min="0" [(ngModel)]="nuevoProvMargen"
                  class="w-28 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white font-mono text-sm focus:outline-none focus:border-neon-cyan text-right" />
              </div>
              <button [disabled]="!nuevoProvNombre.trim() || creandoProv()" (click)="crearProveedor()"
                class="px-5 py-2 rounded-lg text-sm font-semibold neon-button-secondary cursor-pointer disabled:opacity-50">
                {{ creandoProv() ? 'Creando...' : '+ Agregar' }}
              </button>
            </div>
          </div>
        }

        @if (error()) {
          <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2">
            <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
            <span>{{ error() }}</span>
          </div>
        }
        @if (exito()) {
          <div class="mt-4 p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-xs flex items-center gap-2">
            <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg>
            <span>{{ exito() }}</span>
          </div>
        }
      </div>

      <!-- === SECCIÓN 2: Importar precios === -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
          </svg>
          Importar Lista de Precios
        </h3>
        <p class="text-sm text-gray-400 mb-5">
          Subí el archivo del proveedor (CSV o Excel). Seleccionás qué columna es el SKU y cuál el precio de costo,
          y el sistema matchea contra los productos existentes aplicando el margen configurado.
        </p>

        <!-- Paso 1: Subir archivo -->
        @if (impPaso() === 1) {
          <div class="flex flex-col sm:flex-row items-start sm:items-end gap-3">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Archivo (CSV / Excel)</label>
              <input type="file" accept=".csv,.xls,.xlsx" (change)="onArchivoSeleccionado($event)"
                class="text-sm text-gray-300 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-neon-purple/20 file:text-neon-purple file:cursor-pointer hover:file:bg-neon-purple/30" />
            </div>
            <button [disabled]="!impArchivo() || impSubiendo()" (click)="subirArchivo()"
              class="px-5 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer disabled:opacity-50 min-w-[160px]">
              @if (impSubiendo()) {
                @if (impUploadProgress() < 100) {
                  Subiendo... {{ impUploadProgress() }}%
                } @else {
                  Analizando...
                }
              } @else {
                Detectar Columnas
              }
            </button>
          </div>
        }

        <!-- Paso 2: Mapear columnas -->
        @if (impPaso() === 2) {
          <div class="space-y-4">
            <div class="p-3 rounded-lg bg-dark-surface/60 border border-dark-border text-xs text-gray-400">
              Archivo: <span class="text-white font-semibold">{{ impNombreArchivo() }}</span> — {{ impTotalFilas() }} filas detectadas, {{ impColumnas().length }} columnas
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Columna SKU / Código</label>
                <select [(ngModel)]="impColSku" class="w-full px-3 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-purple text-sm">
                  <option value="">Seleccionar...</option>
                  @for (col of impColumnasId(); track col) { <option [value]="col">{{ col }}</option> }
                </select>
              </div>
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Columna Precio</label>
                <select [(ngModel)]="impColPrecio" class="w-full px-3 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-purple text-sm">
                  <option value="">Seleccionar...</option>
                  @for (col of impColumnasPrecio(); track col) { <option [value]="col">{{ col }}</option> }
                </select>
              </div>
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Proveedor</label>
                <select [(ngModel)]="impProveedor" class="w-full px-3 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-purple text-sm">
                  <option value="">Seleccionar...</option>
                  @for (c of configs(); track c.id) { <option [value]="c.proveedor">{{ c.proveedor }} ({{ c.margen }}%)</option> }
                </select>
              </div>
            </div>
            <div class="flex gap-2">
              <button (click)="resetImport()" class="px-4 py-2.5 rounded-xl text-sm font-semibold text-gray-400 hover:text-white cursor-pointer">Cancelar</button>
              <button [disabled]="!impColSku || !impColPrecio || !impProveedor || impPreviewing()" (click)="generarPreview()"
                class="px-5 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer disabled:opacity-50">
                {{ impPreviewing() ? 'Analizando...' : 'Vista Previa' }}
              </button>
            </div>
          </div>
        }

        <!-- Paso 3: Preview con conflictos -->
        @if (impPaso() === 3 && impPreview()) {
          <div class="space-y-4">
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
              <div class="p-3 rounded-lg bg-dark-surface/60 border border-dark-border">
                <p class="text-lg font-bold font-mono text-white">{{ impPreview()!.total }}</p>
                <p class="text-[10px] uppercase text-gray-400">Total filas</p>
              </div>
              <div class="p-3 rounded-lg bg-green-500/10 border border-green-500/30">
                <p class="text-lg font-bold font-mono text-neon-green">{{ impPreview()!.ok }}</p>
                <p class="text-[10px] uppercase text-gray-400">OK</p>
              </div>
              <div class="p-3 rounded-lg bg-red-500/10 border border-red-500/30">
                <p class="text-lg font-bold font-mono text-red-400">{{ impPreview()!.conflictos }}</p>
                <p class="text-[10px] uppercase text-gray-400">Conflictos</p>
              </div>
              <div class="p-3 rounded-lg bg-amber-500/10 border border-amber-500/30">
                <p class="text-lg font-bold font-mono text-amber-400">{{ impPreview()!.noEncontrados }}</p>
                <p class="text-[10px] uppercase text-gray-400">No encontrados</p>
              </div>
            </div>

            <div class="overflow-x-auto max-h-96 overflow-y-auto">
              <table class="w-full text-left text-sm border-collapse">
                <thead class="sticky top-0">
                  <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface">
                    <th class="py-2 px-3">Fila</th>
                    <th class="py-2 px-3">SKU</th>
                    <th class="py-2 px-3 text-right">P. Costo CSV</th>
                    <th class="py-2 px-3">Estado</th>
                    <th class="py-2 px-3">Producto en BD</th>
                    <th class="py-2 px-3 text-right">P. Actual</th>
                    <th class="py-2 px-3 text-right">P. Venta Nuevo</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-dark-border/50">
                  @for (f of impPreview()!.filas; track f.fila) {
                    <tr class="hover:bg-dark-surface/40 transition-colors"
                        [class.opacity-40]="f.estado !== 'OK'">
                      <td class="py-2 px-3 font-mono text-gray-500">{{ f.fila }}</td>
                      <td class="py-2 px-3 font-mono font-bold text-neon-purple">{{ f.skuCsv }}</td>
                      <td class="py-2 px-3 text-right font-mono text-gray-300">{{ f.precioCostoCsv ? '$' + (f.precioCostoCsv | number:'1.2-2') : '—' }}</td>
                      <td class="py-2 px-3">
                        @if (f.estado === 'OK') {
                          <span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-green-500/15 text-green-400 border border-green-500/30">OK</span>
                        } @else if (f.estado === 'CONFLICTO') {
                          <span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-red-500/15 text-red-400 border border-red-500/30" [title]="f.cantidadMatches + ' productos con mismo SKU'">CONFLICTO ({{ f.cantidadMatches }})</span>
                        } @else {
                          <span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/15 text-amber-400 border border-amber-500/30">NO ENCONTRADO</span>
                        }
                      </td>
                      <td class="py-2 px-3 text-xs text-gray-300 max-w-48 truncate" [title]="f.productoDescripcion ?? ''">
                        {{ f.productoDescripcion ?? '—' }}
                        @if (f.productoMarca) { <span class="text-gray-500"> · {{ f.productoMarca }}</span> }
                      </td>
                      <td class="py-2 px-3 text-right font-mono text-xs text-gray-500">{{ f.precioActual ? '$' + (f.precioActual | number:'1.2-2') : '—' }}</td>
                      <td class="py-2 px-3 text-right font-mono font-bold text-xs"
                          [class]="f.estado === 'OK' ? 'text-amber-400' : 'text-gray-600'">
                        {{ f.precioNuevoCalculado ? '$' + (f.precioNuevoCalculado | number:'1.2-2') : '—' }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            @if (impAplicando()) {
              <div class="mt-4 p-4 rounded-xl bg-neon-purple/5 border border-neon-purple/30 space-y-3">
                <div class="flex items-center justify-between text-sm">
                  <span class="text-gray-300 flex items-center gap-2">
                    <svg class="animate-spin w-4 h-4 text-neon-purple" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
                    Importando precios...
                  </span>
                  <span class="font-mono text-neon-purple font-semibold">
                    @if (impProgresoTotal() > 0) {
                      {{ impProgreso() }} / {{ impProgresoTotal() }}
                      ({{ ((impProgreso() / impProgresoTotal()) * 100) | number:'1.0-0' }}%)
                    } @else {
                      Preparando...
                    }
                  </span>
                </div>
                <div class="w-full h-2.5 bg-dark-surface rounded-full overflow-hidden">
                  <div class="h-full bg-gradient-to-r from-neon-purple to-neon-cyan rounded-full transition-all duration-300"
                       [style.width.%]="impProgresoTotal() > 0 ? (impProgreso() / impProgresoTotal()) * 100 : 0"></div>
                </div>
                <p class="text-[11px] text-gray-500">Podés navegar a otra sección mientras se procesan los precios.</p>
              </div>
            } @else {
              <div class="flex gap-2 pt-2">
                <button (click)="resetImport()" class="px-4 py-2.5 rounded-xl text-sm font-semibold text-gray-400 hover:text-white cursor-pointer">Cancelar</button>
                <button (click)="impPaso.set(2)" class="px-4 py-2.5 rounded-xl text-sm font-semibold text-gray-400 hover:text-white cursor-pointer">Volver</button>
                @if (impPreview()!.ok > 0) {
                  <button (click)="aplicarImport()"
                    class="px-6 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer">
                    Aplicar {{ impPreview()!.ok }} precios
                  </button>
                }
              </div>
            }
          </div>
        }

        <!-- Resultado de importación -->
        @if (impResultado()) {
          <div class="mt-4 p-4 rounded-xl bg-green-500/10 border border-green-500/30 space-y-2">
            <p class="text-sm font-semibold text-green-400">Importación completada</p>
            <p class="text-xs text-gray-300">{{ impResultado()!.mensaje }}</p>
            <button (click)="resetImport(); cargarBatches()" class="px-4 py-2 rounded-lg text-xs font-semibold neon-button-secondary cursor-pointer mt-2">
              Nueva importación
            </button>
          </div>
        }

        @if (impError()) {
          <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ impError() }}</div>
        }
      </div>

      <!-- === SECCIÓN 4: Historial de importaciones + Rollback === -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Historial de Actualizaciones
          </h3>
          <button (click)="cargarBatches()" class="text-xs text-neon-cyan hover:underline cursor-pointer">Actualizar</button>
        </div>

        @if (batches().length === 0) {
          <p class="text-gray-500 text-sm py-4 text-center">No hay importaciones registradas.</p>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm border-collapse">
              <thead>
                <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                  <th class="py-2 px-3">Fecha</th>
                  <th class="py-2 px-3">Proveedor</th>
                  <th class="py-2 px-3">Fuente</th>
                  <th class="py-2 px-3 text-right">Aplicados</th>
                  <th class="py-2 px-3 text-right">Conflictos</th>
                  <th class="py-2 px-3 text-center">Estado</th>
                  <th class="py-2 px-3 text-center">Rollback</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border/50">
                @for (b of batches(); track b.id) {
                  <tr class="hover:bg-dark-surface/40 transition-colors">
                    <td class="py-2.5 px-3 text-xs text-gray-400 font-mono">{{ b.createdAt | date:'dd/MM/yy HH:mm' }}</td>
                    <td class="py-2.5 px-3 text-sm text-white">{{ b.proveedor }}</td>
                    <td class="py-2.5 px-3">
                      <span class="px-2 py-0.5 rounded text-[10px] font-semibold"
                            [class]="b.fuente === 'API_SYNC' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30' : 'bg-neon-purple/15 text-neon-purple border border-neon-purple/30'">
                        {{ b.fuente === 'API_SYNC' ? 'API' : 'CSV' }}
                      </span>
                    </td>
                    <td class="py-2.5 px-3 text-right font-mono text-neon-green">{{ b.aplicados }}</td>
                    <td class="py-2.5 px-3 text-right font-mono text-red-400">{{ b.conflictos }}</td>
                    <td class="py-2.5 px-3 text-center">
                      <span class="inline-block w-2.5 h-2.5 rounded-full"
                            [class]="b.estado === 'APLICADO' ? 'bg-green-400' : 'bg-gray-500'"
                            [title]="b.estado"></span>
                    </td>
                    <td class="py-2.5 px-3 text-center">
                      @if (b.estado === 'APLICADO') {
                        <button [disabled]="rollbackId() === b.id" (click)="rollback(b)"
                          class="px-3 py-1 rounded-lg text-[11px] font-semibold text-red-400 bg-red-500/10 border border-red-500/30 hover:bg-red-500/20 cursor-pointer disabled:opacity-50">
                          {{ rollbackId() === b.id ? '...' : 'Revertir' }}
                        </button>
                      } @else {
                        <span class="text-[11px] text-gray-500">Revertido</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>

    <!-- Confirmación de rollback -->
    @if (rollbackBatch(); as b) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="rollbackBatch.set(null)">
        <div class="glass-panel w-full max-w-md p-6 rounded-2xl border border-red-500/40 shadow-neon" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            Revertir importación
          </h3>
          <p class="text-sm text-gray-300">
            Se revertirán los <span class="font-semibold text-white">{{ b.aplicados }}</span> precios
            del batch de <span class="text-white font-semibold">{{ b.proveedor }}</span>
            ({{ b.createdAt | date:'dd/MM/yy HH:mm' }}) a sus valores anteriores.
          </p>
          <p class="text-xs text-amber-400 mt-2">
            Los productos cuyo precio fue modificado después de esta importación NO serán revertidos.
          </p>
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="rollbackBatch.set(null)" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">Cancelar</button>
            <button [disabled]="rollbackId() !== null" (click)="confirmarRollback(b)"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-600 hover:bg-red-500 text-white transition-colors cursor-pointer disabled:opacity-50">
              {{ rollbackId() ? 'Revirtiendo...' : 'Confirmar Rollback' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class Precios implements OnInit, OnDestroy {
  private service = inject(ProductoService);
  private importPollTimer: ReturnType<typeof setInterval> | null = null;

  configs = signal<ConfiguracionPrecio[]>([]);
  cargando = signal(true);
  guardandoId = signal<number | null>(null);
  error = signal<string | null>(null);
  exito = signal<string | null>(null);

  // Import CSV
  impPaso = signal(1);
  impArchivo = signal<File | null>(null);
  impNombreArchivo = signal('');
  impSubiendo = signal(false);
  impUploadProgress = signal(0);
  impUploadId = signal('');
  impColumnas = signal<string[]>([]);
  impColumnasId = signal<string[]>([]);
  impColumnasPrecio = signal<string[]>([]);
  impTotalFilas = signal(0);
  impColSku = '';
  impColPrecio = '';
  impProveedor = '';
  impPreviewing = signal(false);
  impPreview = signal<PrecioImportPreview | null>(null);
  impAplicando = signal(false);
  impProgreso = signal(0);
  impProgresoTotal = signal(0);
  impResultado = signal<PrecioImportResult | null>(null);
  impError = signal<string | null>(null);

  // Nuevo proveedor
  nuevoProvNombre = '';
  nuevoProvMargen = 0;
  creandoProv = signal(false);

  // Batches / rollback
  batches = signal<PrecioBatch[]>([]);
  rollbackBatch = signal<PrecioBatch | null>(null);
  rollbackId = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar();
    this.cargarBatches();
  }

  ngOnDestroy(): void {
    this.stopImportPoll();
  }

  cargar(): void {
    this.cargando.set(true);
    this.service.listarConfigPrecios().subscribe({
      next: (list) => { this.configs.set(list); this.cargando.set(false); },
      error: () => { this.error.set('No se pudo cargar la configuración.'); this.cargando.set(false); },
    });
  }

  onMargenChange(c: ConfiguracionPrecio, val: number): void { c.margen = val; }
  onActivoChange(c: ConfiguracionPrecio, ev: Event): void { c.activo = (ev.target as HTMLInputElement).checked; }

  guardar(c: ConfiguracionPrecio): void {
    this.error.set(null); this.exito.set(null);
    this.guardandoId.set(c.id);
    this.service.actualizarConfigPrecio(c.id, c.margen, c.activo).subscribe({
      next: (updated) => {
        this.configs.set(this.configs().map(x => x.id === updated.id ? updated : x));
        this.guardandoId.set(null);
        this.exito.set(`Margen de "${updated.proveedor}" actualizado a ${updated.margen}%.`);
      },
      error: (e) => { this.guardandoId.set(null); this.error.set(e?.error?.message ?? 'No se pudo guardar.'); },
    });
  }

  crearProveedor(): void {
    const nombre = this.nuevoProvNombre.trim();
    if (!nombre) return;
    this.creandoProv.set(true); this.error.set(null);
    this.service.crearConfigPrecio(nombre, this.nuevoProvMargen).subscribe({
      next: (created) => {
        this.configs.set([...this.configs(), created]);
        this.nuevoProvNombre = '';
        this.nuevoProvMargen = 0;
        this.creandoProv.set(false);
        this.exito.set(`Proveedor "${created.proveedor}" creado con margen ${created.margen}%.`);
      },
      error: (e) => {
        this.creandoProv.set(false);
        const msg = e.status === 409 ? 'Ya existe un proveedor con ese nombre.' : (e?.error?.message ?? 'No se pudo crear el proveedor.');
        this.error.set(msg);
      },
    });
  }

  // --- Import CSV ---

  onArchivoSeleccionado(ev: Event): void {
    const file = (ev.target as HTMLInputElement).files?.[0] ?? null;
    this.impArchivo.set(file);
    this.impNombreArchivo.set(file?.name ?? '');
  }

  subirArchivo(): void {
    const file = this.impArchivo();
    if (!file) return;
    this.impSubiendo.set(true); this.impUploadProgress.set(0); this.impError.set(null);

    const progressTimer = setTimeout(() => {
      if (this.impSubiendo() && this.impUploadProgress() < 100) this.impUploadProgress.set(100);
    }, 3000);

    this.service.importDetectarColumnas(file).subscribe({
      next: (event) => {
        if ('progress' in event) {
          this.impUploadProgress.set(event.progress);
          return;
        }
        clearTimeout(progressTimer);
        const res = event;
        this.impUploadId.set(res.uploadId);
        this.impColumnas.set(res.columnas);
        this.impTotalFilas.set(res.totalFilas);

        const idPat = /c[oó]d|sku|^id$/i;
        const precioPat = /precio|costo|lista|neto|venta|importe|monto|valor|price|cost/i;
        const colsId = res.columnas.filter((c: string) => idPat.test(c));
        const colsPrecio = res.columnas.filter((c: string) => precioPat.test(c));
        this.impColumnasId.set(colsId.length > 0 ? colsId : res.columnas);
        this.impColumnasPrecio.set(colsPrecio.length > 0 ? colsPrecio : res.columnas);

        if (colsId.length === 1) this.impColSku = colsId[0];
        else if (colsId.length > 1) this.impColSku = colsId[0];
        if (colsPrecio.length === 1) this.impColPrecio = colsPrecio[0];

        this.impPaso.set(2);
        this.impSubiendo.set(false);
      },
      error: (e) => {
        clearTimeout(progressTimer);
        let msg: string;
        if (e?.name === 'TimeoutError') {
          msg = 'El servidor tardó demasiado en responder. Esperá unos segundos e intentá de nuevo.';
        } else if (e?.status === 0 || e?.status === undefined) {
          msg = 'No se pudo conectar con el servidor. Verificá tu conexión e intentá de nuevo.';
        } else if (e?.status >= 502 && e?.status <= 504) {
          msg = 'El servidor no está disponible en este momento. Esperá unos segundos e intentá de nuevo.';
        } else {
          msg = e?.error?.message ?? 'No se pudo leer el archivo.';
        }
        this.impError.set(msg);
        this.impSubiendo.set(false);
      },
    });
  }

  generarPreview(): void {
    this.impPreviewing.set(true); this.impError.set(null);
    this.service.importPreview(this.impUploadId(), this.impColSku, this.impColPrecio, this.impProveedor).subscribe({
      next: (res) => { this.impPreview.set(res); this.impPaso.set(3); this.impPreviewing.set(false); },
      error: (e) => { this.impError.set(e?.error?.message ?? 'Error al generar preview.'); this.impPreviewing.set(false); },
    });
  }

  aplicarImport(): void {
    const preview = this.impPreview();
    if (!preview) return;
    this.impAplicando.set(true); this.impError.set(null);
    this.impProgreso.set(0); this.impProgresoTotal.set(0);
    const excluidos = preview.filas.filter(f => f.estado !== 'OK').map(f => f.skuCsv);
    this.service.importAplicar(this.impUploadId(), this.impColSku, this.impColPrecio,
        this.impProveedor, excluidos, this.impNombreArchivo()).subscribe({
      next: () => { this.startImportPoll(); },
      error: (e) => { this.impError.set(e?.error?.message ?? 'Error al aplicar.'); this.impAplicando.set(false); },
    });
  }

  private startImportPoll(): void {
    this.stopImportPoll();
    this.importPollTimer = setInterval(() => {
      this.service.progresoImport().subscribe({
        next: (p) => {
          this.impProgreso.set(p.progreso);
          this.impProgresoTotal.set(p.total);
          if (!p.importando) {
            this.stopImportPoll();
            this.impAplicando.set(false);
            if (p.ultimoResultado) {
              this.impResultado.set(p.ultimoResultado);
              this.impPaso.set(0);
              this.cargarBatches();
            }
          }
        },
        error: () => {},
      });
    }, 1000);
  }

  private stopImportPoll(): void {
    if (this.importPollTimer) { clearInterval(this.importPollTimer); this.importPollTimer = null; }
  }

  resetImport(): void {
    this.stopImportPoll();
    this.impPaso.set(1); this.impArchivo.set(null); this.impNombreArchivo.set('');
    this.impUploadId.set(''); this.impColumnas.set([]); this.impTotalFilas.set(0);
    this.impColumnasId.set([]); this.impColumnasPrecio.set([]);
    this.impColSku = ''; this.impColPrecio = ''; this.impProveedor = '';
    this.impPreview.set(null); this.impResultado.set(null); this.impError.set(null);
    this.impAplicando.set(false); this.impProgreso.set(0); this.impProgresoTotal.set(0);
  }

  // --- Batches / Rollback ---

  cargarBatches(): void {
    this.service.listarBatches().subscribe({
      next: (list) => this.batches.set(list),
      error: () => {},
    });
  }

  rollback(b: PrecioBatch): void {
    this.rollbackBatch.set(b);
  }

  confirmarRollback(b: PrecioBatch): void {
    this.rollbackId.set(b.id);
    this.service.rollbackBatch(b.id).subscribe({
      next: () => {
        this.rollbackId.set(null);
        this.rollbackBatch.set(null);
        this.cargarBatches();
        this.exito.set(`Batch #${b.id} revertido exitosamente.`);
      },
      error: (e) => {
        this.rollbackId.set(null);
        this.rollbackBatch.set(null);
        this.error.set(e?.error?.message ?? 'No se pudo revertir.');
      },
    });
  }
}
