import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
  inject,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor]),
      // CSRF: Angular lee la cookie XSRF-TOKEN y la reenvia en el header X-XSRF-TOKEN
      // en cada POST/PUT/PATCH/DELETE same-origin (coincide con la config del backend).
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    // Al arrancar (o tras un refresh) rehidrata la sesion desde la cookie antes de que
    // corran los guards, para que sepan si hay usuario y con que roles.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).cargarSesion())),
  ],
};
