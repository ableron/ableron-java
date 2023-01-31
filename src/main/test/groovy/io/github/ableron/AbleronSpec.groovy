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
}
