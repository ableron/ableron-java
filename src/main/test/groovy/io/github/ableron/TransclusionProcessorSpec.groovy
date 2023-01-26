package io.github.ableron

import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient

class TransclusionProcessorSpec extends Specification {

  @Shared
  def httpClient = HttpClient.newHttpClient()

  @Shared
  def transclusionProcessor = new TransclusionProcessor(httpClient)

  def "should throw exception if httpClient is not provided"() {
    when:
    new TransclusionProcessor(null)

    then:
    def exception = thrown(NullPointerException)
    exception.message == "httpClient must not be null"
  }

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
      new Include("<ableron-include src=\"https://foo.bar/baz?test=123\" />", Map.of(), null, httpClient),
      new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\"/>", Map.of(), null, httpClient),
      new Include("<ableron-include src=\"https://foo.bar/baz?test=789\" fallback-src=\"https://example.com\"/>", Map.of(), null, httpClient),
      new Include("<ableron-include src=\"https://foo.bar/baz?test=789\" fallback-src=\"https://example.com\">fallback</ableron-include>", Map.of(), null, httpClient)
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
      new Include("<ableron-include src=\"https://foo.bar/baz?test=123\"/>", Map.of(), null, httpClient),
      new Include("<ableron-include foo=\"bar\" src=\"https://foo.bar/baz?test=456\"></ableron-include>", Map.of(), null, httpClient),
      new Include("<ableron-include src=\"...\">...</ableron-include>", Map.of(), null, httpClient)
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
}
