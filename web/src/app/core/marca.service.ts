import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Marca } from './models';

@Injectable({ providedIn: 'root' })
export class MarcaService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/marcas`;

  listar(): Observable<Marca[]> {
    return this.http.get<Marca[]>(this.base);
  }

  crear(nombre: string): Observable<Marca> {
    return this.http.post<Marca>(this.base, { nombre });
  }
}
