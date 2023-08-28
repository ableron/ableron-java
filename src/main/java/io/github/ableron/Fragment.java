package io.github.ableron;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Fragment {

  private final String content;
  private final Instant expirationTime;
  private final String url;
  private final int statusCode;
  private final Map<String, List<String>> responseHeaders;

  public Fragment(int statusCode, String content) {
    this(null, statusCode, content, Instant.EPOCH, Map.of());
  }

  public Fragment(String url, int statusCode, String content, Instant expirationTime, Map<String, List<String>> responseHeaders) {
    this.url = url;
    this.statusCode = statusCode;
    this.content = Objects.requireNonNull(content, "content must not be null");
    this.expirationTime = Objects.requireNonNull(expirationTime, "expirationTime must not be null");
    this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders must not be null");
  }

  public String getContent() {
    return content;
  }

  public Instant getExpirationTime() {
    return expirationTime;
  }

  public Optional<String> getUrl() {
    return Optional.ofNullable(url);
  }

  public boolean isRemote() {
    return url != null;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public Map<String, List<String>> getResponseHeaders() {
    return responseHeaders;
  }
}
