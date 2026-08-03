import { Component, OnInit, inject, signal } from '@angular/core';

import { AiExtraction } from '../core/models';
import { ExtraccionService } from '../core/extraccion.service';

@Component({
  selector: 'app-extracciones',
  standalone: true,
  imports: [],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-dark-border pb-6">
        <div>
          <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
            <span>Extracciones de Visión IA</span>
            <span class="text-xs font-mono bg-neon-pink/20 text-neon-pink border border-neon-pink/30 px-2.5 py-0.5 rounded-full uppercase tracking-wider">
              OPENAI VISION ACTIVE
            </span>
          </h2>
          <p class="text-sm text-gray-400 mt-1">
            Historial de imágenes procesadas por el motor de visión computacional y auditoría de la revisión humana.
          </p>
        </div>

        <button
          (click)="cargar()"
          class="px-4 py-2 rounded-xl text-xs font-semibold neon-button-secondary flex items-center gap-2 self-start md:self-auto cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Actualizar Lista
        </button>
      </div>

      <!-- Extracciones List Cards -->
      <div class="space-y-4">
        @if (cargando()) {
          <div class="glass-panel p-12 text-center text-gray-400 font-mono rounded-2xl">
            <svg class="animate-spin h-8 w-8 text-neon-pink mx-auto mb-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            Consultando registro de extracciones...
          </div>
        } @else if (extracciones().length === 0) {
          <div class="glass-panel p-12 text-center text-gray-500 rounded-2xl border border-dark-border">
            <svg class="w-12 h-12 text-gray-600 mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <p class="text-base font-semibold text-gray-400">No hay extracciones IA registradas en estado PENDIENTE.</p>
            <p class="text-xs text-gray-500 mt-1">Saca una foto desde la app mobile para procesarla con el motor de visión.</p>
          </div>
        } @else {
          <div class="grid grid-cols-1 gap-4">
            @for (item of extracciones(); track item.id) {
              <div class="glass-panel glass-panel-hover p-6 rounded-2xl border border-dark-border flex flex-col md:flex-row gap-6 items-start justify-between">
                
                <!-- Left Details -->
                <div class="space-y-3 flex-1">
                  <div class="flex items-center gap-3">
                    <span class="font-mono text-xs text-gray-400">#{{ item.id }}</span>
                    <span class="neon-badge-purple px-2.5 py-0.5 rounded-md text-xs font-mono font-semibold">
                      {{ item.estado }}
                    </span>
                    <span class="text-xs font-mono text-neon-pink bg-neon-pink/10 px-2 py-0.5 rounded border border-neon-pink/20">
                      {{ item.modelo }}
                    </span>
                  </div>

                  <div class="bg-dark-surface/50 p-3.5 rounded-xl border border-dark-border/60 text-xs font-mono space-y-1">
                    <span class="text-gray-500 uppercase text-[10px] block">Datos Sugeridos por IA:</span>
                    <p class="text-neon-cyan truncate">{{ getDatosString(item.datosSugeridos) }}</p>
                  </div>
                </div>

                <!-- Status Badge -->
                <div class="flex items-center gap-2 self-end md:self-center">
                  @if (item.estado === 'CONFIRMADA') {
                    <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-green-400 bg-green-500/10 border border-green-500/30 px-3 py-1.5 rounded-xl">
                      <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                      </svg>
                      Confirmada
                    </span>
                  } @else if (item.estado === 'DESCARTADA') {
                    <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-red-400 bg-red-500/10 border border-red-500/30 px-3 py-1.5 rounded-xl">
                      Descartada
                    </span>
                  } @else {
                    <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-neon-purple bg-neon-purple/10 border border-neon-purple/30 px-3 py-1.5 rounded-xl">
                      Pendiente Revisión
                    </span>
                  }
                </div>

              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class Extracciones implements OnInit {
  private service = inject(ExtraccionService);

  extracciones = signal<AiExtraction[]>([]);
  cargando = signal(true);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.service.listar('PENDIENTE', 0, 30).subscribe({
      next: (res) => {
        this.extracciones.set(res.content);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  getDatosString(datos: Record<string, unknown>): string {
    if (!datos) return '-';
    return JSON.stringify(datos);
  }
}
