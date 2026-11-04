import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

// Pages are lazy-loaded, so the login screen does not download the rest of the app.
export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in',
    loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'wallets',
        title: 'My wallets',
        loadComponent: () => import('./pages/wallets/wallets.page').then((m) => m.WalletsPage),
      },
      {
        path: 'wallets/:id',
        title: 'Statement',
        loadComponent: () =>
          import('./pages/statement/statement.page').then((m) => m.StatementPage),
      },
      {
        path: 'transfer',
        title: 'Send money',
        loadComponent: () => import('./pages/transfer/transfer.page').then((m) => m.TransferPage),
      },
      { path: '', pathMatch: 'full', redirectTo: 'wallets' },
    ],
  },
  { path: '**', redirectTo: 'wallets' },
];
