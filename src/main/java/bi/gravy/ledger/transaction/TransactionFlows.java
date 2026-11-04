package bi.gravy.ledger.transaction;

import bi.gravy.ledger.account.Account;
import bi.gravy.ledger.account.AccountService;
import bi.gravy.ledger.account.AccountType;
import bi.gravy.ledger.account.SystemAccount;
import bi.gravy.ledger.common.Money;
import bi.gravy.ledger.common.config.LedgerProperties;
import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import bi.gravy.ledger.transaction.dto.PayoutRequest;
import bi.gravy.ledger.transaction.dto.ReversalRequest;
import bi.gravy.ledger.transaction.dto.RidePaymentRequest;
import bi.gravy.ledger.transaction.dto.TopUpRequest;
import bi.gravy.ledger.transaction.dto.TransferRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The money-moving business flows. Each runs in a single database transaction: lock the accounts,
 * check flow-specific rules, describe the postings, and hand them to the {@link PostingEngine}. If
 * anything throws, the whole transaction rolls back and nothing is written.
 *
 * <p>Callers go through {@link TransactionService}, which adds idempotency around these methods.
 * (It has to be a separate bean: Spring's {@code @Transactional} proxy is skipped when a class
 * calls its own methods.)
 */
@Component
class TransactionFlows {

  private final PostingEngine engine;
  private final AccountService accountService;
  private final LedgerTransactionRepository transactions;
  private final PostingRepository postings;
  private final LedgerProperties properties;

  TransactionFlows(
      PostingEngine engine,
      AccountService accountService,
      LedgerTransactionRepository transactions,
      PostingRepository postings,
      LedgerProperties properties) {
    this.engine = engine;
    this.accountService = accountService;
    this.transactions = transactions;
    this.postings = postings;
    this.properties = properties;
  }

  /** Mobile money arrives: debit TOPUP_CLEARING, credit the wallet. */
  @Transactional
  public PostedTransaction topUp(TopUpRequest request, String idempotencyKey, String requestHash) {
    Money amount = Money.of(request.amountMinor(), request.currency());
    UUID clearingId = systemAccountId(SystemAccount.TOPUP_CLEARING, amount.currency());

    LockedAccounts locked = engine.lockAccounts(List.of(clearingId, request.accountId()));
    requireWallet(locked.get(request.accountId()));

    return engine.post(
        new JournalEntry(
            TransactionType.TOP_UP,
            idempotencyKey,
            requestHash,
            request.description(),
            request.externalReference(),
            null,
            List.of(
                PostingLine.debit(clearingId, amount),
                PostingLine.credit(request.accountId(), amount))),
        locked);
  }

