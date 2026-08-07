import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.autenticado) {
    return true;
  }
  router.navigate(['/login']);
  return false;
};

/** Rutas solo para administradores (import masivo, gestion de usuarios). */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.autenticado && auth.esAdmin) {
    return true;
  }
  router.navigate([auth.autenticado ? '/productos' : '/login']);
  return false;
};
