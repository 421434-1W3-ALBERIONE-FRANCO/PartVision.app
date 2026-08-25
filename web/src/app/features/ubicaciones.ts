import { Component, OnInit, inject, signal, computed, ViewChild, ElementRef } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { EstadoOcupacion, StockDetalleUbicacion, StockPorUbicacion, Ubicacion } from '../core/models';
import { StockService } from '../core/stock.service';
import { UbicacionService } from '../core/ubicacion.service';

interface ParsedCodigo {
  pasillo: string;
  posicion: string;
  altura: string;
}

function parseUbicacionCodigo(codigo: string): ParsedCodigo | null {
  if (!/^\d{6}$/.test(codigo)) return null;
  return {
    pasillo: codigo.substring(0, 2),
    posicion: codigo.substring(2, 4),
    altura: codigo.substring(4, 6),
  };
}

@Component({
  selector: 'app-ubicaciones',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  template: `
    <div class="space-y-6 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-dark-border pb-6">
        <div>
          <h2 class="text-2xl md:text-3xl font-extrabold tracking-tight text-white flex flex-wrap items-center gap-3">
            <span>Ubicaciones Físicas</span>
            <span class="text-xs font-mono bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/30 px-2 py-0.5 rounded-full uppercase">
              estructura de depósito
            </span>
          </h2>
          <p class="text-sm text-gray-400 mt-1">
            Visualizá dónde está almacenado cada producto dentro del depósito.
          </p>
        </div>

        <div class="flex items-center gap-2 self-start md:self-auto">
          <div class="flex bg-dark-surface border border-dark-border rounded-xl overflow-hidden">
            <button
              (click)="vistaActual.set('mapa')"
              [class]="vistaActual() === 'mapa'
                ? 'px-4 py-2 text-sm font-semibold text-neon-cyan bg-neon-cyan/10 border-r border-dark-border cursor-pointer'
                : 'px-4 py-2 text-sm font-semibold text-gray-400 hover:text-white border-r border-dark-border cursor-pointer'"
            >
              <svg class="w-4 h-4 inline-block mr-1.5 -mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
              </svg>
              Mapa
            </button>
            <button
              (click)="vistaActual.set('lista')"
              [class]="vistaActual() === 'lista'
                ? 'px-4 py-2 text-sm font-semibold text-neon-cyan bg-neon-cyan/10 cursor-pointer'
                : 'px-4 py-2 text-sm font-semibold text-gray-400 hover:text-white cursor-pointer'"
            >
              <svg class="w-4 h-4 inline-block mr-1.5 -mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 10h16M4 14h16M4 18h16" />
              </svg>
              Lista
            </button>
          </div>

          <button
            (click)="abrirNueva()"
            class="px-4 py-2 rounded-xl font-semibold text-sm neon-button-primary flex items-center gap-1.5 cursor-pointer"
          >
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Nueva
          </button>
        </div>
      </div>

      <!-- Búsqueda -->
      <div class="relative">
        <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <input
          [ngModel]="busqueda()"
          (ngModelChange)="busqueda.set($event)"
          type="text"
          placeholder="Buscar ubicación, código o descripción..."
          class="w-full pl-10 pr-4 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
        />
      </div>

      <!-- Métricas -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div class="glass-panel rounded-xl p-4 border border-dark-border">
          <p class="text-xs text-gray-400 uppercase tracking-wider">Ubicaciones</p>
          <p class="text-2xl font-bold text-white mt-1">{{ metricas().total }}</p>
        </div>
        <div class="glass-panel rounded-xl p-4 border border-dark-border">
          <p class="text-xs text-gray-400 uppercase tracking-wider">Con stock</p>
          <p class="text-2xl font-bold text-neon-green mt-1">{{ metricas().conStock }}</p>
        </div>
        <div class="glass-panel rounded-xl p-4 border border-dark-border">
          <p class="text-xs text-gray-400 uppercase tracking-wider">Unidades</p>
          <p class="text-2xl font-bold text-neon-cyan mt-1">{{ metricas().unidades | number }}</p>
        </div>
        <div class="glass-panel rounded-xl p-4 border border-dark-border">
          <p class="text-xs text-gray-400 uppercase tracking-wider">Pasillos</p>
          <p class="text-2xl font-bold text-neon-purple mt-1">{{ metricas().pasillos }}</p>
        </div>
      </div>

      <!-- Form: Crear / Editar -->
      @if (mostrarForm()) {
        <div #formPanel class="glass-panel p-6 rounded-2xl border border-neon-purple/40 shadow-neon animate-slide-in">
          <h3 class="text-lg font-bold text-white mb-4">
            {{ editandoId() ? 'Editar Ubicación' : 'Crear Ubicación' }}
          </h3>

          @if (!editandoId() && modoFormAsistido()) {
            <!-- Modo asistido -->
            <div class="grid grid-cols-3 gap-3 mb-3">
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Pasillo</label>
                <input
                  [(ngModel)]="formPasillo"
                  type="text" maxlength="2" placeholder="01"
                  class="w-full px-3 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono text-center placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
                />
              </div>
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Posición</label>
                <input
                  [(ngModel)]="formPosicion"
                  type="text" maxlength="2" placeholder="02"
                  class="w-full px-3 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono text-center placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
                />
              </div>
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Altura</label>
                <input
                  [(ngModel)]="formAltura"
                  type="text" maxlength="2" placeholder="03"
                  class="w-full px-3 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono text-center placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
                />
              </div>
            </div>
            <div class="flex items-center gap-3 mb-3">
              <span class="text-xs text-gray-400">Código generado:</span>
              <span class="font-mono font-bold text-neon-cyan text-lg">{{ codigoGenerado() || '------' }}</span>
              <button (click)="modoFormAsistido.set(false)" class="text-xs text-gray-500 hover:text-gray-300 ml-auto cursor-pointer">
                Escribir código libre
              </button>
            </div>
          } @else {
            <!-- Modo libre -->
            <div class="mb-3">
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Código de Ubicación *</label>
              <input
                [(ngModel)]="codigo"
                type="text"
                placeholder="Ej: 010203"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
              />
            </div>
            @if (!editandoId()) {
              <button (click)="modoFormAsistido.set(true)" class="text-xs text-gray-500 hover:text-gray-300 mb-3 cursor-pointer">
                Usar modo asistido (Pasillo + Posición + Altura)
              </button>
            }
          }

          <div class="mb-3">
            <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Descripción (opcional)</label>
            <input
              [(ngModel)]="descripcion"
              type="text"
              placeholder="Ej: Estante rojo, fondo del galpón"
              class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
            />
          </div>

          @if (error()) {
            <div class="mt-3 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">
              {{ error() }}
            </div>
          }

          <div class="mt-4 flex flex-wrap justify-end gap-3">
            <button (click)="cancelar()" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">
              Cancelar
            </button>
            <button [disabled]="guardando()" (click)="guardar()" class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50">
              {{ guardando() ? 'Guardando...' : (editandoId() ? 'Actualizar' : 'Crear Ubicación') }}
            </button>
          </div>
        </div>
      }

      @if (cargando()) {
        <div class="py-12 text-center text-gray-400 font-mono">
          <svg class="animate-spin h-8 w-8 text-neon-cyan mx-auto mb-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          Cargando ubicaciones...
        </div>
      } @else if (vistaActual() === 'mapa') {
        <!-- ======= VISTA MAPA ======= -->

        <!-- Breadcrumb -->
        @if (pasilloSel() || posicionSel()) {
          <div class="flex items-center gap-2 text-sm">
            <button (click)="volverAMapa()" class="text-neon-cyan hover:text-white transition-colors cursor-pointer font-semibold">
              Ubicaciones
            </button>
            @if (pasilloSel()) {
              <span class="text-gray-500">/</span>
              <button
                (click)="posicionSel() ? irAPasillo(pasilloSel()!) : null"
                [class]="posicionSel()
                  ? 'text-neon-cyan hover:text-white transition-colors cursor-pointer font-semibold'
                  : 'text-white font-semibold'"
              >
                Pasillo {{ pasilloSel() }}
              </button>
            }
            @if (posicionSel()) {
              <span class="text-gray-500">/</span>
              <span class="text-white font-semibold">Posición {{ posicionSel() }}</span>
            }
          </div>
        }

        <!-- Nivel 1: Pasillos -->
        @if (!pasilloSel()) {
          @if (pasillosFiltrados().length === 0) {
            <div class="py-12 text-center text-gray-500">
              {{ busqueda() ? 'No se encontraron ubicaciones para "' + busqueda() + '".' : 'No hay ubicaciones registradas. Creá la primera con "Nueva".' }}
            </div>
          } @else {
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
              @for (p of pasillosFiltrados(); track p.id) {
                <button
                  (click)="irAPasillo(p.id)"
                  class="glass-panel rounded-2xl p-5 border border-dark-border hover:border-neon-cyan/60 hover:shadow-neon-cyan transition-all text-left cursor-pointer group"
                >
                  <div class="flex items-center justify-between mb-3">
                    <span class="text-lg font-bold text-white group-hover:text-neon-cyan transition-colors">PASILLO {{ p.id }}</span>
                    <svg class="w-5 h-5 text-gray-500 group-hover:text-neon-cyan transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                    </svg>
                  </div>
                  <div class="text-xs text-gray-400 mb-3">
                    {{ p.posiciones }} posiciones · {{ p.ubicaciones }} ubicaciones
                  </div>
                  <div class="w-full bg-dark-surface rounded-full h-2 mb-2">
                    <div
                      class="h-2 rounded-full transition-all"
                      [style.width.%]="p.stockRelativo"
                      [class]="p.stock > 0 ? (p.stockRelativo > 60 ? 'bg-neon-cyan' : 'bg-neon-orange') : 'bg-gray-600'"
                    ></div>
                  </div>
                  <div class="flex items-center justify-between text-xs">
                    <span class="font-mono" [class]="p.stock > 0 ? 'text-neon-cyan' : 'text-gray-500'">{{ p.stock | number }} u.</span>
                    <span class="text-gray-500">{{ p.productos }} productos</span>
                  </div>
                </button>
              }
            </div>

            <!-- Ubicaciones con código no estándar -->
            @if (ubicacionesNoEstandar().length > 0 && !busqueda()) {
              <div class="mt-6">
                <h3 class="text-sm font-semibold text-gray-400 mb-3">Código personalizado</h3>
                <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                  @for (u of ubicacionesNoEstandar(); track u.id) {
                    <div
                      (click)="seleccionarUbicacion(u)"
                      class="glass-panel rounded-xl p-4 border border-dark-border hover:border-neon-cyan/40 transition-all cursor-pointer"
                    >
                      <span class="font-mono font-bold text-white text-sm">{{ u.codigo }}</span>
                      <span class="text-xs text-gray-400 ml-2">{{ u.descripcion || '' }}</span>
                      <div class="text-xs mt-1">
                        <span class="font-mono" [class]="getStock(u.id).cantidadTotal > 0 ? 'text-neon-cyan' : 'text-gray-500'">
                          {{ getStock(u.id).cantidadTotal }} u.
                        </span>
                      </div>
                    </div>
                  }
                </div>
              </div>
            }
          }
        }

        <!-- Nivel 2: Posiciones dentro de un pasillo -->
        @if (pasilloSel() && !posicionSel()) {
          <div class="flex items-center gap-3 mb-2">
            <button (click)="volverAMapa()" class="px-3 py-1.5 rounded-lg text-xs font-medium text-gray-400 hover:text-white bg-dark-surface border border-dark-border hover:border-neon-cyan transition-colors cursor-pointer">
              ← Volver
            </button>
            <span class="text-gray-400 text-sm">
              {{ posicionesDelPasillo().length }} posiciones · {{ ubicacionesDelPasillo().length }} ubicaciones
            </span>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            @for (pos of posicionesDelPasillo(); track pos.id) {
              <button
                (click)="irAPosicion(pos.id)"
                class="glass-panel rounded-2xl border border-dark-border hover:border-neon-cyan/60 hover:shadow-neon-cyan transition-all text-left cursor-pointer group overflow-hidden"
              >
                <div class="px-4 pt-4 pb-2">
                  <div class="flex items-center justify-between mb-1">
                    <span class="text-sm font-bold text-white group-hover:text-neon-cyan transition-colors">POSICIÓN {{ pos.id }}</span>
                    <span class="text-xs text-gray-500">{{ pos.alturas.length }} alturas</span>
                  </div>
                  <div class="text-xs text-gray-400 mb-2">
                    <span class="font-mono" [class]="pos.stock > 0 ? 'text-neon-cyan' : 'text-gray-500'">{{ pos.stock | number }} u.</span>
                    · {{ pos.productos }} prod.
                  </div>
                </div>
                <!-- Rack visual de alturas -->
                <div class="border-t border-dark-border">
                  @for (alt of pos.alturasDesc; track alt.codigo) {
                    <div
                      class="px-4 py-1.5 flex items-center justify-between text-xs border-b border-dark-border/50 last:border-b-0"
                      [class.bg-neon-cyan/5]="alt.stock > 0"
                    >
                      <span class="font-mono text-gray-300">{{ alt.codigo }}</span>
                      <span class="font-mono" [class]="alt.stock > 0 ? 'text-neon-cyan' : 'text-gray-500'">{{ alt.stock }} u.</span>
                    </div>
                  }
                </div>
              </button>
            }
          </div>
        }

        <!-- Nivel 3: Alturas de una posición (rack vertical) + Mini-mapa -->
        @if (posicionSel()) {
          <div class="flex items-center gap-3 mb-2">
            <button (click)="irAPasillo(pasilloSel()!)" class="px-3 py-1.5 rounded-lg text-xs font-medium text-gray-400 hover:text-white bg-dark-surface border border-dark-border hover:border-neon-cyan transition-colors cursor-pointer">
              ← Volver
            </button>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <!-- Mini-mapa del depósito -->
            <div class="glass-panel rounded-2xl border border-dark-border overflow-hidden">
              <div class="px-4 py-3 border-b border-dark-border flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                <h3 class="text-xs font-bold text-white uppercase tracking-wider">Planta del depósito</h3>
              </div>
              <div class="p-4 space-y-1.5">
                <!-- Encabezado de posiciones -->
                @if (mapaDeposito().length > 0) {
                  <div class="flex items-center gap-2 mb-1">
                    <span class="text-[10px] font-mono w-8 text-right shrink-0 text-gray-500">PAS</span>
                    <div class="flex gap-1 flex-1">
                      @for (slot of mapaDeposito()[0].posiciones; track slot.pos) {
                        <div class="flex-1 text-center text-[9px] font-mono text-gray-500">P{{ slot.pos }}</div>
                      }
                    </div>
                  </div>
                }
                @for (row of mapaDeposito(); track row.pasillo) {
                  <div class="flex items-center gap-2">
                    <span class="text-[10px] font-mono w-8 text-right shrink-0 font-semibold"
                      [class]="row.pasillo === pasilloSel() ? 'text-neon-cyan' : 'text-gray-400'">
                      {{ row.pasillo }}
                    </span>
                    <div class="flex gap-1 flex-1 rounded-md transition-all"
                      [class.ring-1]="row.pasillo === pasilloSel()"
                      [class.ring-neon-cyan/30]="row.pasillo === pasilloSel()"
                      [class.bg-neon-cyan/5]="row.pasillo === pasilloSel()"
                      [class.p-1]="row.pasillo === pasilloSel()">
                      @for (slot of row.posiciones; track slot.pos) {
                        <button
                          (click)="irAPasillo(row.pasillo); irAPosicion(slot.pos)"
                          [title]="'Pasillo ' + row.pasillo + ' · Posición ' + slot.pos"
                          class="flex-1 h-8 rounded flex items-center justify-center text-[10px] font-mono border transition-all cursor-pointer"
                          [class]="
                            row.pasillo === pasilloSel() && slot.pos === posicionSel()
                              ? 'bg-neon-cyan/25 border-neon-cyan text-neon-cyan font-bold shadow-[0_0_8px_rgba(34,211,238,0.3)]'
                              : slot.estado === 'LLENA'
                                ? (row.pasillo === pasilloSel() ? 'bg-red-500/20 border-red-500/40 text-red-300' : 'bg-red-500/15 border-red-500/25 text-red-400')
                                : slot.estado === 'INTERMEDIA'
                                  ? (row.pasillo === pasilloSel() ? 'bg-amber-500/20 border-amber-500/40 text-amber-300' : 'bg-amber-500/15 border-amber-500/25 text-amber-400')
                                  : slot.tieneStock
                                    ? (row.pasillo === pasilloSel() ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-300' : 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400')
                                    : (row.pasillo === pasilloSel() ? 'bg-dark-surface border-dark-border text-gray-500' : 'bg-dark-surface/50 border-dark-border/50 text-gray-600')
                          "
                        >{{ slot.pos }}</button>
                      }
                    </div>
                  </div>
                }
                <div class="flex flex-wrap items-center justify-center gap-3 pt-2 border-t border-dark-border/50 mt-2">
                  <div class="flex items-center gap-1.5">
                    <div class="w-3 h-3 rounded-sm bg-neon-cyan/25 border border-neon-cyan"></div>
                    <span class="text-[10px] text-gray-500">Actual</span>
                  </div>
                  <div class="flex items-center gap-1.5">
                    <div class="w-3 h-3 rounded-sm bg-emerald-500/15 border border-emerald-500/30"></div>
                    <span class="text-[10px] text-gray-500">Libre</span>
                  </div>
                  <div class="flex items-center gap-1.5">
                    <div class="w-3 h-3 rounded-sm bg-amber-500/20 border border-amber-500/40"></div>
                    <span class="text-[10px] text-gray-500">Intermedia</span>
                  </div>
                  <div class="flex items-center gap-1.5">
                    <div class="w-3 h-3 rounded-sm bg-red-500/20 border border-red-500/40"></div>
                    <span class="text-[10px] text-gray-500">Llena</span>
                  </div>
                  <div class="flex items-center gap-1.5">
                    <div class="w-3 h-3 rounded-sm bg-dark-surface/50 border border-dark-border/50"></div>
                    <span class="text-[10px] text-gray-500">Vacía</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Rack -->
            <div class="glass-panel rounded-2xl border border-dark-border overflow-hidden">
              <div class="px-4 py-3 border-b border-dark-border flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                </svg>
                <h3 class="text-xs font-bold text-white uppercase tracking-wider">
                  P{{ pasilloSel() }} · Posición {{ posicionSel() }}
                </h3>
              </div>
              <div>
                @for (alt of alturasActualesDesc(); track alt.ubicacion.id) {
                  <button
                    (click)="seleccionarUbicacion(alt.ubicacion)"
                    class="w-full px-4 py-3 flex items-center justify-between border-b border-dark-border/50 last:border-b-0 hover:bg-dark-surface/60 transition-colors cursor-pointer text-left"
                    [class.bg-neon-cyan/5]="ubicacionSel()?.id === alt.ubicacion.id"
                    [class.border-l-2]="ubicacionSel()?.id === alt.ubicacion.id"
                    [class.border-l-neon-cyan]="ubicacionSel()?.id === alt.ubicacion.id"
                  >
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="text-[10px] text-gray-400 font-mono">ALT {{ alt.altura }}</span>
                        <span class="font-mono font-bold text-white text-sm">{{ alt.ubicacion.codigo }}</span>
                      </div>
                      @if (alt.ubicacion.descripcion) {
                        <p class="text-[10px] text-gray-500 mt-0.5">{{ alt.ubicacion.descripcion }}</p>
                      }
                    </div>
                    <div class="text-right">
                      <div class="font-mono font-bold text-sm" [class]="alt.stock > 0 ? 'text-neon-cyan' : 'text-gray-500'">
                        {{ alt.stock }} u.
                      </div>
                      <div class="text-[10px] text-gray-500">{{ alt.productos }} prod.</div>
                    </div>
                  </button>
                }
              </div>
              <div class="px-4 py-2 border-t border-dark-border/30 text-center">
                <span class="text-[9px] text-gray-600 uppercase tracking-wider">↓ piso del depósito</span>
              </div>
            </div>

            <!-- Panel de detalle -->
            @if (ubicacionSel(); as u) {
              <div class="glass-panel rounded-2xl border border-neon-cyan/40 shadow-neon-cyan animate-fade-in">
                <div class="px-4 py-3 border-b border-dark-border flex items-center justify-between">
                  <span class="font-mono font-bold text-neon-cyan text-xl">{{ u.codigo }}</span>
                  <button (click)="ubicacionSel.set(null)" class="text-gray-500 hover:text-white cursor-pointer">
                    <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
                <div class="p-4 space-y-3">
                  @if (parseCodigo(u.codigo); as parsed) {
                    <div class="text-xs text-gray-300">
                      Pasillo {{ parsed.pasillo }} · Posición {{ parsed.posicion }} · Altura {{ parsed.altura }}
                    </div>
                  }
                  @if (u.descripcion) {
                    <p class="text-xs text-gray-400">{{ u.descripcion }}</p>
                  }

                  <div class="grid grid-cols-2 gap-2">
                    <div class="bg-dark-surface rounded-lg p-2.5 text-center">
                      <p class="text-xl font-bold font-mono" [class]="getStock(u.id).cantidadTotal > 0 ? 'text-neon-cyan' : 'text-gray-500'">
                        {{ getStock(u.id).cantidadTotal }}
                      </p>
                      <p class="text-[10px] text-gray-400 mt-0.5">unidades</p>
                    </div>
                    <div class="bg-dark-surface rounded-lg p-2.5 text-center">
                      <p class="text-xl font-bold font-mono text-neon-purple">{{ getStock(u.id).productosDistintos }}</p>
                      <p class="text-[10px] text-gray-400 mt-0.5">productos</p>
                    </div>
                  </div>

                  <div class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full" [class]="u.activo ? 'bg-neon-green' : 'bg-gray-500'"></span>
                    <span class="text-[10px]" [class]="u.activo ? 'text-neon-green' : 'text-gray-500'">{{ u.activo ? 'Activa' : 'Inactiva' }}</span>
                  </div>

                  <!-- Estado de ocupación -->
                  <div class="border-t border-dark-border pt-2">
                    <h4 class="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Estado de ocupación</h4>
                    <div class="flex gap-1">
                      <button
                        (click)="cambiarEstado(u, 'LIBRE')"
                        class="flex-1 px-2 py-1.5 rounded-lg text-[10px] font-semibold border transition-all cursor-pointer"
                        [class]="u.estadoOcupacion === 'LIBRE'
                          ? 'bg-emerald-500/20 border-emerald-500/50 text-emerald-300 shadow-[0_0_6px_rgba(16,185,129,0.2)]'
                          : 'bg-dark-surface border-dark-border text-gray-500 hover:border-emerald-500/30 hover:text-emerald-400'"
                      >LIBRE</button>
                      <button
                        (click)="cambiarEstado(u, 'INTERMEDIA')"
                        class="flex-1 px-2 py-1.5 rounded-lg text-[10px] font-semibold border transition-all cursor-pointer"
                        [class]="u.estadoOcupacion === 'INTERMEDIA'
                          ? 'bg-amber-500/20 border-amber-500/50 text-amber-300 shadow-[0_0_6px_rgba(245,158,11,0.2)]'
                          : 'bg-dark-surface border-dark-border text-gray-500 hover:border-amber-500/30 hover:text-amber-400'"
                      >INTERMEDIA</button>
                      <button
                        (click)="cambiarEstado(u, 'LLENA')"
                        class="flex-1 px-2 py-1.5 rounded-lg text-[10px] font-semibold border transition-all cursor-pointer"
                        [class]="u.estadoOcupacion === 'LLENA'
                          ? 'bg-red-500/20 border-red-500/50 text-red-300 shadow-[0_0_6px_rgba(239,68,68,0.2)]'
                          : 'bg-dark-surface border-dark-border text-gray-500 hover:border-red-500/30 hover:text-red-400'"
                      >LLENA</button>
                    </div>
                    @if (cambioEstadoOk()) {
                      <p class="text-[10px] text-emerald-400 mt-1">{{ cambioEstadoOk() }}</p>
                    }
                  </div>

                  <!-- Productos en esta ubicación -->
                  <div class="border-t border-dark-border pt-2">
                    <h4 class="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Productos</h4>
                    @if (cargandoDetalle()) {
                      <p class="text-[10px] text-gray-500 py-2">Cargando...</p>
                    } @else if (productosDetalle().length === 0) {
                      <p class="text-[10px] text-gray-500 py-2">Sin productos con stock.</p>
                    } @else {
                      <div class="space-y-1.5 max-h-44 overflow-y-auto">
                        @for (p of productosDetalle(); track p.productoId) {
                          <button
                            (click)="abrirAjusteProducto(p)"
                            class="w-full bg-dark-surface rounded-lg p-2 flex items-start justify-between gap-2 hover:bg-dark-surface/80 hover:ring-1 hover:ring-neon-cyan/30 transition-all cursor-pointer text-left"
                            title="Click para ajustar stock"
                          >
                            <div class="min-w-0 flex-1">
                              <p class="text-[11px] font-semibold text-white truncate">{{ p.descripcion }}</p>
                              <div class="flex flex-wrap items-center gap-1 mt-0.5">
                                @if (p.marcaNombre) {
                                  <span class="text-[9px] px-1.5 py-0.5 rounded bg-neon-purple/15 text-neon-purple border border-neon-purple/20">{{ p.marcaNombre }}</span>
                                }
                                @if (p.categoriaNombre) {
                                  <span class="text-[9px] px-1.5 py-0.5 rounded bg-neon-cyan/15 text-neon-cyan border border-neon-cyan/20">{{ p.categoriaNombre }}</span>
                                }
                                @if (p.sku) {
                                  <span class="text-[9px] font-mono text-gray-500">{{ p.sku }}</span>
                                }
                              </div>
                            </div>
                            <div class="text-right shrink-0 flex items-center gap-1.5">
                              <div>
                                <span class="text-sm font-bold font-mono text-neon-cyan">{{ p.cantidad }}</span>
                                <span class="text-[9px] text-gray-500 block">u.</span>
                              </div>
                              <svg class="w-3 h-3 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                              </svg>
                            </div>
                          </button>
                        }
                      </div>
                    }
                  </div>

                  <div class="flex gap-2 pt-2 border-t border-dark-border">
                    <button (click)="editar(u)" class="flex-1 px-3 py-2 rounded-xl text-xs font-semibold text-neon-cyan bg-neon-cyan/10 border border-neon-cyan/30 hover:bg-neon-cyan/20 transition-colors cursor-pointer">
                      Editar
                    </button>
                    <button (click)="pedirEliminar(u)" class="flex-1 px-3 py-2 rounded-xl text-xs font-semibold text-red-400 bg-red-500/10 border border-red-500/30 hover:bg-red-500/20 transition-colors cursor-pointer">
                      Eliminar
                    </button>
                  </div>
                </div>
              </div>
            } @else {
              <div class="glass-panel rounded-2xl border border-dark-border flex items-center justify-center p-8">
                <p class="text-sm text-gray-500 text-center">Seleccioná una altura para ver el detalle.</p>
              </div>
            }
          </div>
        }

      } @else {
        <!-- ======= VISTA LISTA ======= -->
        <div class="glass-panel rounded-2xl p-4 md:p-6 border border-dark-border shadow-card">
          @if (ubicacionesFiltradas().length === 0) {
            <div class="py-12 text-center text-gray-500">
              {{ busqueda() ? 'No se encontraron ubicaciones para "' + busqueda() + '".' : 'No hay ubicaciones registradas.' }}
            </div>
          } @else {
            <div class="overflow-x-auto">
              <table class="w-full text-left text-sm border-collapse min-w-[640px]">
                <thead>
                  <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                    <th class="py-3 px-4">Código</th>
                    <th class="py-3 px-4">Pasillo</th>
                    <th class="py-3 px-4">Posición</th>
                    <th class="py-3 px-4">Altura</th>
                    <th class="py-3 px-4">Descripción</th>
                    <th class="py-3 px-4 text-right">Stock</th>
                    <th class="py-3 px-4">Estado</th>
                    <th class="py-3 px-4 text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-dark-border/50">
                  @for (u of ubicacionesFiltradas(); track u.id) {
                    <tr class="hover:bg-dark-surface/40 transition-colors">
                      <td class="py-3 px-4 font-mono font-bold text-neon-cyan">{{ u.codigo }}</td>
                      @if (parseCodigo(u.codigo); as parsed) {
                        <td class="py-3 px-4 font-mono text-gray-300">{{ parsed.pasillo }}</td>
                        <td class="py-3 px-4 font-mono text-gray-300">{{ parsed.posicion }}</td>
                        <td class="py-3 px-4 font-mono text-gray-300">{{ parsed.altura }}</td>
                      } @else {
                        <td class="py-3 px-4 text-gray-500" colspan="3">—</td>
                      }
                      <td class="py-3 px-4 text-gray-300">{{ u.descripcion || '—' }}</td>
                      <td class="py-3 px-4 text-right font-mono" [class]="getStock(u.id).cantidadTotal > 0 ? 'text-neon-cyan' : 'text-gray-500'">
                        {{ getStock(u.id).cantidadTotal }} u.
                      </td>
                      <td class="py-3 px-4">
                        <span class="px-2 py-0.5 rounded-md text-xs font-semibold" [class]="u.activo ? 'neon-badge-green' : 'bg-gray-500/20 text-gray-400'">
                          {{ u.activo ? 'Activa' : 'Inactiva' }}
                        </span>
                      </td>
                      <td class="py-3 px-4">
                        <div class="flex items-center justify-end gap-3">
                          <button (click)="editar(u)" class="text-xs font-semibold text-neon-cyan hover:text-white transition-colors cursor-pointer">Editar</button>
                          <button (click)="pedirEliminar(u)" class="text-xs font-semibold text-red-400 hover:text-red-300 transition-colors cursor-pointer">Eliminar</button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }
    </div>

    <!-- Modal de ajuste de stock desde ubicación -->
    @if (productoAjuste(); as pa) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="cerrarAjuste()">
        <div class="glass-panel w-full max-w-md p-6 rounded-2xl border border-neon-purple/40 shadow-neon animate-fade-in" (click)="$event.stopPropagation()">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-bold text-white flex items-center gap-2">
              <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
              </svg>
              Ajustar stock
            </h3>
            <button (click)="cerrarAjuste()" class="text-gray-500 hover:text-white cursor-pointer">
              <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="bg-dark-surface rounded-xl p-3 mb-4">
            <p class="text-sm font-semibold text-white">{{ pa.descripcion }}</p>
            <div class="flex items-center gap-2 mt-1 text-xs text-gray-400">
              @if (pa.marcaNombre) { <span>{{ pa.marcaNombre }}</span> }
              @if (pa.sku) { <span class="font-mono">{{ pa.sku }}</span> }
            </div>
            <div class="flex items-center gap-2 mt-2">
              <span class="text-xs text-gray-400">Stock actual:</span>
              <span class="text-sm font-bold font-mono text-neon-cyan">{{ pa.cantidad }} u.</span>
              <span class="text-xs text-gray-400 ml-1">en</span>
              <span class="text-sm font-mono text-neon-purple">{{ ubicacionSel()?.codigo }}</span>
            </div>
          </div>

          <div class="space-y-3">
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Nueva cantidad</label>
              <input
                [(ngModel)]="ajusteCantidad"
                type="number" min="0"
                placeholder="Cantidad"
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white font-mono placeholder-gray-500 focus:outline-none focus:border-neon-purple text-sm"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Motivo</label>
              <input
                [(ngModel)]="ajusteMotivo"
                type="text"
                placeholder="Motivo del ajuste..."
                class="w-full px-3.5 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-purple text-sm"
              />
            </div>
          </div>

          @if (ajusteError()) {
            <div class="mt-3 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ ajusteError() }}</div>
          }
          @if (ajusteOk()) {
            <div class="mt-3 p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-400 text-xs">{{ ajusteOk() }}</div>
          }

          <div class="mt-5 flex flex-wrap justify-end gap-3">
            <button (click)="cerrarAjuste()" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">
              Cancelar
            </button>
            <button
              [disabled]="guardandoAjuste() || ajusteCantidad == null"
              (click)="confirmarAjusteStock()"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50"
            >
              {{ guardandoAjuste() ? 'Guardando...' : 'Confirmar ajuste' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Modal de confirmación de guardar ubicación -->
    @if (confirmarGuardarUbicacion()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="confirmarGuardarUbicacion.set(false)">
        <div class="glass-panel w-full max-w-sm p-6 rounded-2xl border border-amber-500/40 shadow-neon" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            Confirmar guardado
          </h3>
          <p class="text-sm text-gray-300">
            {{ editandoId() ? '¿Confirmar la modificación de esta ubicación?' : '¿Confirmar la creación de una nueva ubicación?' }}
          </p>
          <div class="mt-5 flex flex-wrap justify-end gap-3">
            <button (click)="confirmarGuardarUbicacion.set(false)" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">
              Cancelar
            </button>
            <button (click)="guardarConfirmado()" class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer">
              Confirmar
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Modal de confirmación de borrado -->
    @if (aEliminar(); as u) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="cancelarEliminar()">
        <div class="glass-panel w-full max-w-md p-6 rounded-2xl border border-red-500/40 shadow-neon" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            Eliminar ubicación
          </h3>
          <p class="text-sm text-gray-300">
            Estás por eliminar la ubicación <span class="font-mono font-bold text-neon-cyan">{{ u.codigo }}</span>.
            Esta acción no se puede deshacer.
          </p>
          @if (error()) {
            <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ error() }}</div>
          }
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="cancelarEliminar()" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">
              Cancelar
            </button>
            <button [disabled]="eliminando()" (click)="eliminarConfirmado(u)" class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-600 hover:bg-red-500 text-white transition-colors cursor-pointer disabled:opacity-50">
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
  private stockService = inject(StockService);
  @ViewChild('formPanel') formPanel?: ElementRef<HTMLElement>;

  ubicaciones = signal<Ubicacion[]>([]);
  stockMap = signal<Record<number, StockPorUbicacion>>({});
  cargando = signal(true);

  vistaActual = signal<'mapa' | 'lista'>('mapa');
  pasilloSel = signal<string | null>(null);
  posicionSel = signal<string | null>(null);
  ubicacionSel = signal<Ubicacion | null>(null);

  busqueda = signal('');

  mostrarForm = signal(false);
  modoFormAsistido = signal(true);
  editandoId = signal<number | null>(null);
  codigo = '';
  descripcion = '';
  formPasillo = '';
  formPosicion = '';
  formAltura = '';
  guardando = signal(false);
  error = signal<string | null>(null);

  aEliminar = signal<Ubicacion | null>(null);
  eliminando = signal(false);

  productosDetalle = signal<StockDetalleUbicacion[]>([]);
  cargandoDetalle = signal(false);

  productoAjuste = signal<StockDetalleUbicacion | null>(null);
  ajusteCantidad: number | null = null;
  ajusteMotivo = '';
  guardandoAjuste = signal(false);
  ajusteError = signal<string | null>(null);
  ajusteOk = signal<string | null>(null);

  confirmarGuardarUbicacion = signal(false);
  cambioEstadoOk = signal<string | null>(null);

  parseCodigo = parseUbicacionCodigo;

  codigoGenerado = computed(() => {
    const p = this.formPasillo.padStart(2, '0');
    const pos = this.formPosicion.padStart(2, '0');
    const a = this.formAltura.padStart(2, '0');
    if (this.formPasillo && this.formPosicion && this.formAltura) {
      return p + pos + a;
    }
    return '';
  });

  private ubicacionesParsed = computed(() => {
    return this.ubicaciones().map(u => ({
      ubicacion: u,
      parsed: parseUbicacionCodigo(u.codigo),
    }));
  });

  ubicacionesFiltradas = computed(() => {
    const q = this.busqueda().toLowerCase().trim();
    if (!q) return this.ubicaciones();
    return this.ubicaciones().filter(u =>
      u.codigo.toLowerCase().includes(q) ||
      (u.descripcion?.toLowerCase().includes(q) ?? false)
    );
  });

  ubicacionesNoEstandar = computed(() => {
    return this.ubicacionesParsed()
      .filter(x => !x.parsed)
      .map(x => x.ubicacion);
  });

  metricas = computed(() => {
    const all = this.ubicaciones();
    const sm = this.stockMap();
    const pasillosSet = new Set<string>();
    let conStock = 0;
    let unidades = 0;

    for (const u of all) {
      const p = parseUbicacionCodigo(u.codigo);
      if (p) pasillosSet.add(p.pasillo);
      const s = sm[u.id];
      if (s && s.cantidadTotal > 0) {
        conStock++;
        unidades += s.cantidadTotal;
      }
    }

    return {
      total: all.length,
      conStock,
      unidades,
      pasillos: pasillosSet.size,
    };
  });

  pasillosFiltrados = computed(() => {
    const parsed = this.ubicacionesParsed().filter(x => x.parsed);
    const q = this.busqueda().toLowerCase().trim();
    const filtrados = q
      ? parsed.filter(x =>
          x.ubicacion.codigo.toLowerCase().includes(q) ||
          (x.ubicacion.descripcion?.toLowerCase().includes(q) ?? false))
      : parsed;

    const groups = new Map<string, typeof filtrados>();
    for (const item of filtrados) {
      const key = item.parsed!.pasillo;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(item);
    }

    const sm = this.stockMap();
    let maxStock = 0;
    const result: { id: string; posiciones: number; ubicaciones: number; stock: number; productos: number; stockRelativo: number }[] = [];

    for (const [pasillo, items] of groups) {
      const posSet = new Set(items.map(x => x.parsed!.posicion));
      let stock = 0;
      let productos = 0;
      for (const item of items) {
        const s = sm[item.ubicacion.id];
        if (s) {
          stock += s.cantidadTotal;
          productos += s.productosDistintos;
        }
      }
      if (stock > maxStock) maxStock = stock;
      result.push({ id: pasillo, posiciones: posSet.size, ubicaciones: items.length, stock, productos, stockRelativo: 0 });
    }

    for (const r of result) {
      r.stockRelativo = maxStock > 0 ? Math.round((r.stock / maxStock) * 100) : 0;
    }

    return result.sort((a, b) => a.id.localeCompare(b.id));
  });

  ubicacionesDelPasillo = computed(() => {
    const sel = this.pasilloSel();
    if (!sel) return [];
    return this.ubicacionesParsed()
      .filter(x => x.parsed?.pasillo === sel)
      .map(x => x.ubicacion);
  });

  posicionesDelPasillo = computed(() => {
    const sel = this.pasilloSel();
    if (!sel) return [];

    const items = this.ubicacionesParsed().filter(x => x.parsed?.pasillo === sel);
    const sm = this.stockMap();
    const groups = new Map<string, { ubicacion: Ubicacion; parsed: ParsedCodigo }[]>();

    for (const item of items) {
      const key = item.parsed!.posicion;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(item as { ubicacion: Ubicacion; parsed: ParsedCodigo });
    }

    const result: { id: string; alturas: { codigo: string; altura: string; stock: number }[]; alturasDesc: { codigo: string; altura: string; stock: number }[]; stock: number; productos: number }[] = [];

    for (const [posicion, posItems] of groups) {
      let stock = 0;
      let productos = 0;
      const alturas = posItems.map(item => {
        const s = sm[item.ubicacion.id];
        const st = s?.cantidadTotal ?? 0;
        stock += st;
        productos += s?.productosDistintos ?? 0;
        return { codigo: item.ubicacion.codigo, altura: item.parsed.altura, stock: st };
      }).sort((a, b) => a.altura.localeCompare(b.altura));

      result.push({
        id: posicion,
        alturas,
        alturasDesc: [...alturas].reverse(),
        stock,
        productos,
      });
    }

    return result.sort((a, b) => a.id.localeCompare(b.id));
  });

  alturasActuales = computed(() => {
    const pasillo = this.pasilloSel();
    const posicion = this.posicionSel();
    if (!pasillo || !posicion) return [];

    const sm = this.stockMap();
    return this.ubicacionesParsed()
      .filter(x => x.parsed?.pasillo === pasillo && x.parsed?.posicion === posicion)
      .map(x => {
        const s = sm[x.ubicacion.id];
        return {
          ubicacion: x.ubicacion,
          altura: x.parsed!.altura,
          stock: s?.cantidadTotal ?? 0,
          productos: s?.productosDistintos ?? 0,
        };
      })
      .sort((a, b) => a.altura.localeCompare(b.altura));
  });

  alturasActualesDesc = computed(() => [...this.alturasActuales()].reverse());

  mapaDeposito = computed(() => {
    const parsed = this.ubicacionesParsed().filter(x => x.parsed);
    const sm = this.stockMap();
    const estadoPrioridad: Record<string, number> = { LIBRE: 0, INTERMEDIA: 1, LLENA: 2 };
    const pasilloMap = new Map<string, Map<string, { tieneStock: boolean; estado: EstadoOcupacion }>>();

    for (const item of parsed) {
      const p = item.parsed!.pasillo;
      const pos = item.parsed!.posicion;
      if (!pasilloMap.has(p)) pasilloMap.set(p, new Map());
      const posMap = pasilloMap.get(p)!;
      const s = sm[item.ubicacion.id];
      const tieneStock = (s?.cantidadTotal ?? 0) > 0;
      const estado = item.ubicacion.estadoOcupacion ?? 'LIBRE';
      const prev = posMap.get(pos);
      if (!prev || estadoPrioridad[estado] > estadoPrioridad[prev.estado]) {
        posMap.set(pos, { tieneStock: prev?.tieneStock || tieneStock, estado });
      } else if (tieneStock && !prev.tieneStock) {
        posMap.set(pos, { ...prev, tieneStock: true });
      }
    }

    return Array.from(pasilloMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([pasillo, posMap]) => ({
        pasillo,
        posiciones: Array.from(posMap.entries())
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([pos, data]) => ({ pos, tieneStock: data.tieneStock, estado: data.estado })),
      }));
  });

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
    this.service.stockResumen().subscribe({
      next: (map) => this.stockMap.set(map),
    });
  }

  getStock(ubicacionId: number): StockPorUbicacion {
    return this.stockMap()[ubicacionId] ?? { ubicacionId, cantidadTotal: 0, productosDistintos: 0 };
  }

  irAPasillo(pasillo: string): void {
    this.pasilloSel.set(pasillo);
    this.posicionSel.set(null);
    this.ubicacionSel.set(null);
  }

  irAPosicion(posicion: string): void {
    this.posicionSel.set(posicion);
    this.ubicacionSel.set(null);
  }

  volverAMapa(): void {
    this.pasilloSel.set(null);
    this.posicionSel.set(null);
    this.ubicacionSel.set(null);
  }

  seleccionarUbicacion(u: Ubicacion): void {
    this.ubicacionSel.set(u);
    this.cargarDetalle(u.id);
  }

  private cargarDetalle(ubicacionId: number): void {
    this.cargandoDetalle.set(true);
    this.productosDetalle.set([]);
    this.service.stockDetalle(ubicacionId).subscribe({
      next: (list) => {
        this.productosDetalle.set(list);
        this.cargandoDetalle.set(false);
      },
      error: () => this.cargandoDetalle.set(false),
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
    this.modoFormAsistido.set(false);
    this.error.set(null);
    this.mostrarForm.set(true);
    setTimeout(() => this.formPanel?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  cancelar(): void {
    this.mostrarForm.set(false);
    this.limpiarForm();
  }

  guardar(): void {
    const codigoFinal = this.editandoId()
      ? this.codigo.trim()
      : (this.modoFormAsistido() ? this.codigoGenerado() : this.codigo.trim());

    if (!codigoFinal) {
      this.error.set('El código de ubicación es obligatorio');
      return;
    }
    this.error.set(null);
    this.confirmarGuardarUbicacion.set(true);
  }

  guardarConfirmado(): void {
    this.confirmarGuardarUbicacion.set(false);
    const codigoFinal = this.editandoId()
      ? this.codigo.trim()
      : (this.modoFormAsistido() ? this.codigoGenerado() : this.codigo.trim());

    this.guardando.set(true);
    const req = { codigo: codigoFinal, descripcion: this.descripcion.trim() || null };
    const id = this.editandoId();
    const op = id ? this.service.actualizar(id, req) : this.service.crear(req);

    const editId = this.editandoId();
    const descFinal = this.descripcion.trim() || null;
    op.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarForm.set(false);
        if (editId && this.ubicacionSel()?.id === editId) {
          this.ubicacionSel.update(sel => sel ? { ...sel, codigo: codigoFinal, descripcion: descFinal } : null);
        }
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
        if (this.ubicacionSel()?.id === u.id) this.ubicacionSel.set(null);
        this.cargar();
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'No se pudo eliminar la ubicación');
        this.eliminando.set(false);
      },
    });
  }

  cambiarEstado(u: Ubicacion, estado: EstadoOcupacion): void {
    if (u.estadoOcupacion === estado) return;
    this.cambioEstadoOk.set(null);
    this.service.cambiarEstadoOcupacion(u.id, estado).subscribe({
      next: (updated) => {
        this.ubicaciones.update(list => list.map(x => x.id === u.id ? updated : x));
        this.ubicacionSel.update(sel => sel?.id === u.id ? updated : sel);
        this.cambioEstadoOk.set('Estado actualizado');
        setTimeout(() => this.cambioEstadoOk.set(null), 2000);
      },
    });
  }

  abrirAjusteProducto(p: StockDetalleUbicacion): void {
    this.productoAjuste.set(p);
    this.ajusteCantidad = p.cantidad;
    this.ajusteMotivo = '';
    this.ajusteError.set(null);
    this.ajusteOk.set(null);
  }

  cerrarAjuste(): void {
    this.productoAjuste.set(null);
    this.ajusteCantidad = null;
    this.ajusteMotivo = '';
    this.ajusteError.set(null);
    this.ajusteOk.set(null);
  }

  confirmarAjusteStock(): void {
    const pa = this.productoAjuste();
    const u = this.ubicacionSel();
    if (!pa || !u || this.ajusteCantidad == null) return;

    this.guardandoAjuste.set(true);
    this.ajusteError.set(null);
    this.ajusteOk.set(null);

    this.stockService.conteo({
      productoId: pa.productoId,
      ubicacionId: u.id,
      cantidadReal: this.ajusteCantidad,
      motivo: this.ajusteMotivo.trim() || 'Ajuste desde ubicaciones',
    }).subscribe({
      next: () => {
        this.guardandoAjuste.set(false);
        this.ajusteOk.set('Stock ajustado correctamente');
        this.cargarDetalle(u.id);
        this.service.stockResumen().subscribe({ next: (map) => this.stockMap.set(map) });
        setTimeout(() => {
          this.cerrarAjuste();
        }, 1200);
      },
      error: (e) => {
        this.ajusteError.set(e?.error?.message ?? 'No se pudo ajustar el stock');
        this.guardandoAjuste.set(false);
      },
    });
  }

  private limpiarForm(): void {
    this.editandoId.set(null);
    this.codigo = '';
    this.descripcion = '';
    this.formPasillo = '';
    this.formPosicion = '';
    this.formAltura = '';
    this.modoFormAsistido.set(true);
    this.error.set(null);
  }
}
