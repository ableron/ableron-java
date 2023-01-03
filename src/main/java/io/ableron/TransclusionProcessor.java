package io.ableron;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransclusionProcessor {

  private static final Pattern FRAGMENT_PATTERN = Pattern.compile("<fragment\s(\"[^\"]*\"|[^\">])*/?>");
  private static final long NANO_2_MILLIS = 1000000L;

  public Set<Fragment> findFragments(String body) {
    Set<Fragment> fragments = new HashSet<>();
    Matcher matcher = FRAGMENT_PATTERN.matcher(body);

    while (matcher.find()) {
      fragments.add(new Fragment(matcher.group(0)));
    }

    return fragments;
  }

  public TransclusionResult applyTransclusion(String body) {
    var startTime = System.nanoTime();
    var transclusionResult = new TransclusionResult();
    var fragments = findFragments(body);

    for (Fragment fragment : fragments) {
      body = body.replace(fragment.getOriginalTag(), fragment.getResolvedContent());
    }

    transclusionResult.setProcessedFragmentsCount(fragments.size());
    transclusionResult.setBody(body);
    transclusionResult.setProcessingTimeMillis((System.nanoTime() - startTime) / NANO_2_MILLIS);
    return transclusionResult;
  }
}
