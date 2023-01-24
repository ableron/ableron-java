package io.github.ableron;

import jakarta.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Include {

  /**
   * Name of the attribute which contains the source URl to resolve the include to.
   */
  private static final String ATTR_SOURCE = "src";

  /**
   * Name of the attribute which contains the fallback URl to resolve the include to in case the
   * source URl could not be resolved.
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
   * Fallback URL to resolve the include to in case the source URl could not be resolved.
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
   * HTTP client used to resolve this include.
   */
  private final HttpClient httpClient;

  /**
   * Constructs a new Include.
   *
   * @param include Raw include string
   * @param attributes Attributes of the include tag
   * @param fallbackContent Fallback content to use in case the include could not be resolved
   * @param httpClient HTTP client used to resolve the includes
   */
  public Include(@Nonnull String include, @Nonnull Map<String, String> attributes, String fallbackContent, @Nonnull HttpClient httpClient) {
    this.rawInclude = Objects.requireNonNull(include, "include must not be null");
    this.src = Objects.requireNonNull(attributes, "attributes must not be null").get(ATTR_SOURCE);
    this.fallbackSrc = attributes.get(ATTR_FALLBACK_SOURCE);
    this.fallbackContent = fallbackContent;
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
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
   * @return The fallback URL to resolve the include to in case the source URl could not be resolved.
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
   * @return The resolved include
   */
  public String getResolvedInclude() {
    if (resolvedInclude == null) {
      resolvedInclude = resolveInclude();
    }

    return resolvedInclude;
  }

  private String resolveInclude() {
    return loadSrc()
      .or(this::loadFallbackSrc)
      .orElse(fallbackContent);
  }

  private Optional<String> loadSrc() {
    return (src == null) ? Optional.empty() : loadUri(src);
  }

  private Optional<String> loadFallbackSrc() {
    return (fallbackSrc == null) ? Optional.empty() : loadUri(fallbackSrc);
  }

  private Optional<String> loadUri(String uri) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .GET()
        .build();

      return Optional.of(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
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
