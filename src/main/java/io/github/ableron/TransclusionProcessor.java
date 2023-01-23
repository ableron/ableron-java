package io.github.ableron;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TransclusionProcessor {

  private static final Pattern INCLUDE_PATTERN = Pattern.compile("<ableron-include\\s(\"[^\"]*\"|[^\">])*/?>");
  private static final long NANO_2_MILLIS = 1000000L;

  /**
   * Finds all includes in the given content.
   *
   * @param content Content to find the includes in
   * @return The includes
   */
  public Set<Include> findIncludes(String content) {
    return INCLUDE_PATTERN.matcher(content)
      .results()
      .map(matchResult -> new Include(matchResult.group(0)))
      .collect(Collectors.toSet());
  }

  /**
   * Resolves all includes in the given content.
   *
   * @param content The content to resolve the includes of
   * @return The content with resolved includes
   */
  public TransclusionResult resolveIncludes(String content) {
    var startTime = System.nanoTime();
    var transclusionResult = new TransclusionResult();
    var includes = findIncludes(content);

    for (Include include : includes) {
      content = content.replace(include.getRawIncludeTag(), include.getResolvedContent());
    }

    transclusionResult.setProcessedIncludesCount(includes.size());
    transclusionResult.setContent(content);
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    return transclusionResult;
  }
}
