package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bi.gravy.ledger.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * Invariant 5 at the database level: even SQL that bypasses the application entirely cannot change
 * or remove a posting.
 */
class PostingImmutabilityIT extends IntegrationTest {

  @Test
  void updatingAPostingIsRejectedByTheDatabase() throws Exception {
    UUID wallet = createFundedWallet("USER_WALLET", "BIF", 1_000);

    assertThatThrownBy(
            () -> jdbc.update("UPDATE postings SET amount_minor = 1 WHERE account_id = ?", wallet))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("postings are immutable: UPDATE is not allowed");
    assertThat(derivedBalance(wallet)).isEqualTo(1_000);
  }

  @Test
  void deletingAPostingIsRejectedByTheDatabase() throws Exception {
    UUID wallet = createFundedWallet("USER_WALLET", "BIF", 1_000);

    assertThatThrownBy(() -> jdbc.update("DELETE FROM postings WHERE account_id = ?", wallet))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("postings are immutable: DELETE is not allowed");
    assertThat(postingCount(wallet)).isEqualTo(1);
  }

  @Test
  void truncatingPostingsIsRejectedByTheDatabase() {
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE postings"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("postings are immutable: TRUNCATE is not allowed");
  }
}
