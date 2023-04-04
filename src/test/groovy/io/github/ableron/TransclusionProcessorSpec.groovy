package io.github.ableron

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.jetbrains.annotations.NotNull
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class TransclusionProcessorSpec extends Specification {

  @Shared
  def transclusionProcessor = new TransclusionProcessor()

  def "should recognize includes of different forms"() {
    expect:
    transclusionProcessor.findIncludes(content).first().rawIncludeTag == expectedRawIncludeTag

    where:
    content                                                         | expectedRawIncludeTag
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
    def result = transclusionProcessor.resolveIncludes(Content.of("""
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
    """), [:])

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
    def result = transclusionProcessor.resolveIncludes(Content.of("""
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #1 --></ableron-include>
      <ableron-include src="foo-bar"><!-- #2 --></ableron-include>
    """), [:])

    then:
    result.content == """
      <!-- #1 -->
      <!-- #1 -->
      <!-- #1 -->
      <!-- #2 -->
    """
  }

  def "should not crash due to include tag #scenarioName"() {
    when:
    def result = transclusionProcessor
      .resolveIncludes(Content.of(
        "<ableron-include >before</ableron-include>" + includeTag + "<ableron-include >after</ableron-include>"
      ), [:])

    then:
    result.content == "before" + expectedResult + "after"

    where:
    scenarioName                   | includeTag                                                                     | expectedResult
    "invalid src url"              | '<ableron-include src=",._">fallback</ableron-include>'                        | "fallback"
    "invalid src timeout"          | '<ableron-include src-timeout-millis="5s">fallback</ableron-include>'          | "fallback"
    "invalid fallback-src timeout" | '<ableron-include fallback-src-timeout-millis="5s">fallback</ableron-include>' | "fallback"
  }

  def "should perform only one request per URL"() {
    given:
    def mockWebServer = new MockWebServer()
    def baseUrl = mockWebServer.url("/").toString()
    mockWebServer.setDispatcher(new Dispatcher() {
      @Override
      MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
        switch (recordedRequest.getPath()) {
          case "/1":
            return new MockResponse()
              .setBody("fragment-1")
              .setHeadersDelay(200, TimeUnit.MILLISECONDS)
              .setResponseCode(200)
        }
        return new MockResponse()
          .setBody("fragment-2")
          .setResponseCode(404)
      }
    })

    when:
    def result = transclusionProcessor.resolveIncludes(Content.of("""
      <html>
      <head>
        <ableron-include src="${baseUrl}1"><!-- failed loading 1st fragment --></ableron-include>
        <title>Foo</title>
        <ableron-include src="${baseUrl}1"><!-- failed loading 2nd fragment --></ableron-include>
      </head>
      <body>
        <ableron-include src="${baseUrl}1"><!-- failed loading 3rd fragment --></ableron-include>
        <ableron-include src="${baseUrl}expect-404"><!-- failed loading 4th fragment --></ableron-include>
      </body>
      </html>
    """), [:])

    then:
    result.content == """
      <html>
      <head>
        fragment-1
        <title>Foo</title>
        fragment-1
      </head>
      <body>
        fragment-1
        <!-- failed loading 4th fragment -->
      </body>
      </html>
    """

    cleanup:
    mockWebServer.shutdown()
  }

  @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
  def "should resolve includes in parallel"() {
    given:
    def mockWebServer = new MockWebServer()
    def baseUrl = mockWebServer.url("/").toString()
    mockWebServer.setDispatcher(new Dispatcher() {
      @Override
      MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
        switch (recordedRequest.getPath()) {
          case "/503-route":
            return new MockResponse()
              .setBody("fragment-1")
              .setHeadersDelay(2000, TimeUnit.MILLISECONDS)
              .setResponseCode(503)
          case "/1000ms-delay-route":
            return new MockResponse()
              .setBody("fragment-2")
              .setHeadersDelay(1000, TimeUnit.MILLISECONDS)
              .setResponseCode(200)
          case "/2000ms-delay-route":
            return new MockResponse()
              .setBody("fragment-3")
              .setHeadersDelay(2000, TimeUnit.MILLISECONDS)
              .setResponseCode(200)
          case "/2100ms-delay-route":
            return new MockResponse()
              .setBody("fragment-4")
              .setHeadersDelay(2100, TimeUnit.MILLISECONDS)
              .setResponseCode(200)
          case "/2200ms-delay-route":
            return new MockResponse()
              .setBody("fragment-5")
              .setHeadersDelay(2200, TimeUnit.MILLISECONDS)
              .setResponseCode(200)
        }
        return new MockResponse().setResponseCode(404)
      }
    })

    when:
    def result = transclusionProcessor.resolveIncludes(Content.of("""
      <html>
      <head>
        <ableron-include src="${baseUrl}503-route"><!-- failed loading fragment #1 --></ableron-include>
        <title>Foo</title>
        <ableron-include src="${baseUrl}1000ms-delay-route"><!-- failed loading fragment #2 --></ableron-include>
      </head>
      <body>
        <ableron-include src="${baseUrl}2000ms-delay-route"><!-- failed loading fragment #3 --></ableron-include>
        <ableron-include src="${baseUrl}2100ms-delay-route"><!-- failed loading fragment #4 --></ableron-include>
        <ableron-include src="${baseUrl}2200ms-delay-route"><!-- failed loading fragment #5 --></ableron-include>
        <ableron-include src="${baseUrl}expect-404"><!-- failed loading fragment #6 --></ableron-include>
      </body>
      </html>
    """), [:])

    then:
    result.content == """
      <html>
      <head>
        <!-- failed loading fragment #1 -->
        <title>Foo</title>
        fragment-2
      </head>
      <body>
        fragment-3
        fragment-4
        fragment-5
        <!-- failed loading fragment #6 -->
      </body>
      </html>
    """

    cleanup:
    mockWebServer.shutdown()
  }
}
