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
   * Timeout for requesting fragments.
   * Defaults to 3 seconds.
   */
  private Duration fragmentRequestTimeout = Duration.ofSeconds(3);

  /**
   * Duration to cache fragments in case no caching information is provided along
   * the response, i.e. neither <code>Cache-Control</code> nor <code>Expires</code> header.
   * Defaults to 5 minutes.
   */
  private Duration fragmentDefaultCacheDuration = Duration.ofMinutes(5);

  /**
   * Maximum size in bytes the fragment cache may have.
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

  public Duration getFragmentRequestTimeout() {
    return fragmentRequestTimeout;
  }

  public Duration getFragmentDefaultCacheDuration() {
    return fragmentDefaultCacheDuration;
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

    public Builder fragmentRequestTimeout(Duration fragmentRequestTimeout) {
      ableronConfig.fragmentRequestTimeout = Objects.requireNonNull(fragmentRequestTimeout, "fragmentRequestTimeout must not be null");
      return this;
    }

    public Builder fragmentDefaultCacheDuration(Duration fragmentDefaultCacheDuration) {
      ableronConfig.fragmentDefaultCacheDuration = Objects.requireNonNull(fragmentDefaultCacheDuration, "fragmentDefaultCacheDuration must not be null");
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
