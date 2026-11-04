package bi.gravy.ledger.transaction;

import java.util.List;

/**
 * A stored transaction together with its postings.
 *
 * @param replayed true when this is the result of an earlier request with the same idempotency key
 *     rather than a newly created transaction (the API answers 200 instead of 201)
 */
public record PostedTransaction(
    LedgerTransaction transaction, List<Posting> postings, boolean replayed) {

  public PostedTransaction {
    postings = List.copyOf(postings);
  }

  public PostedTransaction(LedgerTransaction transaction, List<Posting> postings) {
    this(transaction, postings, false);
  }
}
