package io.github.ableron;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ableron {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AbleronConfig ableronConfig;
  private final TransclusionProcessor transclusionProcessor;

  /**
   * Initializes Ableron with the given configuration.
   *
   * @param ableronConfig The Ableron configuration
   */
  public Ableron(@Nonnull AbleronConfig ableronConfig) {
    this.ableronConfig = Objects.requireNonNull(ableronConfig, "ableronConfig must not be null");
    this.transclusionProcessor = new TransclusionProcessor(ableronConfig);
  }

  /**
   * @see TransclusionProcessor#resolveIncludes(Content)
   */
  public TransclusionResult resolveIncludes(String content) {
    if (ableronConfig.isEnabled()) {
      var transclusionResult = transclusionProcessor.resolveIncludes(Content.of(content));
      logger.debug("Ableron UI composition processed {} includes in {}ms", transclusionResult.getProcessedIncludesCount(), transclusionResult.getProcessingTimeMillis());
      return transclusionResult;
    }

    return getNoOpResult(content);
  }

  private TransclusionResult getNoOpResult(String content) {
    var result = new TransclusionResult();
    result.setContent(content);
    result.setProcessedIncludesCount(0);
    result.setProcessingTimeMillis(0);
    return result;
  }
}
