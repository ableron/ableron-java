package io.github.ableron

import spock.lang.Specification

class TransclusionProcessorSpec extends Specification {

  def transclusionProcessor = new TransclusionProcessor()

  def "should have tolerant include matching pattern"() {
    expect:
    transclusionProcessor.findIncludes(inputContent).first().rawIncludeTag == expectedRawIcludeTag

    where:
    inputContent                                                         | expectedRawIcludeTag
    "<ableron-include src=\"https://example.com\">"                      | "<ableron-include src=\"https://example.com\">"
    "<div><ableron-include src=\"https://example.com\"></div>"           | "<ableron-include src=\"https://example.com\">"
    "<div><ableron-include src=\"https://example.com\" foo=\">\"></div>" | "<ableron-include src=\"https://example.com\" foo=\">\">"
    "<ableron-include src=\"test\">"                                     | "<ableron-include src=\"test\">"
    "<ableron-include src=\"test\"/>"                                    | "<ableron-include src=\"test\"/>"
    "<ableron-include src=\"test\" />"                                   | "<ableron-include src=\"test\" />"
    "<ableron-include   src=\"test\"/>"                                  | "<ableron-include   src=\"test\"/>"
    "<ableron-include   src=\"test\"  >"                                 | "<ableron-include   src=\"test\"  >"
  }

  def "should find all includes in input content"() {
    expect:
    transclusionProcessor.findIncludes("""
      <html>
      <head>
        <ableron-include src="https://foo.bar/baz?test=123" />
        <title>Foo</title>
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456">
      </head>
      <body>
        <ableron-include src="https://foo.bar/baz?test=789">
      </body>
      </html>
    """) == [
            new Include("<ableron-include src=\"https://foo.bar/baz?test=123\" />"),
            new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\">"),
            new Include("<ableron-include src=\"https://foo.bar/baz?test=789\">")
    ] as Set
  }

  def "should treat multiple identical includes as one include"() {
    expect:
    transclusionProcessor.findIncludes("""
      <html>
      <head>
        <ableron-include src="https://foo.bar/baz?test=123" />
        <ableron-include src="https://foo.bar/baz?test=123">
        <title>Foo</title>
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456">
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456">
      </head>
      <body>
        <ableron-include src="https://foo.bar/baz?test=789">
        <ableron-include src="https://foo.bar/baz?test=789">
      </body>
      </html>
    """) == [
            new Include("<ableron-include src=\"https://foo.bar/baz?test=123\" />"),
            new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\">"),
            new Include("<ableron-include src=\"https://foo.bar/baz?test=789\">")
    ] as Set
  }

  def "should populate TransclusionResult"() {
    when:
    def transclusionResult = transclusionProcessor.resolveIncludes("""
      <html>
      <head>
        <ableron-include src="https://foo.bar/baz?test=123" />
        <title>Foo</title>
        <ableron-include foo="bar" src="https://foo.bar/baz?test=456">
      </head>
      <body>
        <ableron-include src="https://foo.bar/baz?test=789">
      </body>
      </html>
    """)

    then:
    transclusionResult.processedIncludesCount == 3
    transclusionResult.processingTimeMillis >= 1
    transclusionResult.content == """
      <html>
      <head>
        <!-- Error loading include -->
        <title>Foo</title>
        <!-- Error loading include -->
      </head>
      <body>
        <!-- Error loading include -->
      </body>
      </html>
    """
  }
}
