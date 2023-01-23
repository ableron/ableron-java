package io.github.ableron

import spock.lang.Specification

class IncludeSpec extends Specification {

  def "should normalize include tag"() {
    expect:
    new Include(originalTag).normalizedIncludeTag == expectedNormalizedIncludeTag

    where:
    originalTag                                                  | expectedNormalizedIncludeTag
    "<ableron-include src=\"https://example.com\">"              | "<ableron-include src=\"https://example.com\">"
    "<ableron-include src=\"https://example.com\"/>"             | "<ableron-include src=\"https://example.com\">"
    "<ableron-include src=\"https://example.com\" >"             | "<ableron-include src=\"https://example.com\">"
    "<ableron-include src=\"https://example.com\" />"            | "<ableron-include src=\"https://example.com\">"
    "<ableron-include src=\"https://example.com\"   >"           | "<ableron-include src=\"https://example.com\">"
    "<ableron-include src=\"https://example.com\"   />"          | "<ableron-include src=\"https://example.com\">"
    "<ableron-include src=\"https://example.com\" test=\"/>\"/>" | "<ableron-include src=\"https://example.com\" test=\"/>\">"
  }

  def "should consider two includes with same normalized tag as equal"() {
    when:
    def include1 = new Include("<ableron-include src=\"https://example.com\">")
    def include2 = new Include("<ableron-include src=\"https://example.com\"/>")
    def include3 = new Include("<ableron-include src=\"https://example.com/foo\">")

    then:
    include1 == include2
    include1 != include3
    include2 != include3
  }

  def "should extract include source from tag"() {
    expect:
    new Include(includeTag).src == expectedSource

    where:
    includeTag                                                       | expectedSource
    "<ableron-include _src=\"https://example.com\">"                 | null
    "<ableron-include src=\"https://example.com\">"                  | "https://example.com"
    "<ableron-include   src=\"https://example.com\">"                | "https://example.com"
    "<ableron-include foo=\"foo\" src=\"https://example.com/test\">" | "https://example.com/test"
  }
}
