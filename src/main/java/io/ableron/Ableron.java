package io.ableron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ableron {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AbleronConfig ableronConfig;
  private final TransclusionProcessor transclusionProcessor = new TransclusionProcessor();

  public Ableron(AbleronConfig ableronConfig) {
    this.ableronConfig = ableronConfig;
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
   * @see TransclusionProcessor#applyTransclusion(String)
   */
  public TransclusionResult applyTransclusion(String content) {
    var transclusionResult = transclusionProcessor.applyTransclusion(content);
    logger.debug("Ableron UI composition processed {} fragments in {}ms", transclusionResult.getProcessedFragmentsCount(), transclusionResult.getProcessingTimeMillis());
    return transclusionResult;
  }
}
