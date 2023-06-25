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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
   * Recorded response of the errored primary fragment.
   */
  private Fragment erroredPrimaryFragment = null;

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
    this.rawAttributes = Optional.ofNullable(rawAttributes).orElseGet(Map::of);
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
   * @param fragmentRequestHeaders Request headers which are passed to fragment requests if allowed by config
   * @param fragmentCache Cache for fragments
   * @param config Global ableron configuration
   * @param resolveThreadPool Thread pool to use for resolving
   * @return The fragment the include has been resolved to
   */
  public CompletableFuture<Fragment> resolve(HttpClient httpClient, Map<String, List<String>> fragmentRequestHeaders, Cache<String, Fragment> fragmentCache, AbleronConfig config, ExecutorService resolveThreadPool) {
    var filteredFragmentRequestHeaders = filterHeaders(fragmentRequestHeaders, config.getFragmentRequestHeadersToPass());
    erroredPrimaryFragment = null;

    return CompletableFuture.supplyAsync(
      () -> load(src, httpClient, filteredFragmentRequestHeaders, fragmentCache, config, getRequestTimeout(srcTimeout, config))
        .or(() -> load(fallbackSrc, httpClient, filteredFragmentRequestHeaders, fragmentCache, config, getRequestTimeout(fallbackSrcTimeout, config)))
        .or(() -> Optional.ofNullable(erroredPrimaryFragment))
        .or(() -> Optional.ofNullable(fallbackContent).map(content -> new Fragment(200, content)))
        .orElseGet(() -> new Fragment(200, "")), resolveThreadPool);
  }

  private Optional<Fragment> load(String uri, HttpClient httpClient, Map<String, List<String>> requestHeaders, Cache<String, Fragment> fragmentCache, AbleronConfig config, Duration requestTimeout) {
    return Optional.ofNullable(uri)
      .map(uri1 -> fragmentCache.get(uri1, uri2 -> performRequest(uri2, httpClient, requestHeaders, requestTimeout)
        .filter(response -> {
          if (!isHttpStatusCacheable(response.statusCode())) {
            logger.error("Fragment URL {} returned status code {}", uri, response.statusCode());
            recordErroredPrimaryFragment(new Fragment(
              response.statusCode(),
              response.body(),
              Instant.EPOCH,
              filterHeaders(response.headers().map(), config.getPrimaryFragmentResponseHeadersToPass())
            ));
            return false;
          }

          return true;
        })
        .map(response -> new Fragment(
          response.statusCode(),
          response.body(),
          HttpUtil.calculateResponseExpirationTime(response.headers()),
          filterHeaders(response.headers().map(), config.getPrimaryFragmentResponseHeadersToPass())
        ))
        .orElse(null)
      ))
      .filter(fragment -> {
        if (!HTTP_STATUS_CODES_SUCCESS.contains(fragment.getStatusCode())) {
          logger.error("Fragment URL {} returned status code {}", uri, fragment.getStatusCode());
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
