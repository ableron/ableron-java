package io.github.ableron

import spock.lang.Specification

class AbleronSpec extends Specification {

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
