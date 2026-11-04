package bi.gravy.ledger.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Fingerprints a request so a reused {@code Idempotency-Key} can be told apart from a genuine
 * retry: same key + same hash = retry, same key + different hash = conflict.
 */
@Component
public class RequestHasher {

  private final ObjectMapper objectMapper;

  public RequestHasher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * SHA-256 (hex) over the operation name and the request re-serialised as JSON. Hashing the parsed
   * request instead of the raw bytes means whitespace or key order do not make two otherwise
   * identical bodies "different"; including the operation stops a key used for a top-up from
   * matching a transfer.
   */
  public String hash(String operation, Object request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(operation.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(objectMapper.writeValueAsBytes(request));
      return HexFormat.of().formatHex(digest.digest());
    } catch (JsonProcessingException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot hash request", e);
    }
  }
}
