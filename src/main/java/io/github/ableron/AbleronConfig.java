package io.github.ableron;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class AbleronConfig {

  /**
   * Whether UI composition is enabled.
   * Defaults to true.
   */
  private boolean enabled = true;

  /**
   * Timeout for requesting fragments.
   * Defaults to 3 seconds.
   */
  private Duration fragmentRequestTimeout = Duration.ofSeconds(3);

  /**
   * Request headers that are passed to fragment requests if present.
   */
  private List<String> fragmentRequestHeadersToPass = List.of(
    "Accept-Language",
    "Correlation-ID",
    "Forwarded",
    "Referer",
    "User-Agent",
    "X-Correlation-ID",
    "X-Forwarded-For",
    "X-Forwarded-Proto",
    "X-Forwarded-Host",
    "X-Real-IP",
    "X-Request-ID"
  );

  /**
   * Request headers that are passed to fragment requests in addition to fragmentRequestHeadersToPass.
   */
  private List<String> fragmentAdditionalRequestHeadersToPass = List.of();

  /**
   * Response headers of primary fragments to pass to the page response if present.
   */
  private List<String> primaryFragmentResponseHeadersToPass = List.of(
    "Content-Language",
    "Location",
    "Refresh"
  );

  /**
   * Maximum size in bytes the fragment cache may have.
   * Defaults to 10 MB.
   */
  private long cacheMaxSizeInBytes = 1024 * 1024 * 10;

  /**
   * Fragment request headers which influence the requested fragment aside from its URL.
   */
  private List<String> cacheVaryByRequestHeaders = List.of();

  /**
   * Whether to enable auto-refreshing of cached fragments.
   */
  private boolean cacheAutoRefreshEnabled = false;

  /**
   * Whether to append UI composition stats as HTML comment to the content.
   * Defaults to false.
   */
  private boolean statsAppendToContent = false;

  /**
   * Whether to expose fragment URLs in the stats appended to the content.
   * Defaults to false.
   */
  private boolean statsExposeFragmentUrl = false;

  private AbleronConfig() {}

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Duration getFragmentRequestTimeout() {
    return fragmentRequestTimeout;
  }

  public List<String> getFragmentRequestHeadersToPass() {
    return fragmentRequestHeadersToPass;
  }

  public List<String> getFragmentAdditionalRequestHeadersToPass() {
    return fragmentAdditionalRequestHeadersToPass;
  }

  public List<String> getPrimaryFragmentResponseHeadersToPass() {
    return primaryFragmentResponseHeadersToPass;
  }

  public long getCacheMaxSizeInBytes() {
    return cacheMaxSizeInBytes;
  }

  public List<String> getCacheVaryByRequestHeaders() {
    return cacheVaryByRequestHeaders;
  }

  public boolean cacheAutoRefreshEnabled() {
    return cacheAutoRefreshEnabled;
  }

  public boolean statsAppendToContent() {
    return statsAppendToContent;
  }

  public boolean statsExposeFragmentUrl() {
    return statsExposeFragmentUrl;
  }

  public static class Builder {

    private final AbleronConfig ableronConfig = new AbleronConfig();

    public Builder enabled(boolean enabled) {
      ableronConfig.enabled = enabled;
      return this;
    }

    public Builder fragmentRequestTimeout(Duration fragmentRequestTimeout) {
      ableronConfig.fragmentRequestTimeout = Objects.requireNonNull(fragmentRequestTimeout, "fragmentRequestTimeout must not be null");
      return this;
    }

    public Builder fragmentRequestHeadersToPass(List<String> fragmentRequestHeadersToPass) {
      ableronConfig.fragmentRequestHeadersToPass = Objects.requireNonNull(fragmentRequestHeadersToPass, "fragmentRequestHeadersToPass must not be null");
      return this;
    }

    public Builder fragmentAdditionalRequestHeadersToPass(List<String> fragmentAdditionalRequestHeadersToPass) {
      ableronConfig.fragmentAdditionalRequestHeadersToPass = Objects.requireNonNull(fragmentAdditionalRequestHeadersToPass, "fragmentAdditionalRequestHeadersToPass must not be null");
      return this;
    }

    public Builder primaryFragmentResponseHeadersToPass(List<String> primaryFragmentResponseHeadersToPass) {
      ableronConfig.primaryFragmentResponseHeadersToPass = Objects.requireNonNull(primaryFragmentResponseHeadersToPass, "primaryFragmentResponseHeadersToPass must not be null");
      return this;
    }

    public Builder cacheMaxSizeInBytes(long cacheMaxSizeInBytes) {
      ableronConfig.cacheMaxSizeInBytes = cacheMaxSizeInBytes;
      return this;
    }

    public Builder cacheVaryByRequestHeaders(List<String> cacheVaryByRequestHeaders) {
      ableronConfig.cacheVaryByRequestHeaders = Objects.requireNonNull(cacheVaryByRequestHeaders, "cacheVaryByRequestHeaders must not be null");
      return this;
    }

    public Builder statsAppendToContent(boolean statsAppendToContent) {
      ableronConfig.statsAppendToContent = statsAppendToContent;
      return this;
    }

    public Builder cacheAutoRefreshEnabled(boolean cacheAutoRefreshEnabled) {
      ableronConfig.cacheAutoRefreshEnabled = cacheAutoRefreshEnabled;
      return this;
    }

    public Builder statsExposeFragmentUrl(boolean statsExposeFragmentUrl) {
      ableronConfig.statsExposeFragmentUrl = statsExposeFragmentUrl;
      return this;
    }

    public AbleronConfig build() {
      return ableronConfig;
    }
  }
}
