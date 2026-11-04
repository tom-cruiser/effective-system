// Money in the browser follows the same rule as the server: amounts are integers of minor units.
// The user types major units ("12.34"), and we convert by moving digits in the string, never by
// multiplying a float (0.29 * 100 === 28.999999999999996 in JavaScript).

/** Decimal places per currency (ISO 4217), matching java.util.Currency on the server. */
const FRACTION_DIGITS: Record<string, number> = { BIF: 0, RWF: 0, KES: 2, USD: 2 };

export const SUPPORTED_CURRENCIES = Object.keys(FRACTION_DIGITS);

export function fractionDigits(currency: string): number {
  const digits = FRACTION_DIGITS[currency];
  if (digits === undefined) {
    throw new Error(`Unsupported currency ${currency}`);
  }
  return digits;
}

/**
 * Parses a positive major-unit amount typed by a user into minor units, or returns null when the
 * text is not a valid amount for that currency (wrong number of decimals, sign, letters, zero or
 * too large to be exact).
 */
export function toMinorUnits(text: string, currency: string): number | null {
  const digits = fractionDigits(currency);
  const pattern = digits === 0 ? /^(\d+)$/ : new RegExp(`^(\\d+)(?:\\.(\\d{1,${digits}}))?$`);
  const match = pattern.exec(text.trim());
  if (!match) {
    return null;
  }
  const fraction = (match[2] ?? '').padEnd(digits, '0');
  const minor = Number(match[1] + fraction);
  return Number.isSafeInteger(minor) && minor > 0 ? minor : null;
}

/** "1234567.5" -> "1,234,567.5": groups the server's decimal string for display, without math. */
export function groupDigits(amount: string): string {
  const [whole, fraction] = amount.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return fraction === undefined ? grouped : `${grouped}.${fraction}`;
}
