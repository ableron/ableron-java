package io.github.ableron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TransclusionResult {

  private static final CacheStats emptyCacheStats = new CacheStats();

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Whether to append stats as HTML comment to the content.
   */
  private final boolean appendStatsToContent;

  /**
   * Whether to include fragment URLs in stats.
   */
  private final boolean exposeFragmentUrl;

  /**
   * Content with resolved includes.
   */
  private String content;

  private final CacheStats cacheStats;

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
   * Time in milliseconds it took to resolve the includes in the content.
   */
  private long processingTimeMillis = 0;

  /**
   * List of processed Includes.
   */
  private final List<Include> processedIncludes = new ArrayList<>();

  public TransclusionResult(String content) {
    this(content, emptyCacheStats, false, false);
  }

  public TransclusionResult(String content, CacheStats cacheStats, boolean appendStatsToContent, boolean exposeFragmentUrl) {
    this.content = content;
    this.cacheStats = cacheStats;
    this.appendStatsToContent = appendStatsToContent;
    this.exposeFragmentUrl = exposeFragmentUrl;
  }

  public String getContent() {
    return appendStatsToContent ? content + getStatsAsHtmlComment() : content;
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
    return processedIncludes.size();
  }

  public long getProcessingTimeMillis() {
    return processingTimeMillis;
  }

  public void setProcessingTimeMillis(long processingTimeMillis) {
    this.processingTimeMillis = processingTimeMillis;
  }

  public synchronized void addResolvedInclude(Include include) {
    Fragment fragment = include.getResolvedFragment();

    if (include.isPrimary()) {
      if (hasPrimaryInclude) {
        logger.error("[Ableron] Found multiple primary includes in one page. Only treating one of them as primary");
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
    processedIncludes.add(include);
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

    if ((contentExpirationTime != null && contentExpirationTime.isBefore(now))
      || pageMaxAge == null
      || pageMaxAge.toSeconds() <= 0) {
      return "no-store";
    }

    if (contentExpirationTime != null && contentExpirationTime.isBefore(now.plus(pageMaxAge))) {
      return "max-age=" + ChronoUnit.SECONDS.between(now, contentExpirationTime.plusSeconds(1));
    }

    return "max-age=" + pageMaxAge.toSeconds();
  }

  /**
   * Calculates the <code>Cache-Control</code> header value based on the fragment with the lowest
   * expiration time and the given response headers which may contain page expiration time.
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

  public String getProcessedIncludesLogLine() {
    return "Processed " + getProcessedIncludesCount() + (getProcessedIncludesCount() == 1 ? " include" : " includes") + " in " + this.processingTimeMillis + "ms";
  }

  public String getCacheStatsLogLine() {
    return "Cache: " +
      this.cacheStats.itemCount() + " items, " +
      this.cacheStats.hitCount()+ " hits, " +
      this.cacheStats.missCount() + " misses, " +
      this.cacheStats.refreshSuccessCount() + " successful refreshs, " +
      this.cacheStats.refreshFailureCount() + " failed refreshs";
  }

  private String getStatsAsHtmlComment() {
    return "\n<!-- " +
      getProcessedIncludesLogLine() +
      getProcessedIncludesDetails() +
      "\n\n" +
      getCacheStatsLogLine() +
      "\n-->";
  }

  private String getProcessedIncludesDetails() {
    var stats = new StringBuilder();

    if (!this.processedIncludes.isEmpty()) {
      stats
        .append("\n\nTime | Include | Resolved With | Fragment Cacheability")
        .append(this.exposeFragmentUrl ? " | Fragment URL" : "")
        .append("\n------------------------------------------------------");

      this.processedIncludes.stream()
        .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
        .forEach((include -> stats.append("\n").append(getProcessedIncludeStatsRow(include))));
    }

    return stats.toString();
  }

  private String getProcessedIncludeStatsRow(Include include) {
    return include.getResolveTimeMillis() + "ms"
      + " | " + getProcessedIncludeStatIncludeId(include)
      + " | " + getProcessedIncludeStatFragmentSource(include)
      + " | " + getProcessedIncludeStatCacheDetails(include)
      + (this.exposeFragmentUrl ? " | " + getProcessedIncludeStatFragmentUrl(include) : "");
  }

  private String getProcessedIncludeStatIncludeId(Include include) {
    return include.getId() + (include.isPrimary() ? " (primary)" : "");
  }

  private String getProcessedIncludeStatFragmentSource(Include include) {
    return Optional.ofNullable(include.getResolvedFragmentSource()).orElse("-");
  }

  private String getProcessedIncludeStatCacheDetails(Include include) {
    if (include.getResolvedFragment() == null || include.getResolvedFragment().getUrl().isEmpty()) {
      return "-";
    }

    if (include.getResolvedFragment().getExpirationTime() == Instant.EPOCH) {
      return "not cacheable";
    }

    return "expires in " + (int) Math.ceil((include.getResolvedFragment().getExpirationTime().toEpochMilli() - Instant.now().toEpochMilli()) / 1000.0) + 's';
  }

  private String getProcessedIncludeStatFragmentUrl(Include include) {
    return Optional.ofNullable(include.getResolvedFragment())
      .flatMap(Fragment::getUrl)
      .orElse("-");
  }
}
