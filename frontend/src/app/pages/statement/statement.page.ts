import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Account, Statement } from '../../core/api.models';
import { LedgerApi } from '../../core/ledger-api.service';
import { MoneyPipe } from '../../core/money.pipe';
import { describeError } from '../../core/problem';

const TYPE_LABELS: Record<string, string> = {
  TOP_UP: 'Top-up',
  TRANSFER: 'Transfer',
  RIDE_PAYMENT: 'Ride payment',
  PAYOUT: 'Payout',
  REVERSAL: 'Reversal',
};

@Component({
  selector: 'wl-statement',
  imports: [RouterLink, DatePipe, MoneyPipe],
  template: `
    <p><a routerLink="/wallets">← My wallets</a></p>

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    @if (statement(); as s) {
      <header class="statement-head">
        <h1>{{ account()?.name ?? 'Statement' }}</h1>
        <p class="balance">{{ s.currentBalance | money }}</p>
      </header>

      @if (s.entries.length === 0) {
        <p class="muted">No transactions yet.</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Description</th>
              <th class="num">Amount</th>
              <th class="num">Balance</th>
            </tr>
          </thead>
          <tbody>
            @for (entry of s.entries; track entry.postingId) {
              <tr>
                <td>{{ entry.createdAt | date: 'medium' }}</td>
                <td>
                  {{ label(entry.transactionType) }}
                  @if (entry.description) {
                    <span class="muted">· {{ entry.description }}</span>
                  }
                </td>
                <!-- Wallets are credit-normal: a CREDIT adds money, a DEBIT takes it away. -->
                <td class="num" [class.in]="entry.direction === 'CREDIT'">
                  {{ entry.direction === 'CREDIT' ? '+' : '−' }}{{ entry.amount | money }}
                </td>
                <td class="num">{{ entry.balanceAfter | money }}</td>
              </tr>
            }
          </tbody>
        </table>

        <nav class="pager">
          <button type="button" class="secondary" [disabled]="page() === 0" (click)="go(-1)">
            Newer
          </button>
          <span class="muted">Page {{ s.page + 1 }} of {{ s.totalPages }}</span>
          <button
            type="button"
            class="secondary"
            [disabled]="page() + 1 >= s.totalPages"
            (click)="go(1)"
          >
            Older
          </button>
        </nav>
      }
    } @else if (!error()) {
      <p class="muted">Loading…</p>
    }
  `,
})
export class StatementPage {
  private readonly api = inject(LedgerApi);

  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  protected readonly page = signal(0);
  protected readonly account = signal<Account | null>(null);
  protected readonly statement = signal<Statement | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Re-runs whenever the wallet id or the page changes.
    effect(() => this.load(this.id(), this.page()));
    effect(() => {
      // Only for the heading; if this fails, the statement request reports the error.
      this.api.account(this.id()).subscribe({ next: (a) => this.account.set(a), error: () => {} });
    });
  }

  protected label(type: string): string {
    return TYPE_LABELS[type] ?? type;
  }

  protected go(delta: number): void {
    this.page.update((p) => p + delta);
  }

  private load(id: string, page: number): void {
    this.error.set(null);
    this.api.statement(id, page).subscribe({
      next: (s) => this.statement.set(s),
      error: (e: unknown) => this.error.set(describeError(e).message),
    });
  }
}
