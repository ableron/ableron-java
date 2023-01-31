package io.github.ableron;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ableron {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final TransclusionProcessor transclusionProcessor;

  /**
   * Initializes Ableron with the given configuration.
   *
   * @param ableronConfig The Ableron configuration
   */
  public Ableron(@Nonnull AbleronConfig ableronConfig) {
    Objects.requireNonNull(ableronConfig, "ableronConfig must not be null");
    this.transclusionProcessor = new TransclusionProcessor(ableronConfig);
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
