package bi.gravy.ledger.security.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRequestTest {

  @Test
  void toStringNeverRevealsThePassword() {
    LoginRequest request = new LoginRequest("rider@demo.local", "s3cret-p4ss");

    assertThat(request.toString()).contains("rider@demo.local").doesNotContain("s3cret-p4ss");
  }
}
