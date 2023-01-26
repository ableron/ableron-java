package io.github.ableron

import spock.lang.Specification

class AbleronSpec extends Specification {

  def "should throw exception if ableronConfig is not provided"() {
    when:
    new Ableron(null)

    then:
    def exception = thrown(NullPointerException)
    exception.message == "ableronConfig must not be null"
  }

  def "handles property ableron.enabled correctly if no set explicitly"() {
    given:
    def ableronConfig = new AbleronConfig()
    def ableron = new Ableron(ableronConfig)

    expect:
    ableron.isEnabled()
  }

  def "handles property ableron.enabled correctly if set explicitly"() {
    given:
    def ableronConfig = new AbleronConfig()
    def ableron = new Ableron(ableronConfig)

    when:
    ableronConfig.put(AbleronConfigParams.ENABLED, ableronEnabledPropertyValue)

    then:
    ableron.isEnabled() == expectedIsEnabled

    where:
    ableronEnabledPropertyValue | expectedIsEnabled
    "true"                      | true
    "false"                     | false
    "on"                        | false
    "off"                       | false
    "null"                      | false
    "foo"                       | false
  }
}
