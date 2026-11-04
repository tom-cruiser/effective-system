# ADR 0003: An append-only ledger, corrected only by reversals

- Status: Accepted
- Date: 2026-11-04

## Context

The postings table is the source of truth for every balance. If a posting could be edited or
deleted, the following would all become impossible to trust:

- statements already sent to users;
- the balance history;
- any audit or reconciliation.

Mistakes still happen (a ride charged twice, a top-up credited to the wrong wallet) and must be
correctable.

## Decision

- **Postings are immutable, enforced in three layers:**
  1. Domain: `Posting` has no setters.
  2. ORM: `Posting` and `LedgerTransaction` are Hibernate `@Immutable`, so Hibernate never
     generates an `UPDATE` for them.
  3. Database: a trigger raises an exception on `UPDATE`, `DELETE` and `TRUNCATE` of `postings`.
     This holds even for a developer with a SQL console or a buggy migration.
- **Corrections are new transactions.** `POST /transactions/{id}/reversal` creates a `REVERSAL`
  transaction:
  - It posts the exact opposite of every original posting (same account and amount, opposite
    direction).
  - It links to the original through `reverses_transaction_id`.
  - Both the original and the reversal stay visible in statements.
- **A transaction can be reversed at most once.** `UNIQUE (reverses_transaction_id)` enforces this
  in the database. The service also checks it *after* locking the affected accounts, so two
  concurrent reversals are serialized and the second gets `409 ALREADY_REVERSED`.
- A reversal cannot itself be reversed. To redo something, post a new transaction.
- **Reversals obey the same invariants as any other transaction.** If the rider has already spent
  the money a top-up brought in, reversing that top-up fails with `INSUFFICIENT_FUNDS` instead of
  making the wallet negative.
- Each posting stores `balance_after_minor` and a database-assigned `entry_seq`. Statements need
  no recalculation, and because history never changes, those values stay correct.

## Consequences

- There is a full audit trail by construction. Any past balance can be reproduced from postings.
- Tests cannot clean up by deleting rows. Each test creates its own fresh accounts instead, and
  global checks (reconciliation) must hold for the whole shared database.
- Data-protection erasure requests cannot delete ledger rows. Personal data must therefore live
  outside the ledger and be referenced by id, which is already the case: `owner_id` is an opaque
  UUID.
- Reversing a top-up whose money was already spent needs a business decision (debt, clawback,
  manual adjustment). This system deliberately refuses rather than guessing.
