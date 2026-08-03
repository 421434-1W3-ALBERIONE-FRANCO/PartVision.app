import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { LoginResponse } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private static readonly TOKEN_KEY = 'pv_token';

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${API_BASE_URL}/auth/login`, { username, password })
      .pipe(tap((res) => localStorage.setItem(AuthService.TOKEN_KEY, res.token)));
  }

  logout(): void {
    localStorage.removeItem(AuthService.TOKEN_KEY);
  }

  get token(): string | null {
    return localStorage.getItem(AuthService.TOKEN_KEY);
  }

  get autenticado(): boolean {
    return this.token !== null;
  }
}
