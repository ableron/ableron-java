package io.github.ableron;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransclusionProcessor {

  private static final Pattern FRAGMENT_PATTERN = Pattern.compile("<fragment\s(\"[^\"]*\"|[^\">])*/?>");
  private static final long NANO_2_MILLIS = 1000000L;

  /**
   * Finds all fragments in the given content.
   *
   * @param content The content to find the fragments in
   * @return The found fragments
   */
  public Set<Fragment> findFragments(String content) {
    Set<Fragment> fragments = new HashSet<>();
    Matcher matcher = FRAGMENT_PATTERN.matcher(content);

    while (matcher.find()) {
      fragments.add(new Fragment(matcher.group(0)));
    }

    return fragments;
  }

  /**
   * Replaces all fragments in the given content.
   *
   * @param content The content to replace the fragments of
   * @return The content with resolved fragments
   */
  public TransclusionResult applyTransclusion(String content) {
    var startTime = System.nanoTime();
    var transclusionResult = new TransclusionResult();
    var fragments = findFragments(content);

    for (Fragment fragment : fragments) {
      content = content.replace(fragment.getOriginalTag(), fragment.getResolvedContent());
    }

    transclusionResult.setProcessedFragmentsCount(fragments.size());
    transclusionResult.setContent(content);
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    return transclusionResult;
  }
}
