package io.github.ableron

import spock.lang.Specification

class TransclusionProcessorSpec extends Specification {

  def transclusionProcessor = new TransclusionProcessor()

  def "should have tolerant fragment matching pattern"() {
    expect:
    transclusionProcessor.findFragments(inputContent).first().originalTag == expectedFragmentTag

    where:
    inputContent                                                  | expectedFragmentTag
    "<fragment src=\"https://example.com\">"                      | "<fragment src=\"https://example.com\">"
    "<div><fragment src=\"https://example.com\"></div>"           | "<fragment src=\"https://example.com\">"
    "<div><fragment src=\"https://example.com\" foo=\">\"></div>" | "<fragment src=\"https://example.com\" foo=\">\">"
    "<fragment src=\"test\">"                                     | "<fragment src=\"test\">"
    "<fragment src=\"test\"/>"                                    | "<fragment src=\"test\"/>"
    "<fragment src=\"test\" />"                                   | "<fragment src=\"test\" />"
    "<fragment   src=\"test\"/>"                                  | "<fragment   src=\"test\"/>"
    "<fragment   src=\"test\"  >"                                 | "<fragment   src=\"test\"  >"
  }

  def "should find all fragments in input content"() {
    expect:
    transclusionProcessor.findFragments("""
      <html>
      <head>
        <fragment src="https://foo.bar/baz?test=123" />
        <title>Foo</title>
        <fragment foo="bar" src="https://foo.bar/baz?test=456">
      </head>
      <body>
        <fragment src="https://foo.bar/baz?test=789">
      </body>
      </html>
    """) == [
      new Fragment("<fragment src=\"https://foo.bar/baz?test=123\" />"),
      new Fragment("<fragment foo=\"bar\" src=\"https://foo.bar/baz?test=456\">"),
      new Fragment("<fragment src=\"https://foo.bar/baz?test=789\">")
    ] as Set
  }

  def "should treat identical fragments as one fragment"() {
    expect:
    transclusionProcessor.findFragments("""
      <html>
      <head>
        <fragment src="https://foo.bar/baz?test=123" />
        <fragment src="https://foo.bar/baz?test=123">
        <title>Foo</title>
        <fragment foo="bar" src="https://foo.bar/baz?test=456">
        <fragment foo="bar" src="https://foo.bar/baz?test=456">
      </head>
      <body>
        <fragment src="https://foo.bar/baz?test=789">
        <fragment src="https://foo.bar/baz?test=789">
      </body>
      </html>
    """) == [
            new Fragment("<fragment src=\"https://foo.bar/baz?test=123\" />"),
            new Fragment("<fragment foo=\"bar\" src=\"https://foo.bar/baz?test=456\">"),
            new Fragment("<fragment src=\"https://foo.bar/baz?test=789\">")
    ] as Set
  }

  def "should populate TransclusionResult"() {
    when:
    def transclusionResult = transclusionProcessor.applyTransclusion("""
      <html>
      <head>
        <fragment src="https://foo.bar/baz?test=123" />
        <title>Foo</title>
        <fragment foo="bar" src="https://foo.bar/baz?test=456">
      </head>
      <body>
        <fragment src="https://foo.bar/baz?test=789">
      </body>
      </html>
    """)

    then:
    transclusionResult.processedFragmentsCount == 3
    transclusionResult.processingTimeMillis >= 1
    transclusionResult.content == """
      <html>
      <head>
        <!-- Error loading fragment -->
        <title>Foo</title>
        <!-- Error loading fragment -->
      </head>
      <body>
        <!-- Error loading fragment -->
      </body>
      </html>
    """
  }
}
