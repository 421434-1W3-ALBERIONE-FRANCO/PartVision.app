import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ExtraccionService } from '../core/extraccion.service';
import { AiExtraction } from '../core/models';

@Component({
  selector: 'app-extracciones',
  imports: [FormsModule],
  template: `
    <h2>Extracciones IA (pendientes)</h2>
    @if (error()) { <p class="error">{{ error() }}</p> }
    @if (cargando()) { <p>Cargando…</p> }

    <div class="card">
      <table>
        <thead><tr><th>ID</th><th>Modelo</th><th>Descripción sugerida</th><th></th></tr></thead>
        <tbody>
          @for (e of items(); track e.id) {
            <tr>
              <td>{{ e.id }}</td>
              <td>{{ e.modelo }}</td>
              <td>{{ sugerido(e, 'descripcion') ?? '—' }}</td>
              <td>
                <button class="secondary" (click)="revisar(e)">Revisar</button>
                <button class="danger" (click)="descartar(e.id)">Descartar</button>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="muted">No hay extracciones pendientes.</td></tr>
          }
        </tbody>
      </table>
    </div>

    @if (seleccion(); as e) {
      <div class="card">
        <h3>Confirmar extracción #{{ e.id }}</h3>
        <p class="muted">Revisá y corregí lo detectado por la IA antes de crear el producto.</p>
        <div class="row">
          <div><label>Descripción *</label><input [(ngModel)]="descripcion" /></div>
          <div><label>SKU</label><input [(ngModel)]="sku" /></div>
          <div><label>Código de barras</label><input [(ngModel)]="codigo" /></div>
        </div>
        <div class="row">
          <div><label>Cantidad inicial (opcional)</label><input type="number" [(ngModel)]="cantidad" /></div>
          <div><label>Id ubicación (opcional)</label><input type="number" [(ngModel)]="ubicacionId" /></div>
        </div>
        @if (errorConfirmar()) { <p class="error">{{ errorConfirmar() }}</p> }
        <div style="margin-top:12px; display:flex; gap:12px">
          <button [disabled]="confirmando()" (click)="confirmar(e.id)">Confirmar y crear</button>
          <button class="secondary" (click)="seleccion.set(null)">Cancelar</button>
        </div>
      </div>
    }
  `,
})
export class Extracciones implements OnInit {
  private service = inject(ExtraccionService);

  items = signal<AiExtraction[]>([]);
  seleccion = signal<AiExtraction | null>(null);
  cargando = signal(false);
  error = signal<string | null>(null);

  descripcion = '';
  sku = '';
  codigo = '';
  cantidad: number | null = null;
  ubicacionId: number | null = null;
  confirmando = signal(false);
  errorConfirmar = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  sugerido(e: AiExtraction, campo: string): string | null {
    const v = e.datosSugeridos?.[campo];
    return typeof v === 'string' ? v : null;
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.service.listar('PENDIENTE').subscribe({
      next: (p) => {
        this.items.set(p.content);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar las extracciones');
        this.cargando.set(false);
      },
    });
  }

  revisar(e: AiExtraction): void {
    this.seleccion.set(e);
    this.errorConfirmar.set(null);
    this.descripcion = this.sugerido(e, 'descripcion') ?? '';
    this.sku = this.sugerido(e, 'codigo_pieza') ?? '';
    this.codigo = this.sugerido(e, 'codigo_barras') ?? '';
    this.cantidad = null;
    this.ubicacionId = null;
  }

  confirmar(id: number): void {
    if (!this.descripcion.trim()) {
      this.errorConfirmar.set('La descripción es obligatoria');
      return;
    }
    if ((this.cantidad == null) !== (this.ubicacionId == null)) {
      this.errorConfirmar.set('Cargá cantidad y ubicación juntas, o ninguna');
      return;
    }
    this.confirmando.set(true);
    this.errorConfirmar.set(null);
    this.service
      .confirmar(id, {
        producto: {
          descripcion: this.descripcion.trim(),
          sku: this.sku.trim() || undefined,
          codigos: this.codigo.trim() ? [{ codigo: this.codigo.trim() }] : undefined,
        },
        cantidad: this.cantidad ?? undefined,
        ubicacionId: this.ubicacionId ?? undefined,
      })
      .subscribe({
        next: () => {
          this.confirmando.set(false);
          this.seleccion.set(null);
          this.cargar();
        },
        error: (e) => {
          this.errorConfirmar.set(e?.error?.message ?? 'No se pudo confirmar');
          this.confirmando.set(false);
        },
      });
  }

  descartar(id: number): void {
    this.service.descartar(id).subscribe({
      next: () => this.cargar(),
      error: (e) => this.error.set(e?.error?.message ?? 'No se pudo descartar'),
    });
  }
}
