import { Routes } from '@angular/router';

import { adminGuard, authGuard } from './core/auth.guard';
import { ForgotPassword } from './features/forgot-password';
import { Layout } from './features/layout';
import { Login } from './features/login';
import { Productos } from './features/productos';
import { ResetPassword } from './features/reset-password';
import { Stock } from './features/stock';
import { TwoFactorRecovery } from './features/two-factor-recovery';
import { Ubicaciones } from './features/ubicaciones';
import { Extracciones } from './features/extracciones';
import { CargaIa } from './features/carga-ia';
import { Configuracion } from './features/configuracion';
import { Importacion } from './features/importacion';
import { Usuarios } from './features/usuarios';
import { Precios } from './features/precios';
import { Compras } from './features/compras';
import { Seguridad } from './features/seguridad';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'olvide-password', component: ForgotPassword },
  { path: 'reset-password', component: ResetPassword },
  { path: 'recuperar-2fa', component: TwoFactorRecovery },
  {
    path: '',
    component: Layout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'productos', pathMatch: 'full' },
      { path: 'productos', component: Productos },
      { path: 'stock', component: Stock },
      { path: 'ubicaciones', component: Ubicaciones },
      { path: 'carga-ia', component: CargaIa },
      { path: 'extracciones', component: Extracciones },
      { path: 'seguridad', component: Seguridad },
      { path: 'configuracion', component: Configuracion },
      { path: 'compras', component: Compras, canActivate: [adminGuard] },
      { path: 'precios', component: Precios, canActivate: [adminGuard] },
      { path: 'importacion', component: Importacion, canActivate: [adminGuard] },
      { path: 'usuarios', component: Usuarios, canActivate: [adminGuard] },
    ],
  },
  { path: '**', redirectTo: '' },
];
