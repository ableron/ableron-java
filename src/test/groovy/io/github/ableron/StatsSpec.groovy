package io.github.ableron

import spock.lang.Specification

class StatsSpec extends Specification {

  def "should record cache hit"() {
    given:
    def stats = new Stats()

    expect:
    stats.getTotalCacheHits() == 0
    stats.recordCacheHit()
    stats.getTotalCacheHits() == 1
    stats.recordCacheHit()
    stats.getTotalCacheHits() == 2
  }

  def "should record cache miss"() {
    given:
    def stats = new Stats()

    expect:
    stats.getTotalCacheMisses() == 0
    stats.recordCacheMiss()
    stats.getTotalCacheMisses() == 1
    stats.recordCacheMiss()
    stats.getTotalCacheMisses() == 2
  }

  def "should record successful cache refresh"() {
    given:
    def stats = new Stats()

    expect:
    stats.getTotalSuccessfulCacheRefreshs() == 0
    stats.recordSuccessfulCacheRefresh()
    stats.getTotalSuccessfulCacheRefreshs() == 1
    stats.recordSuccessfulCacheRefresh()
    stats.getTotalSuccessfulCacheRefreshs() == 2
  }

  def "should record failed cache refresh"() {
    given:
    def stats = new Stats()

    expect:
    stats.getTotalFailedCacheRefreshs() == 0
    stats.recordFailedCacheRefresh()
    stats.getTotalFailedCacheRefreshs() == 1
    stats.recordFailedCacheRefresh()
    stats.getTotalFailedCacheRefreshs() == 2
  }
}
