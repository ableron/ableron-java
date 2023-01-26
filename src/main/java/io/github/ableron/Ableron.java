package io.github.ableron;

import jakarta.annotation.Nonnull;
import java.net.http.HttpClient;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ableron {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AbleronConfig ableronConfig;
  private final TransclusionProcessor transclusionProcessor;

  public Ableron(@Nonnull AbleronConfig ableronConfig) {
    this(ableronConfig, HttpClient.newHttpClient());
  }

  public Ableron(@Nonnull AbleronConfig ableronConfig, @Nonnull HttpClient httpClient) {
    this.ableronConfig = Objects.requireNonNull(ableronConfig, "ableronConfig must not be null");
    this.transclusionProcessor = new TransclusionProcessor(httpClient);
  }

  /**
   * Indicates whether Ableron UI composition is enabled.
   *
   * @return true in case UI composition is enabled. false otherwise
   */
  public boolean isEnabled() {
    return Boolean.parseBoolean(ableronConfig.getProperty(AbleronConfigParams.ENABLED, "true"));
  }

  /**
   * @see TransclusionProcessor#resolveIncludes(String)
   */
  public TransclusionResult resolveIncludes(String content) {
    var transclusionResult = transclusionProcessor.resolveIncludes(content);
    logger.debug("Ableron UI composition processed {} includes in {}ms", transclusionResult.getProcessedIncludesCount(), transclusionResult.getProcessingTimeMillis());
    return transclusionResult;
  }
}
