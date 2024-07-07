package io.github.ableron

import spock.lang.Specification

import java.time.Duration

class FragmentCacheSpec extends Specification {

  def cache = new TransclusionProcessor(AbleronConfig.builder()
    .fragmentRequestTimeout(Duration.ofSeconds(1))
    .cacheAutoRefreshEnabled(true)
    .build()).getFragmentCache()

  def "should have limited capacity to prevent out of memory problems"() {
    when:
    for (int i = 0; i < 1024 * 11; i++) {
      cache.set("" + i, new Fragment(200, "a".repeat(1024)))
    }

    then:
    cache.estimatedSize() <= 1024 * 1024 * 10
  }
}
