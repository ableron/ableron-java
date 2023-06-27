package io.github.ableron;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class HttpUtil {

  private static final String HEADER_AGE = "Age";
  private static final String HEADER_CACHE_CONTROL = "Cache-Control";
  private static final String HEADER_DATE = "Date";
  private static final String HEADER_EXPIRES = "Expires";

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
      return Optional.empty();
    }
  }

  private static Optional<Instant> getCacheLifetimeByExpiresHeader(String expiresHeaderValue, String dateHeaderValue) {
    try {
      return Optional.ofNullable(expiresHeaderValue)
        .map(value -> value.equals("0") ? Instant.EPOCH : parseHttpDate(value))
        .map(expires -> (dateHeaderValue != null) ? Instant.now().plusMillis(expires.toEpochMilli() - parseHttpDate(dateHeaderValue).toEpochMilli()) : expires);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private static Instant parseHttpDate(String httpDate) {
    return ZonedDateTime.parse(httpDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
  }
}
