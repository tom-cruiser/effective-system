package bi.gravy.ledger.transaction;

import java.util.List;
import java.util.UUID;

/** Everything the {@link PostingEngine} needs to record one transaction. */
public record JournalEntry(
    TransactionType type,
    String idempotencyKey,
    String requestHash,
    String description,
    String externalReference,
    UUID reversesTransactionId,
    List<PostingLine> lines) {

  public JournalEntry {
    lines = List.copyOf(lines);
  }

  LedgerTransaction toTransaction() {
    return new LedgerTransaction(
        type, idempotencyKey, requestHash, description, externalReference, reversesTransactionId);
  }
}
