import { Component, inject, signal } from '@angular/core';

import { ImportService } from '../core/import.service';
import { ImportResult } from '../core/models';

@Component({
  selector: 'app-importacion',
  template: `
    <h2>Importación masiva (CSV)</h2>

    <div class="card">
      <p class="muted">
        Columnas (case-insensitive): <code>sku, marca, categoria, descripcion, codigo, tipoCodigo</code>.
        Solo <b>descripcion</b> es obligatoria. Marca y categoría se crean si no existen.
      </p>
      <input type="file" accept=".csv,text/csv" (change)="seleccionar($event)" />
      <div style="margin-top:12px">
        <button [disabled]="!archivo || cargando()" (click)="subir()">
          {{ cargando() ? 'Importando…' : 'Importar' }}
        </button>
      </div>
      @if (error()) { <p class="error">{{ error() }}</p> }
    </div>

    @if (resultado(); as r) {
      <div class="card">
        <h3>Resultado</h3>
        <p>Filas: {{ r.totalFilas }} · Importados: {{ r.importados }} · Errores: {{ r.errores.length }}</p>
        @if (r.errores.length) {
          <table>
            <thead><tr><th>Fila</th><th>Error</th></tr></thead>
            <tbody>
              @for (e of r.errores; track e.fila) {
                <tr><td>{{ e.fila }}</td><td>{{ e.mensaje }}</td></tr>
              }
            </tbody>
          </table>
        }
      </div>
    }
  `,
})
export class Importacion {
  private service = inject(ImportService);

  archivo: File | null = null;
  resultado = signal<ImportResult | null>(null);
  cargando = signal(false);
  error = signal<string | null>(null);

  seleccionar(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.archivo = input.files && input.files.length ? input.files[0] : null;
    this.resultado.set(null);
  }

  subir(): void {
    if (!this.archivo) return;
    this.cargando.set(true);
    this.error.set(null);
    this.service.importarProductos(this.archivo).subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'No se pudo importar el archivo');
        this.cargando.set(false);
      },
    });
  }
}
