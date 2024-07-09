package io.github.ableron

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class FragmentCacheSpec extends Specification {

  def fragmentCache = new TransclusionProcessor(AbleronConfig.builder()
    .fragmentRequestTimeout(Duration.ofSeconds(1))
    .cacheAutoRefreshEnabled(true)
    .build()).getFragmentCache()

  def "should have limited capacity to prevent out of memory problems"() {
    when:
    for (int i = 0; i <= 1024 * 10 + 10; i++) {
      fragmentCache.set("" + i, new Fragment(200, "a".repeat(1024)))
    }

    then:
    fragmentCache.estimatedSize() >= 1024 * 10
    fragmentCache.estimatedSize() < 1024 * 10 + 4
  }

  def "should not auto refresh fragments if disabled"() {
    given:
    def fragmentCache = new TransclusionProcessor(AbleronConfig.builder()
      .cacheAutoRefreshEnabled(false)
      .build()).getFragmentCache()
    def newFragment = () -> new Fragment('url', 200, 'fragment', Instant.now().plusSeconds(1), [:])
    fragmentCache.set('cacheKey', newFragment(), () -> newFragment())

    when:
    sleep(1200)

    then:
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should auto refresh fragments if enabled"() {
    given:
    def newFragment = () -> new Fragment('url', 200, 'fragment', Instant.now().plusSeconds(1), [:])
    fragmentCache.set('cacheKey', newFragment(), () -> newFragment())

    when:
    sleep(1200)

    then:
    fragmentCache.get('cacheKey').isPresent()
  }
}
