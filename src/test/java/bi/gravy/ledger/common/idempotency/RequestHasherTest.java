package bi.gravy.ledger.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bi.gravy.ledger.common.errors.LedgerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RequestHasherTest {

  record Body(String a, long b) {}

  private final RequestHasher hasher = new RequestHasher(new ObjectMapper());

  @Test
  void sameOperationAndBodyGiveTheSameHash() {
    assertThat(hasher.hash("transfer", new Body("x", 1)))
        .isEqualTo(hasher.hash("transfer", new Body("x", 1)))
        .hasSize(64);
  }

  @Test
  void differentBodyOrOperationGiveDifferentHashes() {
    String original = hasher.hash("transfer", new Body("x", 1));

    assertThat(hasher.hash("transfer", new Body("x", 2))).isNotEqualTo(original);
    assertThat(hasher.hash("top-up", new Body("x", 1))).isNotEqualTo(original);
  }

  @Test
  void idempotencyKeyMustBePresentAndShortEnough() {
    assertThat(IdempotencyKeys.requireValid("abc")).isEqualTo("abc");
    assertThatThrownBy(() -> IdempotencyKeys.requireValid(" ")).isInstanceOf(LedgerException.class);
    assertThatThrownBy(() -> IdempotencyKeys.requireValid("k".repeat(101)))
        .isInstanceOf(LedgerException.class);
  }
}
