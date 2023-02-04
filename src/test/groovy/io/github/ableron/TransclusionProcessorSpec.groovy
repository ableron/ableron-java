package io.github.ableron

import spock.lang.Shared
import spock.lang.Specification

class TransclusionProcessorSpec extends Specification {

  @Shared
  def transclusionProcessor = new TransclusionProcessor()

  def "should recognize includes of different forms"() {
    expect:
    transclusionProcessor.findIncludes(content).first().rawInclude == expectedRawInclude

    where:
    content                                                         | expectedRawInclude
    "<ableron-include src=\"test\"/>"                               | "<ableron-include src=\"test\"/>"
    "<ableron-include src=\"test\" />"                              | "<ableron-include src=\"test\" />"
    "<ableron-include\nsrc=\"test\" />"                             | "<ableron-include\nsrc=\"test\" />"
    "<ableron-include\tsrc=\"test\"\t\t/>"                          | "<ableron-include\tsrc=\"test\"\t\t/>"
    "<ableron-include src=\"test\"></ableron-include>"              | "<ableron-include src=\"test\"></ableron-include>"
    "<ableron-include src=\"test\"> </ableron-include>"             | "<ableron-include src=\"test\"> </ableron-include>"
    "<ableron-include src=\"test\">foo\nbar\nbaz</ableron-include>" | "<ableron-include src=\"test\">foo\nbar\nbaz</ableron-include>"
    "<ableron-include src=\">>\"/>"                                 | "<ableron-include src=\">>\"/>"
    "<ableron-include src=\"/>\"/>"                                 | "<ableron-include src=\"/>\"/>"
    "\n<ableron-include src=\"...\"/>\n"                            | "<ableron-include src=\"...\"/>"
    "<div><ableron-include src=\"...\"/></div>"                     | "<ableron-include src=\"...\"/>"
    "<ableron-include src=\"...\"  fallback-src=\"...\"/>"          | "<ableron-include src=\"...\"  fallback-src=\"...\"/>"
  }

  def "should not recognize includes with invalid format"() {
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

  def "should parse include attributes"() {
    when:
    def include = transclusionProcessor.findIncludes(includeTag).first()

    then:
    include.src == expectedSource
    include.fallbackSrc == expectedFallbackSource

    where:
    includeTag                                                    | expectedSource        | expectedFallbackSource
    "<ableron-include src=\"https://example.com\"/>"              | "https://example.com" | null
    "<ableron-include  src=\"https://example.com\"/>"             | "https://example.com" | null
    "<ableron-include -src=\"https://example.com\"/>"             | null                  | null
    "<ableron-include _src=\"https://example.com\"/>"             | null                  | null
    "<ableron-include 0src=\"https://example.com\"/>"             | null                  | null
    "<ableron-include foo=\"\" src=\"https://example.com\"/>"     | "https://example.com" | null
    "<ableron-include fallback-src=\"fallback\" src=\"source\"/>" | "source"              | "fallback"
    "<ableron-include src=\"source\" fallback-src=\"fallback\"/>" | "source"              | "fallback"
    "<ableron-include src=\">\" fallback-src=\"/>\"/>"            | ">"                   | "/>"
  }

  def "should accept line breaks in include tag attributes"() {
    when:
    def include = transclusionProcessor.findIncludes("""
      <ableron-include
          src="https://foo.bar/fragment-1"
          fallback-src="https://foo.bar/fragment-1-fallback"/>
      """).first()

    then:
    include.src == "https://foo.bar/fragment-1"
    include.fallbackSrc == "https://foo.bar/fragment-1-fallback"
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
      new Include("<ableron-include src=\"https://foo.bar/baz?test=123\" />", Map.of(), null),
      new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\"/>", Map.of(), null),
      new Include("<ableron-include src=\"https://foo.bar/baz?test=789\" fallback-src=\"https://example.com\"/>", Map.of(), null),
      new Include("<ableron-include src=\"https://foo.bar/baz?test=789\" fallback-src=\"https://example.com\">fallback</ableron-include>", Map.of(), null)
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
      new Include("<ableron-include src=\"https://foo.bar/baz?test=123\"/>", Map.of(), null),
      new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\"></ableron-include>", Map.of(), null),
      new Include("<ableron-include src=\"...\">...</ableron-include>", Map.of(), null)
    ] as Set
  }

  def "should perform search for includes in big input string"() {
    given:
    def randomStringWithoutIncludes = new Random().ints(32, 127)
      .limit(512 * 1024)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString()
    def randomStringWithIncludeAtTheBeginning = "<ableron-include />" + randomStringWithoutIncludes
    def randomStringWithIncludeAtTheEnd = randomStringWithoutIncludes + "<ableron-include />"
    def randomStringWithIncludeAtTheMiddle = randomStringWithoutIncludes + "<ableron-include />" + randomStringWithoutIncludes

    expect:
    transclusionProcessor.findIncludes(randomStringWithoutIncludes).size() == 0
    transclusionProcessor.findIncludes(randomStringWithIncludeAtTheBeginning).size() == 1
    transclusionProcessor.findIncludes(randomStringWithIncludeAtTheEnd).size() == 1
    transclusionProcessor.findIncludes(randomStringWithIncludeAtTheMiddle).size() == 1
  }

  def "should populate TransclusionResult"() {
    when:
    def result = transclusionProcessor.resolveIncludes("""
      <html>
      <head>
        <ableron-include src="https://foo.bar/baz?test=123"><!-- failed loading 1st include --></ableron-include>
        <title>Foo</title>
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456"><!-- failed loading 2nd include --></ableron-include>
      </head>
      <body>
        <ableron-include src="https://foo.bar/baz?test=789"><!-- failed loading 3rd include --></ableron-include>
      </body>
      </html>
    """)

    then:
    result.processedIncludesCount == 3
    result.processingTimeMillis >= 1
    result.content == """
      <html>
      <head>
        <!-- failed loading 1st include -->
        <title>Foo</title>
        <!-- failed loading 2nd include -->
      </head>
      <body>
        <!-- failed loading 3rd include -->
      </body>
      </html>
    """
  }

  def "should replace identical includes"() {
    when:
    def result = transclusionProcessor.resolveIncludes("""
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #2 --></ableron-include>
    """)

    then:
    result.content == """
      <!-- #1 -->
      <!-- #1 -->
      <!-- #1 -->
      <!-- #2 -->
    """
  }
}
