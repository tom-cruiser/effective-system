# A plain-language walkthrough

This guide explains how the ledger works without assuming you have read the code. It has three
parts:

1. What happens when a rider pays for a ride, from the HTTP request to the database commit.
2. How row locking stops two requests from spending the same money.
3. Ten interview questions about this project, with short answers.

File names are given so you can follow along in the source.

---

## 1. A ride payment, step by step

A rider has finished a 10,000 BIF ride. The rider's app sends:

```http
POST /api/v1/transactions/ride-payments
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Idempotency-Key: 8f14e45f-ceea-467f-a0e6-1e3b6d2f9c1a
Content-Type: application/json

{ "riderAccountId": "0b6f…", "driverAccountId": "7d2e…",
  "fareMinor": 10000, "currency": "BIF", "rideId": "ride-2041" }
```

### Step 1. Who is calling? (`security/SecurityConfig.java`)

Spring Security's filter chain runs before any controller.

- It checks the JWT's **signature** (HS256 with the server's secret), its **expiry** and its
  **issuer**.
- If any of these is wrong, the request stops with `401 UNAUTHENTICATED`.
- If the token is valid, its `sub` claim (the user's id) and its `roles` claim (USER or ADMIN)
  become the "current user" for this request.

There is no server-side session. Every request carries its own proof of identity.

### Step 2. Is the request well formed, and may this user do it? (`transaction/TransactionController.java`)

- **Bean Validation** checks the body. The fare must be positive, the currency must be one of BIF,
  RWF, KES or USD, and `rideId` must be present. A bad body gets `400 VALIDATION_FAILED` with a
  list of field errors.
- **`AccessGuard.requireAccountAccess(riderAccountId)`** checks that the caller owns the rider
  wallet, using one small SQL query on `owner_id`. Paying from someone else's wallet gets
  `403 ACCESS_DENIED`.
  - The driver's wallet is not checked, because anyone may *receive* money.
  - Admins skip this check.
- **The `Idempotency-Key` header** must be present and at most 100 characters.

### Step 3. Have we seen this request before? (`transaction/TransactionService.java`)

Mobile networks drop responses, so the app may send the same payment twice.

1. The service computes a **fingerprint** of the request: a SHA-256 hash of the operation name
   (`ride-payment`) plus the body.
2. It looks up the idempotency key:
   - **Key found, same fingerprint.** This is a retry. The original transaction is returned with
     `200 OK`, and nothing new is posted.
   - **Key found, different fingerprint.** The key was reused for a different payment:
     `409 IDEMPOTENCY_CONFLICT`.
   - **Key not found.** Go on to step 4.

### Step 4. Open a database transaction and work out the entry (`transaction/TransactionFlows.java`)

`TransactionFlows.ridePayment` is annotated `@Transactional`, so everything from here to the end
either commits together or rolls back together.

It computes the split with `FeeSplit` (`transaction/FeeSplit.java`):

- fee = floor(10,000 × 1,500 bps / 10,000) = **1,500**;
- driver share = 10,000 − 1,500 = **8,500**.

The fee is rounded down and the driver gets the remainder, so the two parts always add up to the
fare exactly.

It then finds the id of the `PLATFORM_FEES` account for BIF.

### Step 5. Lock the accounts (`transaction/PostingEngine.java`, `lockAccounts`)

The engine locks the three accounts involved (rider, driver, `PLATFORM_FEES`):

- with `SELECT … FOR UPDATE`;
- **in ascending id order**;
- before it reads any balance.

If another request holds one of these rows, this one **waits** until that transaction finishes.
Then it checks that the rider's account really is a rider wallet and the driver's really is a
driver wallet.

### Step 6. Check every rule on the locked rows (`PostingEngine.post`)

With the rows locked, nobody else can change these balances, so the checks are trustworthy:

- at least two lines, debits equal credits, every amount positive;
- one currency, matching every account;
- no account is frozen;
- **no wallet would go negative.** The engine works out each account's balance *after* the entry.
  If the rider has less than 10,000 BIF, the result is `422 INSUFFICIENT_FUNDS` and nothing is
  written.

### Step 7. Write (`PostingEngine.post`)

It inserts:

- one `ledger_transactions` row, holding the idempotency key and the fingerprint;
- three `postings` rows:
  - rider DEBIT 10,000;
  - driver CREDIT 8,500;
  - `PLATFORM_FEES` CREDIT 1,500.

Each posting also records the account's balance right after it, which the statement shows as the
running balance. The cached `balance_minor` on each account is updated.

### Step 8. Commit, with the database as the last line of defence

On every one of these writes, PostgreSQL checks its own constraints:

- a wallet balance can never be stored below zero;
- an idempotency key can exist only once;
- a transaction can be reversed only once.

A trigger also makes postings impossible to update or delete. If application code ever had a
bug, the database would refuse the write rather than store bad money. Commit releases the locks.

### Step 9. Answer

The controller returns:

- `201 Created`;
- a `Location` header pointing to the new transaction;
- a body listing the three postings.

If a duplicate request lost a race in step 7 (see question 4), the service catches that failure,
finds the winner's transaction by its key, and returns it with `200` instead.

---

## 2. How locking prevents double-spending

### The problem

Alice's wallet holds **50 BIF**. Two requests to send 50 BIF arrive at the same moment. Without
locking:

| Time | Request A | Request B |
|---|---|---|
| t1 | reads balance: 50 | |
| t2 | | reads balance: 50 |
| t3 | 50 ≥ 50, OK | |
| t4 | | 50 ≥ 50, OK |
| t5 | writes balance 0, sends 50 | |
| t6 | | writes balance 0, sends 50 |

Both checks passed, because each read the balance before the other wrote. Alice has spent 100 BIF
with only 50. This is a **double spend**, and it is a race condition: it only happens when the
timing is unlucky, which is why it is hard to catch without dedicated tests.

### The fix: lock first, then read

`SELECT … FOR UPDATE` puts a lock on the row. Any other transaction that wants to lock the same
row must **wait** until the first one commits or rolls back.

| Time | Request A | Request B |
|---|---|---|
| t1 | locks Alice's row, reads 50 | |
| t2 | | tries to lock Alice's row: **waits** |
| t3 | 50 ≥ 50, OK, writes 0, commits (lock released) | |
| t4 | | gets the lock, reads **0** |
| t5 | | 0 < 50: `INSUFFICIENT_FUNDS` |

B now reads the balance *after* A's change, so the check is honest. `ConcurrencyIT` proves this
with **100** simultaneous transfers of 1 BIF from a wallet holding 50:

- exactly 50 succeed and 50 are refused;
- the balance ends at exactly 0 and never dips below it at any point.

### The second problem: deadlocks, and why lock order matters

Suppose Alice sends money to Bob while Bob sends money to Alice at the same moment, and each
transfer locks its *sender* first:

- Transfer 1 locks **Alice**, then wants **Bob**.
- Transfer 2 locks **Bob**, then wants **Alice**.

Each waits for the other forever. PostgreSQL detects this **deadlock** and kills one transaction
with an error.

The rule in `PostingEngine.lockAccounts` is: **always lock accounts in ascending id order**,
whatever the request looks like. Both transfers now lock the *same* account first. One of them
gets it and the other waits in line, so a cycle can never form.

`ConcurrencyIT` checks this from both sides:

- 200 opposing transfers between the same two wallets all succeed;
- a deliberate "wrong order" test shows that the deadlock really does happen without the rule.

### Two safety nets

- **Don't read before locking.** If code loaded an account *before* locking it, Hibernate could
  keep using that old copy. That is why the engine locks first, and why `AccessGuard` reads only
  `owner_id` with plain SQL.
- **`@Version`.** Every account update also checks a version number. If a stale copy were ever
  saved by mistake, the update would fail loudly instead of silently losing money.

---

## 3. Ten interview questions

**1. Why store money as a `long` of minor units instead of `double` or `BigDecimal`?**

- `double` is binary floating point and cannot represent 0.1 exactly, so sums drift (0.1 + 0.2 ≠
  0.3).
- `BigDecimal` is exact, but it has scale and rounding-mode pitfalls (e.g. `equals` treats `1.0`
  and `1.00` as different), and it is slower.
- Our currencies have at most 2 decimals, so a `long` count of the smallest unit (cents, or whole
  francs for BIF) is exact, fast and simple. `Math.addExact` makes overflow throw instead of
  wrapping around.
- See ADR 0001.

**2. Why pessimistic locking instead of optimistic locking?**

- Optimistic locking (`@Version` plus retry) is ideal when conflicts are rare.
- Here some rows are hot. Every ride payment in a currency writes the same `PLATFORM_FEES`
  account, so optimistic writers would keep colliding, throwing work away and retrying.
- Pessimistic locks make them queue instead: correct by construction, with no retry loops.
- The cost is that writes to one hot row are serial. That is acceptable because the transactions
  are short. If it ever became the bottleneck, I would split the fee account or post fees in
  batches.
- See ADR 0002.

**3. How do you avoid deadlocks?**

- All locks go through one method, which takes them in ascending UUID order, so two transactions
  can never wait for each other in a cycle.
- No external calls are made while locks are held, so they are held only briefly.
- A test shows the deadlock does happen when the order is reversed, which proves the rule matters.

**4. What happens if the client sends the same payment twice? What if both copies arrive at the
same time?**

- **One after the other.** The second finds the key, the fingerprints match, and the original is
  returned with `200`. Nothing is charged twice.
- **At the same time.** Both copies may miss the first lookup. The loser then fails on the unique
  key constraint, or on `INSUFFICIENT_FUNDS` because the winner already spent the money. The
  service catches that failure, looks the key up again, and returns the winner's transaction.
- `IdempotencyApiIT` fires 10 identical requests at once and checks for exactly one `201`, nine
  `200`s and one set of postings.

**5. Why is the idempotency key stored on the transaction row and not in a separate table or a
cache?**

- The key and the money movement are then written in the same database transaction. They exist
  together or not at all.
- With a separate store, a crash between the two writes could leave "key saved, money not moved"
  (a retry wrongly does nothing) or the reverse (a retry pays twice).
- See ADR 0004.

**6. Postings can't be changed. How do you fix a mistake?**

- You post a **reversal**: a new transaction with the exact opposite postings, linked to the
  original.
- Both stay visible, so the history is a complete audit trail.
- A unique constraint allows only one reversal per transaction.
- Reversals follow the normal rules. If the money has already been spent, the reversal is refused
  rather than making a wallet negative.
- Immutability is enforced three times: no setters, Hibernate `@Immutable`, and a database
  trigger.
- See ADR 0003.

**7. Why keep a cached balance on the account if you can sum the postings?**

- Summing every posting on each read gets slower as history grows. The cached balance makes
  reads, and the funds check under lock, a single-row lookup.
- It stays correct because it is updated in the **same transaction** as the postings, while the
  row is locked.
- Reconciliation independently recomputes every balance from postings to prove they agree
  (invariant 8).

**8. Why is `TransactionFlows` a separate bean from `TransactionService`?**

- `@Transactional` works through a Spring proxy. A method calling another method on the *same*
  object bypasses the proxy, so no transaction is opened (the self-invocation problem).
- The idempotency logic in `TransactionService` must also run *outside* the transaction. When the
  money transaction fails and rolls back, the service needs a fresh read to find the winner's
  key.
- Putting the transactional flows in their own bean solves both problems.

**9. How does authentication work, and why is CSRF protection disabled?**

- Login checks a BCrypt password hash and returns a signed JWT that is valid for one hour.
- Each request sends it as `Authorization: Bearer …`. The server verifies the signature and
  expiry, with no session storage (stateless).
- CSRF attacks rely on the browser automatically attaching **cookies** to forged requests. A
  bearer token in a header is never attached automatically, so there is nothing for CSRF to
  exploit.
- Authorization happens in two layers:
  - URL rules for roles, e.g. reversals are ADMIN only;
  - `AccessGuard` for ownership: you can only move money out of your own wallet.

**10. How do you know the concurrency handling really works?**

- Integration tests run against **real PostgreSQL** in Docker via Testcontainers. An in-memory
  database like H2 would not behave the same for row locks, constraints or triggers.
- The tests fire 100 to 200 parallel requests and then check exact outcomes and balances.
- They also check the lowest balance a wallet ever had, using the running balance on each
  posting, to prove it never went negative even for a moment.
- Each concurrency test that moves money then runs a full reconciliation and requires a clean
  ledger.
- The concurrency suite was run five times in a row without a failure.
