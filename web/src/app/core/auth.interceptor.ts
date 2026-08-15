import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Manda la cookie de auth en cada request ({@code withCredentials}) y, ante un 401,
 * limpia la sesion local y redirige al login. El token ya no se agrega a mano: viaja
 * en la cookie HttpOnly que el navegador adjunta solo. El header CSRF (X-XSRF-TOKEN) lo
 * agrega el soporte nativo de Angular (ver withXsrfConfiguration en app.config).
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
