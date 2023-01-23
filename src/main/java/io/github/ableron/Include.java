package io.github.ableron;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Include {

  private static final Pattern SRC_PATTERN = Pattern.compile("\\ssrc=\"([^\"]+)\"");

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final HttpClient httpClient;
  private final String rawIncludeTag;
  private final String normalizedIncludeTag;
  private final String src;
  private String resolvedContent = null;

  public Include(@Nonnull String tag) {
    this(tag, HttpClient.newHttpClient());
  }

  public Include(@Nonnull String tag, @Nonnull HttpClient httpClient) {
    this.httpClient = httpClient;
    this.rawIncludeTag = tag;
    this.normalizedIncludeTag = normalizeTag(tag);
    this.src = extractSrc(tag);
  }

  /**
   * @return The original tag string as found in the input content
   */
  public String getRawIncludeTag() {
    return rawIncludeTag;
  }

  /**
   * @return The normalized representation of the include tag
   */
  public String getNormalizedIncludeTag() {
    return normalizedIncludeTag;
  }

  /**
   * @return Source URL of the include
   */
  public String getSrc() {
    return src;
  }

  /**
   * @return The content of the include as provided by the source URL
   */
  public String getResolvedContent() {
    if (resolvedContent == null) {
      resolvedContent = resolveContent();
    }

    return resolvedContent;
  }

  /**
   * Normalizes the include tag.
   * <br>
   * Performs the following actions:
   * <ul>
   *   <li>Remove tag-self-closing-slash if available</li>
   * </ul>
   *
   * @param rawIncludeTag The original include tag
   * @return The normalized include tag
   */
  private String normalizeTag(String rawIncludeTag) {
    return rawIncludeTag.replaceAll("\\s*/?>$", ">");
  }

  private String extractSrc(String tag) {
    Matcher matcher = SRC_PATTERN.matcher(tag);
    return matcher.find() ? matcher.group(1) : null;
  }

  private String resolveContent() {
    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(src))
      .GET()
      .build();

    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (IOException | InterruptedException e) {
      logger.error("Unable to resolve Ableron include", e);
      return "<!-- Error loading include -->";
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
    return normalizedIncludeTag.equals(include.normalizedIncludeTag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(normalizedIncludeTag);
  }
}
