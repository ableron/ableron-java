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
   * Maximum amount of time to wait for a successful connection to an include source or
   * fallback URL.
   *
   * Defaults to 2 seconds.
   */
  private Duration connectTimeout = Duration.ofMillis(2000);

  /**
   * Maximum amount of time to wait for a successful and complete response of an include
   * source or fallback URL.
   *
   * Defaults to 5 seconds.
   */
  private Duration readTimeout = Duration.ofMillis(5000);

  private AbleronConfig() {}

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public static class Builder {

    private final AbleronConfig ableronConfig = new AbleronConfig();

    public Builder enabled(boolean enabled) {
      ableronConfig.enabled = enabled;
      return this;
    }

    public Builder connectTimeout(Duration connectTimeout) {
      ableronConfig.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
      return this;
    }

    public Builder readTimeout(Duration readTimeout) {
      ableronConfig.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout must not be null");
      return this;
    }

    public AbleronConfig build() {
      return ableronConfig;
    }
  }
}
