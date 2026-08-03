import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Usuario } from './models';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private http = inject(HttpClient);

  // El backend expone /auth/register (crea OPERARIO). El listado de usuarios
  // todavia no existe como endpoint.
  registrar(req: { username: string; password: string; nombre: string }): Observable<Usuario> {
    return this.http.post<Usuario>(`${API_BASE_URL}/auth/register`, req);
  }
}
