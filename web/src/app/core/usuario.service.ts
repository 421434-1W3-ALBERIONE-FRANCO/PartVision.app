import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Usuario } from './models';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private http = inject(HttpClient);

  me(): Observable<Usuario> {
    return this.http.get<Usuario>(`${API_BASE_URL}/usuarios/me`);
  }

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(`${API_BASE_URL}/usuarios`);
  }

  crear(req: { username: string; password: string; nombre: string; rol?: string }): Observable<Usuario> {
    return this.http.post<Usuario>(`${API_BASE_URL}/usuarios`, req);
  }

  toggleActivo(id: number): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API_BASE_URL}/usuarios/${id}/activo`, {});
  }

  cambiarRol(id: number, rol: string): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API_BASE_URL}/usuarios/${id}/rol`, { rol });
  }

  /** Elimina un usuario por completo (hard delete). */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/usuarios/${id}`);
  }
}
