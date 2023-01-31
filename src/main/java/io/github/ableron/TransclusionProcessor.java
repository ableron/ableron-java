package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TransclusionProcessor {

  /**
   * Regular expression for matching ableron includes.
   */
  private static final Pattern INCLUDE_PATTERN =
    Pattern.compile("<(ableron-include)\\s(([^\">]|\"[^\"]*\")*?)(/>|>(.*?)</\\1>)", Pattern.DOTALL);

  /**
   * Regular expression for parsing include tag attributes.
   */
  private static final Pattern ATTRIBUTES_PATTERN = Pattern.compile("\\s*([a-zA-Z_0-9-]+)=\"([^\"]+)\"");

  private static final long NANO_2_MILLIS = 1000000L;

  private final AbleronConfig ableronConfig;

  /**
   * The HTTP client used to resolve includes.
   */
  private final HttpClient httpClient;

  /**
   * Cache for HTTP responses.
   */
  private final Cache<String, HttpResponse> responseCache;

  public TransclusionProcessor() {
    this(null);
  }

  public TransclusionProcessor(AbleronConfig ableronConfig) {
    this.ableronConfig = (ableronConfig != null) ? ableronConfig : AbleronConfig.builder().build();
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(this.ableronConfig.getConnectTimeout())
      .build();
    this.responseCache = buildDefaultCache();
  }

  public Cache<String, HttpResponse> getResponseCache() {
    return responseCache;
  }

  /**
   * Finds all includes in the given content.
   *
   * @param content Content to find the includes in
   * @return The includes
   */
  public Set<Include> findIncludes(String content) {
    int firstIncludePosition = content.indexOf("<ableron-include");

    return (firstIncludePosition == -1) ? Set.of() : INCLUDE_PATTERN.matcher(content.substring(firstIncludePosition))
      .results()
      .parallel()
      .map(match -> new Include(match.group(0), parseAttributes(match.group(2)), match.group(5)))
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

    //TODO: Improve performance: Resolve all includes in parallel immediately after finding them
    //TODO: Improve performance: Replace include tags in the order of finished resolving. First resolved include should be replaced first

    for (Include include : includes) {
      content = content.replace(include.getRawInclude(), include.resolve(httpClient, responseCache));
    }

    transclusionResult.setProcessedIncludesCount(includes.size());
    transclusionResult.setContent(content);
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    return transclusionResult;
  }

  private Cache<String, HttpResponse> buildDefaultCache() {
    return Caffeine.newBuilder()
      //TODO: Make maximumWeight() configurable (max size in MB)
      .maximumWeight(1024 * 1024 * 10)
      .weigher((String url, HttpResponse response) -> response.getResponseBody().length())
      .expireAfter(new Expiry<String, HttpResponse>() {
        public long expireAfterCreate(String url, HttpResponse response, long currentTime) {
          long milliseconds = response.getExpirationTime()
            .minusMillis(System.currentTimeMillis())
            .toEpochMilli();
          return TimeUnit.MILLISECONDS.toNanos(milliseconds);
        }
        public long expireAfterUpdate(String url, HttpResponse response, long currentTime, long currentDuration) {
          return expireAfterCreate(url, response, currentTime);
        }
        public long expireAfterRead(String url, HttpResponse response, long currentTime, long currentDuration) {
          return currentDuration;
        }
      })
      .build();
  }

  /**
   * Parses the given include tag attributes string.
   *
   * @param attributesString Attributes string to parse
   * @return A key-value map of the attributes
   */
  private Map<String, String> parseAttributes(String attributesString) {
    Map<String, String> attributes = new HashMap<>();

    if (attributesString != null) {
      ATTRIBUTES_PATTERN.matcher(attributesString)
        .results()
        .forEach(match -> attributes.put(match.group(1), match.group(2)));
    }

    return attributes;
  }
}
