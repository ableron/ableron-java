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

  public boolean isEnabled() {
    return Boolean.parseBoolean(ableronConfig.getProperty(AbleronConfigParams.ENABLED, "true"));
  }

  public TransclusionResult applyTransclusion(String body) {
    var transclusionResult = transclusionProcessor.applyTransclusion(body);
    logger.debug("Ableron UI composition processed {} fragments in {}ms", transclusionResult.getProcessedFragmentsCount(), transclusionResult.getProcessingTimeMillis());
    return transclusionResult;
  }
}
