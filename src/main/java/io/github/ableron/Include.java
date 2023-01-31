package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
  public String resolve(@Nonnull HttpClient httpClient, @Nonnull Cache<String, io.github.ableron.HttpResponse> responseCache, @Nonnull AbleronConfig ableronConfig) {
    if (resolvedInclude == null) {
      resolvedInclude = load(src, httpClient, responseCache, ableronConfig)
        .or(() -> load(fallbackSrc, httpClient, responseCache, ableronConfig))
        .or(() -> Optional.ofNullable(fallbackContent))
        .orElse("");
    }

    return resolvedInclude;
  }

  private Optional<String> load(String uri, @Nonnull HttpClient httpClient, @Nonnull Cache<String, io.github.ableron.HttpResponse> responseCache, @Nonnull AbleronConfig ableronConfig) {
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
        //TODO: Use correct value for lifetime based on Cache-Control, Max-Age and Expires headers
        .map(httpResponse -> new io.github.ableron.HttpResponse(httpResponse.body(), Instant.now().plus(5, ChronoUnit.MINUTES)))
        .orElse(null)
      ))
      .map(io.github.ableron.HttpResponse::getResponseBody);
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
