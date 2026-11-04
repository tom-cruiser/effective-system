package bi.gravy.ledger.transaction;

import static bi.gravy.ledger.transaction.PostingLine.credit;
import static bi.gravy.ledger.transaction.PostingLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bi.gravy.ledger.account.Account;
import bi.gravy.ledger.account.AccountRepository;
import bi.gravy.ledger.account.AccountStatus;
import bi.gravy.ledger.account.AccountType;
import bi.gravy.ledger.common.Money;
import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostingEngineTest {

  private static final UUID WALLET_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID OTHER_WALLET_ID =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID SYSTEM_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");

  @Mock private AccountRepository accounts;
  @Mock private LedgerTransactionRepository transactions;
  @Mock private PostingRepository postings;

  private PostingEngine engine;
  private Account wallet;
  private Account otherWallet;
  private Account system;

  @BeforeEach
  void setUp() {
    engine = new PostingEngine(accounts, transactions, postings);
    wallet = account(WALLET_ID, AccountType.USER_WALLET, "BIF");
    otherWallet = account(OTHER_WALLET_ID, AccountType.DRIVER_WALLET, "BIF");
    system = account(SYSTEM_ID, AccountType.SYSTEM, "BIF");
  }

  // --- locking -------------------------------------------------------------------------------

  @Test
  void locksAccountsInAscendingIdOrderWhateverTheRequestOrder() {
    when(accounts.findByIdForUpdate(any()))
        .thenAnswer(inv -> Optional.of(account(inv.getArgument(0), AccountType.SYSTEM, "BIF")));

    engine.lockAccounts(List.of(SYSTEM_ID, WALLET_ID, OTHER_WALLET_ID, WALLET_ID));

    InOrder order = inOrder(accounts);
    order.verify(accounts).findByIdForUpdate(WALLET_ID);
    order.verify(accounts).findByIdForUpdate(OTHER_WALLET_ID);
    order.verify(accounts).findByIdForUpdate(SYSTEM_ID);
    order.verifyNoMoreInteractions();
  }

  @Test
  void lockingAnUnknownAccountFailsWithAccountNotFound() {
    when(accounts.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> engine.lockAccounts(List.of(WALLET_ID)))
        .satisfies(e -> assertCode(e, ErrorCode.ACCOUNT_NOT_FOUND));
  }

  // --- invariants 1-3: shape of the entry ------------------------------------------------------

  @Test
  void invariant1_rejectsFewerThanTwoPostings() {
    assertThatThrownBy(() -> post(credit(WALLET_ID, bif(100))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 2");
    verifyNothingWritten();
  }

  @Test
  void invariant2_rejectsUnbalancedEntries() {
    assertThatThrownBy(() -> post(debit(SYSTEM_ID, bif(100)), credit(WALLET_ID, bif(99))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unbalanced");
    verifyNothingWritten();
  }

  @Test
  void invariant2_rejectsNonPositiveAmounts() {
    assertThatThrownBy(() -> post(debit(SYSTEM_ID, bif(0)), credit(WALLET_ID, bif(0))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void invariant3_rejectsEntriesMixingCurrencies() {
    assertThatThrownBy(
            () -> post(debit(SYSTEM_ID, bif(100)), credit(WALLET_ID, Money.of(100, "RWF"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mix currencies");
  }

  @Test
  void invariant3_rejectsPostingInAnotherCurrencyThanTheAccount() {
    Account usdWallet = account(WALLET_ID, AccountType.USER_WALLET, "USD");
    Money usd = Money.of(100, "USD");
    LockedAccounts locked = locked(usdWallet, account(SYSTEM_ID, AccountType.SYSTEM, "BIF"));

    assertThatThrownBy(
            () -> engine.post(entry(debit(SYSTEM_ID, usd), credit(WALLET_ID, usd)), locked))
        .satisfies(e -> assertCode(e, ErrorCode.CURRENCY_MISMATCH));
    verifyNothingWritten();
  }

  // --- invariants 4 and 6: account state ------------------------------------------------------

  @Test
  void invariant4_walletCannotGoNegative() {
    wallet.adjustBalance(50);

    assertThatThrownBy(() -> post(debit(WALLET_ID, bif(51)), credit(OTHER_WALLET_ID, bif(51))))
        .satisfies(e -> assertCode(e, ErrorCode.INSUFFICIENT_FUNDS));
    assertThat(wallet.getBalanceMinor()).isEqualTo(50);
    verifyNothingWritten();
  }

  @Test
  void invariant4_walletCanBeSpentExactlyToZero() {
    stubSaves();
    wallet.adjustBalance(50);

    post(debit(WALLET_ID, bif(50)), credit(OTHER_WALLET_ID, bif(50)));

    assertThat(wallet.getBalanceMinor()).isZero();
    assertThat(otherWallet.getBalanceMinor()).isEqualTo(50);
  }

  @Test
  void invariant4_systemAccountsMayGoNegative() {
    stubSaves();

    post(debit(SYSTEM_ID, bif(10_000)), credit(WALLET_ID, bif(10_000)));

    assertThat(system.getBalanceMinor()).isEqualTo(-10_000);
  }

  @Test
  void invariant6_frozenAccountCannotBeCreditedOrDebited() {
    ReflectionTestUtils.setField(otherWallet, "status", AccountStatus.FROZEN);
    wallet.adjustBalance(100);

    assertThatThrownBy(() -> post(debit(WALLET_ID, bif(10)), credit(OTHER_WALLET_ID, bif(10))))
        .satisfies(e -> assertCode(e, ErrorCode.ACCOUNT_FROZEN));
    assertThatThrownBy(() -> post(debit(OTHER_WALLET_ID, bif(10)), credit(WALLET_ID, bif(10))))
        .satisfies(e -> assertCode(e, ErrorCode.ACCOUNT_FROZEN));
    verifyNothingWritten();
  }

  @Test
  void refusesToPostToAnAccountThatWasNotLocked() {
    LockedAccounts onlyWallet = locked(wallet);

    assertThatThrownBy(() ->
            engine.post(entry(debit(SYSTEM_ID, bif(1)), credit(WALLET_ID, bif(1))), onlyWallet))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not locked");
  }

  // --- happy path ----------------------------------------------------------------------------

  @Test
  void writesTransactionAndPostingsWithRunningBalances() {
    stubSaves();
    wallet.adjustBalance(1_000);

    PostedTransaction posted =
        post(
            debit(WALLET_ID, bif(300)),
            credit(OTHER_WALLET_ID, bif(250)),
            credit(SYSTEM_ID, bif(50)));

    assertThat(posted.transaction().getType()).isEqualTo(TransactionType.TRANSFER);
    assertThat(posted.postings())
        .extracting(Posting::getAccountId, Posting::getDirection, Posting::getBalanceAfterMinor)
        .containsExactly(
            tuple(WALLET_ID, Direction.DEBIT, 700L),
            tuple(OTHER_WALLET_ID, Direction.CREDIT, 250L),
            tuple(SYSTEM_ID, Direction.CREDIT, 50L));
    assertThat(wallet.getBalanceMinor()).isEqualTo(700);
  }

  @Test
  void directionEncodesTheCreditNormalConvention() {
    assertThat(Direction.CREDIT.signedAmount(5)).isEqualTo(5);
    assertThat(Direction.DEBIT.signedAmount(5)).isEqualTo(-5);
    assertThat(Direction.DEBIT.opposite()).isEqualTo(Direction.CREDIT);
    assertThat(Direction.CREDIT.opposite()).isEqualTo(Direction.DEBIT);
  }

  // --- helpers -------------------------------------------------------------------------------

  private PostedTransaction post(PostingLine... lines) {
    return engine.post(entry(lines), locked(wallet, otherWallet, system));
  }

  private static JournalEntry entry(PostingLine... lines) {
    return new JournalEntry(
        TransactionType.TRANSFER, "key", "hash", null, null, null, List.of(lines));
  }

  private static LockedAccounts locked(Account... accounts) {
    Map<UUID, Account> byId = new HashMap<>();
    for (Account account : accounts) {
      byId.put(account.getId(), account);
    }
    return new LockedAccounts(byId);
  }

  private static Account account(UUID id, AccountType type, String currency) {
    UUID owner = type == AccountType.SYSTEM ? null : UUID.randomUUID();
    Account account = new Account(owner, type.name(), type, currency);
    ReflectionTestUtils.setField(account, "id", id); // normally assigned by Hibernate on persist
    return account;
  }

  private static Money bif(long amountMinor) {
    return Money.of(amountMinor, "BIF");
  }

  private void stubSaves() {
    when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(postings.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
  }

  private void verifyNothingWritten() {
    verify(transactions, never()).save(any());
    verify(postings, never()).saveAll(anyList());
  }

  private static void assertCode(Throwable e, ErrorCode code) {
    assertThat(e).isInstanceOf(LedgerException.class);
    assertThat(((LedgerException) e).code()).isEqualTo(code);
  }
}
