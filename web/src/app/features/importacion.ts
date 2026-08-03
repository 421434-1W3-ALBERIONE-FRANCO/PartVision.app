import { Component, inject, signal } from '@angular/core';

import { ImportResult } from '../core/models';
import { ImportService } from '../core/import.service';

@Component({
  selector: 'app-importacion',
  standalone: true,
  imports: [],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="border-b border-dark-border pb-6">
        <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
          <span>Importación Masiva CSV</span>
          <span class="text-xs font-mono bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/30 px-2.5 py-0.5 rounded-full uppercase">
            BULK CATALOG
          </span>
        </h2>
        <p class="text-sm text-gray-400 mt-1">
          Carga masiva de catálogo mediante archivos CSV con validación de columnas y reporte de errores fila por fila.
        </p>
      </div>

      <!-- File Dropzone Card -->
      <div class="glass-panel p-8 rounded-2xl border border-dark-border shadow-card text-center space-y-6">
        <div class="border-2 border-dashed border-dark-border hover:border-neon-cyan rounded-2xl p-10 transition-colors bg-dark-surface/30 cursor-pointer group">
          <input
            type="file"
            accept=".csv"
            (change)="onFileSelected($event)"
            class="hidden"
            #fileInput
          />
          <div (click)="fileInput.click()" class="space-y-3">
            <div class="w-16 h-16 rounded-2xl bg-neon-cyan/10 border border-neon-cyan/30 flex items-center justify-center mx-auto text-neon-cyan group-hover:scale-110 transition-transform">
              <svg class="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
              </svg>
            </div>
            <div>
              <p class="text-base font-semibold text-white">
                {{ archivo() ? archivo()!.name : 'Hacé clic para seleccionar tu archivo CSV' }}
              </p>
              <p class="text-xs text-gray-400 mt-1">
                Formato esperado: <code class="text-neon-cyan bg-dark-surface px-1.5 py-0.5 rounded font-mono">codigo_pieza,marca,descripcion,codigo_barras</code>
              </p>
            </div>
          </div>
        </div>

        @if (error()) {
          <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs flex items-center justify-center gap-2">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{{ error() }}</span>
          </div>
        }

        <button
          [disabled]="!archivo() || subiendo()"
          (click)="subir()"
          class="px-8 py-3 rounded-xl font-semibold text-sm neon-button-primary disabled:opacity-40 cursor-pointer"
        >
          {{ subiendo() ? 'Procesando e Importando CSV...' : 'Iniciar Importación Masiva' }}
        </button>
      </div>

      <!-- Result Report -->
      @if (resultado()) {
        <div class="glass-panel p-6 rounded-2xl border border-neon-purple/40 shadow-neon space-y-4 animate-slide-in">
          <h3 class="text-lg font-bold text-white flex items-center gap-2">
            <svg class="w-5 h-5 text-neon-green" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Resumen de la Importación
          </h3>

          <div class="grid grid-cols-3 gap-4 text-center">
            <div class="bg-dark-surface p-4 rounded-xl border border-dark-border">
              <span class="text-xs text-gray-400 uppercase font-mono block">Procesadas</span>
              <span class="text-2xl font-extrabold text-white font-mono">{{ resultado()!.totalFilas }}</span>
            </div>
            <div class="bg-green-500/10 p-4 rounded-xl border border-green-500/30">
              <span class="text-xs text-green-400 uppercase font-mono block">Exitosas</span>
              <span class="text-2xl font-extrabold text-green-400 font-mono">{{ resultado()!.importados }}</span>
            </div>
            <div class="bg-red-500/10 p-4 rounded-xl border border-red-500/30">
              <span class="text-xs text-red-400 uppercase font-mono block">Con Error</span>
              <span class="text-2xl font-extrabold text-red-400 font-mono">{{ resultado()!.errores.length }}</span>
            </div>
          </div>

          @if (resultado()!.errores.length > 0) {
            <div class="space-y-2 pt-2">
              <h4 class="text-xs font-mono uppercase text-red-400 font-semibold">Detalle de errores por fila:</h4>
              <div class="max-h-48 overflow-y-auto space-y-1.5 pr-2">
                @for (err of resultado()!.errores; track err.fila) {
                  <div class="text-xs font-mono text-red-300 bg-red-500/10 p-2.5 rounded-lg border border-red-500/20">
                    Fila {{ err.fila }}: {{ err.mensaje }}
                  </div>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class Importacion {
  private service = inject(ImportService);

  archivo = signal<File | null>(null);
  subiendo = signal(false);
  error = signal<string | null>(null);
  resultado = signal<ImportResult | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.archivo.set(input.files[0]);
      this.error.set(null);
      this.resultado.set(null);
    }
  }

  subir(): void {
    const file = this.archivo();
    if (!file) return;

    this.subiendo.set(true);
    this.error.set(null);
    this.resultado.set(null);

    this.service.importarProductos(file).subscribe({
      next: (res: ImportResult) => {
        this.resultado.set(res);
        this.subiendo.set(false);
      },
      error: (e: any) => {
        this.error.set(e?.error?.message ?? 'Ocurrió un error al procesar el archivo CSV');
        this.subiendo.set(false);
      },
    });
  }
}
