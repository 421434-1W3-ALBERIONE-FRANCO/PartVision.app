import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { AiExtraction, SugerenciaAccion } from '../core/models';
import { ExtraccionService } from '../core/extraccion.service';

interface ItemCarga {
  file: File;
  previewUrl: string;
  abierto: boolean;
  estado: 'pendiente' | 'procesando' | 'listo' | 'error' | 'hecho';
  error?: string;
  extraccionId?: number;
  sugerencia?: SugerenciaAccion;
  // Campos editables (para alta de producto nuevo)
  descripcion: string;
  marca: string;
  sku: string;
  codigoBarras: string;
  resultado?: string;
  trabajando: boolean;
}

const MAX_IMAGENES = 6;

@Component({
  selector: 'app-carga-ia',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="space-y-8 animate-fade-in max-w-7xl mx-auto">
      <!-- Header -->
      <div class="border-b border-dark-border pb-6">
        <h2 class="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
          <span>Carga por Imagen (IA)</span>
          <span class="text-xs font-mono bg-neon-pink/20 text-neon-pink border border-neon-pink/30 px-2.5 py-0.5 rounded-full uppercase tracking-wider">
            VISIÓN IA
          </span>
        </h2>
        <p class="text-sm text-gray-400 mt-1">
          Subí hasta {{ max }} fotos de etiquetas/cajas. La IA sugiere los datos y vos revisás y confirmás cada una.
        </p>
      </div>

      <!-- Selector -->
      <div class="glass-panel p-6 rounded-2xl border border-dark-border shadow-card space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center gap-3">
          <input type="file" accept="image/*" multiple (change)="onSelect($event)" class="hidden" #fileInput />
          <button (click)="fileInput.click()"
            class="px-5 py-2.5 rounded-xl font-semibold text-sm bg-dark-surface border border-dark-border hover:border-neon-cyan text-gray-200 cursor-pointer">
            Seleccionar imágenes
          </button>
          <span class="text-xs text-gray-400">
            {{ items().length }} / {{ max }} seleccionadas
          </span>
          <button [disabled]="items().length === 0 || procesando()" (click)="procesar()"
            class="sm:ml-auto px-6 py-2.5 rounded-xl font-semibold text-sm neon-button-primary cursor-pointer disabled:opacity-40">
            {{ procesando() ? 'Procesando con IA...' : 'Procesar con IA' }}
          </button>
          @if (items().length > 0) {
            <button (click)="limpiar()" class="px-4 py-2.5 rounded-xl text-sm font-semibold text-gray-400 hover:text-white cursor-pointer">
              Limpiar
            </button>
          }
        </div>
        @if (avisoLimite()) {
          <div class="p-3 bg-amber-500/10 border border-amber-500/30 rounded-xl text-amber-400 text-xs">
            {{ avisoLimite() }}
          </div>
        }
      </div>

      <!-- Acordeón de items -->
      <div class="space-y-3">
        @for (item of items(); track item.previewUrl) {
          <div class="glass-panel rounded-2xl border border-dark-border overflow-hidden">
            <!-- Cabecera -->
            <button (click)="item.abierto = !item.abierto"
              class="w-full flex items-center gap-3 p-4 text-left hover:bg-dark-surface/40 cursor-pointer">
              <svg class="w-4 h-4 text-gray-400 transition-transform" [class.rotate-90]="item.abierto" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
              </svg>
              <img [src]="item.previewUrl" class="w-10 h-10 rounded object-cover border border-dark-border" alt="mini" />
              <span class="text-sm text-white truncate flex-1">{{ item.file.name }}</span>
              @switch (item.estado) {
                @case ('procesando') { <span class="text-xs font-semibold text-neon-cyan">Procesando…</span> }
                @case ('error') { <span class="text-xs font-semibold text-red-400">Error</span> }
                @case ('hecho') { <span class="text-xs font-semibold text-green-400">✓ {{ item.resultado }}</span> }
                @case ('listo') {
                  <span class="text-xs font-semibold px-2 py-0.5 rounded-md border"
                    [class]="badgeClase(item.sugerencia?.accion)">
                    {{ etiquetaAccion(item.sugerencia?.accion) }}
                  </span>
                }
                @default { <span class="text-xs text-gray-500">Sin procesar</span> }
              }
            </button>

            <!-- Cuerpo -->
            @if (item.abierto) {
              <div class="border-t border-dark-border p-4 grid grid-cols-1 lg:grid-cols-2 gap-6">
                <!-- Imagen -->
                <div class="flex items-start justify-center">
                  <img [src]="item.previewUrl" alt="foto"
                    class="max-h-80 w-full object-contain rounded-xl border border-dark-border bg-black/30" />
                </div>

                <!-- Panel derecho -->
                <div class="space-y-4">
                  @if (item.estado === 'error') {
                    <div class="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-red-400 text-xs">
                      {{ item.error }}
                    </div>
                  }

                  @if (item.estado === 'hecho') {
                    <div class="p-3 bg-green-500/10 border border-green-500/30 rounded-xl text-green-400 text-sm">
                      {{ item.resultado }}
                    </div>
                  } @else if (item.estado === 'listo' && item.sugerencia) {
                    <!-- Leyenda de la sugerencia -->
                    <div class="p-3 rounded-xl text-sm border"
                      [class]="badgeClase(item.sugerencia.accion)">
                      {{ item.sugerencia.mensaje }}
                    </div>

                    @if (item.sugerencia.accion === 'NUEVO') {
                      <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <label class="block md:col-span-2">
                          <span class="text-xs uppercase text-gray-400">Descripción *</span>
                          <input [(ngModel)]="item.descripcion" type="text"
                            class="mt-1 w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-cyan" />
                        </label>
                        <label class="block">
                          <span class="text-xs uppercase text-gray-400">Marca</span>
                          <input [(ngModel)]="item.marca" type="text"
                            class="mt-1 w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white text-sm focus:outline-none focus:border-neon-cyan" />
                        </label>
                        <label class="block">
                          <span class="text-xs uppercase text-gray-400">SKU</span>
                          <input [(ngModel)]="item.sku" type="text"
                            class="mt-1 w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white font-mono text-sm focus:outline-none focus:border-neon-cyan" />
                        </label>
                        <label class="block md:col-span-2">
                          <span class="text-xs uppercase text-gray-400">Código de barras</span>
                          <input [(ngModel)]="item.codigoBarras" type="text"
                            class="mt-1 w-full px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-white font-mono text-sm focus:outline-none focus:border-neon-cyan" />
                        </label>
                      </div>
                      <div class="flex gap-2 justify-end">
                        <button (click)="descartar(item)" [disabled]="item.trabajando"
                          class="px-4 py-2 rounded-lg text-sm font-semibold text-gray-400 hover:text-white cursor-pointer disabled:opacity-50">Descartar</button>
                        <button (click)="crear(item)" [disabled]="item.trabajando"
                          class="px-5 py-2 rounded-lg text-sm font-semibold neon-button-primary cursor-pointer disabled:opacity-50">
                          {{ item.trabajando ? 'Creando…' : 'Crear producto' }}
                        </button>
                      </div>
                    } @else if (item.sugerencia.accion === 'AGREGAR_CODIGO') {
                      <div class="text-xs text-gray-400 font-mono">
                        Código detectado: <span class="text-neon-cyan">{{ item.sugerencia.codigoBarras }}</span>
                      </div>
                      <div class="flex gap-2 justify-end">
                        <button (click)="descartar(item)" [disabled]="item.trabajando"
                          class="px-4 py-2 rounded-lg text-sm font-semibold text-gray-400 hover:text-white cursor-pointer disabled:opacity-50">Descartar</button>
                        <button (click)="asociar(item)" [disabled]="item.trabajando"
                          class="px-5 py-2 rounded-lg text-sm font-semibold neon-button-primary cursor-pointer disabled:opacity-50">
                          {{ item.trabajando ? 'Agregando…' : 'Agregar código al producto' }}
                        </button>
                      </div>
                    } @else {
                      <!-- YA_EXISTE -->
                      <div class="flex justify-end">
                        <button (click)="descartar(item)" [disabled]="item.trabajando"
                          class="px-5 py-2 rounded-lg text-sm font-semibold bg-dark-surface border border-dark-border hover:border-neon-purple text-gray-200 cursor-pointer disabled:opacity-50">
                          {{ item.trabajando ? '…' : 'Descartar (ya cargado)' }}
                        </button>
                      </div>
                    }
                  } @else {
                    <p class="text-sm text-gray-500">Todavía no procesada. Tocá "Procesar con IA".</p>
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
export class CargaIa {
  private service = inject(ExtraccionService);

  readonly max = MAX_IMAGENES;
  items = signal<ItemCarga[]>([]);
  procesando = signal(false);
  avisoLimite = signal<string | null>(null);

  onSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;
    const seleccionadas = Array.from(input.files);
    const cupo = this.max - this.items().length;
    this.avisoLimite.set(null);
    if (seleccionadas.length > cupo) {
      this.avisoLimite.set(`Máximo ${this.max} imágenes por tanda. Se tomaron las primeras ${Math.max(cupo, 0)}.`);
    }
    const nuevos = seleccionadas.slice(0, Math.max(cupo, 0)).map<ItemCarga>((file) => ({
      file,
      previewUrl: URL.createObjectURL(file),
      abierto: false,
      estado: 'pendiente',
      descripcion: '',
      marca: '',
      sku: '',
      codigoBarras: '',
      trabajando: false,
    }));
    this.items.update((xs) => [...xs, ...nuevos]);
    input.value = '';
  }

  async procesar(): Promise<void> {
    this.procesando.set(true);
    // Secuencial: no saturar el motor de IA (cuotas / rate limits).
    for (const item of this.items()) {
      if (item.estado !== 'pendiente') continue;
      item.estado = 'procesando';
      this.refrescar();
      try {
        const extraccion = await firstValueFrom(this.service.extraer(item.file));
        item.extraccionId = extraccion.id;
        this.prefill(item, extraccion);
        item.sugerencia = await firstValueFrom(this.service.sugerencia(extraccion.id));
        item.estado = 'listo';
        item.abierto = true;
      } catch (e: any) {
        item.estado = 'error';
        item.error = e?.error?.message ?? 'No se pudo procesar la imagen';
      }
      this.refrescar();
    }
    this.procesando.set(false);
  }

  private prefill(item: ItemCarga, e: AiExtraction): void {
    const d = e.datosSugeridos ?? {};
    item.descripcion = (d['descripcion'] as string) ?? '';
    item.marca = (d['marca'] as string) ?? '';
    item.sku = (d['codigo_pieza'] as string) ?? '';
    item.codigoBarras = (d['codigo_barras'] as string) ?? '';
  }

  crear(item: ItemCarga): void {
    if (!item.extraccionId) return;
    if (!item.descripcion.trim()) {
      item.error = 'La descripción es obligatoria';
      this.refrescar();
      return;
    }
    item.trabajando = true;
    this.refrescar();
    const codigos = item.codigoBarras.trim()
      ? [{ codigo: item.codigoBarras.trim(), tipo: 'BARRA' }]
      : undefined;
    this.service
      .confirmar(item.extraccionId, {
        producto: {
          descripcion: item.descripcion.trim(),
          marcaNombre: item.marca.trim() || undefined,
          sku: item.sku.trim() || undefined,
          codigos,
        },
      })
      .subscribe({
        next: (res) => this.marcarHecho(item, `Producto creado (#${res.producto.id})`),
        error: (e) => this.marcarError(item, e?.error?.message ?? 'No se pudo crear el producto'),
      });
  }

  asociar(item: ItemCarga): void {
    if (!item.extraccionId || !item.sugerencia?.productoExistenteId) return;
    item.trabajando = true;
    this.refrescar();
    this.service.asociarCodigo(item.extraccionId, item.sugerencia.productoExistenteId).subscribe({
      next: () => this.marcarHecho(item, 'Código asociado al producto existente'),
      error: (e) => this.marcarError(item, e?.error?.message ?? 'No se pudo asociar el código'),
    });
  }

  descartar(item: ItemCarga): void {
    if (!item.extraccionId) return;
    item.trabajando = true;
    this.refrescar();
    this.service.descartar(item.extraccionId).subscribe({
      next: () => this.marcarHecho(item, 'Descartada'),
      error: (e) => this.marcarError(item, e?.error?.message ?? 'No se pudo descartar'),
    });
  }

  limpiar(): void {
    this.items().forEach((i) => URL.revokeObjectURL(i.previewUrl));
    this.items.set([]);
    this.avisoLimite.set(null);
  }

  etiquetaAccion(accion?: string): string {
    switch (accion) {
      case 'NUEVO': return 'Producto nuevo';
      case 'AGREGAR_CODIGO': return 'Agregar código';
      case 'YA_EXISTE': return 'Ya cargado';
      default: return '';
    }
  }

  badgeClase(accion?: string): string {
    switch (accion) {
      case 'NUEVO': return 'bg-green-500/10 text-green-400 border-green-500/30';
      case 'AGREGAR_CODIGO': return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'YA_EXISTE': return 'bg-gray-500/10 text-gray-400 border-gray-500/30';
      default: return 'bg-dark-surface text-gray-300 border-dark-border';
    }
  }

  private marcarHecho(item: ItemCarga, msg: string): void {
    item.estado = 'hecho';
    item.resultado = msg;
    item.trabajando = false;
    item.abierto = false;
    this.refrescar();
  }

  private marcarError(item: ItemCarga, msg: string): void {
    item.error = msg;
    item.trabajando = false;
    this.refrescar();
  }

  /** Fuerza la actualización de la señal tras mutar objetos del array. */
  private refrescar(): void {
    this.items.set([...this.items()]);
  }
}
