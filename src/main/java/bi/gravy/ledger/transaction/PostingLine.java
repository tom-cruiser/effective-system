package bi.gravy.ledger.transaction;

import bi.gravy.ledger.common.Money;
import java.util.UUID;

/** A posting a business flow wants to make, before the engine has validated and stored it. */
public record PostingLine(UUID accountId, Direction direction, Money amount) {

  public static PostingLine debit(UUID accountId, Money amount) {
    return new PostingLine(accountId, Direction.DEBIT, amount);
  }

  public static PostingLine credit(UUID accountId, Money amount) {
    return new PostingLine(accountId, Direction.CREDIT, amount);
  }
}
