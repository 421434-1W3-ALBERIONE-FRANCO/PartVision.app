import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { MarcaService } from '../core/marca.service';
import { ProductoService } from '../core/producto.service';
import { Marca, Page, ProductoListItem } from '../core/models';

@Component({
  selector: 'app-productos',
  imports: [FormsModule],
  template: `
    <h2>Productos</h2>

    <div class="card">
      <h3>Nuevo producto</h3>
      <div class="row">
        <div>
          <label>Descripción *</label>
          <input [(ngModel)]="descripcion" />
        </div>
        <div>
          <label>SKU</label>
          <input [(ngModel)]="sku" />
        </div>
        <div>
          <label>Marca</label>
          <select [(ngModel)]="marcaId">
            <option [ngValue]="null">— sin marca —</option>
            @for (m of marcas(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
          </select>
        </div>
        <div>
          <label>Código de barras</label>
          <input [(ngModel)]="codigo" />
        </div>
        <div style="flex:0 0 auto">
          <button [disabled]="guardando()" (click)="crear()">Crear</button>
        </div>
      </div>
      @if (errorCrear()) { <p class="error">{{ errorCrear() }}</p> }
    </div>

    <div class="card">
      @if (cargando()) { <p>Cargando…</p> }
      @if (error()) { <p class="error">{{ error() }}</p> }
      @if (data(); as p) {
        <table>
          <thead>
            <tr><th>ID</th><th>Descripción</th><th>SKU</th><th>Marca</th><th>Estado</th></tr>
          </thead>
          <tbody>
            @for (item of p.content; track item.id) {
              <tr>
                <td>{{ item.id }}</td>
                <td>{{ item.descripcion }}</td>
                <td>{{ item.sku ?? '—' }}</td>
                <td>{{ item.marcaNombre ?? '—' }}</td>
                <td>{{ item.estado }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="muted">Sin productos.</td></tr>
            }
          </tbody>
        </table>
        <div class="pager">
          <button class="secondary" [disabled]="p.number === 0" (click)="ir(p.number - 1)">Anterior</button>
          <span class="muted">Página {{ p.number + 1 }} de {{ p.totalPages || 1 }}</span>
          <button class="secondary" [disabled]="p.number + 1 >= p.totalPages" (click)="ir(p.number + 1)">Siguiente</button>
        </div>
      }
    </div>
  `,
})
export class Productos implements OnInit {
  private productos = inject(ProductoService);
  private marcaService = inject(MarcaService);

  data = signal<Page<ProductoListItem> | null>(null);
  marcas = signal<Marca[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);

  descripcion = '';
  sku = '';
  codigo = '';
  marcaId: number | null = null;
  guardando = signal(false);
  errorCrear = signal<string | null>(null);

  ngOnInit(): void {
    this.ir(0);
    this.marcaService.listar().subscribe({ next: (m) => this.marcas.set(m), error: () => {} });
  }

  ir(page: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.productos.listar(page, 10).subscribe({
      next: (p) => {
        this.data.set(p);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los productos');
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.descripcion.trim()) {
      this.errorCrear.set('La descripción es obligatoria');
      return;
    }
    this.guardando.set(true);
    this.errorCrear.set(null);
    this.productos
      .crear({
        descripcion: this.descripcion.trim(),
        sku: this.sku.trim() || undefined,
        marcaId: this.marcaId ?? undefined,
        codigos: this.codigo.trim() ? [{ codigo: this.codigo.trim() }] : undefined,
      })
      .subscribe({
        next: () => {
          this.descripcion = '';
          this.sku = '';
          this.codigo = '';
          this.marcaId = null;
          this.guardando.set(false);
          this.ir(0);
        },
        error: (e) => {
          this.errorCrear.set(e?.error?.message ?? 'No se pudo crear el producto');
          this.guardando.set(false);
        },
      });
  }
}
