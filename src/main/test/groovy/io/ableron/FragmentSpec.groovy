package io.ableron

import spock.lang.Specification

class FragmentSpec extends Specification {

  def "should normalize tag"() {
    expect:
    new Fragment(originalTag).normalizedTag == expectedNormalizedTag

    where:
    originalTag                                           | expectedNormalizedTag
    "<fragment src=\"https://example.com\">"              | "<fragment src=\"https://example.com\">"
    "<fragment src=\"https://example.com\"/>"             | "<fragment src=\"https://example.com\">"
    "<fragment src=\"https://example.com\" >"             | "<fragment src=\"https://example.com\">"
    "<fragment src=\"https://example.com\" />"            | "<fragment src=\"https://example.com\">"
    "<fragment src=\"https://example.com\"   >"           | "<fragment src=\"https://example.com\">"
    "<fragment src=\"https://example.com\"   />"          | "<fragment src=\"https://example.com\">"
    "<fragment src=\"https://example.com\" test=\"/>\"/>" | "<fragment src=\"https://example.com\" test=\"/>\">"
  }

  def "should consider two fragments with same normalized tag as equal"() {
    when:
    def fragment1 = new Fragment("<fragment src=\"https://example.com\">")
    def fragment2 = new Fragment("<fragment src=\"https://example.com\"/>")
    def fragment3 = new Fragment("<fragment src=\"https://example.com/foo\">")

    then:
    fragment1 == fragment2
    fragment1 != fragment3
    fragment2 != fragment3
  }

  def "should extract fragment source from tag"() {
    expect:
    new Fragment(fragmentTag).src == expectedSource

    where:
    fragmentTag                                               | expectedSource
    "<fragment _src=\"https://example.com\">"                 | null
    "<fragment src=\"https://example.com\">"                  | "https://example.com"
    "<fragment   src=\"https://example.com\">"                | "https://example.com"
    "<fragment foo=\"foo\" src=\"https://example.com/test\">" | "https://example.com/test"
  }
}
