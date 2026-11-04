import { HttpErrorResponse } from '@angular/common/http';
import { Problem } from './api.models';

/**
 * Wording for the API's stable error codes. The UI switches on {@code code}, never on the
 * human-readable {@code detail}, so server-side wording can change without breaking the client.
 */
const MESSAGES: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'There is not enough money in this wallet.',
  ACCOUNT_FROZEN: 'One of these wallets is frozen, so no money can move in or out of it.',
  CURRENCY_MISMATCH: 'Both wallets must use the same currency.',
  SAME_ACCOUNT_TRANSFER: 'Choose two different wallets.',
  ACCOUNT_NOT_FOUND: 'That wallet does not exist.',
  ACCESS_DENIED: 'You are not allowed to use that wallet.',
  IDEMPOTENCY_CONFLICT: 'This request was already used for a different payment. Please try again.',
  INVALID_CREDENTIALS: 'Wrong email or password.',
  UNAUTHENTICATED: 'Your session has expired. Please sign in again.',
};

/** Shown when the request may or may not have reached the server. */
export const NETWORK_ERROR =
  'Could not reach the server. Nothing is lost: it is safe to press the button again.';

export interface ErrorMessage {
  message: string;
  /** Per-field validation messages, when the server sent any. */
  fieldErrors: string[];
}

export function describeError(error: unknown): ErrorMessage {
  if (!(error instanceof HttpErrorResponse)) {
    return { message: 'Something went wrong.', fieldErrors: [] };
  }
  if (error.status === 0) {
    return { message: NETWORK_ERROR, fieldErrors: [] };
  }
  const problem = asProblem(error.error);
  const fieldErrors = (problem?.errors ?? []).map((e) => `${e.field}: ${e.message}`);
  const known = problem?.code ? MESSAGES[problem.code] : undefined;
  return {
    message: known ?? problem?.detail ?? `Request failed (${error.status}).`,
    fieldErrors,
  };
}

function asProblem(body: unknown): Problem | null {
  return typeof body === 'object' && body !== null ? (body as Problem) : null;
}
