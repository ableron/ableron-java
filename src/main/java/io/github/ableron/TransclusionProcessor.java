package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransclusionProcessor {

  /**
   * Regular expression for matching ableron includes.
   */
  private static final Pattern INCLUDE_PATTERN =
    Pattern.compile("<(ableron-include)\\s(([^\">]|\"[^\"]*\")*?)(/>|>(.*?)</\\1>)", Pattern.DOTALL);

  /**
   * Regular expression for parsing include tag attributes.
   */
  private static final Pattern ATTRIBUTES_PATTERN = Pattern.compile("\\s*([a-zA-Z_0-9-]+)(=\"([^\"]+)\")?");

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
    this(null);
  }

  public TransclusionProcessor(AbleronConfig ableronConfig) {
    this.ableronConfig = (ableronConfig != null) ? ableronConfig : AbleronConfig.builder().build();
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
  public TransclusionResult resolveIncludes(Content content, Map<String, List<String>> presentRequestHeaders) {
    var startTime = System.nanoTime();
    var transclusionResult = new TransclusionResult();
    var includes = findIncludes(content.get());
    validateIncludes(includes);
    CompletableFuture.allOf(includes.stream()
      .map(include -> include.resolve(httpClient, presentRequestHeaders, fragmentCache, ableronConfig, resolveThreadPool)
        .thenApplyAsync(s -> {
          content.replace(include.getRawIncludeTag(), s);
          return s;
        })
        .exceptionally(throwable -> {
          logger.error("Unable to resolve include", throwable);
          return null;
        })
      )
      .toArray(CompletableFuture[]::new)
    ).join();
    transclusionResult.setProcessedIncludesCount(includes.size());
    transclusionResult.setContent(content.get());
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    //TODO: Set result.statusCodeOverride
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

  //TODO: Do not make this in void method. Add test
  private void validateIncludes(Set<Include> includes) {
    long primaryIncludesCount = includes.stream()
      .filter(Include::isPrimary)
      .count();

    if (primaryIncludesCount > 1) {
      logger.error("Only one primary include per page allowed. Found {}", primaryIncludesCount);
    }
  }

  private HttpClient buildHttpClient() {
    return HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  }

  private Cache<String, Fragment> buildFragmentCache(long cacheMaxSizeInBytes) {
    return Caffeine.newBuilder()
      .maximumWeight(cacheMaxSizeInBytes)
      .weigher((String url, Fragment fragment) -> fragment.getContent().length())
      .expireAfter(new Expiry<String, Fragment>() {
        public long expireAfterCreate(String url, Fragment fragment, long currentTime) {
          long milliseconds = fragment.getExpirationTime()
            .minusMillis(System.currentTimeMillis())
            .toEpochMilli();
          return TimeUnit.MILLISECONDS.toNanos(milliseconds);
        }
        public long expireAfterUpdate(String url, Fragment fragment, long currentTime, long currentDuration) {
          return expireAfterCreate(url, fragment, currentTime);
        }
        public long expireAfterRead(String url, Fragment fragment, long currentTime, long currentDuration) {
          return currentDuration;
        }
      })
      .build();
  }
}
