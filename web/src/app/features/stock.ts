import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Movimiento, StockResumen } from '../core/models';
import { ConteoLinea, StockService } from '../core/stock.service';

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-dark-border pb-6">
        <div>
          <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
            <span>Control de Stock e Inventario</span>
            <span class="text-xs font-mono bg-neon-purple/20 text-neon-purple border border-neon-purple/30 px-2 py-0.5 rounded-full uppercase">
              DEPOSITO REAL
            </span>
          </h2>
          <p class="text-sm text-gray-400 mt-1">
            Consultas de posiciones físicas y auditoría de movimientos de entrada y salida.
          </p>
        </div>

        <div class="flex gap-3 items-center">
          <input
            [(ngModel)]="productoIdInput"
            type="number"
            placeholder="ID de Producto..."
            class="px-3.5 py-2 bg-dark-surface border border-dark-border rounded-xl text-white text-xs w-36 focus:outline-none focus:border-neon-purple"
          />
          <button
            (click)="consultar()"
            class="px-4 py-2 rounded-xl text-xs font-semibold neon-button-primary cursor-pointer"
          >
            Consultar
          </button>
        </div>
      </div>

      <!-- Resumen de Stock -->
      @if (resumen()) {
        <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card space-y-4">
          <div class="flex items-center justify-between border-b border-dark-border pb-3">
            <h3 class="text-lg font-bold text-white flex items-center gap-2">
              <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
              Stock del Producto #{{ resumen()!.productoId }}
            </h3>
            <span class="text-xl font-extrabold text-neon-green font-mono">
              Total: {{ resumen()!.total }} u.
            </span>
          </div>

          @if (resumen()!.ubicaciones.length === 0) {
            <p class="text-xs text-gray-500 py-4 text-center">Este producto no cuenta con existencias registradas en ubicaciones.</p>
          } @else {
            <div class="overflow-x-auto">
              <table class="w-full text-left text-sm border-collapse">
                <thead>
                  <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                    <th class="py-3 px-4">ID Ubicación</th>
                    <th class="py-3 px-4">Ruta / Código</th>
                    <th class="py-3 px-4">Cantidad</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-dark-border/50">
                  @for (l of resumen()!.ubicaciones; track l.ubicacionId) {
                    <tr class="hover:bg-dark-surface/40 transition-colors">
                      <td class="py-3 px-4 font-mono text-xs text-gray-400">#{{ l.ubicacionId }}</td>
                      <td class="py-3 px-4 font-mono font-semibold text-neon-purple">
                        {{ l.ubicacionPath }}
                      </td>
                      <td class="py-3 px-4 font-mono font-bold text-neon-green">
                        {{ l.cantidad }} u.
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }

      <!-- Conteo físico / carga de cantidades reales -->
      <div class="glass-panel rounded-2xl p-6 border border-neon-cyan/40 shadow-neon-cyan space-y-4">
        <div class="flex items-center justify-between border-b border-dark-border pb-3">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
            </svg>
            Conteo físico (carga de cantidades reales)
          </h3>
          <button (click)="agregarLinea()" class="px-3 py-1.5 rounded-lg text-xs font-semibold bg-dark-surface border border-dark-border hover:border-neon-cyan text-gray-200 cursor-pointer">
            + Línea
          </button>
        </div>
        <p class="text-xs text-gray-400">
          Ingresá la cantidad <span class="text-white font-semibold">real</span> que hay del producto en la ubicación. El sistema calcula el ajuste automáticamente.
        </p>

        <div class="space-y-2">
          @for (l of lineasConteo(); track $index) {
            <div class="grid grid-cols-12 gap-2 items-center">
              <input [(ngModel)]="l.productoId" type="number" placeholder="ID Producto"
                class="col-span-4 md:col-span-3 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-cyan" />
              <input [(ngModel)]="l.ubicacionId" type="number" placeholder="ID Ubicación"
                class="col-span-4 md:col-span-3 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-cyan" />
              <input [(ngModel)]="l.cantidadReal" type="number" placeholder="Cant. real"
                class="col-span-3 md:col-span-2 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-cyan" />
              <button (click)="quitarLinea($index)" class="col-span-1 text-red-400 hover:text-red-300 cursor-pointer text-lg" title="Quitar">×</button>
            </div>
          }
        </div>

        @if (conteoError()) {
          <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">{{ conteoError() }}</div>
        }
        @if (conteoOk()) {
          <div class="p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-xs">{{ conteoOk() }}</div>
        }

        <div class="flex justify-end">
          <button [disabled]="guardandoConteo()" (click)="guardarConteo()"
            class="px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-50">
            {{ guardandoConteo() ? 'Guardando...' : 'Registrar conteo' }}
          </button>
        </div>
      </div>

      <!-- Movimientos Table -->
      <div class="glass-panel rounded-2xl p-6 border border-dark-border shadow-card space-y-4">
        <h3 class="text-lg font-bold text-white flex items-center gap-2">
          <svg class="w-5 h-5 text-neon-purple" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Historial de Movimientos
        </h3>

        @if (cargando()) {
          <div class="py-12 text-center text-gray-400 font-mono">
            <svg class="animate-spin h-8 w-8 text-neon-purple mx-auto mb-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            Ingresá un ID de producto arriba para consultar su stock y movimientos.
          </div>
        } @else if (movimientos().length === 0) {
          <div class="py-12 text-center text-gray-500">
            Sin movimientos registrados para esta consulta.
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm border-collapse">
              <thead>
                <tr class="border-b border-dark-border text-xs uppercase font-mono text-gray-400 bg-dark-surface/30">
                  <th class="py-3 px-4">ID</th>
                  <th class="py-3 px-4">Fecha</th>
                  <th class="py-3 px-4">Tipo</th>
                  <th class="py-3 px-4">Cantidad</th>
                  <th class="py-3 px-4">Origen / Destino</th>
                  <th class="py-3 px-4">Motivo</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border/50">
                @for (m of movimientos(); track m.id) {
                  <tr class="hover:bg-dark-surface/40 transition-colors">
                    <td class="py-3.5 px-4 font-mono text-gray-400 text-xs">#{{ m.id }}</td>
                    <td class="py-3.5 px-4 font-mono text-gray-400 text-xs">
                      {{ m.fecha ? (m.fecha | date:'dd/MM/yyyy HH:mm') : '-' }}
                    </td>
                    <td class="py-3.5 px-4 font-semibold">
                      <span class="px-2.5 py-1 rounded-md text-xs font-mono"
                            [class]="m.tipo === 'ENTRADA' ? 'neon-badge-green' : m.tipo === 'SALIDA' ? 'bg-red-500/15 text-red-400 border border-red-500/30' : 'neon-badge-cyan'">
                        {{ m.tipo }}
                      </span>
                    </td>
                    <td class="py-3.5 px-4 font-mono font-bold text-white">
                      {{ m.cantidad }} u.
                    </td>
                    <td class="py-3.5 px-4 text-xs font-mono text-gray-300">
                      {{ m.ubicacionOrigenId ?? '-' }} ➔ {{ m.ubicacionDestinoId ?? '-' }}
                    </td>
                    <td class="py-3.5 px-4 text-gray-400 text-xs">
                      {{ m.motivo ?? '-' }}
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
export class Stock {
  private service = inject(StockService);

  productoIdInput: number | null = 1;
  resumen = signal<StockResumen | null>(null);
  movimientos = signal<Movimiento[]>([]);
  cargando = signal(false);

  // Conteo físico
  lineasConteo = signal<ConteoLinea[]>([
    { productoId: null as unknown as number, ubicacionId: null as unknown as number, cantidadReal: null as unknown as number },
  ]);
  guardandoConteo = signal(false);
  conteoError = signal<string | null>(null);
  conteoOk = signal<string | null>(null);

  agregarLinea(): void {
    this.lineasConteo.update((ls) => [
      ...ls,
      { productoId: null as unknown as number, ubicacionId: null as unknown as number, cantidadReal: null as unknown as number },
    ]);
  }

  quitarLinea(i: number): void {
    this.lineasConteo.update((ls) => ls.filter((_, idx) => idx !== i));
  }

  guardarConteo(): void {
    const validas = this.lineasConteo().filter(
      (l) => l.productoId != null && l.ubicacionId != null && l.cantidadReal != null && l.cantidadReal >= 0,
    );
    if (validas.length === 0) {
      this.conteoError.set('Completá al menos una línea (producto, ubicación y cantidad real).');
      this.conteoOk.set(null);
      return;
    }
    this.conteoError.set(null);
    this.conteoOk.set(null);
    this.guardandoConteo.set(true);
    this.service.conteoLote(validas).subscribe({
      next: (res) => {
        const ajustados = res.filter((r) => r.movimiento != null).length;
        this.conteoOk.set(`Conteo registrado: ${res.length} línea(s), ${ajustados} con ajuste.`);
        this.guardandoConteo.set(false);
        this.lineasConteo.set([
          { productoId: null as unknown as number, ubicacionId: null as unknown as number, cantidadReal: null as unknown as number },
        ]);
        // Si estaba consultando un producto, refresca el resumen.
        if (this.productoIdInput) this.consultar();
      },
      error: (e) => {
        this.conteoError.set(e?.error?.message ?? 'No se pudo registrar el conteo.');
        this.guardandoConteo.set(false);
      },
    });
  }

  consultar(): void {
    if (!this.productoIdInput) return;
    this.cargando.set(true);

    this.service.resumen(this.productoIdInput).subscribe({
      next: (res) => this.resumen.set(res),
      error: () => this.resumen.set(null),
    });

    this.service.movimientos(this.productoIdInput).subscribe({
      next: (page) => {
        this.movimientos.set(page.content);
        this.cargando.set(false);
      },
      error: () => {
        this.movimientos.set([]);
        this.cargando.set(false);
      },
    });
  }
}
