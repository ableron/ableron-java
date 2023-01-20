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

public class Fragment {

  private static final Pattern SRC_PATTERN = Pattern.compile("\\ssrc=\"([^\"]+)\"");

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final HttpClient httpClient;
  private final String originalTag;
  private final String normalizedTag;
  private final String src;
  private String resolvedContent = null;

  public Fragment(@Nonnull String tag) {
    this(tag, HttpClient.newHttpClient());
  }

  public Fragment(@Nonnull String tag, @Nonnull HttpClient httpClient) {
    this.httpClient = httpClient;
    this.originalTag = tag;
    this.normalizedTag = normalizeTag(tag);
    this.src = extractSrc(tag);
  }

  /**
   * @return The original tag string as found in the input content
   */
  public String getOriginalTag() {
    return originalTag;
  }

  /**
   * @return The normalized version of the fragment tag
   */
  public String getNormalizedTag() {
    return normalizedTag;
  }

  /**
   * @return The source URL of the actual fragment content the fragment tag should be replaced with
   */
  public String getSrc() {
    return src;
  }

  /**
   * @return The content of the fragment as provided by the source URL
   */
  public String getResolvedContent() {
    if (resolvedContent == null) {
      resolvedContent = resolveContent();
    }

    return resolvedContent;
  }

  /**
   * Normalizes the fragment tag.
   * <br>
   * Performs the following actions:
   * <ul>
   *   <li>Remove tag-self-closing-slash if available</li>
   * </ul>
   *
   * @param tag The original fragment tag
   * @return The normalized fragment tag
   */
  private String normalizeTag(String tag) {
    return tag.replaceAll("\\s*/?>$", ">");
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
      logger.error("Unable to resolve fragment content", e);
      return "<!-- Error loading fragment -->";
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
    Fragment fragment = (Fragment) o;
    return normalizedTag.equals(fragment.normalizedTag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(normalizedTag);
  }
}
