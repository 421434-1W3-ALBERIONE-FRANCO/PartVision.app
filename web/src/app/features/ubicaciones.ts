import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { UbicacionService } from '../core/ubicacion.service';
import { Ubicacion } from '../core/models';

const TIPOS = ['DEPOSITO', 'SECTOR', 'PASILLO', 'ESTANTERIA', 'NIVEL'];

@Component({
  selector: 'app-ubicaciones',
  imports: [FormsModule],
  template: `
    <h2>Ubicaciones</h2>

    <div class="card">
      <h3>Nueva ubicación</h3>
      <div class="row">
        <div>
          <label>Tipo</label>
          <select [(ngModel)]="tipo">
            @for (t of tipos; track t) { <option [ngValue]="t">{{ t }}</option> }
          </select>
        </div>
        <div>
          <label>Código</label>
          <input [(ngModel)]="codigo" />
        </div>
        <div>
          <label>Id padre (opcional)</label>
          <input type="number" [(ngModel)]="parentId" />
        </div>
        <div style="flex:0 0 auto">
          <button [disabled]="guardando()" (click)="crear()">Crear</button>
        </div>
      </div>
      @if (errorCrear()) { <p class="error">{{ errorCrear() }}</p> }
    </div>

    <div class="card">
      <h3>Árbol</h3>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <table>
        <thead><tr><th>ID</th><th>Tipo</th><th>Código</th><th>Path</th><th></th></tr></thead>
        <tbody>
          @for (u of nodos(); track u.id) {
            <tr>
              <td>{{ u.id }}</td>
              <td>{{ u.tipo }}</td>
              <td>{{ u.codigo }}</td>
              <td>{{ u.path }}</td>
              <td><button class="secondary" (click)="abrir(u)">Ver hijos</button></td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="muted">Sin ubicaciones.</td></tr>
          }
        </tbody>
      </table>
      @if (actual()) {
        <p class="pager">
          <button class="secondary" (click)="volverRaices()">◀ Raíces</button>
          <span class="muted">Hijos de: {{ actual()!.path }}</span>
        </p>
      }
    </div>
  `,
})
export class Ubicaciones implements OnInit {
  private service = inject(UbicacionService);
  tipos = TIPOS;

  nodos = signal<Ubicacion[]>([]);
  actual = signal<Ubicacion | null>(null);
  error = signal<string | null>(null);

  tipo = 'DEPOSITO';
  codigo = '';
  parentId: number | null = null;
  guardando = signal(false);
  errorCrear = signal<string | null>(null);

  ngOnInit(): void {
    this.volverRaices();
  }

  volverRaices(): void {
    this.actual.set(null);
    this.error.set(null);
    this.service.raices().subscribe({
      next: (u) => this.nodos.set(u),
      error: () => this.error.set('No se pudieron cargar las ubicaciones'),
    });
  }

  abrir(u: Ubicacion): void {
    this.actual.set(u);
    this.service.hijos(u.id).subscribe({
      next: (h) => this.nodos.set(h),
      error: () => this.error.set('No se pudieron cargar los hijos'),
    });
  }

  crear(): void {
    if (!this.codigo.trim()) {
      this.errorCrear.set('El código es obligatorio');
      return;
    }
    this.guardando.set(true);
    this.errorCrear.set(null);
    this.service
      .crear({ tipo: this.tipo, codigo: this.codigo.trim(), parentId: this.parentId ?? undefined })
      .subscribe({
        next: () => {
          this.codigo = '';
          this.parentId = null;
          this.guardando.set(false);
          this.volverRaices();
        },
        error: (e) => {
          this.errorCrear.set(e?.error?.message ?? 'No se pudo crear la ubicación');
          this.guardando.set(false);
        },
      });
  }
}
