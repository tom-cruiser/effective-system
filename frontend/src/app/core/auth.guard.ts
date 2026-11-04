import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** A convenience redirect only: the API enforces access on every request regardless. */
export const authGuard: CanActivateFn = () =>
  inject(AuthService).token() !== null || inject(Router).createUrlTree(['/login']);
