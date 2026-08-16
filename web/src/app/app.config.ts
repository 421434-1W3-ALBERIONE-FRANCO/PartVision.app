import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
  inject,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // El token CSRF (X-XSRF-TOKEN) lo adjunta authInterceptor a mano, leído fresco de la
    // cookie en cada request mutante (más confiable que el XSRF nativo, que cacheaba el valor).
    provideHttpClient(withInterceptors([authInterceptor])),
    // Al arrancar (o tras un refresh) rehidrata la sesion desde la cookie antes de que
    // corran los guards, para que sepan si hay usuario y con que roles.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).cargarSesion())),
  ],
};
