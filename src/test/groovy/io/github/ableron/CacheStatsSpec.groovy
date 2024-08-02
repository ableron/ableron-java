package io.github.ableron

import spock.lang.Specification

class CacheStatsSpec extends Specification {

  def "should record cache hit"() {
    given:
    def stats = new CacheStats()

    expect:
    stats.hitCount() == 0
    stats.recordHit()
    stats.hitCount() == 1
    stats.recordHit()
    stats.hitCount() == 2
  }

  def "should record cache miss"() {
    given:
    def stats = new CacheStats()

    expect:
    stats.missCount() == 0
    stats.recordMiss()
    stats.missCount() == 1
    stats.recordMiss()
    stats.missCount() == 2
  }

  def "should record successful cache refresh"() {
    given:
    def stats = new CacheStats()

    expect:
    stats.refreshSuccessCount() == 0
    stats.recordRefreshSuccess()
    stats.refreshSuccessCount() == 1
    stats.recordRefreshSuccess()
    stats.refreshSuccessCount() == 2
  }

  def "should record failed cache refresh"() {
    given:
    def stats = new CacheStats()

    expect:
    stats.refreshFailureCount() == 0
    stats.recordRefreshFailure()
    stats.refreshFailureCount() == 1
    stats.recordRefreshFailure()
    stats.refreshFailureCount() == 2
  }
}
