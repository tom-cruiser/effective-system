// TypeScript mirrors of the API's JSON. Field names match the Java records one to one.

export interface Money {
  /** Integer amount in the currency's smallest unit; this is what the ledger stores. */
  amountMinor: number;
  currency: string;
  /** The same amount in major units, formatted by the server, e.g. "12.34". */
  amount: string;
}

export type AccountType = 'USER_WALLET' | 'DRIVER_WALLET' | 'SYSTEM';

export interface Account {
  id: string;
  ownerId: string | null;
  name: string;
  type: AccountType;
  currency: string;
  status: 'ACTIVE' | 'FROZEN';
  balance: Money;
  createdAt: string;
}

export interface StatementEntry {
  postingId: string;
  transactionId: string;
  transactionType: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: Money;
  balanceAfter: Money;
  description: string | null;
  createdAt: string;
}

export interface Statement {
  accountId: string;
  currency: string;
  currentBalance: Money;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  entries: StatementEntry[];
}

export interface Posting {
  id: string;
  accountId: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: Money;
}

export interface Transaction {
  id: string;
  type: string;
  description: string | null;
  externalReference: string | null;
  reversesTransactionId: string | null;
  createdAt: string;
  postings: Posting[];
}

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

/** RFC 7807 error body. {@code code} is the API's stable machine-readable error code. */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}
