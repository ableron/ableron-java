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
      fallbackResponseCacheTime == Duration.ofMinutes(5)
    }
  }

  def "should use values provided via builder"() {
    when:
    def config = AbleronConfig.builder()
      .enabled(false)
      .requestTimeout(Duration.ofMillis(200))
      .fallbackResponseCacheTime(Duration.ofMinutes(15))
      .build()

    then:
    with(config) {
      !enabled
      requestTimeout == Duration.ofMillis(200)
      fallbackResponseCacheTime == Duration.ofMinutes(15)
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

  def "should throw exception if fallbackResponseCacheTime is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .fallbackResponseCacheTime(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "fallbackResponseCacheTime must not be null"
  }
}
