import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { StockService } from '../core/stock.service';
import { Movimiento, StockResumen } from '../core/models';

@Component({
  selector: 'app-stock',
  imports: [FormsModule],
  template: `
    <h2>Stock y movimientos</h2>

    <div class="card">
      <div class="row">
        <div>
          <label>Id de producto</label>
          <input type="number" [(ngModel)]="productoId" (keyup.enter)="consultar()" />
        </div>
        <div style="flex:0 0 auto">
          <button [disabled]="cargando()" (click)="consultar()">Consultar</button>
        </div>
      </div>
      @if (error()) { <p class="error">{{ error() }}</p> }
    </div>

    @if (resumen(); as r) {
      <div class="card">
        <h3>Stock total: {{ r.total }}</h3>
        <table>
          <thead><tr><th>Ubicación</th><th>Cantidad</th></tr></thead>
          <tbody>
            @for (l of r.ubicaciones; track l.ubicacionId) {
              <tr><td>{{ l.ubicacionPath }}</td><td>{{ l.cantidad }}</td></tr>
            } @empty {
              <tr><td colspan="2" class="muted">Sin stock registrado.</td></tr>
            }
          </tbody>
        </table>
      </div>

      <div class="card">
        <h3>Historial de movimientos</h3>
        <table>
          <thead><tr><th>Fecha</th><th>Tipo</th><th>Cant.</th><th>Origen</th><th>Destino</th><th>Usuario</th><th>Motivo</th></tr></thead>
          <tbody>
            @for (m of movimientos(); track m.id) {
              <tr>
                <td>{{ m.fecha }}</td>
                <td>{{ m.tipo }}</td>
                <td>{{ m.cantidad }}</td>
                <td>{{ m.ubicacionOrigenId ?? '—' }}</td>
                <td>{{ m.ubicacionDestinoId ?? '—' }}</td>
                <td>{{ m.usuarioId ?? '—' }}</td>
                <td>{{ m.motivo ?? '—' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="muted">Sin movimientos.</td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class Stock {
  private stock = inject(StockService);

  productoId: number | null = null;
  resumen = signal<StockResumen | null>(null);
  movimientos = signal<Movimiento[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);

  consultar(): void {
    if (this.productoId == null) {
      this.error.set('Ingresá el id del producto');
      return;
    }
    const id = this.productoId;
    this.cargando.set(true);
    this.error.set(null);
    this.resumen.set(null);
    this.movimientos.set([]);
    this.stock.resumen(id).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
        this.stock.movimientos(id).subscribe({
          next: (p) => this.movimientos.set(p.content),
          error: () => {},
        });
      },
      error: (e) => {
        this.error.set(e?.status === 404 ? 'No existe el producto' : 'No se pudo consultar el stock');
        this.cargando.set(false);
      },
    });
  }
}
