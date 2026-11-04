import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Account, AccountType } from '../../core/api.models';
import { newIdempotencyKey } from '../../core/idempotency';
import { LedgerApi } from '../../core/ledger-api.service';
import { SUPPORTED_CURRENCIES } from '../../core/money';
import { MoneyPipe } from '../../core/money.pipe';
import { describeError } from '../../core/problem';

/** 50,000 minor units: 50,000 BIF or RWF, 500.00 KES or USD. */
const DEMO_TOP_UP_MINOR = 50_000;

@Component({
  selector: 'wl-wallets',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe],
  template: `
    <h1>My wallets</h1>

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    @if (accounts(); as list) {
      @for (account of list; track account.id) {
        <article class="card wallet">
          <div>
            <h2>{{ account.name }}</h2>
            <p class="muted">
              {{ account.type === 'DRIVER_WALLET' ? 'Driver wallet' : 'Rider wallet' }}
              @if (account.status === 'FROZEN') {
                · <strong class="error">Frozen</strong>
              }
            </p>
          </div>
          <p class="balance">{{ account.balance | money }}</p>
          <div class="actions">
            <a [routerLink]="['/wallets', account.id]">Statement</a>
            <a routerLink="/transfer" [queryParams]="{ from: account.id }">Send</a>
            <button
              type="button"
              class="secondary"
              [disabled]="toppingUp() === account.id"
              (click)="topUp(account)"
            >
              Add demo money
            </button>
          </div>
        </article>
      } @empty {
        <p class="muted">You have no wallets yet. Open one below.</p>
      }
    } @else {
      <p class="muted">Loading…</p>
    }

    <section class="card">
      <h2>Open a wallet</h2>
      <form class="inline" [formGroup]="form" (ngSubmit)="create()">
        <label>
          Name
          <input formControlName="name" maxlength="100" />
        </label>
        <label>
          Type
          <select formControlName="type">
            <option value="USER_WALLET">Rider</option>
            <option value="DRIVER_WALLET">Driver</option>
          </select>
        </label>
        <label>
          Currency
          <select formControlName="currency">
            @for (currency of currencies; track currency) {
              <option [value]="currency">{{ currency }}</option>
            }
          </select>
        </label>
        <button type="submit" [disabled]="form.invalid || creating()">Open wallet</button>
      </form>
    </section>
  `,
})
export class WalletsPage implements OnInit {
  private readonly api = inject(LedgerApi);

  protected readonly currencies = SUPPORTED_CURRENCIES;
  protected readonly accounts = signal<Account[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly toppingUp = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    type: ['USER_WALLET' as AccountType, Validators.required],
    currency: ['BIF', Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    const { name, type, currency } = this.form.getRawValue();
    this.creating.set(true);
    this.api.createWallet(name.trim(), type, currency).subscribe({
      next: () => {
        this.form.reset();
        this.creating.set(false);
        this.load();
      },
      error: (e: unknown) => {
        this.showError(e);
        this.creating.set(false);
      },
    });
  }

  topUp(account: Account): void {
    this.toppingUp.set(account.id);
    this.api
      .demoTopUp(account.id, DEMO_TOP_UP_MINOR, account.currency, newIdempotencyKey())
      .subscribe({
        next: () => {
          this.toppingUp.set(null);
          this.load();
        },
        error: (e: unknown) => {
          this.showError(e);
          this.toppingUp.set(null);
        },
      });
  }

  private load(): void {
    this.error.set(null);
    this.api.myAccounts().subscribe({
      next: (accounts) => this.accounts.set(accounts),
      error: (e: unknown) => this.showError(e),
    });
  }

  private showError(e: unknown): void {
    this.error.set(describeError(e).message);
  }
}
