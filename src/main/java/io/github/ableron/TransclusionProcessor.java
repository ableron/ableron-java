package io.github.ableron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private static final Pattern ATTRIBUTES_PATTERN = Pattern.compile("\\s*([a-zA-Z0-9_-]+)(=\"([^\"]+)\")?");

  private static final long NANO_2_MILLIS = 1000000L;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AbleronConfig ableronConfig;

  /**
   * The HTTP client used to resolve includes.
   */
  private final HttpClient httpClient;

  /**
   * Cache for fragments.
   */
  private final FragmentCache fragmentCache;

  /**
   * Thread pool used to resolve includes in parallel.
   */
  private final ExecutorService resolveThreadPool = Executors.newFixedThreadPool(64);

  public TransclusionProcessor() {
    this(AbleronConfig.builder().build());
  }

  public TransclusionProcessor(AbleronConfig ableronConfig) {
    this.ableronConfig = ableronConfig;
    this.httpClient = buildHttpClient();
    this.fragmentCache = new FragmentCache(this.ableronConfig.getCacheMaxSizeInBytes(), this.ableronConfig.cacheAutoRefreshEnabled());
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

  public FragmentCache getFragmentCache() {
    return fragmentCache;
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
   * @param parentRequestHeaders Request headers of the initial request having the includes in its response
   * @return Content with resolved includes
   */
  public TransclusionResult resolveIncludes(String content, Map<String, List<String>> parentRequestHeaders) {
    var startTime = System.nanoTime();
    var transclusionResult = new TransclusionResult(content, this.fragmentCache.stats(), ableronConfig.statsAppendToContent(), ableronConfig.statsExposeFragmentUrl());
    CompletableFuture.allOf(findIncludes(content).stream()
      .map(include -> {
        try {
          return include.resolve(httpClient, parentRequestHeaders, fragmentCache, ableronConfig, resolveThreadPool)
            .thenAccept(transclusionResult::addResolvedInclude);
        } catch (Exception e) {
          handleResolveError(include, e, transclusionResult, startTime);
          return CompletableFuture.completedFuture(null);
        }
      })
      .toArray(CompletableFuture[]::new)
    ).join();
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    return transclusionResult;
  }

  private void handleResolveError(Include include, Exception e, TransclusionResult transclusionResult, long resolveStartTimeMillis) {
    logger.error("[Ableron] Unable to resolve include '{}'", include.getId(), e);
    transclusionResult.addResolvedInclude(include.resolveWith(
      new Fragment(null, 200, include.getFallbackContent(), Instant.now().plusSeconds(60), Map.of()),
      (int) ((System.nanoTime() - resolveStartTimeMillis) / NANO_2_MILLIS),
      "fallback content"));
  }

  /**
   * Parses the given include tag attributes string.
   *
   * @param attributesString Attributes string to parse
   * @return A key-value map of the attributes
   */
  private Map<String, String> parseAttributes(String attributesString) {
    return ATTRIBUTES_PATTERN.matcher(Optional.ofNullable(attributesString).orElse(""))
      .results()
      .map(match -> new AbstractMap.SimpleEntry<>(match.group(1), Optional.ofNullable(match.group(3)).orElse("")))
      .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
  }

  private HttpClient buildHttpClient() {
    return HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();
  }
}
