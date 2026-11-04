import { HttpErrorResponse } from '@angular/common/http';
import { NETWORK_ERROR, describeError } from './problem';

function problem(status: number, body: object): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('describeError', () => {
  it('words known codes for people, ignoring the server detail', () => {
    const error = problem(422, { code: 'INSUFFICIENT_FUNDS', detail: 'Account x has 5 BIF' });
    expect(describeError(error).message).toBe('There is not enough money in this wallet.');
  });

  it('falls back to the server detail for codes it does not know', () => {
    expect(describeError(problem(409, { code: 'NEW_CODE', detail: 'Explained' })).message).toBe(
      'Explained',
    );
  });

  it('lists field errors', () => {
    const error = problem(400, {
      code: 'VALIDATION_FAILED',
      detail: 'Invalid request',
      errors: [{ field: 'amountMinor', message: 'must be greater than 0' }],
    });
    expect(describeError(error).fieldErrors).toEqual(['amountMinor: must be greater than 0']);
  });

  it('tells the user a retry is safe when the server could not be reached', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 })).message).toBe(NETWORK_ERROR);
  });
});
