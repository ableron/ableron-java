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
      requestTimeout == Duration.ofSeconds(5)
      defaultResponseCacheDuration == Duration.ofMinutes(5)
      maxCacheSizeInBytes == 1024 * 1024 * 10
    }
  }

  def "should use values provided via builder"() {
    when:
    def config = AbleronConfig.builder()
      .enabled(false)
      .requestTimeout(Duration.ofMillis(200))
      .defaultResponseCacheDuration(Duration.ofMinutes(15))
      .maxCacheSizeInBytes(1024 * 100)
      .build()

    then:
    with(config) {
      !enabled
      requestTimeout == Duration.ofMillis(200)
      defaultResponseCacheDuration == Duration.ofMinutes(15)
      maxCacheSizeInBytes == 1024 * 100
    }
  }

  def "should throw exception if requestTimeout is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .requestTimeout(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "requestTimeout must not be null"
  }

  def "should throw exception if defaultResponseCacheDuration is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .defaultResponseCacheDuration(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "defaultResponseCacheDuration must not be null"
  }
}
