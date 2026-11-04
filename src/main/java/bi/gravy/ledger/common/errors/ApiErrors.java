package bi.gravy.ledger.common.errors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The business errors an endpoint can return. Swagger documents each one as an {@code
 * application/problem+json} response with an example, built from {@link ErrorCode} so that the
 * documented status and code can never drift from what the API actually sends.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {
  ErrorCode[] value();
}
