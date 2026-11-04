# Architecture Decision Records

Short records of the decisions that shape this ledger, each with its context and trade-offs.

| ADR | Decision |
|---|---|
| [0001](0001-money-as-minor-units.md) | Money as `long` minor units, never floating point |
| [0002](0002-pessimistic-locking-and-lock-ordering.md) | Pessimistic row locks, taken in ascending id order |
| [0003](0003-immutable-ledger-with-reversals.md) | Append-only ledger, corrected only by reversals |
| [0004](0004-idempotency-keys.md) | Idempotency keys stored on the ledger transaction |
