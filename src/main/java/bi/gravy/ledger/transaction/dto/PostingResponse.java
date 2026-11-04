package bi.gravy.ledger.transaction.dto;

import bi.gravy.ledger.common.MoneyResponse;
import bi.gravy.ledger.transaction.Direction;
import bi.gravy.ledger.transaction.Posting;
import java.util.UUID;

public record PostingResponse(UUID id, UUID accountId, Direction direction, MoneyResponse amount) {

  public static PostingResponse from(Posting posting) {
    return new PostingResponse(
        posting.getId(),
        posting.getAccountId(),
        posting.getDirection(),
        MoneyResponse.of(posting.amount()));
  }
}
