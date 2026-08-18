import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';

import { Marca, ProductoListItem, StockResumen, Ubicacion } from '../core/models';
import { AuthService } from '../core/auth.service';
import { MarcaService } from '../core/marca.service';
import { ProductoService } from '../core/producto.service';
import { StockService } from '../core/stock.service';
import { UbicacionService } from '../core/ubicacion.service';

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
            {{ editandoId() ? 'Editar Producto #' + editandoId() : 'Alta Manual de Producto' }}
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

            @if (!editandoId()) {
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
            }

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

          <div class="mt-5 flex justify-end gap-2">
            @if (editandoId()) {
              <button (click)="cancelarEdicion()" class="px-4 py-2.5 rounded-xl font-semibold text-sm text-gray-400 hover:text-white cursor-pointer">
                Cancelar
              </button>
            }
            <button
              [disabled]="guardando()"
              (click)="guardar()"
              class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50"
            >
              {{ guardando() ? 'Guardando...' : (editandoId() ? 'Guardar Cambios' : 'Guardar Producto') }}
            </button>
          </div>
        </div>
      }

      <!-- Products Table -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <!-- Buscador multi-característico -->
        <div class="flex items-center gap-2 mb-5">
          <div class="relative flex-1">
            <svg class="w-4 h-4 text-gray-500 absolute left-3 top-1/2 -translate-y-1/2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              [(ngModel)]="q"
              type="text"
              placeholder="Buscar por código, SKU, marca, categoría o descripción…"
              (keyup.enter)="buscar()"
              (input)="onQueryInput()"
              class="w-full pl-9 pr-3 py-2.5 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-cyan text-sm"
            />
          </div>
          <button (click)="buscar()" class="px-5 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer">Buscar</button>
          @if (q.trim()) {
            <button (click)="limpiarBusqueda()" class="px-4 py-2.5 rounded-xl text-sm font-semibold text-gray-400 hover:text-white cursor-pointer">Limpiar</button>
          }
        </div>

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
                  <th class="py-3 px-4">SKU</th>
                  <th class="py-3 px-4">Descripción</th>
                  <th class="py-3 px-4">Marca</th>
                  <th class="py-3 px-4">Categoría</th>
                  <th class="py-3 px-4">Ubicación</th>
                  <th class="py-3 px-4 text-right">Cantidad</th>
                  <th class="py-3 px-4">Estado</th>
                  <th class="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border/50">
                @for (p of productos(); track p.id) {
                  <tr class="hover:bg-dark-surface/40 transition-colors">
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
                      @if (p.ubicaciones && p.ubicaciones.length > 0) {
                        <div class="flex flex-wrap gap-1">
                          @for (u of p.ubicaciones; track u.codigo) {
                            <span class="neon-badge-purple px-2 py-0.5 rounded text-[11px] font-mono">{{ u.codigo }} ({{ u.cantidad }})</span>
                          }
                        </div>
                      } @else {
                        <span class="text-gray-500 text-xs">—</span>
                      }
                    </td>
                    <td class="py-3.5 px-4 text-right font-mono font-bold"
                        [class]="p.stockTotal > 0 ? 'text-neon-green' : 'text-gray-500'">
                      {{ p.stockTotal }}
                    </td>
                    <td class="py-3.5 px-4">
                      <span class="px-2.5 py-1 rounded-md text-xs font-semibold"
                            [class]="p.estado === 'ACTIVO' ? 'neon-badge-green' : 'bg-gray-500/15 text-gray-400 border border-gray-500/30'">
                        {{ p.estado }}
                      </span>
                    </td>
                    <td class="py-3.5 px-4 text-right">
                      <button
                        (click)="abrirMenu(p)"
                        class="w-8 h-8 inline-flex items-center justify-center rounded-lg bg-dark-surface border border-dark-border hover:border-neon-cyan text-neon-cyan text-lg font-bold cursor-pointer"
                        title="Acciones"
                      >
                        +
                      </button>
                    </td>
                  </tr>
                  @if (agregandoCodigoId() === p.id) {
                    <tr class="bg-dark-surface/40">
                      <td colspan="8" class="py-3 px-4">
                        <div class="flex flex-wrap items-center gap-2">
                          <span class="text-xs text-gray-400">Código de barras para <span class="text-white font-semibold">{{ p.descripcion }}</span>:</span>
                          <input
                            [(ngModel)]="nuevoCodigo"
                            type="text"
                            placeholder="Escaneá o escribí el código"
                            (keyup.enter)="guardarCodigo(p.id)"
                            class="flex-1 min-w-48 px-3 py-1.5 bg-dark-surface border border-neon-cyan/50 rounded-lg text-white font-mono text-sm focus:outline-none focus:border-neon-cyan"
                          />
                          <button [disabled]="codigoGuardando()" (click)="guardarCodigo(p.id)" class="px-3 py-1.5 rounded-lg text-xs font-semibold neon-button-primary cursor-pointer disabled:opacity-50">Asociar</button>
                          <button (click)="cerrarCodigo()" class="px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-400 hover:underline cursor-pointer">Cancelar</button>
                          @if (codigoError()) {
                            <span class="text-xs text-red-400 w-full">{{ codigoError() }}</span>
                          }
                        </div>
                      </td>
                    </tr>
                  }
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

    <!-- Menú de acciones -->
    @if (menu(); as p) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="cerrarMenu()">
        <div class="glass-panel w-full max-w-xs p-4 rounded-2xl border border-neon-cyan/40 shadow-neon-cyan" (click)="$event.stopPropagation()">
          <p class="text-xs uppercase tracking-wider text-gray-400 mb-1">Acciones</p>
          <p class="text-sm font-semibold text-white mb-4 truncate">{{ p.descripcion }}</p>
          <div class="space-y-2">
            <button (click)="modificar(p)" class="w-full text-left px-3 py-2.5 rounded-lg text-sm text-gray-200 bg-dark-surface border border-dark-border hover:border-neon-cyan transition-colors cursor-pointer">Modificar datos</button>
            <button (click)="gestionarStock(p)" class="w-full text-left px-3 py-2.5 rounded-lg text-sm text-gray-200 bg-dark-surface border border-dark-border hover:border-neon-green transition-colors cursor-pointer">Gestionar stock</button>
            <button (click)="agregarCodigoDe(p)" class="w-full text-left px-3 py-2.5 rounded-lg text-sm text-gray-200 bg-dark-surface border border-dark-border hover:border-neon-cyan transition-colors cursor-pointer">Agregar código</button>
            @if (esAdmin) {
              <button (click)="pedirBaja(p)" class="w-full text-left px-3 py-2.5 rounded-lg text-sm text-red-400 bg-dark-surface border border-dark-border hover:border-red-500/50 transition-colors cursor-pointer">Eliminar (dar de baja)</button>
            }
          </div>
          <button (click)="cerrarMenu()" class="mt-4 w-full px-3 py-2 rounded-lg text-sm text-gray-400 hover:text-white cursor-pointer">Cancelar</button>
        </div>
      </div>
    }

    <!-- Confirmación de baja -->
    @if (aEliminar(); as p) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="cancelarBaja()">
        <div class="glass-panel w-full max-w-md p-6 rounded-2xl border border-red-500/40 shadow-neon" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            Dar de baja producto
          </h3>
          <p class="text-sm text-gray-300">
            Estás por dar de baja <span class="font-semibold text-white">{{ p.descripcion }}</span>.
            Dejará de figurar como activo, pero se conserva su historial. ¿Seguro?
          </p>
          @if (bajaError()) {
            <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ bajaError() }}</div>
          }
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="cancelarBaja()" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">Cancelar</button>
            <button [disabled]="eliminando()" (click)="confirmarBaja(p)" class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-600 hover:bg-red-500 text-white transition-colors cursor-pointer disabled:opacity-50">
              {{ eliminando() ? 'Procesando...' : 'Aceptar' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Modal de eliminación de marca -->
    @if (aMarcaEliminar(); as m) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="cancelarEliminarMarca()">
        <div class="glass-panel w-full max-w-md p-6 rounded-2xl border border-red-500/40 shadow-neon" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 mb-3">
            <svg class="w-6 h-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            Eliminar marca
          </h3>
          <p class="text-sm text-gray-300">
            ¿Estás seguro de eliminar la marca <span class="font-semibold text-white">"{{ m.nombre }}"</span>?
            Si hay productos asociados, no se podrá eliminar.
          </p>
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="cancelarEliminarMarca()" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 transition-colors cursor-pointer">Cancelar</button>
            <button (click)="confirmarEliminarMarca(m)" class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-red-600 hover:bg-red-500 text-white transition-colors cursor-pointer">
              Eliminar
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Modal de gestión de stock -->
    @if (stockProducto(); as p) {
      <div class="fixed inset-0 z-50 flex items-start justify-center bg-black/70 p-4 overflow-y-auto" (click)="cerrarStock()">
        <div class="glass-panel w-full max-w-2xl my-8 p-5 md:p-6 rounded-2xl border border-neon-green/40 shadow-neon space-y-5" (click)="$event.stopPropagation()">
          <div class="flex items-start justify-between gap-3">
            <div class="space-y-1 min-w-0">
              <p class="text-xs uppercase tracking-wider text-gray-400">Gestionar stock</p>
              <p class="text-lg font-bold text-white truncate">{{ p.descripcion }}</p>
              <p class="text-sm font-mono text-neon-cyan">
                SKU {{ p.sku || '—' }}
                @if (p.marcaNombre) { <span class="text-gray-400"> · {{ p.marcaNombre }}</span> }
              </p>
            </div>
            <button (click)="cerrarStock()" class="text-gray-400 hover:text-white cursor-pointer text-lg shrink-0">✕</button>
          </div>

          @if (stockResumen(); as r) {
            <div class="flex flex-wrap gap-3">
              <div class="text-center px-4 py-2 rounded-xl bg-dark-surface/60 border border-dark-border">
                <p class="text-xl font-extrabold text-neon-green font-mono">{{ r.total }}</p>
                <p class="text-[10px] uppercase text-gray-400 font-semibold">Unidades</p>
              </div>
              <div class="text-center px-4 py-2 rounded-xl bg-dark-surface/60 border border-dark-border">
                <p class="text-xl font-extrabold text-neon-purple font-mono">{{ r.ubicaciones.length }}</p>
                <p class="text-[10px] uppercase text-gray-400 font-semibold">Ubicaciones</p>
              </div>
            </div>
          }

          <!-- Operación: entrada / salida -->
          <div class="border-t border-dark-border pt-4 space-y-3">
            <div class="flex gap-2">
              <button (click)="stockOpTipo.set('entrada')"
                [class]="stockOpTipo() === 'entrada'
                  ? 'px-4 py-1.5 rounded-lg text-xs font-semibold bg-green-500/15 text-green-400 border border-green-500/30 cursor-pointer'
                  : 'px-4 py-1.5 rounded-lg text-xs font-medium text-gray-400 hover:text-white cursor-pointer'">
                Entrada
              </button>
              <button (click)="stockOpTipo.set('salida')"
                [class]="stockOpTipo() === 'salida'
                  ? 'px-4 py-1.5 rounded-lg text-xs font-semibold bg-red-500/15 text-red-400 border border-red-500/30 cursor-pointer'
                  : 'px-4 py-1.5 rounded-lg text-xs font-medium text-gray-400 hover:text-white cursor-pointer'">
                Salida
              </button>
              <button (click)="stockOpTipo.set('ajuste')"
                [class]="stockOpTipo() === 'ajuste'
                  ? 'px-4 py-1.5 rounded-lg text-xs font-semibold bg-amber-500/15 text-amber-400 border border-amber-500/30 cursor-pointer'
                  : 'px-4 py-1.5 rounded-lg text-xs font-medium text-gray-400 hover:text-white cursor-pointer'">
                Ajuste
              </button>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">
                  {{ stockOpTipo() === 'salida' ? 'Ubicación origen' : 'Ubicación' }}
                </label>
                <select [(ngModel)]="stockOpUbicacionId"
                  class="w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-green text-sm">
                  <option [ngValue]="null" disabled>Elegí ubicación…</option>
                  @if (stockOpTipo() === 'salida') {
                    @for (u of stockResumen()?.ubicaciones ?? []; track u.ubicacionId) {
                      <option [ngValue]="u.ubicacionId">{{ u.ubicacionPath }} ({{ u.cantidad }} u.)</option>
                    }
                  } @else {
                    @for (u of stockUbicaciones(); track u.id) {
                      <option [ngValue]="u.id">{{ u.codigo }}{{ u.descripcion ? ' — ' + u.descripcion : '' }}</option>
                    }
                  }
                </select>
              </div>
              @if (stockOpTipo() === 'ajuste') {
                <div>
                  <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Tipo</label>
                  <select [(ngModel)]="stockAjusteTipo"
                    class="w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-xl text-white focus:outline-none focus:border-neon-green text-sm">
                    <option value="AJUSTE_POSITIVO">Ajuste positivo (+)</option>
                    <option value="AJUSTE_NEGATIVO">Ajuste negativo (−)</option>
                  </select>
                </div>
              }
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Cantidad</label>
                <input [(ngModel)]="stockOpCantidad" type="number" min="1" placeholder="0"
                  class="w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-xl text-white font-mono focus:outline-none focus:border-neon-green text-sm" />
              </div>
              <div>
                <label class="block text-xs font-semibold uppercase tracking-wider text-gray-300 mb-1">Motivo{{ stockOpTipo() === 'ajuste' ? '' : ' (opcional)' }}</label>
                <input [(ngModel)]="stockOpMotivo" type="text" placeholder="Ej: ingreso de mercadería"
                  class="w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-neon-green text-sm" />
              </div>
            </div>

            @if (stockOpError()) {
              <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ stockOpError() }}</div>
            }
            @if (stockOpOk()) {
              <div class="p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-xs">{{ stockOpOk() }}</div>
            }

            <div class="flex justify-end">
              <button [disabled]="stockOpGuardando()" (click)="ejecutarStockOp()"
                class="px-5 py-2 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50">
                {{ stockOpGuardando() ? 'Procesando...'
                  : stockOpTipo() === 'entrada' ? 'Registrar entrada'
                  : stockOpTipo() === 'salida' ? 'Registrar salida'
                  : 'Registrar ajuste' }}
              </button>
            </div>
          </div>

          <!-- Stock actual por ubicación -->
          @if (stockResumen(); as r) {
            @if (r.ubicaciones.length > 0) {
              <div class="border-t border-dark-border pt-4 space-y-3">
                <h4 class="text-sm font-bold text-white">Stock por ubicación</h4>
                <div class="overflow-x-auto">
                  <table class="w-full text-left text-sm border-collapse">
                    <thead>
                      <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                        <th class="py-2 px-3">Ubicación</th>
                        <th class="py-2 px-3 text-right">Cantidad</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-dark-border/50">
                      @for (l of r.ubicaciones; track l.ubicacionId) {
                        <tr class="hover:bg-dark-surface/40 transition-colors">
                          <td class="py-2 px-3 font-mono font-semibold text-neon-purple">{{ l.ubicacionPath }}</td>
                          <td class="py-2 px-3 font-mono font-bold text-neon-green text-right">{{ l.cantidad }} u.</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </div>
            }
          }
        </div>
      </div>
    }
  `,
})
export class Productos implements OnInit {
  private service = inject(ProductoService);
  private marcaService = inject(MarcaService);
  private stockService = inject(StockService);
  private ubicacionService = inject(UbicacionService);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private queryChanged = new Subject<string>();

  get esAdmin(): boolean {
    return this.auth.esAdmin;
  }

  // Menú de acciones y baja de producto
  menu = signal<ProductoListItem | null>(null);
  aEliminar = signal<ProductoListItem | null>(null);
  eliminando = signal(false);
  bajaError = signal<string | null>(null);

  productos = signal<ProductoListItem[]>([]);
  marcas = signal<Marca[]>([]);
  pagina = signal(0);
  totalPaginas = signal(1);
  totalElements = signal(0);
  cargando = signal(true);
  q = '';

  mostrarNuevo = signal(false);
  editandoId = signal<number | null>(null);
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

  // Confirmación de eliminación de marca
  aMarcaEliminar = signal<Marca | null>(null);

  // Gestión de stock inline
  stockProducto = signal<ProductoListItem | null>(null);
  stockResumen = signal<StockResumen | null>(null);
  stockUbicaciones = signal<Ubicacion[]>([]);
  stockOpTipo = signal<'entrada' | 'salida' | 'ajuste'>('entrada');
  stockOpUbicacionId: number | null = null;
  stockOpCantidad: number | null = null;
  stockOpMotivo = '';
  stockAjusteTipo: 'AJUSTE_POSITIVO' | 'AJUSTE_NEGATIVO' = 'AJUSTE_POSITIVO';
  stockOpGuardando = signal(false);
  stockOpError = signal<string | null>(null);
  stockOpOk = signal<string | null>(null);

  // Asociar código a producto existente
  agregandoCodigoId = signal<number | null>(null);
  nuevoCodigo = '';
  codigoGuardando = signal(false);
  codigoError = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
    this.cargarMarcas();
    // Búsqueda en vivo: filtra a medida que se tipea. switchMap cancela las consultas
    // viejas (evita que una búsqueda parcial anterior pise el resultado de la última).
    this.queryChanged
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        tap(() => {
          this.pagina.set(0);
          this.cargando.set(true);
        }),
        switchMap((term) =>
          (term ? this.service.buscarTexto(term, 0) : this.service.listar(0)).pipe(
            catchError(() => of(null)),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => {
        if (res) {
          this.productos.set(res.content);
          this.totalPaginas.set(res.totalPages);
          this.totalElements.set(res.totalElements);
        }
        this.cargando.set(false);
      });
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
    this.aMarcaEliminar.set(m);
  }

  cancelarEliminarMarca(): void {
    this.aMarcaEliminar.set(null);
  }

  confirmarEliminarMarca(m: Marca): void {
    this.marcaError.set(null);
    this.aMarcaEliminar.set(null);
    this.marcaService.eliminar(m.id).subscribe({
      next: () => this.cargarMarcas(),
      error: (e) => this.marcaError.set(e?.error?.message ?? 'No se pudo eliminar la marca'),
    });
  }

  abrirCodigo(productoId: number): void {
    this.agregandoCodigoId.set(productoId);
    this.nuevoCodigo = '';
    this.codigoError.set(null);
  }

  cerrarCodigo(): void {
    this.agregandoCodigoId.set(null);
    this.nuevoCodigo = '';
  }

  guardarCodigo(productoId: number): void {
    const codigo = this.nuevoCodigo.trim();
    if (!codigo) {
      this.codigoError.set('Ingresá un código');
      return;
    }
    this.codigoError.set(null);
    this.codigoGuardando.set(true);
    this.service.agregarCodigo(productoId, codigo).subscribe({
      next: () => {
        this.codigoGuardando.set(false);
        this.cerrarCodigo();
        this.cargar();
      },
      error: (e) => {
        this.codigoError.set(e?.error?.message ?? 'No se pudo asociar el código');
        this.codigoGuardando.set(false);
      },
    });
  }

  cargar(): void {
    this.cargando.set(true);
    const term = this.q.trim();
    const req = term
      ? this.service.buscarTexto(term, this.pagina())
      : this.service.listar(this.pagina());
    req.subscribe({
      next: (res) => {
        this.productos.set(res.content);
        this.totalPaginas.set(res.totalPages);
        this.totalElements.set(res.totalElements);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  buscar(): void {
    this.pagina.set(0);
    this.cargar();
  }

  /**
   * En cada tecla dispara la búsqueda en vivo (debounced). Con el término vacío vuelve al
   * listado completo; con 1 sola letra espera a que haya al menos 2 caracteres.
   */
  onQueryInput(): void {
    const term = this.q.trim();
    if (term.length === 1) {
      return;
    }
    this.queryChanged.next(term);
  }

  limpiarBusqueda(): void {
    this.q = '';
    this.buscar();
  }

  cambiarPagina(delta: number): void {
    this.pagina.update((p) => Math.max(0, p + delta));
    this.cargar();
  }

  guardar(): void {
    if (this.editandoId()) {
      this.actualizar();
    } else {
      this.crear();
    }
  }

  iniciarEdicion(p: ProductoListItem): void {
    this.error.set(null);
    this.service.getById(p.id).subscribe((full) => {
      this.editandoId.set(full.id);
      this.sku = full.sku ?? '';
      this.marcaId = full.marcaId ?? null;
      this.descripcion = full.descripcion;
      this.codigoBarras = '';
      this.mostrarNuevaMarca.set(false);
      this.mostrarNuevo.set(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
    this.sku = '';
    this.marcaId = null;
    this.descripcion = '';
    this.codigoBarras = '';
    this.mostrarNuevo.set(false);
    this.error.set(null);
  }

  private actualizar(): void {
    const id = this.editandoId();
    if (!id) return;
    if (!this.descripcion.trim()) {
      this.error.set('La descripción es obligatoria');
      return;
    }
    this.error.set(null);
    this.guardando.set(true);
    this.service
      .editar(id, {
        sku: this.sku.trim() || undefined,
        marcaId: this.marcaId || undefined,
        descripcion: this.descripcion.trim(),
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.cancelarEdicion();
          this.cargar();
        },
        error: (e) => {
          this.error.set(e?.error?.message ?? 'No se pudo actualizar el producto');
          this.guardando.set(false);
        },
      });
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

  // --- Menú de acciones "+" ---
  abrirMenu(p: ProductoListItem): void {
    this.menu.set(p);
  }

  cerrarMenu(): void {
    this.menu.set(null);
  }

  modificar(p: ProductoListItem): void {
    this.cerrarMenu();
    this.iniciarEdicion(p);
  }

  // --- Gestión de stock desde productos ---

  gestionarStock(p: ProductoListItem): void {
    this.cerrarMenu();
    this.stockProducto.set(p);
    this.stockOpError.set(null);
    this.stockOpOk.set(null);
    this.stockOpUbicacionId = null;
    this.stockOpCantidad = null;
    this.stockOpMotivo = '';
    this.stockOpTipo.set('entrada');
    this.stockService.resumen(p.id).subscribe({
      next: (r) => this.stockResumen.set(r),
      error: () => this.stockResumen.set(null),
    });
    if (this.stockUbicaciones().length === 0) {
      this.ubicacionService.listar().subscribe({
        next: (list) => this.stockUbicaciones.set(list),
        error: () => this.stockUbicaciones.set([]),
      });
    }
  }

  cerrarStock(): void {
    this.stockProducto.set(null);
    this.stockResumen.set(null);
  }

  ejecutarStockOp(): void {
    const p = this.stockProducto();
    if (!p) return;
    this.stockOpError.set(null);
    this.stockOpOk.set(null);

    if (!this.stockOpUbicacionId) { this.stockOpError.set('Elegí una ubicación.'); return; }
    if (!this.stockOpCantidad || this.stockOpCantidad < 1) { this.stockOpError.set('La cantidad debe ser ≥ 1.'); return; }

    this.stockOpGuardando.set(true);
    const tipo = this.stockOpTipo();

    if (tipo === 'entrada') {
      this.stockService.entrada({
        productoId: p.id, ubicacionId: this.stockOpUbicacionId,
        cantidad: this.stockOpCantidad, motivo: this.stockOpMotivo.trim() || undefined,
      }).subscribe({
        next: () => this.onStockOpOk(`+${this.stockOpCantidad} u. registradas.`, p.id),
        error: (e) => this.onStockOpError(e),
      });
    } else if (tipo === 'salida') {
      this.stockService.salida({
        productoId: p.id, ubicacionId: this.stockOpUbicacionId,
        cantidad: this.stockOpCantidad, motivo: this.stockOpMotivo.trim() || undefined,
      }).subscribe({
        next: () => this.onStockOpOk(`−${this.stockOpCantidad} u. registradas.`, p.id),
        error: (e) => this.onStockOpError(e),
      });
    } else {
      if (!this.stockOpMotivo.trim()) { this.stockOpError.set('El motivo es obligatorio para ajustes.'); this.stockOpGuardando.set(false); return; }
      this.stockService.ajuste({
        productoId: p.id, ubicacionId: this.stockOpUbicacionId,
        tipo: this.stockAjusteTipo, cantidad: this.stockOpCantidad,
        motivo: this.stockOpMotivo.trim(),
      }).subscribe({
        next: () => this.onStockOpOk(`Ajuste registrado.`, p.id),
        error: (e) => this.onStockOpError(e),
      });
    }
  }

  private onStockOpOk(msg: string, productoId: number): void {
    this.stockOpOk.set(msg);
    this.stockOpCantidad = null;
    this.stockOpMotivo = '';
    this.stockOpGuardando.set(false);
    this.stockService.resumen(productoId).subscribe({
      next: (r) => this.stockResumen.set(r),
      error: () => this.stockResumen.set(null),
    });
    this.cargar();
  }

  private onStockOpError(e: any): void {
    this.stockOpError.set(e?.error?.message ?? 'No se pudo completar la operación.');
    this.stockOpGuardando.set(false);
  }

  agregarCodigoDe(p: ProductoListItem): void {
    this.cerrarMenu();
    this.abrirCodigo(p.id);
  }

  pedirBaja(p: ProductoListItem): void {
    this.cerrarMenu();
    this.bajaError.set(null);
    this.aEliminar.set(p);
  }

  cancelarBaja(): void {
    this.aEliminar.set(null);
    this.bajaError.set(null);
  }

  confirmarBaja(p: ProductoListItem): void {
    this.eliminando.set(true);
    this.service.darDeBaja(p.id).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.aEliminar.set(null);
        this.cargar();
      },
      error: (e) => {
        this.bajaError.set(e?.error?.message ?? 'No se pudo dar de baja el producto');
        this.eliminando.set(false);
      },
    });
  }
}
