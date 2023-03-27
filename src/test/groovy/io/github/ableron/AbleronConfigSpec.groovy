package io.github.ableron

import spock.lang.Specification

import java.time.Duration

class AbleronConfigSpec extends Specification {

  def "should have default value for each property"() {
    when:
    def config = AbleronConfig.builder().build()

    then:
    with(config) {
      enabled
      fragmentRequestTimeout == Duration.ofSeconds(3)
      fragmentDefaultCacheDuration == Duration.ofMinutes(5)
      cacheMaxSizeInBytes == 1024 * 1024 * 10
    }
  }

  def "should use values provided via builder"() {
    when:
    def config = AbleronConfig.builder()
      .enabled(false)
      .fragmentRequestTimeout(Duration.ofMillis(200))
      .fragmentDefaultCacheDuration(Duration.ofMinutes(15))
      .cacheMaxSizeInBytes(1024 * 100)
      .build()

    then:
    with(config) {
      !enabled
      fragmentRequestTimeout == Duration.ofMillis(200)
      fragmentDefaultCacheDuration == Duration.ofMinutes(15)
      cacheMaxSizeInBytes == 1024 * 100
    }
  }

  def "should throw exception if fragmentRequestTimeout is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .fragmentRequestTimeout(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "fragmentRequestTimeout must not be null"
  }

  def "should throw exception if fragmentDefaultCacheDuration is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .fragmentDefaultCacheDuration(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "fragmentDefaultCacheDuration must not be null"
  }
}
