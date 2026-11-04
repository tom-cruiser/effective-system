# ADR 0001: Store money as integer minor units

- Status: Accepted
- Date: 2026-11-04

## Context

The ledger moves money in four currencies with different precisions: USD and KES have 2 decimal
places, while BIF and RWF have none. Every amount must add up exactly. A ledger that is wrong by
one franc is wrong.

The candidates were:

- `double`/`float`. Binary floating point cannot represent 0.10 exactly, so `0.1 + 0.2 != 0.3`.
  Errors build up silently. Rejected outright.
- `BigDecimal`. It is exact, but its scale is a property of each value (`1.0` vs `1.00`), so
  `equals` and the database column scale both need care. It is also heavier to store and compute.
- `long` in minor units (cents, or whole francs for BIF). It is exact, fast and maps directly to
  PostgreSQL `BIGINT`. There is no scale to get wrong, because the currency defines it.

## Decision

- All amounts are `long` minor units: in Java (`Money.amountMinor`), in the API (`amountMinor`) and
  in the database (`BIGINT`). There is no floating point anywhere on the money path.
- `Money` is a record of `(long amountMinor, String currency)`. It accepts only BIF, RWF, KES and
  USD, and its `plus`/`minus` throw `CURRENCY_MISMATCH` rather than mixing currencies.
- Arithmetic uses `Math.addExact`/`subtractExact`/`multiplyExact`. Overflow throws instead of
  wrapping around to a negative balance.
- The number of decimals comes from ISO 4217 via `java.util.Currency.getDefaultFractionDigits()`.
  It is used only to *format* responses (`"amount": "12.34"`) and is never stored.
- Rounding is explicit and happens in one place. The platform fee
  (`FeeSplit.of(fare, bps)`) is rounded **down** to a whole minor unit and the driver receives
  `fare - fee`. The two parts always add up to the fare, so no money is created or lost.

## Consequences

- Clients send and receive integers. They must know a currency's minor unit to show amounts, so
  responses include a formatted `amount` string to help.
- A `long` holds about 9.2 × 10¹⁸ minor units. That is far more than any wallet will hold, even in
  BIF, but overflow fails loudly if it ever happens.
- Supporting a currency with 3 decimals (e.g. KWD) needs no schema change: only the allowed
  currency list changes.
- Fractions of a minor unit (e.g. interest accrual) cannot be represented. That would need a
  separate higher-precision accrual model.
