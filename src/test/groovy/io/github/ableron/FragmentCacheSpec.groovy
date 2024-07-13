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

  def "should handle cache refresh failure"() {
    given:
    fragmentCache.set('cacheKey', new Fragment('url', 200, 'fragment', Instant.now().plusSeconds(1), [:]), () -> null)

    when:
    sleep(1200)

    then:
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should use fragment expiration time as cache entry ttl"() {
    given:
    fragmentCache.set('cacheKey', new Fragment('url', 200, 'fragment', Instant.now().plusSeconds(1), [:]))

    expect:
    fragmentCache.get('cacheKey').get().content == 'fragment'

    when:
    sleep(1010)

    then:
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should not cache expired fragments"() {
    when:
    fragmentCache.set('cacheKey', new Fragment('url', 200, 'fragment', Instant.now(), [:]))

    then:
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should clear cache"() {
    given:
    def newFragment = () -> new Fragment('url', 200, 'fragment', Instant.now().plusMillis(200), [:])
    fragmentCache.set('cacheKey', newFragment(), () -> newFragment())

    expect:
    fragmentCache.get('cacheKey').isPresent()
    sleep(300)
    fragmentCache.get('cacheKey').isPresent()
    fragmentCache.clear()
    fragmentCache.get('cacheKey').isEmpty()
    sleep(300)
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should not auto refresh cached fragment when status code is not cacheable"() {
    given:
    def newFragment = (int status) -> new Fragment('url', status, 'fragment', Instant.now().plusMillis(300), [:])
    fragmentCache.set('cacheKey', newFragment(200), () -> newFragment(500))

    expect:
    fragmentCache.get('cacheKey').isPresent()
    sleep(300)
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should not auto refresh cached fragment when fragment is marked as not cacheable"() {
    given:
    fragmentCache.set('cacheKey', new Fragment('url', 200, 'fragment', Instant.now().plusMillis(250), [:]), () ->
      new Fragment(200, 'fragment')
    )

    expect:
    fragmentCache.get('cacheKey').isPresent()
    sleep(300)
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should continuously refresh cache"() {
    given:
    def newFragment = () -> new Fragment('url', 200, 'fragment', Instant.now().plusMillis(200), [:])
    fragmentCache.set('cacheKey', newFragment(), () -> newFragment())

    expect:
    fragmentCache.get('cacheKey').isPresent()
    sleep(250)
    fragmentCache.get('cacheKey').isPresent()
    sleep(250)
    fragmentCache.get('cacheKey').isPresent()
    sleep(250)
    fragmentCache.get('cacheKey').isPresent()
  }

  def "should retry to refresh cache on failure with max 3 attempts"() {
    given:
    def counter = 0
    def newFragment = () -> {
      counter++

      switch (counter) {
        case 1:
        case 4:
        case 8:
          return new Fragment('url', 200, 'fragment', Instant.now().plusSeconds(1), [:])
        default:
          return null
      }
    }
    fragmentCache.set('cacheKey', newFragment(), () -> newFragment())

    expect:
    fragmentCache.get('cacheKey').isPresent()
    sleep(1100)
    fragmentCache.get('cacheKey').isEmpty()
    sleep(1000)
    fragmentCache.get('cacheKey').isEmpty()
    sleep(1000)
    fragmentCache.get('cacheKey').isPresent()

    sleep(1100)
    fragmentCache.get('cacheKey').isEmpty()
    sleep(1000)
    fragmentCache.get('cacheKey').isEmpty()
    sleep(1000)
    fragmentCache.get('cacheKey').isEmpty()
    sleep(1000)
    fragmentCache.get('cacheKey').isEmpty()
  }

  def "should not pollute stats when refreshing cache"() {
    given:
    def newFragment = () -> new Fragment('url', 200, 'fragment', Instant.now().plusMillis(200), [:])
    def fragmentCache = new FragmentCache(1024, true)
    fragmentCache.set('cacheKey', newFragment(), () -> newFragment())

    expect:
    fragmentCache.stats().hitCount() == 0
    fragmentCache.stats().missCount() == 0
    fragmentCache.stats().refreshSuccessCount() == 0
    fragmentCache.stats().refreshFailureCount() == 0
    sleep(750)
    fragmentCache.stats().hitCount() == 0
    fragmentCache.stats().missCount() == 0
    fragmentCache.stats().refreshSuccessCount() == 4
    fragmentCache.stats().refreshFailureCount() == 0
  }
}
