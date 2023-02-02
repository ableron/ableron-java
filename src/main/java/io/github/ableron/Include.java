package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Include {

  /**
   * Name of the attribute which contains the source URl to resolve the include to.
   */
  private static final String ATTR_SOURCE = "src";

  /**
   * Name of the attribute which contains the fallback URL to resolve the include to in case the
   * source URL could not be loaded.
   */
  private static final String ATTR_FALLBACK_SOURCE = "fallback-src";

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Raw include string.
   */
  private final String rawInclude;

  /**
   * Source URL the include resolves to.
   */
  private final String src;

  /**
   * Fallback URL to resolve the include to in case the source URL could not be loaded.
   */
  private final String fallbackSrc;

  /**
   * Fallback content to use in case the include could not be resolved.
   */
  private final String fallbackContent;

  /**
   * Resolved include content.
   */
  private String resolvedInclude = null;

  /**
   * Constructs a new Include.
   *
   * @param rawInclude Raw include string
   * @param attributes Attributes of the include tag
   * @param fallbackContent Fallback content to use in case the include could not be resolved
   */
  public Include(@Nonnull String rawInclude, @Nonnull Map<String, String> attributes, String fallbackContent) {
    this.rawInclude = Objects.requireNonNull(rawInclude, "rawInclude must not be null");
    this.src = Objects.requireNonNull(attributes, "attributes must not be null").get(ATTR_SOURCE);
    this.fallbackSrc = attributes.get(ATTR_FALLBACK_SOURCE);
    this.fallbackContent = fallbackContent;
  }

  /**
   * @return The raw include string
   */
  public String getRawInclude() {
    return rawInclude;
  }

  /**
   * @return The source URL the include resolves to
   */
  public String getSrc() {
    return src;
  }

  /**
   * @return The fallback URL to resolve the include to in case the source URL could not be loaded.
   */
  public String getFallbackSrc() {
    return fallbackSrc;
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
   * @param responseCache Cache for HTTP responses
   * @param ableronConfig Global ableron configuration
   * @return Content of the resolved include
   */
  public String resolve(@Nonnull HttpClient httpClient, @Nonnull Cache<String, CachedResponse> responseCache, @Nonnull AbleronConfig ableronConfig) {
    if (resolvedInclude == null) {
      resolvedInclude = load(src, httpClient, responseCache, ableronConfig)
        .or(() -> load(fallbackSrc, httpClient, responseCache, ableronConfig))
        .or(() -> Optional.ofNullable(fallbackContent))
        .orElse("");
    }

    return resolvedInclude;
  }

  private Optional<String> load(String uri, @Nonnull HttpClient httpClient, @Nonnull Cache<String, CachedResponse> responseCache, @Nonnull AbleronConfig ableronConfig) {
    return Optional.ofNullable(uri)
      .map(uri1 -> responseCache.get(uri1, uri2 -> loadUri(uri2, httpClient, ableronConfig)
        .filter(response -> {
          if (response.statusCode() == 200) {
            return true;
          } else {
            logger.error("Unable to load uri {} of ableron-include. Response status was {}", uri, response.statusCode());
            return false;
          }
        })
        .map(httpResponse -> new CachedResponse(httpResponse.body(), calculateResponseCacheExpirationTime(httpResponse, ableronConfig.getFallbackResponseCacheTime())))
        .orElse(null)
      ))
      .map(CachedResponse::getResponseBody);
  }

  private Optional<HttpResponse<String>> loadUri(@Nonnull String uri, @Nonnull HttpClient httpClient, @Nonnull AbleronConfig ableronConfig) {
    try {
      return Optional.of(CompletableFuture.supplyAsync(() -> {
        try {
          return httpClient.send(HttpRequest.newBuilder()
                                   .uri(URI.create(uri))
                                   .GET()
                                   .build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
          logger.error("Unable to load uri {} of ableron-include", uri, e);
          return null;
        }
      }).get(ableronConfig.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS));
    } catch (Exception e) {
      logger.error("Unable to load uri {} of ableron-include", uri, e);
      return Optional.empty();
    }
  }

  private Instant calculateResponseCacheExpirationTime(HttpResponse<String> response, Duration fallbackResponseCacheTime) {

    var cacheControlDirectives = response.headers()
      .firstValue("Cache-Control")
      .stream()
      .flatMap(value -> Arrays.stream(value.split(",")))
      .map(String::trim)
      .collect(Collectors.toList());

    // If the cache is shared and the s-maxage response directive (Section 5.2.2.10) is present, use its value, or
    //    Cache-Control: s-maxage=604800
    var sharedCacheMaxAgeValueStartIndex = "s-maxage=".length();
    var sharedCacheMaxAge = cacheControlDirectives.stream()
      .filter(directive -> directive.matches("^s-maxage=[1-9][0-9]*$"))
      .findFirst()
      .map(directive -> Duration.ofSeconds(Long.parseLong(directive.substring(sharedCacheMaxAgeValueStartIndex))));

    if (sharedCacheMaxAge.isPresent()) {
      return sharedCacheMaxAge
        .map(duration -> Instant.now().plusSeconds(duration.toSeconds()))
        .get();
    }


    // If the max-age response directive (Section 5.2.2.1) is present, use its value, or
    var maxAgeValueStartIndex = "max-age=".length();
    var maxAge = cacheControlDirectives.stream()
      .filter(directive -> directive.matches("^max-age=[1-9][0-9]*$"))
      .findFirst()
      .map(directive -> Duration.ofSeconds(Long.parseLong(directive.substring(maxAgeValueStartIndex))));

    if (maxAge.isPresent()) {
      var age = response.headers()
        .firstValue("Age")
        .map(Long::parseLong)
        .map(Math::abs)
        .orElse(0L);

      return maxAge
        .map(duration -> Instant.now().plusSeconds(duration.toSeconds()).minusSeconds(age))
        .get();
    }



    // If the Expires response header field (Section 5.3) is present, use its value
    // --- minus the value of the Date response header field (using the time the message was received if it is not present, as per Section 6.6.1 of [HTTP]), or
    var formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O").withLocale(Locale.ENGLISH);
    var expires = response.headers()
      .firstValue("Expires")
      .map(value -> value.equals("0") ? Instant.EPOCH : ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());

    if (expires.isPresent()) {
      var date = response.headers()
        .firstValue("Date")
        .map(value -> ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME))
        .map(ChronoZonedDateTime::toInstant);

      if (date.isPresent()) {
        return Instant.now().plusMillis(expires.get().toEpochMilli() - date.get().toEpochMilli());
      }

      return expires.get();
    }


    // if Cache-Control header is set and does not contain max-age, do not cache
    if (response.headers().firstValue("Cache-Control").isPresent()) {
      return Instant.EPOCH;
    }

    return Instant.now().plusSeconds(fallbackResponseCacheTime.toSeconds());

    // When a cache receives a request that can be satisfied by a stored response and that stored response contains a Vary header field (Section 12.5.5 of [HTTP]), the cache MUST NOT use that stored response without revalidation unless all the presented request header fields nominated by that Vary field value match those fields in the original request (i.e., the request that caused the cached response to be stored).
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

    return rawInclude.equals(include.rawInclude);
  }

  @Override
  public int hashCode() {
    return rawInclude.hashCode();
  }
}
