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
      connectTimeout == Duration.ofSeconds(2)
      readTimeout == Duration.ofSeconds(5)
    }
  }

  def "should use values provided via builder"() {
    when:
    def config = AbleronConfig.builder()
      .enabled(false)
      .connectTimeout(Duration.ofSeconds(3))
      .readTimeout(Duration.ofMillis(500))
      .build()

    then:
    with(config) {
      !enabled
      connectTimeout == Duration.ofSeconds(3)
      readTimeout == Duration.ofMillis(500)
    }
  }

  def "should throw exception if connectTimeout is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .connectTimeout(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "connectTimeout must not be null"
  }

  def "should throw exception if readTimeout is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .readTimeout(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "readTimeout must not be null"
  }
}
