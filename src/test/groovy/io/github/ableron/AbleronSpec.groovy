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

  def "should perform transclusion only if enabled"() {
    when:
    def result = new Ableron(AbleronConfig.builder().enabled(enabled).build())
      .resolveIncludes("<ableron-include src=\"https://foo-bar\">fallback</ableron-include>", [:])

    then:
    result.content == expectedContent
    result.processedIncludesCount == expectedProcessedIncludesCount

    where:
    enabled | expectedContent                                                       | expectedProcessedIncludesCount
    true    | "fallback"                                                            | 1
    false   | "<ableron-include src=\"https://foo-bar\">fallback</ableron-include>" | 0
  }
}
