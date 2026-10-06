import { Component, OnDestroy, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';

import { PrecioRevision, SincronizacionEstado, SincronizacionPrecios } from '../core/models';
import { PrecioSyncService } from '../core/precio-sync.service';

type Resultado = SincronizacionPrecios['resultado'];

/**
 * Panel de la actualizacion automatica de precios de UNA lista (ADS o EGSA) dentro de la
 * pantalla Precios: estado, la constancia de cada corrida y los precios que esperan revision.
 * ADS la baja el servidor (boton "Actualizar ahora"); EGSA la manda el robot del cliente y aca
 * solo se ve lo que llego. La importacion manual de abajo queda intacta como respaldo.
 */
@Component({
  selector: 'app-precios-fuente',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    <div class="glass-panel rounded-2xl p-6 border shadow-card"
         [class]="estado()?.alerta?.nivel === 'ERROR' ? 'border-red-500/40' : estado()?.alerta ? 'border-amber-500/40' : 'border-dark-border'">
      <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div class="min-w-0">
          <h3 class="text-lg font-bold text-white flex items-center gap-2 flex-wrap">
            <svg class="w-5 h-5 text-neon-cyan" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ esEgsa() ? 'Lista recibida' : 'Actualización automática' }}
            <span class="text-sm font-semibold text-gray-400">· {{ proveedor() }}</span>
            @if (estado(); as e) {
              <span class="inline-flex items-center gap-1.5 text-xs font-semibold px-2 py-0.5 rounded-full border" [class]="chip(e).clase">
                <span class="w-2 h-2 rounded-full" [class]="chip(e).punto"></span>{{ chip(e).texto }}
              </span>
            }
          </h3>
          @if (esEgsa()) {
            <p class="text-xs text-gray-500 mt-1">
              El equipo del cliente que corre EGSA CAT manda la lista de precios todas las mañanas. Si un día no llega,
              aparece un aviso arriba de todas las pantallas. La importación manual de abajo sigue funcionando igual.
            </p>
          } @else {
            <p class="text-xs text-gray-500 mt-1">
              Baja sola la lista de precios del portal de ADS (el mismo Excel del botón «Lista de precios») todos los días a las 6:30 y a las 13:30 h.
              La importación manual de abajo sigue funcionando igual, por si el portal no responde.
            </p>
          }
        </div>
        @if (!esEgsa()) {
          <button (click)="actualizarAhora(false)"
                  [disabled]="!estado()?.habilitada || estado()?.enCurso || pidiendo()"
                  class="shrink-0 px-5 py-2.5 rounded-xl text-sm font-semibold neon-button-primary cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2">
            <svg class="w-4 h-4" [class.animate-spin]="estado()?.enCurso" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ estado()?.enCurso ? 'Actualizando...' : 'Actualizar ahora' }}
          </button>
        }
      </div>

      @if (cargando()) {
        <p class="py-6 text-center text-gray-500 text-sm font-mono">Cargando...</p>
      } @else if (errorCarga()) {
        <div class="mt-4 p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{{ errorCarga() }}</div>
      } @else if (estado(); as e) {
        @if (!e.habilitada) {
          <div class="mt-4 p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-sm">
            @if (esEgsa()) {
              Todavía no está activada la recepción directa: falta cargar la clave en el servidor. Mientras tanto
              el robot sigue cargando la lista por la pantalla, con la importación de abajo.
            } @else {
              Todavía no está activada: falta cargar en el servidor el usuario y la contraseña del portal de ADS.
              Mientras tanto, los precios se cargan a mano con la importación de abajo.
            }
          </div>
        }

        @if (e.alerta; as a) {
          <div class="mt-4 p-3 rounded-xl text-sm flex items-start gap-2 border"
               [class]="a.nivel === 'ERROR' ? 'bg-red-500/10 border-red-500/30 text-red-300' : 'bg-amber-500/10 border-amber-500/30 text-amber-300'">
            <svg class="w-5 h-5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
            </svg>
            <span>{{ a.mensaje }}</span>
          </div>
        }

        @if (e.ultima; as u) {
          <div class="mt-5 rounded-xl bg-dark-surface/50 border border-dark-border p-4">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-400">
              <span class="font-semibold text-gray-300">{{ esEgsa() ? 'Última lista recibida' : 'Última corrida' }}</span>
              <span class="font-mono">{{ u.iniciadaEn | date:'dd/MM/yy HH:mm' }}</span>
              <span>· {{ origen(u.origen) }}{{ u.forzada ? ' (aplicada igual)' : '' }}</span>
              @if (e.ultimaBuenaEn) {
                <span class="sm:ml-auto">Precios al día desde el <span class="font-mono text-gray-300">{{ e.ultimaBuenaEn | date:'dd/MM HH:mm' }}</span></span>
              }
            </div>
            @if (u.mensaje) {
              <p class="mt-2 text-sm text-white">{{ u.mensaje }}</p>
            }
            @if (u.resultado !== 'EN_CURSO' && u.resultado !== 'ERROR') {
              <div class="mt-3 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
                <div class="rounded-lg bg-dark/40 px-3 py-2"><p class="text-[10px] uppercase text-gray-500">Actualizados</p><p class="font-mono text-neon-green">{{ u.actualizados | number }}</p></div>
                <div class="rounded-lg bg-dark/40 px-3 py-2"><p class="text-[10px] uppercase text-gray-500">Sin cambio</p><p class="font-mono text-gray-300">{{ u.sinCambio | number }}</p></div>
                <div class="rounded-lg bg-dark/40 px-3 py-2"><p class="text-[10px] uppercase text-gray-500">Para revisar</p><p class="font-mono" [class]="u.enRevision ? 'text-amber-400' : 'text-gray-300'">{{ u.enRevision | number }}</p></div>
                <div class="rounded-lg bg-dark/40 px-3 py-2"><p class="text-[10px] uppercase text-gray-500">No están en catálogo</p><p class="font-mono text-gray-300">{{ u.noEncontrados | number }}</p></div>
                <div class="rounded-lg bg-dark/40 px-3 py-2"><p class="text-[10px] uppercase text-gray-500">Precio en cero</p><p class="font-mono text-gray-300">{{ u.filasInvalidas | number }}</p></div>
              </div>
            }
            @if (u.problemas.length) {
              <ul class="mt-3 space-y-1.5 text-xs text-gray-300 list-disc pl-5">
                @for (p of u.problemas; track $index) {
                  <li>{{ p }}</li>
                }
              </ul>
            }
            @if (u.resultado === 'RETENIDA' && !e.enCurso && (!e.recibeArchivo || e.hayListaRetenida)) {
              <div class="mt-4 flex flex-wrap items-center gap-3">
                <button (click)="confirmandoForzar.set(true)" [disabled]="pidiendo()"
                        class="px-4 py-2 rounded-lg text-xs font-semibold text-amber-300 border border-amber-500/40 hover:bg-amber-500/10 cursor-pointer disabled:opacity-50">
                  Aplicar igual
                </button>
                <span class="text-[11px] text-gray-500">Solo si revisaste los motivos y la lista está bien.</span>
              </div>
            }
          </div>
        } @else if (e.habilitada) {
          <p class="mt-4 text-sm text-gray-400">
            {{ esEgsa() ? 'Todavía no llegó ninguna lista por esta vía.' : 'Todavía no corrió ninguna vez. Tocá «Actualizar ahora» para la primera.' }}
          </p>
        }

        @if (mensaje(); as m) {
          <div class="mt-4 p-3 rounded-xl text-xs border"
               [class]="m.tipo === 'ok' ? 'bg-green-500/10 border-green-500/30 text-green-400' : 'bg-red-500/10 border-red-500/30 text-red-400'">
            {{ m.texto }}
          </div>
        }

        <!-- Precios que cambiaron de mas: los decide una persona -->
        @if (revisiones().length) {
          <div class="mt-5 rounded-xl border border-amber-500/30 bg-amber-500/5 p-4">
            <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-amber-300">{{ revisiones().length }} precio(s) cambiaron más de lo normal</p>
                <p class="text-[11px] text-gray-400">No se aplicaron solos. Aplicá los que estén bien; los que descartes quedan como estaban.</p>
              </div>
              <div class="flex flex-wrap gap-2 shrink-0">
                <button (click)="alternarTodos()" class="px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-300 border border-dark-border hover:text-white cursor-pointer">
                  {{ seleccion().size === revisiones().length ? 'Ninguno' : 'Todos' }}
                </button>
                <button (click)="resolver('descartar')" [disabled]="!seleccion().size || resolviendo()"
                        class="px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-300 border border-dark-border hover:border-gray-500 cursor-pointer disabled:opacity-40">
                  Descartar ({{ seleccion().size }})
                </button>
                <button (click)="resolver('aplicar')" [disabled]="!seleccion().size || resolviendo()"
                        class="px-3 py-1.5 rounded-lg text-xs font-semibold neon-button-primary cursor-pointer disabled:opacity-40">
                  Aplicar ({{ seleccion().size }})
                </button>
              </div>
            </div>
            <div class="mt-3 max-h-80 overflow-y-auto">
              <table class="w-full text-left text-sm border-collapse">
                <thead class="sticky top-0 bg-dark-card">
                  <tr class="text-[10px] uppercase font-mono text-gray-500 border-b border-dark-border">
                    <th class="py-2 pr-2 w-8"></th>
                    <th class="py-2 pr-3">Código</th>
                    <th class="py-2 pr-3 hidden md:table-cell">Descripción</th>
                    <th class="py-2 pr-3 text-right">Costo hoy</th>
                    <th class="py-2 pr-3 text-right">Según {{ esEgsa() ? 'la lista' : 'ADS' }}</th>
                    <th class="py-2 text-right">Cambio</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-dark-border/50">
                  @for (r of revisiones(); track r.id) {
                    <tr class="hover:bg-dark-surface/40 cursor-pointer" (click)="alternar(r.id)">
                      <td class="py-2 pr-2">
                        <input type="checkbox" [checked]="seleccion().has(r.id)" (click)="$event.stopPropagation()" (change)="alternar(r.id)"
                               class="w-4 h-4 rounded accent-amber-400 cursor-pointer" />
                      </td>
                      <td class="py-2 pr-3 font-mono font-bold text-neon-purple whitespace-nowrap">{{ r.sku }}</td>
                      <td class="py-2 pr-3 text-xs text-gray-400 hidden md:table-cell">{{ r.descripcion }}</td>
                      <td class="py-2 pr-3 text-right font-mono text-xs text-gray-300 whitespace-nowrap">{{ r.costoActual === null ? '—' : '$' + (r.costoActual | number:'1.2-2') }}</td>
                      <td class="py-2 pr-3 text-right font-mono text-xs text-white whitespace-nowrap">\${{ r.costoNuevo | number:'1.2-2' }}</td>
                      <td class="py-2 text-right font-mono text-xs font-bold whitespace-nowrap" [class]="(r.variacionPct ?? 0) > 0 ? 'text-red-400' : 'text-neon-cyan'">
                        {{ (r.variacionPct ?? 0) > 0 ? '+' : '' }}{{ r.variacionPct | number:'1.0-1' }}%
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

        <!-- Constancia de las ultimas corridas -->
        @if (e.historial.length > 1) {
          <div class="mt-4">
            <button (click)="verHistorial.set(!verHistorial())" class="text-xs text-neon-cyan hover:underline cursor-pointer">
              {{ verHistorial() ? 'Ocultar' : 'Ver' }} las últimas {{ e.historial.length }} corridas
            </button>
            @if (verHistorial()) {
              <div class="mt-2 overflow-x-auto">
                <table class="w-full text-left text-xs border-collapse">
                  <tbody class="divide-y divide-dark-border/50">
                    @for (h of e.historial; track h.id) {
                      <tr>
                        <td class="py-2 pr-3 font-mono text-gray-400 whitespace-nowrap">{{ h.iniciadaEn | date:'dd/MM HH:mm' }}</td>
                        <td class="py-2 pr-3 whitespace-nowrap">
                          <span class="inline-flex items-center gap-1.5 font-semibold" [class]="resultado(h.resultado).texto">
                            <span class="w-2 h-2 rounded-full" [class]="resultado(h.resultado).punto"></span>{{ resultado(h.resultado).nombre }}
                          </span>
                        </td>
                        <td class="py-2 pr-3 text-gray-500 whitespace-nowrap hidden sm:table-cell">{{ origen(h.origen, true) }}</td>
                        <td class="py-2 text-gray-300">{{ h.mensaje }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }
      }
    </div>

    <!-- Confirmacion de "Aplicar igual" -->
    @if (confirmandoForzar()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="confirmandoForzar.set(false)">
        <div class="glass-panel w-full max-w-md p-6 rounded-2xl border border-amber-500/40 shadow-neon" (click)="$event.stopPropagation()">
          <h3 class="text-lg font-bold text-white mb-3">Aplicar la lista igual</h3>
          <p class="text-sm text-gray-300">
            {{ esEgsa() ? 'Se aplica la lista que mandó el robot, la que quedó retenida,' : 'Se vuelve a bajar la lista de ADS y se aplica' }} aunque llegue con los mismos datos raros.
            Los precios que cambien demasiado igual quedan para que los revises uno por uno.
          </p>
          <p class="text-xs text-amber-400 mt-2">Si algo sale mal, se puede revertir desde el Historial de Actualizaciones.</p>
          <div class="mt-6 flex flex-wrap justify-end gap-3">
            <button (click)="confirmandoForzar.set(false)" class="px-5 py-2.5 rounded-xl font-semibold text-sm text-gray-300 bg-dark-surface border border-dark-border hover:border-gray-500 cursor-pointer">Cancelar</button>
            <button (click)="actualizarAhora(true)" class="px-6 py-2.5 rounded-xl font-semibold text-sm bg-amber-500 hover:bg-amber-400 text-black cursor-pointer">Aplicar igual</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class PreciosFuente implements OnInit, OnDestroy {
  private service = inject(PrecioSyncService);
  private timer: ReturnType<typeof setInterval> | null = null;

  /** Cuando cambian precios: la pantalla recarga su historial de actualizaciones. */
  actualizado = output<void>();

  estado = signal<SincronizacionEstado | null>(null);
  cargando = signal(true);
  errorCarga = signal<string | null>(null);
  pidiendo = signal(false);
  mensaje = signal<{ tipo: 'ok' | 'error'; texto: string } | null>(null);
  confirmandoForzar = signal(false);

  /** De que lista es el panel: "Autopartes del Sur" o "EGSA". */
  proveedor = input.required<string>();
  esEgsa = computed(() => this.proveedor() === 'EGSA');
  verHistorial = signal(false);

  revisiones = signal<PrecioRevision[]>([]);
  seleccion = signal<Set<number>>(new Set());
  resolviendo = signal(false);

  private pendientesVistos = computed(() => this.estado()?.pendientesRevision ?? 0);

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.dejarDeSeguir();
  }

  cargar(): void {
    this.service.estado(this.proveedor()).subscribe({
      next: (e) => {
        const estabaCorriendo = this.estado()?.enCurso;
        this.estado.set(e);
        this.cargando.set(false);
        this.errorCarga.set(null);
        if (e.enCurso) this.seguir();
        else this.dejarDeSeguir();
        if (estabaCorriendo && !e.enCurso) this.actualizado.emit();
        if (this.pendientesVistos() !== this.revisiones().length) this.cargarRevisiones();
      },
      error: () => {
        this.cargando.set(false);
        this.errorCarga.set('No se pudo cargar el estado de la actualización automática.');
      },
    });
  }

  actualizarAhora(forzar: boolean): void {
    this.confirmandoForzar.set(false);
    this.pidiendo.set(true);
    this.mensaje.set(null);
    const pedido = this.esEgsa() ? this.service.aplicarRetenida() : this.service.actualizarAhora(forzar);
    pedido.subscribe({
      next: () => {
        this.pidiendo.set(false);
        this.cargar();
      },
      error: (e) => {
        this.pidiendo.set(false);
        this.mensaje.set({ tipo: 'error', texto: e?.error?.message ?? 'No se pudo iniciar la actualización.' });
      },
    });
  }

  private cargarRevisiones(): void {
    this.service.revisiones(this.proveedor()).subscribe({
      next: (lista) => {
        this.revisiones.set(lista);
        const vigentes = new Set(lista.map(r => r.id));
        this.seleccion.set(new Set([...this.seleccion()].filter(id => vigentes.has(id))));
      },
    });
  }

  alternar(id: number): void {
    const s = new Set(this.seleccion());
    if (s.has(id)) s.delete(id);
    else s.add(id);
    this.seleccion.set(s);
  }

  alternarTodos(): void {
    this.seleccion.set(this.seleccion().size === this.revisiones().length
      ? new Set()
      : new Set(this.revisiones().map(r => r.id)));
  }

  resolver(accion: 'aplicar' | 'descartar'): void {
    const ids = [...this.seleccion()];
    if (!ids.length) return;
    this.resolviendo.set(true);
    this.mensaje.set(null);
    const pedido = accion === 'aplicar' ? this.service.aplicar(ids) : this.service.descartar(ids);
    pedido.subscribe({
      next: (r) => {
        this.resolviendo.set(false);
        this.seleccion.set(new Set());
        this.mensaje.set({ tipo: 'ok', texto: r.mensaje });
        if (r.batchId) this.actualizado.emit();
        this.cargar();
        this.cargarRevisiones();
      },
      error: (e) => {
        this.resolviendo.set(false);
        this.mensaje.set({ tipo: 'error', texto: e?.error?.message ?? 'No se pudo completar.' });
      },
    });
  }

  /** Quien disparo la corrida, en palabras. */
  origen(o: SincronizacionPrecios['origen'], corto = false): string {
    if (o === 'AUTOMATICA') return 'automática';
    if (o === 'RECEPCION') return corto ? 'robot' : 'la mandó el robot';
    return corto ? 'botón' : 'con el botón';
  }

  chip(e: SincronizacionEstado): { texto: string; clase: string; punto: string } {
    if (!e.habilitada) return { texto: 'Sin activar', clase: 'text-gray-400 border-gray-500/40 bg-gray-500/10', punto: 'bg-gray-500' };
    if (e.enCurso) return { texto: 'Actualizando', clase: 'text-neon-cyan border-neon-cyan/40 bg-neon-cyan/10', punto: 'bg-neon-cyan animate-pulse' };
    if (e.alerta?.nivel === 'ERROR') return { texto: 'Con problemas', clase: 'text-red-400 border-red-500/40 bg-red-500/10', punto: 'bg-red-400' };
    if (e.alerta) return { texto: 'Para revisar', clase: 'text-amber-400 border-amber-500/40 bg-amber-500/10', punto: 'bg-amber-400' };
    if (!e.ultima) return { texto: 'Sin correr', clase: 'text-gray-400 border-gray-500/40 bg-gray-500/10', punto: 'bg-gray-500' };
    return { texto: 'Al día', clase: 'text-neon-green border-green-500/40 bg-green-500/10', punto: 'bg-green-400' };
  }

  resultado(r: Resultado): { nombre: string; texto: string; punto: string } {
    switch (r) {
      case 'ACTUALIZADA': return { nombre: 'Actualizada', texto: 'text-neon-green', punto: 'bg-green-400' };
      case 'SIN_CAMBIOS': return { nombre: 'Sin cambios', texto: 'text-gray-300', punto: 'bg-gray-400' };
      case 'RETENIDA': return { nombre: 'Frenada', texto: 'text-amber-400', punto: 'bg-amber-400' };
      case 'ERROR': return { nombre: 'Error', texto: 'text-red-400', punto: 'bg-red-400' };
      default: return { nombre: 'En curso', texto: 'text-neon-cyan', punto: 'bg-neon-cyan' };
    }
  }

  private seguir(): void {
    if (this.timer) return;
    this.timer = setInterval(() => this.cargar(), 3000);
  }

  private dejarDeSeguir(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}
