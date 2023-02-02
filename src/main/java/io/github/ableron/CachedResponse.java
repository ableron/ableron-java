package io.github.ableron;

import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Objects;

public class CachedResponse {

  private final String responseBody;
  private final Instant expirationTime;

  public CachedResponse(@Nonnull String responseBody, @Nonnull Instant expirationTime) {
    this.responseBody = Objects.requireNonNull(responseBody, "responseBody must not be null");
    this.expirationTime = Objects.requireNonNull(expirationTime, "expirationTime must not be null");
  }

  public String getResponseBody() {
    return responseBody;
  }

  public Instant getExpirationTime() {
    return expirationTime;
  }
}
