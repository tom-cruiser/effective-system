import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { TokenResponse } from './api.models';

const STORAGE_KEY = 'wallet-ledger.token';

export interface Session {
  token: string;
  userId: string;
  email: string;
  roles: string[];
  /** Epoch milliseconds. */
  expiresAt: number;
}

/**
 * Holds the signed-in user's access token.
 *
 * The token lives in sessionStorage so a page refresh keeps you signed in, while closing the tab
 * signs you out. Any script on the page can read it, which is the usual trade-off of bearer tokens
 * in a single-page app; Angular's template escaping is the defence against injected scripts. The
 * alternative, an httpOnly cookie, would need CSRF protection that this stateless API turns off.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(restore());

  readonly user = this.session.asReadonly();
  readonly signedIn = computed(() => this.session() !== null);

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/v1/auth/login', { email, password })
      .pipe(tap((response) => this.start(response.accessToken)));
  }

  /** The token to send, or null when there is none or it has expired. */
  token(): string | null {
    const session = this.session();
    if (session && session.expiresAt <= Date.now()) {
      this.clear();
      return null;
    }
    return session?.token ?? null;
  }

  logout(): void {
    this.clear();
    void this.router.navigate(['/login']);
  }

  private start(token: string): void {
    const session = decode(token);
    sessionStorage.setItem(STORAGE_KEY, token);
    this.session.set(session);
  }

  private clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }
}

function restore(): Session | null {
  const token = sessionStorage.getItem(STORAGE_KEY);
  if (!token) {
    return null;
  }
  try {
    const session = decode(token);
    return session.expiresAt > Date.now() ? session : null;
  } catch {
    return null;
  }
}

/**
 * Reads the token's claims for display only (who is signed in, when it expires). The browser cannot
 * verify the signature and does not need to: the server checks it on every request.
 */
export function decode(token: string): Session {
  const payload = token.split('.')[1];
  const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
  const claims = JSON.parse(json) as { sub: string; email: string; roles: string[]; exp: number };
  return {
    token,
    userId: claims.sub,
    email: claims.email,
    roles: claims.roles ?? [],
    expiresAt: claims.exp * 1000,
  };
}
