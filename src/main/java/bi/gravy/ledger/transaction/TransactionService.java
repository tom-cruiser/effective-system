package bi.gravy.ledger.transaction;

import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import bi.gravy.ledger.common.idempotency.RequestHasher;
import bi.gravy.ledger.transaction.dto.PayoutRequest;
import bi.gravy.ledger.transaction.dto.ReversalRequest;
import bi.gravy.ledger.transaction.dto.RidePaymentRequest;
import bi.gravy.ledger.transaction.dto.TopUpRequest;
import bi.gravy.ledger.transaction.dto.TransferRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for every money movement. Wraps each {@link TransactionFlows} call with idempotency
 * (ADR 0004): a request whose {@code Idempotency-Key} was already used returns the original
 * transaction if the body is the same, or fails with IDEMPOTENCY_CONFLICT if it is not.
 */
@Service
public class TransactionService {

  private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

  private final TransactionFlows flows;
  private final LedgerTransactionRepository transactions;
  private final PostingRepository postings;
  private final RequestHasher hasher;

  TransactionService(
      TransactionFlows flows,
      LedgerTransactionRepository transactions,
      PostingRepository postings,
      RequestHasher hasher) {
    this.flows = flows;
    this.transactions = transactions;
    this.postings = postings;
    this.hasher = hasher;
  }

  public PostedTransaction topUp(TopUpRequest request, String idempotencyKey) {
    return idempotent(
        "top-up", request, idempotencyKey, hash -> flows.topUp(request, idempotencyKey, hash));
  }

  public PostedTransaction transfer(TransferRequest request, String idempotencyKey) {
    return idempotent(
        "transfer", request, idempotencyKey, hash -> flows.transfer(request, idempotencyKey, hash));
  }

  public PostedTransaction ridePayment(RidePaymentRequest request, String idempotencyKey) {
    return idempotent(
        "ride-payment",
        request,
        idempotencyKey,
        hash -> flows.ridePayment(request, idempotencyKey, hash));
  }

  public PostedTransaction payout(PayoutRequest request, String idempotencyKey) {
    return idempotent(
        "payout", request, idempotencyKey, hash -> flows.payout(request, idempotencyKey, hash));
  }

  public PostedTransaction reverse(
      UUID transactionId, ReversalRequest request, String idempotencyKey) {
    return idempotent(
        "reversal:" + transactionId,
        request,
        idempotencyKey,
        hash -> flows.reverse(transactionId, request, idempotencyKey, hash));
  }

  @Transactional(readOnly = true)
  public PostedTransaction get(UUID transactionId) {
    LedgerTransaction transaction =
        transactions
            .findById(transactionId)
            .orElseThrow(() -> TransactionFlows.transactionNotFound(transactionId));
    return new PostedTransaction(
        transaction, postings.findByTransactionIdOrderByEntrySeq(transactionId));
  }

  /**
   * Runs {@code flow} unless this key has been used before.
   *
   * <p>The first lookup handles ordinary retries. Two identical requests can still arrive at the
   * same moment and both miss it; then one of them fails, either on the UNIQUE(idempotency_key)
   * constraint or on a business rule (e.g. the first one already spent the money). Looking the key
   * up again turns that failure into the first request's result.
   */
  private PostedTransaction idempotent(
      String operation,
      Object request,
      String idempotencyKey,
      Function<String, PostedTransaction> flow) {
    String hash = hasher.hash(operation, request);
    Optional<PostedTransaction> previous = findPrevious(idempotencyKey, hash);
    if (previous.isPresent()) {
      return previous.get();
    }
    try {
      return flow.apply(hash);
    } catch (DataIntegrityViolationException | LedgerException e) {
      return findPrevious(idempotencyKey, hash).orElseThrow(() -> e);
    }
  }

  private Optional<PostedTransaction> findPrevious(String idempotencyKey, String hash) {
    return transactions
        .findByIdempotencyKey(idempotencyKey)
        .map(existing -> {
          if (!existing.getRequestHash().equals(hash)) {
            throw new LedgerException(
                ErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key was already used for a different request");
          }
          log.info("Idempotent replay of transaction {}", existing.getId());
          return new PostedTransaction(
              existing, postings.findByTransactionIdOrderByEntrySeq(existing.getId()), true);
        });
  }
}
