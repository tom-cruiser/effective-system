import { groupDigits, toMinorUnits } from './money';

describe('toMinorUnits', () => {
  it('converts without floating-point error', () => {
    // 0.29 * 100 is 28.999999999999996 in JavaScript.
    expect(toMinorUnits('0.29', 'USD')).toBe(29);
    expect(toMinorUnits('12.5', 'KES')).toBe(1250);
    expect(toMinorUnits(' 10000 ', 'BIF')).toBe(10000);
  });

  it('rejects more decimals than the currency has', () => {
    expect(toMinorUnits('1.5', 'BIF')).toBeNull();
    expect(toMinorUnits('1.234', 'USD')).toBeNull();
  });

  it('rejects zero, negatives and junk', () => {
    for (const text of ['0', '0.00', '-5', '5e3', '', '1,000', '.5']) {
      expect(toMinorUnits(text, 'USD')).toBeNull();
    }
  });

  it('rejects amounts too large to be exact', () => {
    expect(toMinorUnits('90071992547409930', 'BIF')).toBeNull();
  });
});

describe('groupDigits', () => {
  it('adds thousands separators and keeps the decimals as sent', () => {
    expect(groupDigits('1234567.50')).toBe('1,234,567.50');
    expect(groupDigits('999')).toBe('999');
  });
});
