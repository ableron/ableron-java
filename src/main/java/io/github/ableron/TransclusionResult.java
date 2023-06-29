package io.github.ableron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransclusionResult {

  private final Logger logger = LoggerFactory.getLogger(getClass());

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
  private Integer statusCodeOverride;

  /**
   * Response headers set by a primary include which are to be sent along the content.
   */
  private final Map<String, List<String>> responseHeadersToPass = new HashMap<>();

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

  public Optional<Integer> getStatusCodeOverride() {
    return Optional.ofNullable(statusCodeOverride);
  }

  public Map<String, List<String>> getResponseHeadersToPass() {
    return responseHeadersToPass;
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
      if (hasPrimaryInclude) {
        logger.warn("Only one primary include per page allowed. Multiple found");
      } else {
        hasPrimaryInclude = true;
        statusCodeOverride = fragment.getStatusCode();
        responseHeadersToPass.putAll(fragment.getResponseHeaders());
      }
    }

    if (contentExpirationTime == null || fragment.getExpirationTime().isBefore(contentExpirationTime)) {
      contentExpirationTime = fragment.getExpirationTime();
    }

    content = content.replace(include.getRawIncludeTag(), fragment.getContent());
    processedIncludesCount++;
  }

  //TODO: Check whether this makes sense
  //TODO: Improve
  //TODO: Add unit tests
  //TODO: Use in examples and dependent libs
  public String calculateCacheControlHeaderValue(Duration initialPageMaxAge) {
    if (contentExpirationTime.isBefore(Instant.now())) {
      return "no-store";
    }

    if (contentExpirationTime.isBefore(Instant.now().plusSeconds(initialPageMaxAge.toSeconds()))) {
      return "max-age=" + ChronoUnit.SECONDS.between(Instant.now(), contentExpirationTime);
    }

    return "max-age=" + initialPageMaxAge.toSeconds();
  }

  //TODO: Check whether this makes sense
  //TODO: Improve
  //TODO: Add unit tests
  //TODO: Use in examples and dependent libs
  public String calculateCacheControlHeaderValue(Map<String, List<String>> responseHeaders) {
    if (contentExpirationTime.isBefore(Instant.now())) {
      return "no-store";
    }

    if (contentExpirationTime.isBefore(HttpUtil.calculateResponseExpirationTime(responseHeaders))) {
      return "max-age=" + ChronoUnit.SECONDS.between(Instant.now(), contentExpirationTime);
    }

    return responseHeaders.getOrDefault("Cache-Control", List.of("no-store"))
      .stream()
      .findFirst()
      .orElse("no-store");
  }
}
