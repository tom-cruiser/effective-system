# ADR 0002: Pessimistic row locks, taken in ascending id order

- Status: Accepted
- Date: 2026-11-04

## Context

A money movement reads balances, checks a rule ("a wallet may not go negative"), then writes. If
two requests do this on the same account at the same time, both can read balance 50, both pass
the check, and together they spend 100. That is a double spend. The cached `balance_minor` must
also stay equal to the sum of postings (invariant 8).

The workload has **hot rows**:

- A rider's wallet receives bursts from the same user, from retries and from parallel app sessions.
- `PLATFORM_FEES` (one per currency) is written by *every* ride payment.
- `TOPUP_CLEARING` is written by every top-up.

Options considered:

1. **Optimistic locking (`@Version`) with retries.** Read without locking, then
   `UPDATE … WHERE version = ?`, and retry on conflict. This works well when conflicts are rare.
   On hot rows most attempts conflict, so work is thrown away, retries pile up and latency becomes
   unpredictable. Every caller also needs a correct retry loop.
2. **`SERIALIZABLE` isolation.** PostgreSQL detects the anomaly and aborts one transaction. This
   also needs retry loops, has the same waste under contention, and is harder to reason about.
3. **Pessimistic locking (`SELECT … FOR UPDATE`).** The second transaction waits until the first
   commits, then reads the fresh balance. It is correct by construction and needs no retries.

## Decision

Use pessimistic row locks, with these rules (all enforced in `PostingEngine`):

1. Every money movement runs in one `@Transactional` service method. The engine's methods use
   `Propagation.MANDATORY`, so calling them outside a transaction fails immediately.
2. `PostingEngine.lockAccounts` locks every affected account with
   `@Lock(PESSIMISTIC_WRITE)` (`SELECT … FOR UPDATE`) **before** anything else reads them.
3. **Lock ordering rule: accounts are always locked in ascending UUID order** (a `TreeSet`).
   Transfer A→B and transfer B→A then both try to lock A first. One waits, and a cycle (a
   deadlock) cannot form. Java's `UUID.compareTo` does not order UUIDs the same way PostgreSQL
   does. That is fine, because all locking goes through this one method, so the order is the same
   for every transaction.
4. Balances are checked **after** the locks are held. Postings and new cached balances are written
   in the same database transaction.
5. `@Version` stays on `Account` as a safety net. If code ever updated a balance from a stale copy
   (for example an entity loaded before the lock), the `UPDATE … WHERE version = ?` would fail
   instead of silently losing money.

## Consequences

- **Proven by tests** (`ConcurrencyIT`):
  - 100 concurrent transfers of 1 against a balance of 50 give exactly 50 successes and 50
    `INSUFFICIENT_FUNDS`, and the balance never goes below 0.
  - 200 opposing A↔B transfers all succeed.
  - A negative-control test shows that locking the same rows in *opposite* order really does
    deadlock (SQLSTATE `40P01`).
- **Throughput on a hot row is serial.** All ride payments in one currency queue on the
  `PLATFORM_FEES` row. Transactions are short (a few indexed statements, no network calls), so the
  queue drains quickly. This is still the main scaling limit. Fixes if it bites:
  - stop caching system-account balances, and derive them;
  - split a hot system account into N sub-accounts;
  - post fees asynchronously in batches.
- A waiting transaction holds a database connection. Long waits could exhaust the pool. A
  `lock_timeout` would turn this into a fast, retryable error.
- Rule for contributors: never load an `Account` in a money-moving transaction before
  `lockAccounts`, and never call external services inside one.
