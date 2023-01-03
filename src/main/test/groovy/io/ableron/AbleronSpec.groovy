package io.ableron

import spock.lang.Specification

class AbleronSpec extends Specification {

  def "handles property ableron.enabled correctly if no set"() {
    given:
    def ableronConfig = new AbleronConfig()
    def ableron = new Ableron(ableronConfig)

    expect:
    ableron.isEnabled()
  }

  def "handles property ableron.enabled correctly if set"() {
    when:
    def ableronConfig = new AbleronConfig()
    ableronConfig.put(AbleronConfigParams.ENABLED, ableronEnabledPropertyValue)
    def ableron = new Ableron(ableronConfig)

    then:
    ableron.isEnabled() == expectedIsEnabled

    where:
    ableronEnabledPropertyValue | expectedIsEnabled
    "true"                      | true
    "false"                     | false
    "null"                      | false
    "foo"                       | false
  }
}