  /** Wallet to wallet: debit the sender, credit the receiver. */
  @Transactional
  public PostedTransaction transfer(
      TransferRequest request, String idempotencyKey, String requestHash) {
    if (request.fromAccountId().equals(request.toAccountId())) {
      throw new LedgerException(
          ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and destination accounts must differ");
    }
    Money amount = Money.of(request.amountMinor(), request.currency());

    LockedAccounts locked =
        engine.lockAccounts(List.of(request.fromAccountId(), request.toAccountId()));
    requireWallet(locked.get(request.fromAccountId()));
    requireWallet(locked.get(request.toAccountId()));

    return engine.post(
        new JournalEntry(
            TransactionType.TRANSFER,
            idempotencyKey,
            requestHash,
            request.description(),
            null,
            null,
            List.of(
                PostingLine.debit(request.fromAccountId(), amount),
                PostingLine.credit(request.toAccountId(), amount))),
        locked);
  }

  /**
   * The rider pays the full fare; the driver receives the fare minus the platform fee and
   * PLATFORM_FEES receives the fee. Three postings, still balanced: fare = share + fee.
   */
  @Transactional
  public PostedTransaction ridePayment(
      RidePaymentRequest request, String idempotencyKey, String requestHash) {
    Money fare = Money.of(request.fareMinor(), request.currency());
    FeeSplit split = FeeSplit.of(fare, properties.platformFeeBps());
    UUID feesId = systemAccountId(SystemAccount.PLATFORM_FEES, fare.currency());

    LockedAccounts locked =
        engine.lockAccounts(List.of(request.riderAccountId(), request.driverAccountId(), feesId));
    requireType(locked.get(request.riderAccountId()), AccountType.USER_WALLET);
    requireType(locked.get(request.driverAccountId()), AccountType.DRIVER_WALLET);

    List<PostingLine> lines = new ArrayList<>();
    lines.add(PostingLine.debit(request.riderAccountId(), fare));
    // A posting must be > 0, so a zero share (0% or 100% fee, or a tiny fare) gets no line.
    if (split.driverShare().isPositive()) {
      lines.add(PostingLine.credit(request.driverAccountId(), split.driverShare()));
    }
    if (split.platformFee().isPositive()) {
      lines.add(PostingLine.credit(feesId, split.platformFee()));
    }

    return engine.post(
        new JournalEntry(
            TransactionType.RIDE_PAYMENT,
            idempotencyKey,
            requestHash,
            request.description(),
            request.rideId(),
            null,
            lines),
        locked);
  }

  /** Driver cashes out: debit the driver wallet, credit PAYOUT_CLEARING. */
  @Transactional
  public PostedTransaction payout(
      PayoutRequest request, String idempotencyKey, String requestHash) {
    Money amount = Money.of(request.amountMinor(), request.currency());
    UUID clearingId = systemAccountId(SystemAccount.PAYOUT_CLEARING, amount.currency());

    LockedAccounts locked = engine.lockAccounts(List.of(request.driverAccountId(), clearingId));
    requireType(locked.get(request.driverAccountId()), AccountType.DRIVER_WALLET);

    return engine.post(
        new JournalEntry(
            TransactionType.PAYOUT,
            idempotencyKey,
            requestHash,
            request.description(),
            request.externalReference(),
            null,
            List.of(
                PostingLine.debit(request.driverAccountId(), amount),
                PostingLine.credit(clearingId, amount))),
        locked);
  }

  /**
   * Undoes a transaction by posting the exact opposite of each of its postings. History is never
   * edited: both the original and the reversal stay in the ledger (ADR 0003).
   */
  @Transactional
  public PostedTransaction reverse(
      UUID originalId, ReversalRequest request, String idempotencyKey, String requestHash) {
    LedgerTransaction original =
        transactions.findById(originalId).orElseThrow(() -> transactionNotFound(originalId));
    if (original.getType() == TransactionType.REVERSAL) {
      throw LedgerException.validation("A reversal cannot itself be reversed");
    }
    List<Posting> originalPostings = postings.findByTransactionIdOrderByEntrySeq(originalId);

    LockedAccounts locked =
        engine.lockAccounts(originalPostings.stream().map(Posting::getAccountId).toList());
    // Checked only now, while holding the locks: a concurrent reversal of the same transaction
    // needs the same locks, so it has either committed (and we see it) or is still waiting.
    if (transactions.existsByReversesTransactionId(originalId)) {
      throw new LedgerException(
          ErrorCode.ALREADY_REVERSED, "Transaction %s is already reversed".formatted(originalId));
    }

    List<PostingLine> lines =
        originalPostings.stream()
            .map(p -> new PostingLine(p.getAccountId(), p.getDirection().opposite(), p.amount()))
            .toList();
    return engine.post(
        new JournalEntry(
            TransactionType.REVERSAL,
            idempotencyKey,
            requestHash,
            reversalDescription(originalId, request),
            null,
            originalId,
            lines),
        locked);
  }

  private UUID systemAccountId(SystemAccount account, String currency) {
    return accountService.systemAccountId(account, currency);
  }

  private static String reversalDescription(UUID originalId, ReversalRequest request) {
    String base = "Reversal of " + originalId;
    return request == null || request.reason() == null ? base : base + ": " + request.reason();
  }

  static LedgerException transactionNotFound(UUID id) {
    return new LedgerException(
        ErrorCode.TRANSACTION_NOT_FOUND, "Transaction %s not found".formatted(id));
  }

  /** System accounts are only touched by the flows themselves, never named by a client. */
  private static void requireWallet(Account account) {
    if (!account.isWallet()) {
      throw LedgerException.validation("Account %s is not a wallet".formatted(account.getId()));
    }
  }

  private static void requireType(Account account, AccountType expected) {
    if (account.getType() != expected) {
      throw LedgerException.validation(
          "Account %s must be a %s".formatted(account.getId(), expected));
    }
  }
}
