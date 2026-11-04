import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Account, Transaction } from '../../core/api.models';
import { TransferPage } from './transfer.page';

function wallet(id: string, name: string): Account {
  return {
    id,
    ownerId: 'u1',
    name,
    type: 'USER_WALLET',
    currency: 'KES',
    status: 'ACTIVE',
    balance: { amountMinor: 100_00, currency: 'KES', amount: '100.00' },
    createdAt: '2026-11-04T10:00:00Z',
  };
}

const TRANSFER: Transaction = {
  id: 'tx-1',
  type: 'TRANSFER',
  description: null,
  externalReference: null,
  reversesTransactionId: null,
  createdAt: '2026-11-04T10:00:00Z',
  postings: [
    {
      id: 'p1',
      accountId: 'a',
      direction: 'DEBIT',
      amount: { amountMinor: 1250, currency: 'KES', amount: '12.50' },
    },
    {
      id: 'p2',
      accountId: 'b',
      direction: 'CREDIT',
      amount: { amountMinor: 1250, currency: 'KES', amount: '12.50' },
    },
  ],
};

describe('TransferPage', () => {
  let fixture: ComponentFixture<TransferPage>;
  let backend: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [TransferPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TransferPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    backend.expectOne('/api/v1/accounts').flush([wallet('a', 'Main'), wallet('b', 'Savings')]);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  async function type(value: string): Promise<void> {
    const amount = page.querySelector<HTMLInputElement>('input[formcontrolname=amount]')!;
    amount.value = value;
    amount.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function send(): Promise<void> {
    page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  const shownKey = () => page.querySelector('[data-testid=idempotency-key]')!.textContent!.trim();

  it('sends the amount in minor units with an Idempotency-Key', async () => {
    await type('12.5');
    await send();

    const request = backend.expectOne('/api/v1/transactions/transfers').request;
    expect(request.body).toEqual({
      fromAccountId: 'a',
      toAccountId: 'b',
      amountMinor: 1250,
      currency: 'KES',
      description: undefined,
    });
    expect(request.headers.get('Idempotency-Key')).toBe(shownKey());
  });

  it('reuses the same key when retrying after a network failure', async () => {
    await type('12.50');
    await send();
    const first = backend.expectOne('/api/v1/transactions/transfers');
    first.error(new ProgressEvent('error')); // status 0: the server may or may not have it
    await fixture.whenStable();
    expect(page.textContent).toContain('safe to press the button again');

    await send();
    const retry = backend.expectOne('/api/v1/transactions/transfers');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(
      first.request.headers.get('Idempotency-Key'),
    );

    // The server recognises the key and returns the original transfer with 200.
    retry.flush(TRANSFER, { status: 200, statusText: 'OK' });
    backend.expectOne('/api/v1/accounts').flush([]);
    await fixture.whenStable();
    expect(page.textContent).toContain('You were not charged twice');
  });

  it('uses a new key once the transfer is changed', async () => {
    await type('12.50');
    const before = shownKey();
    await type('13.00');
    expect(shownKey()).not.toBe(before);
  });

  it('starts a new key after a successful transfer', async () => {
    await type('12.50');
    await send();
    const request = backend.expectOne('/api/v1/transactions/transfers');
    request.flush(TRANSFER, { status: 201, statusText: 'Created' });
    backend.expectOne('/api/v1/accounts').flush([wallet('a', 'Main'), wallet('b', 'Savings')]);
    await fixture.whenStable();

    expect(page.textContent).toContain('Sent 12.50 KES');
    expect(shownKey()).not.toBe(request.request.headers.get('Idempotency-Key'));
  });

  it('rejects an amount with too many decimals before calling the API', async () => {
    await type('1.234');
    await send();
    backend.expectNone('/api/v1/transactions/transfers');
    expect(page.textContent).toContain('Enter a positive amount in KES');
  });

  it('shows the business error from the API', async () => {
    await type('500');
    await send();
    backend
      .expectOne('/api/v1/transactions/transfers')
      .flush(
        { status: 422, code: 'INSUFFICIENT_FUNDS', detail: 'x' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    await fixture.whenStable();
    expect(page.textContent).toContain('There is not enough money in this wallet.');
  });
});
