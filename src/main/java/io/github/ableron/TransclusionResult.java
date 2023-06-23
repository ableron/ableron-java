package io.github.ableron;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransclusionResult {

  /**
   * Content with resolved includes.
   */
  private String content;

  /**
   * Expiration time of the content as defined by the fragment with the lowest expiration
   * time.
   */
  private Instant contentExpirationTime;

  /**
   * Whether one of the resolved includes was a primary include and thus result contains
   * status code and response headers of this primary include.
   */
  private boolean hasPrimaryInclude = false;

  /**
   * Status code set by a primary include which is to be sent along the content.
   */
  private Integer primaryIncludeStatusCode;

  /**
   * Response headers set by a primary include which are to be sent along the content.
   */
  private final Map<String, List<String>> primaryIncludeResponseHeaders = new HashMap<>();

  /**
   * Number of includes that have been processed.
   */
  private int processedIncludesCount = 0;

  /**
   * Time in milliseconds it took to resolve the includes in the content.
   */
  private long processingTimeMillis = 0;

  public TransclusionResult(String content) {
    this.content = content;
  }

  public String getContent() {
    return content;
  }

  public Optional<Instant> getContentExpirationTime() {
    return Optional.ofNullable(contentExpirationTime);
  }

  public boolean hasPrimaryInclude() {
    return hasPrimaryInclude;
  }

  public Optional<Integer> getPrimaryIncludeStatusCode() {
    return Optional.ofNullable(primaryIncludeStatusCode);
  }

  public Map<String, List<String>> getPrimaryIncludeResponseHeaders() {
    return primaryIncludeResponseHeaders;
  }

  public int getProcessedIncludesCount() {
    return processedIncludesCount;
  }

  public long getProcessingTimeMillis() {
    return processingTimeMillis;
  }

  public void setProcessingTimeMillis(long processingTimeMillis) {
    this.processingTimeMillis = processingTimeMillis;
  }

  public synchronized void addResolvedInclude(Include include, Fragment fragment) {
    if (include.isPrimary()) {
      hasPrimaryInclude = true;
      primaryIncludeStatusCode = fragment.getStatusCode();
      primaryIncludeResponseHeaders.clear();
      primaryIncludeResponseHeaders.putAll(fragment.getResponseHeaders());
    }

    if (contentExpirationTime == null || fragment.getExpirationTime().isBefore(contentExpirationTime)) {
      contentExpirationTime = fragment.getExpirationTime();
    }

    content = content.replace(include.getRawIncludeTag(), fragment.getContent());
    processedIncludesCount++;
  }
}
