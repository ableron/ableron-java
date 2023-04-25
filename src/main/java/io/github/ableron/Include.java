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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class Include {

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

  private static final String HEADER_AGE = "Age";
  private static final String HEADER_CACHE_CONTROL = "Cache-Control";
  private static final String HEADER_DATE = "Date";
  private static final String HEADER_EXPIRES = "Expires";

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
  private final Map<String, String> rawAttributes;

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
   * Resolved include content.
   */
  private CompletableFuture<String> resolvedInclude = null;

  /**
   * Constructs a new Include.
   *
   * @param rawAttributes Raw attributes of the include tag
   */
  public Include(Map<String, String> rawAttributes) {
    this(rawAttributes, null);
  }

  /**
   * Constructs a new Include.
   *
   * @param rawAttributes Raw attributes of the include tag
   * @param fallbackContent Fallback content to use in case the include could not be resolved
   */
  public Include(Map<String, String> rawAttributes, String fallbackContent) {
    this(rawAttributes, fallbackContent, "");
  }

  /**
   * Constructs a new Include.
   *
   * @param rawAttributes Raw attributes of the include tag
   * @param fallbackContent Fallback content to use in case the include could not be resolved
   * @param rawIncludeTag Raw include tag
   */
  public Include(Map<String, String> rawAttributes, String fallbackContent, String rawIncludeTag) {
    this.rawIncludeTag = Optional.ofNullable(rawIncludeTag).orElse("");
    this.rawAttributes = Optional.ofNullable(rawAttributes).orElse(Map.of());
    this.src = this.rawAttributes.get(ATTR_SOURCE);
    this.srcTimeout = parseTimeout(this.rawAttributes.get(ATTR_SOURCE_TIMEOUT_MILLIS));
    this.fallbackSrc = this.rawAttributes.get(ATTR_FALLBACK_SOURCE);
    this.fallbackSrcTimeout = parseTimeout(this.rawAttributes.get(ATTR_FALLBACK_SOURCE_TIMEOUT_MILLIS));
    this.primary = this.rawAttributes.containsKey(ATTR_PRIMARY) && List.of("", "primary").contains(this.rawAttributes.get(ATTR_PRIMARY).toLowerCase());
    this.fallbackContent = fallbackContent;
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
   * @param fragmentRequestHeaders Request headers which are passed to fragment requests if allowed by config
   * @param fragmentCache Cache for fragments
   * @param config Global ableron configuration
   * @param resolveThreadPool Thread pool to use for resolving
   * @return The fragment the include has been resolved to
   */
  public CompletableFuture<String> resolve(HttpClient httpClient, Map<String, List<String>> fragmentRequestHeaders, Cache<String, Fragment> fragmentCache, AbleronConfig config, ExecutorService resolveThreadPool) {
    if (resolvedInclude == null) {
      var filteredFragmentRequestHeaders = filterFragmentRequestHeaders(fragmentRequestHeaders, config.getFragmentRequestHeadersToPass());

      resolvedInclude = CompletableFuture.supplyAsync(
        () -> load(src, httpClient, filteredFragmentRequestHeaders, fragmentCache, config, getRequestTimeout(srcTimeout, config))
          .or(() -> load(fallbackSrc, httpClient, filteredFragmentRequestHeaders, fragmentCache, config, getRequestTimeout(fallbackSrcTimeout, config)))
          .or(() -> Optional.ofNullable(fallbackContent))
          .orElse(""), resolveThreadPool
      );
    }

    return resolvedInclude;
  }

  private Map<String, List<String>> filterFragmentRequestHeaders(Map<String, List<String>> requestHeaders, List<String> allowedRequestHeaders) {
    return requestHeaders.entrySet()
      .stream()
      .filter(header -> allowedRequestHeaders.stream().anyMatch(headerName -> headerName.equalsIgnoreCase(header.getKey())))
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

  private Optional<String> load(String uri, HttpClient httpClient, Map<String, List<String>> requestHeaders, Cache<String, Fragment> fragmentCache, AbleronConfig config, Duration requestTimeout) {
    return Optional.ofNullable(uri)
      .map(uri1 -> fragmentCache.get(uri1, uri2 -> performRequest(uri2, httpClient, requestHeaders, requestTimeout)
        .filter(response -> {
          if (!HTTP_STATUS_CODES_CACHEABLE.contains(response.statusCode())) {
            logger.error("Unable to load URL {}: Status code {}", uri, response.statusCode());
            return false;
          }

          return true;
        })
        .map(response -> new Fragment(
          response.statusCode(),
          HTTP_STATUS_CODES_SUCCESS.contains(response.statusCode()) ? response.body() : "",
          calculateFragmentExpirationTime(response, config.getFragmentDefaultCacheDuration())
        ))
        .orElse(null)
      ))
      .filter(fragment -> {
        if (!HTTP_STATUS_CODES_SUCCESS.contains(fragment.getStatusCode())) {
          logger.error("Unable to load URL {}: Status code {}", uri, fragment.getStatusCode());
          return false;
        }

        return true;
      })
      .map(Fragment::getContent);
  }

  private Optional<HttpResponse<String>> performRequest(String uri, HttpClient httpClient, Map<String, List<String>> requestHeaders, Duration requestTimeout) {
    try {
      var httpResponse = httpClient.sendAsync(buildHttpRequest(uri, requestHeaders), HttpResponse.BodyHandlers.ofString());
      return Optional.of(httpResponse.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS));
    } catch (TimeoutException e) {
      logger.error("Unable to load URL {} within {}ms", uri, requestTimeout.toMillis());
      return Optional.empty();
    } catch (Exception e) {
      logger.error("Unable to load URL {}: {}", uri, Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
      return Optional.empty();
    }
  }

  private HttpRequest buildHttpRequest(String uri, Map<String, List<String>> requestHeaders) {
    var httpRequestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(uri));
    requestHeaders.forEach((name, values) -> values.forEach(value -> httpRequestBuilder.header(name, value)));
    return httpRequestBuilder
      .GET()
      .build();
  }

  private Instant calculateFragmentExpirationTime(HttpResponse<String> response, Duration fragmentDefaultCacheDuration) {
    var cacheControlDirectives = response.headers()
      .firstValue(HEADER_CACHE_CONTROL)
      .stream()
      .flatMap(value -> Arrays.stream(value.split(",")))
      .map(String::trim)
      .collect(Collectors.toList());

    return getCacheLifetimeBySharedCacheMaxAge(cacheControlDirectives)
      .or(() -> getCacheLifetimeByMaxAge(
        cacheControlDirectives,
        response.headers().firstValue(HEADER_AGE).orElse(null)
      ))
      .or(() -> getCacheLifetimeByExpiresHeader(
        response.headers().firstValue(HEADER_EXPIRES).orElse(null),
        response.headers().firstValue(HEADER_DATE).orElse(null)
      ))
      .or(() -> response.headers().firstValue(HEADER_CACHE_CONTROL).map(cacheControl -> Instant.EPOCH))
      .orElse(Instant.now().plusSeconds(fragmentDefaultCacheDuration.toSeconds()));
  }

  private Optional<Instant> getCacheLifetimeBySharedCacheMaxAge(List<String> cacheControlDirectives) {
    return cacheControlDirectives.stream()
      .filter(directive -> directive.matches("^s-maxage=[1-9][0-9]*$"))
      .findFirst()
      .map(sMaxAge -> sMaxAge.substring("s-maxage=".length()))
      .map(Long::parseLong)
      .map(seconds -> Instant.now().plusSeconds(seconds));
  }

  private Optional<Instant> getCacheLifetimeByMaxAge(List<String> cacheControlDirectives, String ageHeaderValue) {
    try {
      return cacheControlDirectives.stream()
        .filter(directive -> directive.matches("^max-age=[1-9][0-9]*$"))
        .findFirst()
        .map(maxAge -> maxAge.substring("max-age=".length()))
        .map(Long::parseLong)
        .map(seconds -> seconds - Optional.ofNullable(ageHeaderValue)
          .map(Long::parseLong)
          .map(Math::abs)
          .orElse(0L))
        .map(seconds -> Instant.now().plusSeconds(seconds));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private Optional<Instant> getCacheLifetimeByExpiresHeader(String expiresHeaderValue, String dateHeaderValue) {
    try {
      return Optional.ofNullable(expiresHeaderValue)
        .map(value -> value.equals("0") ? Instant.EPOCH : parseHttpDate(value))
        .map(expires -> (dateHeaderValue != null) ? Instant.now().plusMillis(expires.toEpochMilli() - parseHttpDate(dateHeaderValue).toEpochMilli()) : expires);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private Instant parseHttpDate(String httpDate) {
    return ZonedDateTime.parse(httpDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
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
