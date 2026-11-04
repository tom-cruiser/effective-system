import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Account, AccountType, Statement, Transaction } from './api.models';

const API = '/api/v1';

export interface TransferInput {
  fromAccountId: string;
  toAccountId: string;
  amountMinor: number;
  currency: string;
  description?: string;
}

/** Thin typed wrapper over the REST API; no business rules live here. */
@Injectable({ providedIn: 'root' })
export class LedgerApi {
  private readonly http = inject(HttpClient);

  myAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${API}/accounts`);
  }

  account(id: string): Observable<Account> {
    return this.http.get<Account>(`${API}/accounts/${id}`);
  }

  createWallet(name: string, type: AccountType, currency: string): Observable<Account> {
    return this.http.post<Account>(`${API}/accounts`, { name, type, currency });
  }

  statement(accountId: string, page: number, size = 20): Observable<Statement> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Statement>(`${API}/accounts/${accountId}/statement`, { params });
  }

  /** The full response, because 201 (new transfer) and 200 (replayed retry) mean different things. */
  transfer(input: TransferInput, idempotencyKey: string): Observable<HttpResponse<Transaction>> {
    return this.http.post<Transaction>(`${API}/transactions/transfers`, input, {
      headers: { 'Idempotency-Key': idempotencyKey },
      observe: 'response',
    });
  }

  /**
   * A demo stand-in for the mobile-money webhook that would credit a wallet in production, so a
   * new wallet has something to spend.
   */
  demoTopUp(accountId: string, amountMinor: number, currency: string, idempotencyKey: string) {
    return this.http.post<Transaction>(
      `${API}/transactions/top-ups`,
      {
        accountId,
        amountMinor,
        currency,
        externalReference: `DEMO-${idempotencyKey}`,
        description: 'Demo top-up',
      },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }
}
