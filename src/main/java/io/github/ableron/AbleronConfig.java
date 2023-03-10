package io.github.ableron;

import java.time.Duration;
import java.util.Objects;

public class AbleronConfig {

  /**
   * Whether UI composition is enabled.
   * Defaults to true.
   */
  private boolean enabled = true;

  /**
   * Maximum duration to wait for a successful and complete response of an include source
   * or fallback URL.
   *
   * Defaults to 5 seconds.
   */
  private Duration requestTimeout = Duration.ofMillis(5000);

  /**
   * Duration to cache HTTP responses in case there is no caching information provided
   * along the response, i.e. neither Cache-Control nor Expires header.
   *
   * Defaults to 5 minutes.
   */
  private Duration defaultResponseCacheDuration = Duration.ofMinutes(5);

  /**
   * Maximum size in bytes the response cache may have.
   *
   * Defaults to 10 MB.
   */
  private long maxCacheSizeInBytes = 1024 * 1024 * 10;

  private AbleronConfig() {}

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public Duration getDefaultResponseCacheDuration() {
    return defaultResponseCacheDuration;
  }

  public long getMaxCacheSizeInBytes() {
    return maxCacheSizeInBytes;
  }

  public static class Builder {

    private final AbleronConfig ableronConfig = new AbleronConfig();

    public Builder enabled(boolean enabled) {
      ableronConfig.enabled = enabled;
      return this;
    }

    public Builder requestTimeout(Duration requestTimeout) {
      ableronConfig.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
      return this;
    }

    public Builder defaultResponseCacheDuration(Duration defaultResponseCacheDuration) {
      ableronConfig.defaultResponseCacheDuration = Objects.requireNonNull(defaultResponseCacheDuration, "defaultResponseCacheDuration must not be null");
      return this;
    }

    public Builder maxCacheSizeInBytes(long maxCacheSizeInBytes) {
      ableronConfig.maxCacheSizeInBytes = maxCacheSizeInBytes;
      return this;
    }

    public AbleronConfig build() {
      return ableronConfig;
    }
  }
}
