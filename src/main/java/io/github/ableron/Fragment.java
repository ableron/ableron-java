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

  private static final Pattern SRC_PATTERN = Pattern.compile("\ssrc=\"([^\"]+)\"");

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

  public String getOriginalTag() {
    return originalTag;
  }

  public String getNormalizedTag() {
    return normalizedTag;
  }

  public String getSrc() {
    return src;
  }

  public String getResolvedContent() {
    if (resolvedContent == null) {
      resolvedContent = resolveContent();
    }

    return resolvedContent;
  }

  private String normalizeTag(String tag) {
    return tag.replaceAll("\s*/?>$", ">");
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
