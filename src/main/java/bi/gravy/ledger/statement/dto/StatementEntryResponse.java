package bi.gravy.ledger.statement.dto;

import bi.gravy.ledger.common.Money;
import bi.gravy.ledger.common.MoneyResponse;
import bi.gravy.ledger.statement.StatementLine;
import bi.gravy.ledger.transaction.Direction;
import bi.gravy.ledger.transaction.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record StatementEntryResponse(
    UUID postingId,
    UUID transactionId,
    TransactionType transactionType,
    Direction direction,
    MoneyResponse amount,
    MoneyResponse balanceAfter,
    String description,
    Instant createdAt) {

  public static StatementEntryResponse from(StatementLine line) {
    return new StatementEntryResponse(
        line.postingId(),
        line.transactionId(),
        line.transactionType(),
        line.direction(),
        MoneyResponse.of(Money.of(line.amountMinor(), line.currency())),
        MoneyResponse.of(Money.of(line.balanceAfterMinor(), line.currency())),
        line.description(),
        line.createdAt());
  }
}
