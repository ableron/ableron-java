package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private final Cache<String, Fragment> fragmentCache;

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
    this.fragmentCache = buildFragmentCache(this.ableronConfig.getCacheMaxSizeInBytes());
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

  public Cache<String, Fragment> getFragmentCache() {
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
      .map(match -> new Include(parseAttributes(match.group(2)), match.group(5), match.group(0)))
      .collect(Collectors.toSet());
  }

  /**
   * Resolves all includes in the given content.
   *
   * @param content The content to resolve the includes of
   * @param presentRequestHeaders Request headers of the initial request having the includes in its response
   * @return Content with resolved includes
   */
  public TransclusionResult resolveIncludes(String content, Map<String, List<String>> presentRequestHeaders) {
    var startTime = System.nanoTime();
    var transclusionResult = new TransclusionResult(content, ableronConfig.statsAppendToContent());
    CompletableFuture.allOf(findIncludes(content).stream()
      .map(include -> {
        try {
          var includeResolveStartTime = System.nanoTime();
          return include.resolve(httpClient, presentRequestHeaders, fragmentCache, ableronConfig, resolveThreadPool)
            .thenApply(fragment -> {
              logger.debug("Resolved include {} in {}ms", include.getId(), (System.nanoTime() - includeResolveStartTime) / NANO_2_MILLIS);
              transclusionResult.addResolvedInclude(include, fragment, (System.nanoTime() - includeResolveStartTime) / NANO_2_MILLIS);
              return fragment;
            });
        } catch (Exception e) {
          logger.error("Unable to resolve include {}", include.getId(), e);
          transclusionResult.addUnresolvableInclude(include, e.getMessage());
          return CompletableFuture.completedFuture(null);
        }
      })
      .toArray(CompletableFuture[]::new)
    ).join();
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    return transclusionResult;
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

  private Cache<String, Fragment> buildFragmentCache(long cacheMaxSizeInBytes) {
    return Caffeine.newBuilder()
      .maximumWeight(cacheMaxSizeInBytes)
      .weigher((String fragmentCacheKey, Fragment fragment) -> fragment.getContent().length())
      .expireAfter(new Expiry<String, Fragment>() {
        public long expireAfterCreate(String fragmentCacheKey, Fragment fragment, long currentTime) {
          long milliseconds = fragment.getExpirationTime()
            .minusMillis(System.currentTimeMillis())
            .toEpochMilli();
          return TimeUnit.MILLISECONDS.toNanos(milliseconds);
        }
        public long expireAfterUpdate(String fragmentCacheKey, Fragment fragment, long currentTime, long currentDuration) {
          return expireAfterCreate(fragmentCacheKey, fragment, currentTime);
        }
        public long expireAfterRead(String fragmentCacheKey, Fragment fragment, long currentTime, long currentDuration) {
          return currentDuration;
        }
      })
      .evictionListener((String fragmentCacheKey, Fragment fragment, RemovalCause cause) -> {
        if (cause == RemovalCause.SIZE) {
          logger.info("Fragment cache size exceeded. Removing {} from cache. Consider increasing cache size", fragmentCacheKey);
        }
      })
      .build();
  }
}
