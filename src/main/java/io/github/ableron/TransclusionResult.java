package io.github.ableron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TransclusionResult {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Whether to append stats as HTML comment to the content.
   */
  private final boolean appendStatsToContent;

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

  /**
   * Log entries containing information about resolved includes to be used
   * in stats.
   */
  private final List<String> resolvedIncludesLog = new ArrayList<>();

  public TransclusionResult(String content) {
    this(content, false);
  }

  public TransclusionResult(String content, boolean appendStatsToContent) {
    this.content = content;
    this.appendStatsToContent = appendStatsToContent;
  }

  public String getContent() {
    return appendStatsToContent ? content + getStats() : content;
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

  public synchronized void addResolvedInclude(Include include, Fragment fragment, long includeResolveTimeMillis) {
    if (include.isPrimary()) {
      if (hasPrimaryInclude) {
        logger.warn("Only one primary include per page allowed. Multiple found");
        resolvedIncludesLog.add("Ignoring primary include with status code " + fragment.getStatusCode() + " because there is already another primary include");
      } else {
        hasPrimaryInclude = true;
        statusCodeOverride = fragment.getStatusCode();
        responseHeadersToPass.putAll(fragment.getResponseHeaders());
        resolvedIncludesLog.add("Primary include with status code " + fragment.getStatusCode());
      }
    }

    if (contentExpirationTime == null || fragment.getExpirationTime().isBefore(contentExpirationTime)) {
      contentExpirationTime = fragment.getExpirationTime();
    }

    content = content.replace(include.getRawIncludeTag(), fragment.getContent());
    processedIncludesCount++;
    resolvedIncludesLog.add(String.format("Resolved include %s with %s in %dms",
      include.getId(),
      fragment.isRemote() ? "remote fragment" : "fallback content",
      includeResolveTimeMillis
    ));
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

    if (contentExpirationTime == null || contentExpirationTime.isBefore(now) || pageMaxAge.toSeconds() <= 0) {
      return "no-store";
    }

    if (contentExpirationTime.isBefore(now.plus(pageMaxAge))) {
      return "max-age=" + ChronoUnit.SECONDS.between(now, contentExpirationTime.plusSeconds(1));
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
      ? Duration.ofSeconds(ChronoUnit.SECONDS.between(Instant.now(), pageExpirationTime.plusSeconds(1)))
      : Duration.ZERO;
    return calculateCacheControlHeaderValue(pageMaxAge);
  }

  private String getStats() {
    final var stats = new StringBuilder("\n<!-- Ableron stats:\n"
      + "Processed " + processedIncludesCount + " include(s) in " + processingTimeMillis + "ms\n");
    resolvedIncludesLog.forEach(logEntry -> stats.append(logEntry).append("\n"));
    return stats.append("-->").toString();
  }
}
