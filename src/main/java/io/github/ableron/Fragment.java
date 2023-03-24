package io.github.ableron;

import java.time.Instant;
import java.util.Objects;

public class Fragment {

  private final int statusCode;
  private final String content;
  private final Instant expirationTime;

  public Fragment(int statusCode, String content, Instant expirationTime) {
    this.statusCode = statusCode;
    this.content = Objects.requireNonNull(content, "content must not be null");
    this.expirationTime = Objects.requireNonNull(expirationTime, "expirationTime must not be null");
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getContent() {
    return content;
  }

  public Instant getExpirationTime() {
    return expirationTime;
  }
}
