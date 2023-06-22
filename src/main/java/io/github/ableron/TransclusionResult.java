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

  public TransclusionResult() {}

  public TransclusionResult(String content) {
    this.content = content;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Optional<Instant> getContentExpirationTime() {
    return Optional.ofNullable(contentExpirationTime);
  }

  public void setContentExpirationTime(Instant contentExpirationTime) {
    this.contentExpirationTime = contentExpirationTime;
  }

  public boolean hasPrimaryInclude() {
    return hasPrimaryInclude;
  }

  public void setHasPrimaryInclude(boolean hasPrimaryInclude) {
    this.hasPrimaryInclude = hasPrimaryInclude;
  }

  public Optional<Integer> getPrimaryIncludeStatusCode() {
    return Optional.ofNullable(primaryIncludeStatusCode);
  }

  public void setPrimaryIncludeStatusCode(Integer primaryIncludeStatusCode) {
    this.primaryIncludeStatusCode = primaryIncludeStatusCode;
  }

  public Map<String, List<String>> getPrimaryIncludeResponseHeaders() {
    return primaryIncludeResponseHeaders;
  }

  public void setPrimaryIncludeResponseHeaders(Map<String, List<String>> primaryIncludeResponseHeaders) {
    this.primaryIncludeResponseHeaders.clear();
    this.primaryIncludeResponseHeaders.putAll(primaryIncludeResponseHeaders);
  }

  public int getProcessedIncludesCount() {
    return processedIncludesCount;
  }

  public void setProcessedIncludesCount(int processedIncludesCount) {
    this.processedIncludesCount = processedIncludesCount;
  }

  public long getProcessingTimeMillis() {
    return processingTimeMillis;
  }

  public void setProcessingTimeMillis(long processingTimeMillis) {
    this.processingTimeMillis = processingTimeMillis;
  }
}
