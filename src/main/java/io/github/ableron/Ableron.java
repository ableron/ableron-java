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
   * @return The Ableron configuration
   */
  public AbleronConfig getConfig() {
    return ableronConfig;
  }

  /**
   * Resolves all includes in the given content.
   *
   * @param content The content to resolve the includes of
   * @param parentRequestHeaders Request headers of the initial request having the includes in its response
   * @return Transclusion result including the content with resolved includes as well as metadata
   */
  public TransclusionResult resolveIncludes(String content, Map<String, List<String>> parentRequestHeaders) {
    if (ableronConfig.isEnabled()) {
      return transclusionProcessor.resolveIncludes(content, parentRequestHeaders);
    }

    return new TransclusionResult(content);
  }
}
