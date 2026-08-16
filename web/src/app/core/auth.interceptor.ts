import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Manda la cookie de auth en cada request ({@code withCredentials}) y, ante un 401, limpia la
 * sesión local y redirige al login. El token JWT viaja en la cookie HttpOnly que el navegador
 * adjunta solo; la protección CSRF la da esa cookie con SameSite=Strict (no hace falta header).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const request = req.clone({ withCredentials: true });

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        auth.limpiarSesion();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
