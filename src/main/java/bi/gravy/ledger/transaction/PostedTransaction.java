package bi.gravy.ledger.transaction;

import java.util.List;

/** A stored transaction together with its postings. */
public record PostedTransaction(LedgerTransaction transaction, List<Posting> postings) {

  public PostedTransaction {
    postings = List.copyOf(postings);
  }
}
