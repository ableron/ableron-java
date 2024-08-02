package io.github.ableron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class HttpUtil {

  private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

  /**
   * HTTP status codes indicating cacheable responses.
   *
   * @link <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.1">RFC 9110 Section 15.1. Overview of Status Codes</a>
   */
  public static final List<Integer> HTTP_STATUS_CODES_CACHEABLE = Arrays.asList(
    200, 203, 204, 206,
    300,
    404, 405, 410, 414,
    501
  );

  private static final String HEADER_AGE = "Age";
  private static final String HEADER_CACHE_CONTROL = "Cache-Control";
  private static final String HEADER_DATE = "Date";
  private static final String HEADER_EXPIRES = "Expires";

  private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)\\bcharset\\s*=\\s*\"?([^\\s;\"]+)");

  public static Optional<HttpResponse<byte[]>> loadUrl(String uri, HttpClient httpClient, Map<String, List<String>> requestHeaders, Duration requestTimeout) {
    try {
      logger.debug("[Ableron] Loading {} with timeout {}ms", uri, requestTimeout.toMillis());
      var httpRequestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .header("Accept-Encoding", "gzip");
      requestHeaders.forEach((name, values) -> values.forEach(value -> httpRequestBuilder.header(name, value)));
      var httpResponse = httpClient.sendAsync(httpRequestBuilder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
      return Optional.of(httpResponse.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS));
    } catch (TimeoutException e) {
      logger.error("[Ableron] Unable to load {}: {}ms timeout exceeded", uri, requestTimeout.toMillis());
      return Optional.empty();
    } catch (Exception e) {
      logger.error("[Ableron] Unable to load {}: {}", uri, Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
      return Optional.empty();
    }
  }

  public static Instant calculateResponseExpirationTime(Map<String, List<String>> responseHeaders) {
    var headers = HttpHeaders.of(responseHeaders, (name, value) -> true);
    var cacheControlDirectives = headers
      .firstValue(HEADER_CACHE_CONTROL)
      .stream()
      .flatMap(value -> Arrays.stream(value.toLowerCase().split(",")))
      .map(String::trim)
      .collect(Collectors.toList());

    return getCacheLifetimeBySharedCacheMaxAge(cacheControlDirectives)
      .or(() -> getCacheLifetimeByMaxAge(
        cacheControlDirectives,
        headers.firstValue(HEADER_AGE).orElse(null)
      ))
      .or(() -> getCacheLifetimeByExpiresHeader(
        headers.firstValue(HEADER_EXPIRES).orElse(null),
        headers.firstValue(HEADER_DATE).orElse(null)
      ))
      .orElse(Instant.EPOCH);
  }

  private static Optional<Instant> getCacheLifetimeBySharedCacheMaxAge(List<String> cacheControlDirectives) {
    return cacheControlDirectives.stream()
      .filter(directive -> directive.matches("^s-maxage=[1-9][0-9]*$"))
      .findFirst()
      .map(sMaxAge -> sMaxAge.substring("s-maxage=".length()))
      .map(Long::parseLong)
      .map(seconds -> Instant.now().plusSeconds(seconds));
  }

  private static Optional<Instant> getCacheLifetimeByMaxAge(List<String> cacheControlDirectives, String ageHeaderValue) {
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
      logger.error("[Ableron] Unable to calculate cache lifetime by max-age", e);
      return Optional.empty();
    }
  }

  private static Optional<Instant> getCacheLifetimeByExpiresHeader(String expiresHeaderValue, String dateHeaderValue) {
    try {
      return Optional.ofNullable(expiresHeaderValue)
        .map(value -> value.equals("0") ? Instant.EPOCH : parseHttpDate(value))
        .map(expires -> (dateHeaderValue != null) ? Instant.now().plusMillis(expires.toEpochMilli() - parseHttpDate(dateHeaderValue).toEpochMilli()) : expires);
    } catch (Exception e) {
      logger.error("[Ableron] Unable to calculate cache lifetime by Expires header", e);
      return Optional.empty();
    }
  }

  private static Instant parseHttpDate(String httpDate) {
    return ZonedDateTime.parse(httpDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
  }

  /**
   * Returns the body of the given http response as string.
   * Handles gzip compressed body automatically.
   */
  public static String getResponseBodyAsString(HttpResponse<byte[]> httpResponse) {
    var contentEncoding = httpResponse.headers()
      .firstValue("Content-Encoding")
      .orElse("plaintext");
    var charset = charsetFrom(httpResponse.headers());

    switch (contentEncoding) {
      case "plaintext":
        return new String(httpResponse.body(), charset);
      case "gzip":
        try {
          return new String(new GZIPInputStream(new ByteArrayInputStream(httpResponse.body())).readAllBytes(), charset);
        } catch (IOException e) {
          logger.error("[Ableron] Unable to decode response body with content encoding 'gzip'", e);
          return "";
        }
      default:
        logger.error("[Ableron] Unknown content encoding '{}'. Discarding response body", contentEncoding);
        return "";
    }
  }

  /**
   * Returns the Charset from the Content-Encoding header as
   * jdk.internal.net.http.common.Utils.charsetFrom() is unfortunately not public API.
   * Defaults to UTF-8.
   */
  public static Charset charsetFrom(HttpHeaders headers) {
    var contentType = headers.firstValue("Content-Type")
      .orElse("text/html; charset=utf-8");
    var contentTypeMatcher = CHARSET_PATTERN.matcher(contentType);

    if (contentTypeMatcher.find()) {
      try {
        return Charset.forName(contentTypeMatcher.group(1).trim());
      } catch (UnsupportedCharsetException e) {
        logger.debug("[Ableron] Unknown charset '{}' found in Content-Type header. Falling back to UTF-8", e.getCharsetName());
      }
    }

    return StandardCharsets.UTF_8;
  }
}
