package io.github.ableron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Ableron {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AbleronConfig ableronConfig;
  private final TransclusionProcessor transclusionProcessor;

  /**
   * Initializes Ableron with the given configuration.
   *
   * @param ableronConfig The Ableron configuration
   */
  public Ableron(AbleronConfig ableronConfig) {
    this.ableronConfig = Objects.requireNonNull(ableronConfig, "ableronConfig must not be null");
    this.transclusionProcessor = new TransclusionProcessor(ableronConfig);
  }

  /**
   * Resolves all includes in the given content.
   *
   * @param content The content to resolve the includes of
   * @param presentRequestHeaders Request headers of the initial request having the includes in its response
   * @return Transclusion result including the content with resolved includes as well as metadata
   */
  public TransclusionResult resolveIncludes(String content, Map<String, List<String>> presentRequestHeaders) {
    if (ableronConfig.isEnabled()) {
      var transclusionResult = transclusionProcessor.resolveIncludes(Content.of(content), presentRequestHeaders);
      logger.debug("Ableron UI composition processed {} includes in {}ms", transclusionResult.getProcessedIncludesCount(), transclusionResult.getProcessingTimeMillis());
      return transclusionResult;
    }

    return new TransclusionResult(content);
  }
}
