import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Account, Money, Transaction } from '../../core/api.models';
import { newIdempotencyKey } from '../../core/idempotency';
import { LedgerApi } from '../../core/ledger-api.service';
import { fractionDigits, toMinorUnits } from '../../core/money';
import { MoneyPipe } from '../../core/money.pipe';
import { describeError } from '../../core/problem';

/** Value of the "To" select that reveals a field for someone else's wallet id. */
const OTHER = 'other';

interface Receipt {
  transactionId: string;
  amount: Money;
  /** True when the server answered 200: an earlier attempt had already gone through. */
  replayed: boolean;
}

@Component({
  selector: 'wl-transfer',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe],
  template: `
    <h1>Send money</h1>

    @if (receipt(); as r) {
      <div class="card success" role="status">
        <p>
          Sent <strong>{{ r.amount | money }}</strong
          >.
          <span class="muted">Transaction {{ r.transactionId }}</span>
        </p>
        @if (r.replayed) {
          <p class="muted">
            An earlier attempt had already gone through, so this is that same transfer. You were not
            charged twice.
          </p>
        }
      </div>
    }

    @if (accounts().length === 0) {
      <p class="muted">You need a wallet first. <a routerLink="/wallets">Open one</a>.</p>
    } @else {
      <form class="card" [formGroup]="form" (ngSubmit)="submit()">
        <label>
          From
          <select formControlName="fromAccountId">
            @for (account of accounts(); track account.id) {
              <option [value]="account.id">
                {{ account.name }} — {{ account.balance | money }}
              </option>
            }
          </select>
        </label>

        <label>
          To
          <select formControlName="toChoice">
            @for (account of destinations(); track account.id) {
              <option [value]="account.id">{{ account.name }} ({{ account.currency }})</option>
            }
            <option value="other">Someone else's wallet…</option>
          </select>
        </label>
        @if (values().toChoice === 'other') {
          <label>
            Wallet id
            <input formControlName="toOther" placeholder="e.g. 3f1c9b10-…" />
          </label>
        }

        <label>
          Amount ({{ currency() }})
          <input formControlName="amount" inputmode="decimal" autocomplete="off" />
          <small class="muted">
            @if (decimals() === 0) {
              Whole {{ currency() }} only, no decimals.
            } @else {
              Up to {{ decimals() }} decimals, e.g. 12.50.
            }
          </small>
        </label>

        <label>
          Note (optional)
          <input formControlName="description" maxlength="255" />
        </label>

        @if (error()) {
          <div class="error" role="alert">
            <p>{{ error() }}</p>
            @for (line of fieldErrors(); track line) {
              <p>{{ line }}</p>
            }
          </div>
        }

        <button type="submit" [disabled]="busy()">{{ busy() ? 'Sending…' : 'Send' }}</button>
        <p class="hint">
          Request key <code data-testid="idempotency-key">{{ idempotencyKey() }}</code> is reused if
          you press Send again, so a retry can never pay twice.
        </p>
      </form>
    }
  `,
})
export class TransferPage implements OnInit {
  private readonly api = inject(LedgerApi);

  /** Optional ?from= query parameter, set by the "Send" link on a wallet. */
  readonly from = input<string>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fromAccountId: ['', Validators.required],
    toChoice: ['', Validators.required],
    toOther: [''],
    amount: ['', Validators.required],
    description: [''],
  });
  protected readonly values = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly accounts = signal<Account[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<string[]>([]);
  protected readonly receipt = signal<Receipt | null>(null);

  /**
   * One key per logical transfer. It survives failed attempts, so pressing Send again after a
   * timeout replays the same request and the server answers with the original transfer instead of
   * moving the money twice. Editing the form makes it a different transfer, so it gets a new key
   * (reusing the old one would be rejected with IDEMPOTENCY_CONFLICT).
   */
  protected readonly idempotencyKey = signal(newIdempotencyKey());

  private readonly source = computed(() =>
    this.accounts().find((a) => a.id === this.values().fromAccountId),
  );
  protected readonly currency = computed(() => this.source()?.currency ?? '');
  protected readonly decimals = computed(() =>
    this.currency() ? fractionDigits(this.currency()) : 0,
  );
  protected readonly destinations = computed(() =>
    this.destinationsFor(this.values().fromAccountId),
  );

  constructor() {
    this.form.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.idempotencyKey.set(newIdempotencyKey()));
    // A new source wallet may have a different currency, so offer matching destinations again.
    // The control emits before the group does, so use the new id rather than values().
    this.form.controls.fromAccountId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((fromId) =>
        this.form.controls.toChoice.setValue(this.destinationsFor(fromId)[0]?.id ?? OTHER),
      );
  }

  ngOnInit(): void {
    this.api.myAccounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        const preselected = accounts.find((a) => a.id === this.from()) ?? accounts[0];
        if (preselected) {
          this.form.controls.fromAccountId.setValue(preselected.id);
        }
      },
      error: (e: unknown) => this.error.set(describeError(e).message),
    });
  }

  submit(): void {
    const v = this.form.getRawValue();
    const currency = this.currency();
    const toAccountId = v.toChoice === OTHER ? v.toOther.trim() : v.toChoice;
    const amountMinor = currency ? toMinorUnits(v.amount, currency) : null;
    this.receipt.set(null);
    this.fieldErrors.set([]);
    if (amountMinor === null) {
      this.error.set(`Enter a positive amount in ${currency}.`);
      return;
    }
    if (!toAccountId) {
      this.error.set('Choose who to send the money to.');
      return;
    }

    this.busy.set(true);
    this.error.set(null);
    const input = {
      fromAccountId: v.fromAccountId,
      toAccountId,
      amountMinor,
      currency,
      description: v.description.trim() || undefined,
    };
    this.api.transfer(input, this.idempotencyKey()).subscribe({
      next: (response) =>
        this.succeeded(response.body!, input.fromAccountId, response.status === 200),
      error: (e: unknown) => {
        // Keep the key: if this was a timeout, the transfer may have happened.
        const { message, fieldErrors } = describeError(e);
        this.error.set(message);
        this.fieldErrors.set(fieldErrors);
        this.busy.set(false);
      },
    });
  }

  private succeeded(transaction: Transaction, fromAccountId: string, replayed: boolean): void {
    const debit = transaction.postings.find((p) => p.accountId === fromAccountId)!;
    this.receipt.set({ transactionId: transaction.id, amount: debit.amount, replayed });
    this.form.patchValue({ amount: '', description: '' }); // also issues a fresh key
    this.busy.set(false);
    this.refreshBalances();
  }

  /** Your other wallets in the same currency; a transfer cannot change currency. */
  private destinationsFor(fromId: string | undefined): Account[] {
    const source = this.accounts().find((a) => a.id === fromId);
    return this.accounts().filter((a) => a.id !== fromId && a.currency === source?.currency);
  }

  private refreshBalances(): void {
    this.api.myAccounts().subscribe({ next: (accounts) => this.accounts.set(accounts) });
  }
}
