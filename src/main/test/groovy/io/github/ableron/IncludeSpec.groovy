package io.github.ableron

import spock.lang.Specification

class IncludeSpec extends Specification {

  def "provided include must not be null"() {
    when:
    new Include(null, Map.of(), "")

    then:
    thrown(NullPointerException)
  }

  def "provided include attributes must not be null"() {
    when:
    new Include("", null, "")

    then:
    thrown(NullPointerException)
  }

  def "provided HTTP client must not be null"() {
    when:
    new Include("", Map.of(), "", null)

    then:
    thrown(NullPointerException)
  }

  def "constructor should set raw include"() {
    when:
    def include = new Include("<ableron-include src=\"https://example.com\"/>", Map.of())

    then:
    include.rawInclude == "<ableron-include src=\"https://example.com\"/>"
  }

  def "constructor should set src attribute"() {
    expect:
    include.src == expectedSrc

    where:
    include                                                                             | expectedSrc
    new Include("<ableron-include src=\"...\"/>", Map.of())                             | null
    new Include("<ableron-include src=\"...\"/>", Map.of("src", "https://example.com")) | "https://example.com"
  }

  def "constructor should set fallback-src attribute"() {
    expect:
    include.fallbackSrc == expectedFallbackSrc

    where:
    include                                                                                      | expectedFallbackSrc
    new Include("<ableron-include src=\"...\"/>", Map.of())                                      | null
    new Include("<ableron-include src=\"...\"/>", Map.of("fallback-src", "https://example.com")) | "https://example.com"
  }

  def "constructor should set fallback content"() {
    expect:
    include.fallbackContent == expectedFallbackContent

    where:
    include                                                             | expectedFallbackContent
    new Include("<ableron-include src=\"...\"/>", Map.of())             | null
    new Include("<ableron-include src=\"...\"/>", Map.of(), "fallback") | "fallback"
  }

  def "should consider include objects with identical include string as equal"() {
    when:
    def include1 = new Include("<ableron-include src=\"...\"></ableron-include>", Map.of())
    def include2 = new Include("<ableron-include src=\"...\"></ableron-include>", Map.of("foo", "bar"))
    def include3 = new Include("<ableron-include src=\"...\"/>", Map.of("test", "test"))

    then:
    include1 == include2
    include1 != include3
    include2 != include3
  }
}
