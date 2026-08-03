import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';
import { Layout } from './features/layout';
import { Login } from './features/login';
import { Productos } from './features/productos';
import { Stock } from './features/stock';
import { Ubicaciones } from './features/ubicaciones';
import { Extracciones } from './features/extracciones';
import { Importacion } from './features/importacion';
import { Usuarios } from './features/usuarios';

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    path: '',
    component: Layout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'productos', pathMatch: 'full' },
      { path: 'productos', component: Productos },
      { path: 'stock', component: Stock },
      { path: 'ubicaciones', component: Ubicaciones },
      { path: 'extracciones', component: Extracciones },
      { path: 'importacion', component: Importacion },
      { path: 'usuarios', component: Usuarios },
    ],
  },
  { path: '**', redirectTo: '' },
];
