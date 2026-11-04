package bi.gravy.ledger.transaction.dto;

import bi.gravy.ledger.transaction.LedgerTransaction;
import bi.gravy.ledger.transaction.PostedTransaction;
import bi.gravy.ledger.transaction.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    TransactionType type,
    String description,
    String externalReference,
    UUID reversesTransactionId,
    Instant createdAt,
    List<PostingResponse> postings) {

  public static TransactionResponse from(PostedTransaction posted) {
    LedgerTransaction tx = posted.transaction();
    return new TransactionResponse(
        tx.getId(),
        tx.getType(),
        tx.getDescription(),
        tx.getExternalReference(),
        tx.getReversesTransactionId(),
        tx.getCreatedAt(),
        posted.postings().stream().map(PostingResponse::from).toList());
  }
}
