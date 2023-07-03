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

  /**
   * Calculates the <code>Cache-Control</code> header value. Due to page max age is considered
   * zero, return value is always <code>no-store</code>.
   *
   * @return Fixed Cache-Control header value "no-store"
   */
  public String calculateCacheControlHeaderValue() {
    return calculateCacheControlHeaderValue(Duration.ZERO);
  }

  /**
   * Calculates the <code>Cache-Control</code> header value based on the fragment with the lowest
   * expiration time and the given page max age.
   *
   * @return The Cache-Control header value. Either "no-store" or "max-age=xxx"
   */
  public String calculateCacheControlHeaderValue(Duration pageMaxAge) {
    Instant now = Instant.now();

    if (contentExpirationTime == null
      || contentExpirationTime.isBefore(now)
      || !now.plusSeconds(pageMaxAge.toSeconds()).isAfter(now)) {
      return "no-store";
    }

    if (contentExpirationTime.isBefore(now.plusSeconds(pageMaxAge.toSeconds()))) {
      return "max-age=" + ChronoUnit.SECONDS.between(now, contentExpirationTime);
    }

    return "max-age=" + pageMaxAge.toSeconds();
  }

  /**
   * Calculates the <code>Cache-Control</code> header value based on the fragment with the lowest
   * expiration time and the given response headers which max contain page expiration time.
   *
   * @return The Cache-Control header value. Either "no-store" or "max-age=xxx"
   */
  public String calculateCacheControlHeaderValue(Map<String, List<String>> responseHeaders) {
    Instant pageExpirationTime = HttpUtil.calculateResponseExpirationTime(responseHeaders);
    Duration pageMaxAge = pageExpirationTime.isAfter(Instant.now())
      ? Duration.between(Instant.now(), pageExpirationTime)
      : Duration.ZERO;
    return calculateCacheControlHeaderValue(pageMaxAge);
  }
}
