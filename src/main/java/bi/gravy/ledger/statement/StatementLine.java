package bi.gravy.ledger.statement;

import bi.gravy.ledger.transaction.Direction;
import bi.gravy.ledger.transaction.TransactionType;
import java.time.Instant;
import java.util.UUID;

/** One row of an account statement: a posting joined with its transaction's header. */
public record StatementLine(
    UUID postingId,
    UUID transactionId,
    TransactionType transactionType,
    Direction direction,
    long amountMinor,
    String currency,
    long balanceAfterMinor,
    String description,
    Instant createdAt) {}
