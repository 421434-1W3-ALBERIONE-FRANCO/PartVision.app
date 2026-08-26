import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ConfiguracionPrecio, SyncResult } from '../core/models';
import { ProductoService } from '../core/producto.service';

@Component({
  selector: 'app-precios',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  template: `
    <div class="space-y-8 animate-fade-in max-w-4xl mx-auto">
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
          Configuración de márgenes de ganancia por proveedor y sincronización de precios.
        </p>
      </div>

      <!-- Configuración de márgenes -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <h3 class="text-lg font-bold text-white mb-5 flex items-center gap-2">
          <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
          Márgenes por Proveedor
        </h3>

        @if (cargando()) {
          <div class="py-8 text-center text-gray-400 font-mono">
            <svg class="animate-spin h-8 w-8 text-neon-purple mx-auto mb-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            Cargando configuración...
          </div>
        } @else if (configs().length === 0) {
          <p class="text-gray-500 text-sm py-6 text-center">No hay proveedores configurados.</p>
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
                    <input
                      type="number"
                      step="0.001"
                      min="0"
                      [ngModel]="c.margen"
                      (ngModelChange)="onMargenChange(c, $event)"
                      class="w-28 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white font-mono text-sm focus:outline-none focus:border-amber-400 text-right"
                    />
                  </div>

                  <label class="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      [checked]="c.activo"
                      (change)="onActivoChange(c, $event)"
                      class="w-4 h-4 rounded accent-neon-green cursor-pointer"
                    />
                    <span class="text-xs font-semibold" [class]="c.activo ? 'text-neon-green' : 'text-gray-500'">
                      {{ c.activo ? 'Activo' : 'Inactivo' }}
                    </span>
                  </label>

                  <button
                    [disabled]="guardandoId() === c.id"
                    (click)="guardar(c)"
                    class="px-4 py-2 rounded-lg text-xs font-semibold neon-button-primary cursor-pointer disabled:opacity-50 whitespace-nowrap"
                  >
                    {{ guardandoId() === c.id ? 'Guardando...' : 'Guardar' }}
                  </button>
                </div>
              </div>
            }
          </div>
        }

        @if (error()) {
          <div class="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center gap-2">
            <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{{ error() }}</span>
          </div>
        }

        @if (exito()) {
          <div class="mt-4 p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-xs flex items-center gap-2">
            <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <span>{{ exito() }}</span>
          </div>
        }
      </div>

      <!-- Sincronización -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card">
        <h3 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Sincronización con Proveedores
        </h3>

        <p class="text-sm text-gray-400 mb-5">
          Sincroniza los precios de costo desde la API de Autopartes del Sur para todos los
          productos con SKU. El precio de venta se recalcula con el margen configurado arriba.
        </p>

        <div class="flex items-center gap-4">
          <button
            [disabled]="sincronizando()"
            (click)="sincronizar()"
            class="px-6 py-3 rounded-xl font-semibold text-sm bg-amber-500/20 text-amber-400 border border-amber-500/40 hover:bg-amber-500/30 transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-2"
          >
            @if (sincronizando()) {
              <svg class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Sincronizando...
            } @else {
              <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Sincronizar Precios (ADS)
            }
          </button>
        </div>

        @if (syncResult()) {
          <div class="mt-5 p-4 rounded-xl bg-dark-surface/60 border border-dark-border space-y-2">
            <p class="text-sm font-semibold text-white">Resultado de sincronización</p>
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
              <div>
                <p class="text-lg font-bold font-mono text-white">{{ syncResult()!.totalProductos }}</p>
                <p class="text-[10px] uppercase text-gray-400">Total</p>
              </div>
              <div>
                <p class="text-lg font-bold font-mono text-neon-green">{{ syncResult()!.actualizados }}</p>
                <p class="text-[10px] uppercase text-gray-400">Actualizados</p>
              </div>
              <div>
                <p class="text-lg font-bold font-mono text-amber-400">{{ syncResult()!.noEncontrados }}</p>
                <p class="text-[10px] uppercase text-gray-400">No encontrados</p>
              </div>
              <div>
                <p class="text-lg font-bold font-mono text-red-400">{{ syncResult()!.errores }}</p>
                <p class="text-[10px] uppercase text-gray-400">Errores</p>
              </div>
            </div>
            <p class="text-xs text-gray-500 mt-2">{{ syncResult()!.mensaje }}</p>
          </div>
        }
      </div>
    </div>
  `,
})
export class Precios implements OnInit {
  private service = inject(ProductoService);

  configs = signal<ConfiguracionPrecio[]>([]);
  cargando = signal(true);
  guardandoId = signal<number | null>(null);
  error = signal<string | null>(null);
  exito = signal<string | null>(null);

  sincronizando = signal(false);
  syncResult = signal<SyncResult | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.service.listarConfigPrecios().subscribe({
      next: (list) => {
        this.configs.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar la configuración.');
        this.cargando.set(false);
      },
    });
  }

  onMargenChange(c: ConfiguracionPrecio, val: number): void {
    c.margen = val;
  }

  onActivoChange(c: ConfiguracionPrecio, ev: Event): void {
    c.activo = (ev.target as HTMLInputElement).checked;
  }

  guardar(c: ConfiguracionPrecio): void {
    this.error.set(null);
    this.exito.set(null);
    this.guardandoId.set(c.id);
    this.service.actualizarConfigPrecio(c.id, c.margen, c.activo).subscribe({
      next: (updated) => {
        const list = this.configs().map((x) => (x.id === updated.id ? updated : x));
        this.configs.set(list);
        this.guardandoId.set(null);
        this.exito.set(`Margen de "${updated.proveedor}" actualizado a ${updated.margen}%.`);
      },
      error: (e) => {
        this.guardandoId.set(null);
        this.error.set(e?.error?.message ?? 'No se pudo guardar la configuración.');
      },
    });
  }

  sincronizar(): void {
    this.sincronizando.set(true);
    this.syncResult.set(null);
    this.service.sincronizarPrecios().subscribe({
      next: (r) => {
        this.syncResult.set(r);
        this.sincronizando.set(false);
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Error al sincronizar precios.');
        this.sincronizando.set(false);
      },
    });
  }
}
