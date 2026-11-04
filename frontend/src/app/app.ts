import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'wl-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <span class="brand">Wallet Ledger</span>
      @if (auth.user(); as user) {
        <nav>
          <a routerLink="/wallets" routerLinkActive="active">Wallets</a>
          <a routerLink="/transfer" routerLinkActive="active">Send money</a>
        </nav>
        <span class="who">{{ user.email }}</span>
        <button type="button" class="link" (click)="auth.logout()">Sign out</button>
      }
    </header>
    <main>
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
}
