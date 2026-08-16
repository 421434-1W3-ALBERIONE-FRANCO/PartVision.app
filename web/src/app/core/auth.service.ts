import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { RecoveryCodesResponse, TotpSetupResponse, TotpStatus, Usuario } from './models';

/**
 * Autenticacion basada en cookie HttpOnly: el token JWT lo maneja el navegador (el JS
 * nunca lo ve, blinda contra XSS). Por eso el estado de sesion y los roles NO se leen del
 * token sino de {@code GET /usuarios/me}, y se cachean en signals para consumo sincrono
 * (guards, UI). Se hidrata al arrancar la app (ver provideAppInitializer en app.config).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  private readonly usuario = signal<Usuario | null>(null);

  /** Usuario actual (o null si no hay sesion), como signal para quien quiera reactividad. */
  readonly currentUser = this.usuario.asReadonly();

  /** Expuestos como getters (no signals) para consumo directo en guards y templates. */
  get autenticado(): boolean {
    return this.usuario() !== null;
  }

  get esAdmin(): boolean {
    return this.usuario()?.roles.includes('ADMIN') ?? false;
  }

  /**
   * Login: el backend setea la cookie; luego cargamos el perfil para tener roles. El {@code code}
   * (2FA) es opcional: si el usuario tiene 2FA y falta, el backend responde 401 con
   * {@code twoFactorRequired: true} (el componente de login lo detecta y pide el código).
   */
  login(username: string, password: string, code?: string): Observable<Usuario> {
    return this.http
      .post(`${API_BASE_URL}/auth/login`, { username, password, code: code ?? null })
      .pipe(switchMap(() => this.cargarSesionOrThrow()));
  }

  // ===== 2FA (TOTP) =====

  estado2fa(): Observable<TotpStatus> {
    return this.http.get<TotpStatus>(`${API_BASE_URL}/2fa/status`);
  }

  /** Paso 1: genera el secret y devuelve el otpauth:// para el QR. */
  setup2fa(): Observable<TotpSetupResponse> {
    return this.http.post<TotpSetupResponse>(`${API_BASE_URL}/2fa/setup`, {});
  }

  /** Paso 2: confirma con el primer código; activa el 2FA y devuelve los códigos de recuperación. */
  activar2fa(code: string): Observable<RecoveryCodesResponse> {
    return this.http.post<RecoveryCodesResponse>(`${API_BASE_URL}/2fa/activate`, { code });
  }

  /** Desactiva el 2FA (requiere un código válido). */
  desactivar2fa(code: string): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/2fa/disable`, { code });
  }

  /** Logout: el backend borra la cookie; limpiamos el estado local. */
  logout(): Observable<void> {
    return this.http.post(`${API_BASE_URL}/auth/logout`, {}).pipe(
      map(() => undefined),
      tap(() => this.usuario.set(null)),
      catchError(() => {
        this.usuario.set(null);
        return of(undefined);
      }),
    );
  }

  /**
   * Rehidrata la sesion desde la cookie (al arrancar la app o tras un refresh): pide el
   * perfil y, si la cookie es valida, deja el usuario en el signal. Nunca lanza: si no hay
   * sesion (401) deja el estado en null. Usado por el APP_INITIALIZER.
   */
  cargarSesion(): Observable<Usuario | null> {
    return this.http.get<Usuario>(`${API_BASE_URL}/usuarios/me`).pipe(
      tap((u) => this.usuario.set(u)),
      catchError(() => {
        this.usuario.set(null);
        return of(null);
      }),
    );
  }

  /** Marca la sesion como cerrada localmente (ej. ante un 401 en el interceptor). */
  limpiarSesion(): void {
    this.usuario.set(null);
  }

  private cargarSesionOrThrow(): Observable<Usuario> {
    return this.http.get<Usuario>(`${API_BASE_URL}/usuarios/me`).pipe(
      tap((u) => this.usuario.set(u)),
    );
  }
}
