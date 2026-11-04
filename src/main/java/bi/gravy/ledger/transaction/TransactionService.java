package bi.gravy.ledger.transaction;

import bi.gravy.ledger.account.Account;
import bi.gravy.ledger.account.AccountService;
import bi.gravy.ledger.account.SystemAccount;
import bi.gravy.ledger.common.Money;
import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import bi.gravy.ledger.common.idempotency.RequestHasher;
import bi.gravy.ledger.transaction.dto.TopUpRequest;
import bi.gravy.ledger.transaction.dto.TransferRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The money-moving business flows. Each one runs in a single database transaction: lock the
 * accounts, check flow-specific rules, describe the postings, and hand them to the {@link
 * PostingEngine}. If anything throws, the whole transaction rolls back and nothing is written.
 */
@Service
public class TransactionService {

  private final PostingEngine engine;
  private final AccountService accountService;
  private final LedgerTransactionRepository transactions;
  private final PostingRepository postings;
  private final RequestHasher hasher;

  public TransactionService(
      PostingEngine engine,
      AccountService accountService,
      LedgerTransactionRepository transactions,
      PostingRepository postings,
      RequestHasher hasher) {
    this.engine = engine;
    this.accountService = accountService;
    this.transactions = transactions;
    this.postings = postings;
    this.hasher = hasher;
  }

  /** Mobile money arrives: debit TOPUP_CLEARING, credit the wallet. */
  @Transactional
  public PostedTransaction topUp(TopUpRequest request, String idempotencyKey) {
    Money amount = Money.of(request.amountMinor(), request.currency());
    UUID clearingId =
        accountService.systemAccountId(SystemAccount.TOPUP_CLEARING, amount.currency());

    LockedAccounts locked = engine.lockAccounts(List.of(clearingId, request.accountId()));
    requireWallet(locked.get(request.accountId()));

    return engine.post(
        new JournalEntry(
            TransactionType.TOP_UP,
            idempotencyKey,
            hasher.hash("top-up", request),
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
  public PostedTransaction transfer(TransferRequest request, String idempotencyKey) {
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
            hasher.hash("transfer", request),
            request.description(),
            null,
            null,
            List.of(
                PostingLine.debit(request.fromAccountId(), amount),
                PostingLine.credit(request.toAccountId(), amount))),
        locked);
  }

  @Transactional(readOnly = true)
  public PostedTransaction get(UUID transactionId) {
    LedgerTransaction transaction =
        transactions
            .findById(transactionId)
            .orElseThrow(() ->
                new LedgerException(
                    ErrorCode.TRANSACTION_NOT_FOUND,
                    "Transaction %s not found".formatted(transactionId)));
    return new PostedTransaction(
        transaction, postings.findByTransactionIdOrderByEntrySeq(transactionId));
  }

  /** System accounts are only touched by the flows themselves, never named by a client. */
  private static void requireWallet(Account account) {
    if (!account.isWallet()) {
      throw LedgerException.validation("Account %s is not a wallet".formatted(account.getId()));
    }
  }
}
