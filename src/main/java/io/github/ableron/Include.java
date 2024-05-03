package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Include {

  /**
   * Name of the attribute which contains the ID of the include - an optional unique name.
   */
  private static final String ATTR_ID = "id";

  /**
   * Name of the attribute which contains the source URl to resolve the include to.
   */
  private static final String ATTR_SOURCE = "src";

  /**
   * Name of the attribute which contains the timeout for requesting the src URL.
   */
  private static final String ATTR_SOURCE_TIMEOUT_MILLIS = "src-timeout-millis";

  /**
   * Name of the attribute which contains the fallback URL to resolve the include to in case the
   * source URL could not be loaded.
   */
  private static final String ATTR_FALLBACK_SOURCE = "fallback-src";

  /**
   * Name of the attribute which contains the timeout for requesting the fallback-src URL.
   */
  private static final String ATTR_FALLBACK_SOURCE_TIMEOUT_MILLIS = "fallback-src-timeout-millis";

  /**
   * Name of the attribute which denotes a fragment whose response code is set as response code
   * for the page.
   */
  private static final String ATTR_PRIMARY = "primary";

  /**
   * HTTP status codes indicating successful and cacheable responses.
   */
  private static final List<Integer> HTTP_STATUS_CODES_SUCCESS = Arrays.asList(
    200, 203, 204, 206
  );

  /**
   * HTTP status codes indicating cacheable responses.
   *
   * @link <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.1">RFC 9110 Section 15.1. Overview of Status Codes</a>
   */
  private static final List<Integer> HTTP_STATUS_CODES_CACHEABLE = Arrays.asList(
    200, 203, 204, 206,
    300,
    404, 405, 410, 414,
    501
  );

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Raw include tag.
   */
  private final String rawIncludeTag;

  /**
   * Raw attributes of the include tag.
   */
  private final Map<String, String> rawAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  /**
   * Fragment ID. Either generated or passed via attribute.
   */
  private final String id;

  /**
   * URL of the fragment to include.
   */
  private final String src;

  /**
   * Timeout for requesting the src URL.
   */
  private final Duration srcTimeout;

  /**
   * URL of the fragment to include in case the request to the source URL failed.
   */
  private final String fallbackSrc;

  /**
   * Timeout for requesting the fallback-src URL.
   */
  private final Duration fallbackSrcTimeout;

  /**
   * Whether the include provides the primary fragment and thus sets the response code of the page.
   */
  private final boolean primary;

  /**
   * Fallback content to use in case the include could not be resolved.
   */
  private final String fallbackContent;

  /**
   * Recorded response of the errored primary fragment.
   */
  private Fragment erroredPrimaryFragment = null;

  /**
   * Constructs a new Include.
   *
   * @param rawIncludeTag Raw include tag
   */
  public Include(String rawIncludeTag) {
    this(rawIncludeTag, null, null);
  }

  /**
   * Constructs a new Include.
   *
   * @param rawIncludeTag Raw include tag
   * @param rawAttributes Raw attributes of the include tag
   */
  public Include(String rawIncludeTag, Map<String, String> rawAttributes) {
    this(rawIncludeTag, rawAttributes, null);
  }

  /**
   * Constructs a new Include.
   *
   * @param rawIncludeTag Raw include tag
   * @param rawAttributes Raw attributes of the include tag
   * @param fallbackContent Fallback content to use in case the include could not be resolved
   */
  public Include(String rawIncludeTag, Map<String, String> rawAttributes, String fallbackContent) {
    this.rawIncludeTag = Optional.ofNullable(rawIncludeTag).orElse("");
    this.rawAttributes.putAll(Optional.ofNullable(rawAttributes).orElseGet(Map::of));
    this.id = buildIncludeId(this.rawAttributes.get(ATTR_ID));
    this.src = this.rawAttributes.get(ATTR_SOURCE);
    this.srcTimeout = parseTimeout(this.rawAttributes.get(ATTR_SOURCE_TIMEOUT_MILLIS));
    this.fallbackSrc = this.rawAttributes.get(ATTR_FALLBACK_SOURCE);
    this.fallbackSrcTimeout = parseTimeout(this.rawAttributes.get(ATTR_FALLBACK_SOURCE_TIMEOUT_MILLIS));
    this.primary = hasBooleanAttribute(ATTR_PRIMARY);
    this.fallbackContent = Optional.ofNullable(fallbackContent).orElse("");
  }

  /**
   * @return The raw include tag
   */
  public String getRawIncludeTag() {
    return rawIncludeTag;
  }

  /**
   * @return The raw attributes of the include tag.
   */
  public Map<String, String> getRawAttributes() {
    return rawAttributes;
  }

  /**
   * @return ID of the include
   */
  public String getId() {
    return id;
  }

  /**
   * @return URL of the fragment to include
   */
  public String getSrc() {
    return src;
  }

  /**
   * @return The timeout for requesting the src URL
   */
  public Duration getSrcTimeout() {
    return srcTimeout;
  }

  /**
   * @return URL of the fragment to include in case the source URL could not be loaded
   */
  public String getFallbackSrc() {
    return fallbackSrc;
  }

  /**
   * @return The timeout for requesting the fallback-src URL
   */
  public Duration getFallbackSrcTimeout() {
    return fallbackSrcTimeout;
  }

  /**
   * @return Whether this is a primary include
   */
  public boolean isPrimary() {
    return primary;
  }

  /**
   * @return Fallback content to use in case the include could not be resolved
   */
  public String getFallbackContent() {
    return fallbackContent;
  }

  /**
   * Resolves this include.
   *
   * @param httpClient HTTP client used to resolve this include
   * @param parentRequestHeaders Parent request headers which are passed to fragment requests if allowed by config
   * @param fragmentCache Cache for fragments
   * @param config Global ableron configuration
   * @param resolveThreadPool Thread pool to use for resolving
   * @return The fragment the include has been resolved to
   */
  public CompletableFuture<Fragment> resolve(HttpClient httpClient, Map<String, List<String>> parentRequestHeaders, Cache<String, Fragment> fragmentCache, AbleronConfig config, ExecutorService resolveThreadPool) {
    var fragmentRequestHeaders = filterHeaders(parentRequestHeaders, Stream.concat(
        config.getFragmentRequestHeadersToPass().stream(),
        config.getFragmentAdditionalRequestHeadersToPass().stream()
      ).collect(Collectors.toList())
    );
    erroredPrimaryFragment = null;

    return CompletableFuture.supplyAsync(
      () -> load(src, httpClient, fragmentRequestHeaders, fragmentCache, config, getRequestTimeout(srcTimeout, config))
        .or(() -> load(fallbackSrc, httpClient, fragmentRequestHeaders, fragmentCache, config, getRequestTimeout(fallbackSrcTimeout, config)))
        .or(() -> Optional.ofNullable(erroredPrimaryFragment))
        .orElseGet(() -> new Fragment(200, fallbackContent)), resolveThreadPool);
  }

  private Optional<Fragment> load(String uri, HttpClient httpClient, Map<String, List<String>> requestHeaders, Cache<String, Fragment> fragmentCache, AbleronConfig config, Duration requestTimeout) {
    var fragmentCacheKey = this.buildFragmentCacheKey(uri, requestHeaders, config.getCacheVaryByRequestHeaders());

    return Optional.ofNullable(uri)
      .map(uri1 -> {
        var fragmentFromCache = fragmentCache.getIfPresent(fragmentCacheKey);

        return fragmentFromCache != null ? fragmentFromCache : performRequest(uri, httpClient, requestHeaders, requestTimeout)
          .filter(response -> {
            if (!isHttpStatusCacheable(response.statusCode())) {
              logger.error("Fragment {} returned status code {}", uri, response.statusCode());
              recordErroredPrimaryFragment(new Fragment(
                uri,
                response.statusCode(),
                HttpUtil.getResponseBodyAsString(response),
                Instant.EPOCH,
                filterHeaders(response.headers().map(), config.getPrimaryFragmentResponseHeadersToPass())
              ));
              return false;
            }

            return true;
          })
          .map(response -> {
            var fragment = new Fragment(
              response.uri().toString(),
              response.statusCode(),
              HttpUtil.getResponseBodyAsString(response),
              HttpUtil.calculateResponseExpirationTime(response.headers().map()),
              filterHeaders(response.headers().map(), config.getPrimaryFragmentResponseHeadersToPass())
            );
            fragmentCache.put(fragmentCacheKey, fragment);
            return fragment;
          })
          .orElse(null);
      })
      .filter(fragment -> {
        if (!HTTP_STATUS_CODES_SUCCESS.contains(fragment.getStatusCode())) {
          logger.error("Fragment {} returned status code {}", uri, fragment.getStatusCode());
          recordErroredPrimaryFragment(fragment);
          return false;
        }

        return true;
      });
  }

  private void recordErroredPrimaryFragment(Fragment fragment) {
    if (primary && erroredPrimaryFragment == null) {
      erroredPrimaryFragment = fragment;
    }
  }

  private Map<String, List<String>> filterHeaders(Map<String, List<String>> headersToFilter, List<String> allowedHeaders) {
    return headersToFilter.entrySet()
      .stream()
      .filter(header -> allowedHeaders.stream().anyMatch(headerName -> headerName.equalsIgnoreCase(header.getKey())))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Duration parseTimeout(String timeoutAsString) {
    return Optional.ofNullable(timeoutAsString)
      .map(timeout -> {
        try {
          return Long.parseLong(timeout);
        } catch (NumberFormatException e) {
          logger.error("Invalid request timeout: {}", timeout);
          return null;
        }
      })
      .map(Duration::ofMillis)
      .orElse(null);
  }

  private Duration getRequestTimeout(Duration localTimeout, AbleronConfig config) {
    return Optional.ofNullable(localTimeout)
      .orElse(config.getFragmentRequestTimeout());
  }

  private boolean isHttpStatusCacheable(int httpStatusCode) {
    return HTTP_STATUS_CODES_CACHEABLE.contains(httpStatusCode);
  }

  private Optional<HttpResponse<byte[]>> performRequest(String uri, HttpClient httpClient, Map<String, List<String>> requestHeaders, Duration requestTimeout) {
    try {
      logger.debug("Loading fragment {} for include {} with timeout {}ms", uri, id, requestTimeout.toMillis());
      var httpResponse = httpClient.sendAsync(buildHttpRequest(uri, requestHeaders), HttpResponse.BodyHandlers.ofByteArray());
      return Optional.of(httpResponse.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS));
    } catch (TimeoutException e) {
      logger.error("Unable to load fragment {} for include {}: {}ms timeout exceeded", uri, id, requestTimeout.toMillis());
      return Optional.empty();
    } catch (Exception e) {
      logger.error("Unable to load fragment {} for include {}: {}", uri, id, Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
      return Optional.empty();
    }
  }

  private HttpRequest buildHttpRequest(String uri, Map<String, List<String>> requestHeaders) {
    var httpRequestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(uri))
      .header("Accept-Encoding", "gzip");
    requestHeaders.forEach((name, values) -> values.forEach(value -> httpRequestBuilder.header(name, value)));
    return httpRequestBuilder
      .GET()
      .build();
  }

  private String buildIncludeId(String providedId) {
    return Optional.ofNullable(providedId)
      .map(id -> id.replaceAll("[^A-Za-z0-9_-]", ""))
      .filter(id -> !id.isEmpty())
      .orElse(String.valueOf(Math.abs(rawIncludeTag.hashCode())));
  }

  private String buildFragmentCacheKey(String fragmentUrl, Map<String, List<String>> fragmentRequestHeaders, List<String> cacheVaryByRequestHeaders) {
    return fragmentUrl +
      fragmentRequestHeaders.entrySet()
        .stream()
        .filter(header -> cacheVaryByRequestHeaders.stream().anyMatch(headerName -> headerName.equalsIgnoreCase(header.getKey())))
        .sorted((c1, c2) -> c1.getKey().compareToIgnoreCase(c2.getKey()))
        .map(entry -> "|" + entry.getKey() + "=" + String.join(",", entry.getValue()))
        .map(String::toLowerCase)
        .collect(Collectors.joining());
  }

  private boolean hasBooleanAttribute(String attributeName) {
    return rawAttributes.containsKey(attributeName)
      && List.of("", attributeName.toLowerCase()).contains(rawAttributes.get(attributeName).toLowerCase());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Include include = (Include) o;

    return rawIncludeTag.equals(include.rawIncludeTag);
  }

  @Override
  public int hashCode() {
    return rawIncludeTag.hashCode();
  }
}
