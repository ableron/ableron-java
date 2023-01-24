package io.github.ableron;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TransclusionProcessor {

  /**
   * Regular expression matching ableron includes.
   */
  private static final Pattern INCLUDE_PATTERN =
    Pattern.compile("<(ableron-include)\\s(([^\">]|\"[^\"]*\")*?)(/>|>(.*?)</\\1>)", Pattern.DOTALL);

  /**
   * Regular expression used to parse include tag attributes.
   */
  private static final Pattern ATTRIBUTES_PATTERN = Pattern.compile("(\\W*(\\w+)=\"([^\"]+)\")+");

  private static final long NANO_2_MILLIS = 1000000L;

  private HttpClient httpClient = HttpClient.newHttpClient();

  /**
   * Finds all includes in the given content.
   *
   * @param content Content to find the includes in
   * @return The includes
   */
  public Set<Include> findIncludes(String content) {
    return INCLUDE_PATTERN.matcher(content)
      .results()
      .map(matchResult -> new Include(matchResult.group(0), parseAttributes(matchResult.group(2)), matchResult.group(5), httpClient))
      .collect(Collectors.toSet());
  }

  /**
   * Resolves all includes in the given content.
   *
   * @param content The content to resolve the includes of
   * @return Content with resolved includes
   */
  public TransclusionResult resolveIncludes(String content) {
    var startTime = System.nanoTime();
    var transclusionResult = new TransclusionResult();
    var includes = findIncludes(content);

    for (Include include : includes) {
      content = content.replace(include.getRawInclude(), include.getResolvedInclude());
    }

    transclusionResult.setProcessedIncludesCount(includes.size());
    transclusionResult.setContent(content);
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    return transclusionResult;
  }

  /**
   * Parses the given include tag attributes string.
   *
   * @param attributesString Attributes string to parse
   * @return A key-value map of the include tag attributes
   */
  private Map<String, String> parseAttributes(String attributesString) {
    Map<String, String> attributes = new HashMap<>();

    if (attributesString != null) {
      ATTRIBUTES_PATTERN.matcher(attributesString)
        .results()
        .forEach(matchResult -> attributes.put(matchResult.group(2), matchResult.group(3)));
    }

    return attributes;
  }
}
