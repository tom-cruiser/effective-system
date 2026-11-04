package bi.gravy.ledger.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 255) @Schema(example = "rider@demo.local") String email,
    @NotBlank @Size(max = 200) @Schema(example = "rider-demo-123") String password) {

  /** Never print the password, e.g. if this record ends up in a log line or exception. */
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=***]";
  }
}
