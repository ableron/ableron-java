package io.github.ableron

import spock.lang.Specification

class TransclusionProcessorSpec extends Specification {

  def transclusionProcessor = new TransclusionProcessor()

  def "should recognize includes of different forms"() {
    expect:
    transclusionProcessor.findIncludes(content).first().rawInclude == expectedRawInclude

    where:
    content                                                              | expectedRawInclude
    "<ableron-include src=\"test\"/>"                                    | "<ableron-include src=\"test\"/>"
    "<ableron-include src=\"test\" />"                                   | "<ableron-include src=\"test\" />"
    "<ableron-include\nsrc=\"test\" />"                                  | "<ableron-include\nsrc=\"test\" />"
    "<ableron-include\tsrc=\"test\"\t\t/>"                               | "<ableron-include\tsrc=\"test\"\t\t/>"
    "<ableron-include src=\"test\"></ableron-include>"                   | "<ableron-include src=\"test\"></ableron-include>"
    "<ableron-include src=\"test\"> </ableron-include>"                  | "<ableron-include src=\"test\"> </ableron-include>"
    "<ableron-include src=\"test\">foo\nbar\nbaz</ableron-include>"      | "<ableron-include src=\"test\">foo\nbar\nbaz</ableron-include>"
    "<ableron-include src=\">>\"/>"                                      | "<ableron-include src=\">>\"/>"
    "<ableron-include src=\"/>\"/>"                                      | "<ableron-include src=\"/>\"/>"
    "\n<ableron-include src=\"...\"/>\n"                                 | "<ableron-include src=\"...\"/>"
    "<div><ableron-include src=\"...\"/></div>"                          | "<ableron-include src=\"...\"/>"
    "<ableron-include src=\"...\"  fallback-src=\"...\"/>"               | "<ableron-include src=\"...\"  fallback-src=\"...\"/>"
  }

  def "should not recognize includes with invalid format as valid"() {
    expect:
    transclusionProcessor.findIncludes(inputContent).isEmpty()

    where:
    inputContent << [
      "<ableron-include/>",
      "<ableron-include >",
      "<ableron-include src=\"s\">",
      "<ableron-include src=\"s\" b=\"b\">"
    ]
  }

  def "should find all includes in input content"() {
    expect:
    transclusionProcessor.findIncludes("""
      <html>
      <head>
        <ableron-include src="https://foo.bar/baz?test=123" />
        <title>Foo</title>
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456"/>
      </head>
      <body>
        <ableron-include src="https://foo.bar/baz?test=789" fallback-src="https://example.com"/>
        <ableron-include src="https://foo.bar/baz?test=789" fallback-src="https://example.com">fallback</ableron-include>
      </body>
      </html>
    """) == [
      new Include("<ableron-include src=\"https://foo.bar/baz?test=123\" />", Map.of("src", "https://foo.bar/baz?test=123")),
      new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\"/>", Map.of("foo", "bar", "src", "https://foo.bar/baz?test=456")),
      new Include("<ableron-include src=\"https://foo.bar/baz?test=789\" fallback-src=\"https://example.com\"/>", Map.of("src", "https://foo.bar/baz?test=789", "fallback-src", "https://example.com")),
      new Include("<ableron-include src=\"https://foo.bar/baz?test=789\" fallback-src=\"https://example.com\">fallback</ableron-include>", Map.of("src", "https://foo.bar/baz?test=789", "fallback-src", "https://example.com"))
    ] as Set
  }

  def "should treat multiple identical includes as one include"() {
    expect:
    transclusionProcessor.findIncludes("""
      <html>
      <head>
        <ableron-include src="https://foo.bar/baz?test=123"/>
        <ableron-include src="https://foo.bar/baz?test=123"/>
        <title>Foo</title>
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456"></ableron-include>
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456"></ableron-include>
      </head>
      <body>
        <ableron-include src="...">...</ableron-include>
        <ableron-include src="...">...</ableron-include>
      </body>
      </html>
    """) == [
      new Include("<ableron-include src=\"https://foo.bar/baz?test=123\"/>", Map.of("src", "https://foo.bar/baz?test=123")),
      new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\"></ableron-include>", Map.of("src", "https://foo.bar/baz?test=456")),
      new Include("<ableron-include src=\"...\">...</ableron-include>", Map.of("src", "...")),
    ] as Set
  }
}
