package io.github.ableron;

import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Objects;

public class CachedResponse {

  private final int statusCode;
  private final String body;
  private final Instant expirationTime;

  public CachedResponse(int statusCode, @Nonnull String body, @Nonnull Instant expirationTime) {
    this.statusCode = statusCode;
    this.body = Objects.requireNonNull(body, "body must not be null");
    this.expirationTime = Objects.requireNonNull(expirationTime, "expirationTime must not be null");
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }

  public Instant getExpirationTime() {
    return expirationTime;
  }
}
