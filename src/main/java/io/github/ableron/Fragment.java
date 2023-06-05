package io.github.ableron;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Fragment {

  private final int statusCode;
  private final String content;
  private final Instant expirationTime;
  private final Map<String, List<String>> responseHeaders;

  public Fragment(int statusCode, String content) {
    this(statusCode, content, Instant.EPOCH, Map.of());
  }

  public Fragment(int statusCode, String content, Instant expirationTime, Map<String, List<String>> responseHeaders) {
    this.statusCode = statusCode;
    this.content = Objects.requireNonNull(content, "content must not be null");
    this.expirationTime = Objects.requireNonNull(expirationTime, "expirationTime must not be null");
    this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders must not be null");
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

  public Map<String, List<String>> getResponseHeaders() {
    return responseHeaders;
  }
}
