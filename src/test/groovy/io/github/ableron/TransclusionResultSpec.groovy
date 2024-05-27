package io.github.ableron

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class TransclusionResultSpec extends Specification {

  def "should return reasonable defaults"() {
    when:
    def transclusionResult = new TransclusionResult("content")

    then:
    transclusionResult.getContent() == "content"
    transclusionResult.getContentExpirationTime() == Optional.empty()
    !transclusionResult.hasPrimaryInclude()
    transclusionResult.getStatusCodeOverride() == Optional.empty()
    transclusionResult.getResponseHeadersToPass() == [:]
    transclusionResult.getProcessedIncludesCount() == 0
    transclusionResult.getProcessingTimeMillis() == 0
  }

  def "should set processing time"() {
    given:
    def transclusionResult = new TransclusionResult("content")

    when:
    transclusionResult.setProcessingTimeMillis(111)

    then:
    transclusionResult.getProcessingTimeMillis() == 111
  }

  def "should handle resolved include correctly"() {
    given:
    def transclusionResult = new TransclusionResult("content: <include>")

    when:
    transclusionResult.addResolvedInclude(new Include("<include>", ["primary": ""], "fallback").resolveWith(
      new Fragment(null, 404, "not found", Instant.EPOCH, ["X-Test": ["Foo"]]),
      0,
      "fallback content"
    ))

    then:
    transclusionResult.getContent() == "content: not found"
    transclusionResult.getContentExpirationTime() == Optional.of(Instant.EPOCH)
    transclusionResult.hasPrimaryInclude()
    transclusionResult.getStatusCodeOverride() == Optional.of(404)
    transclusionResult.getResponseHeadersToPass() == ["X-Test":["Foo"]]
    transclusionResult.getProcessedIncludesCount() == 1
    transclusionResult.getProcessingTimeMillis() == 0
  }

  def "should calculate cache control header value"() {
    given:
    def transclusionResult = new TransclusionResult("content")
    transclusionResult.addResolvedInclude(new Include("").resolveWith(
      new Fragment(null, 200, "", fragmentExpirationTime, [:]), 0, ""
    ))

    expect:
    transclusionResult.calculateCacheControlHeaderValue() == expectedCacheControlHeaderValue

    where:
    fragmentExpirationTime         | expectedCacheControlHeaderValue
    Instant.EPOCH                  | "no-store"
    Instant.now().minusSeconds(5)  | "no-store"
    Instant.now()                  | "no-store"
    Instant.now().plusSeconds(300) | "no-store"
  }

  def "should calculate cache control header value based on given page max age"() {
    given:
    def transclusionResult = new TransclusionResult("content")
    transclusionResult.addResolvedInclude(new Include("").resolveWith(
      new Fragment(null, 200, "", fragmentExpirationTime, [:]), 0, ""
    ))

    expect:
    transclusionResult.calculateCacheControlHeaderValue(pageMaxAge) == expectedCacheControlHeaderValue

    where:
    fragmentExpirationTime         | pageMaxAge              | expectedCacheControlHeaderValue
    Instant.EPOCH                  | Duration.ofSeconds(0)   | "no-store"
    Instant.now().minusSeconds(5)  | Duration.ofSeconds(0)   | "no-store"
    Instant.now()                  | Duration.ofSeconds(0)   | "no-store"
    Instant.now().plusSeconds(300) | Duration.ofSeconds(0)   | "no-store"
    Instant.EPOCH                  | Duration.ofSeconds(120) | "no-store"
    Instant.now().minusSeconds(5)  | Duration.ofSeconds(120) | "no-store"
    Instant.now()                  | Duration.ofSeconds(120) | "no-store"
    Instant.now().plusSeconds(300) | Duration.ofSeconds(120) | "max-age=120"
    Instant.now().plusSeconds(300) | Duration.ofSeconds(300) | "max-age=300"
    Instant.now().plusSeconds(300) | Duration.ofSeconds(600) | "max-age=300"
  }

  def "should calculate cache control header value based on given response headers"() {
    given:
    def transclusionResult = new TransclusionResult("content")
    transclusionResult.addResolvedInclude(new Include("").resolveWith(
      new Fragment(null, 200, "", fragmentExpirationTime, [:]), 0, ""
    ))

    expect:
    transclusionResult.calculateCacheControlHeaderValue(responseHeaders) == expectedCacheControlHeaderValue

    where:
    fragmentExpirationTime         | responseHeaders                   | expectedCacheControlHeaderValue
    Instant.EPOCH                  | [:]                               | "no-store"
    Instant.now().minusSeconds(5)  | [:]                               | "no-store"
    Instant.now()                  | [:]                               | "no-store"
    Instant.now().plusSeconds(300) | [:]                               | "no-store"
    Instant.EPOCH                  | ["Cache-Control":["no-cache"]]    | "no-store"
    Instant.now().minusSeconds(5)  | ["Cache-Control":["no-cache"]]    | "no-store"
    Instant.now()                  | ["Cache-Control":["no-cache"]]    | "no-store"
    Instant.now().plusSeconds(300) | ["Cache-Control":["no-cache"]]    | "no-store"
    Instant.EPOCH                  | ["Cache-Control":["max-age=0"]]   | "no-store"
    Instant.now().minusSeconds(5)  | ["Cache-Control":["max-age=0"]]   | "no-store"
    Instant.now()                  | ["Cache-Control":["max-age=0"]]   | "no-store"
    Instant.now().plusSeconds(300) | ["Cache-Control":["max-age=0"]]   | "no-store"
    Instant.EPOCH                  | ["Cache-Control":["max-age=120"]] | "no-store"
    Instant.now().minusSeconds(5)  | ["Cache-Control":["max-age=120"]] | "no-store"
    Instant.now()                  | ["Cache-Control":["max-age=120"]] | "no-store"
    Instant.now().plusSeconds(300) | ["Cache-Control":["max-age=120"]] | "max-age=120"
    Instant.now().plusSeconds(300) | ["Cache-Control":["max-age=300"]] | "max-age=300"
    Instant.now().plusSeconds(300) | ["Cache-Control":["max-age=600"]] | "max-age=300"
  }

  def "should handle missing content expiration time when calculating cache control header value"() {
    given:
    def transclusionResult = new TransclusionResult("")

    expect:
    transclusionResult.calculateCacheControlHeaderValue([:]) == "no-store"
  }

  def "should not append stats to content by default"() {
    expect:
    new TransclusionResult("content").getContent() == "content"
  }

  def "should append stats to content - zero includes"() {
    expect:
    new TransclusionResult("content", true, true).getContent()
      == "content\n<!-- Ableron stats:\nProcessed 0 include(s) in 0ms\n-->"
  }

  def "should append stats to content"() {
    given:
    def mockWebServer = new MockWebServer()
    def baseUrl = mockWebServer.url("/").toString()
    mockWebServer.setDispatcher(new Dispatcher() {
      @Override
      MockResponse dispatch(RecordedRequest recordedRequest) throws InterruptedException {
        switch (recordedRequest.getPath()) {
          case "/uncacheable-fragment":
            return new MockResponse()
              .setResponseCode(200)
              .setHeader("Cache-Control", "no-store")
              .setBody("uncacheable-fragment")
          case "/cacheable-fragment-1":
            return new MockResponse()
              .setResponseCode(200)
              .setHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT")
              .setBody("cacheable-fragment-1")
          case "/cacheable-fragment-2":
            return new MockResponse()
              .setResponseCode(200)
              .setHeader("Cache-Control", "max-age=10")
              .setBody("cacheable-fragment-2")
        }
        return new MockResponse().setResponseCode(404)
      }
    })
    def transclusionProcessor = new TransclusionProcessor(AbleronConfig.builder()
      .statsAppendToContent(true)
      .statsExposeFragmentUrl(true)
      .build())
    transclusionProcessor.resolveIncludes("""
      <ableron-include src="${baseUrl}cacheable-fragment-2" />
    """, [:])

    when:
    def result = transclusionProcessor.resolveIncludes("""
      <ableron-include id="1">fallback content</ableron-include>
      <ableron-include id="2" src="${baseUrl}uncacheable-fragment" />
      <ableron-include id="3" src="${baseUrl}cacheable-fragment-1" />
      <ableron-include id="4" fallback-src="${baseUrl}cacheable-fragment-2" />
    """, [:])

    then:
    result.content.startsWith("""
      fallback content
      uncacheable-fragment
      cacheable-fragment-1
      cacheable-fragment-2
    """)
    result.content.matches("(?s).+<!-- Ableron stats:\\nProcessed 4 include\\(s\\) in \\d+ms.+")
    result.content.contains("Time | Include | Resolved With | Fragment Cacheability | Fragment URL")
    result.content.contains("------------------------------------------------------")
    result.content.matches("(?s).+\\d+ms \\| 1 \\| fallback content \\| - \\| -\\n.+")
    result.content.matches("(?s).+\\d+ms \\| 2 \\| remote src \\| not cacheable \\| http://localhost:\\d+/uncacheable-fragment\\n.+")
    result.content.matches("(?s).+\\d+ms \\| 3 \\| remote src \\| expires in \\d+s \\| http://localhost:\\d+/cacheable-fragment-1\\n.+")
    result.content.matches("(?s).+\\d+ms \\| 4 \\| cached fallback-src \\| expires in 10s \\| http://localhost:\\d+/cacheable-fragment-2\\n.+")

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not expose fragment URL to stats by default"() {
    given:
    def transclusionResult = new TransclusionResult("", true, false)

    when:
    transclusionResult.addResolvedInclude(new Include("").resolveWith(
      new Fragment("example.com", 200, "", Instant.EPOCH, [:]), 71, "src"
    ))

    then:
    transclusionResult.getContent() ==
      "\n<!-- Ableron stats:\n" +
      "Processed 1 include(s) in 0ms\n\n" +
      "Time | Include | Resolved With | Fragment Cacheability\n" +
      "------------------------------------------------------\n" +
      "71ms | 0 | src | not cacheable\n" +
      "-->"
  }

  def "should append stats for primary include"() {
    given:
    def transclusionResult = new TransclusionResult("", true, false)

    when:
    transclusionResult.addResolvedInclude(new Include("include#1", ["primary":""]).resolveWith(
      new Fragment(200, ""), 0, "fallback content"
    ))

    then:
    transclusionResult.getContent() ==
      "\n<!-- Ableron stats:\n" +
      "Processed 1 include(s) in 0ms\n\n" +
      "Time | Include | Resolved With | Fragment Cacheability\n" +
      "------------------------------------------------------\n" +
      "0ms | 1496920298 (primary) | fallback content | -\n" +
      "-->"
  }

  def "should append stats for primary include - multiple primary includes"() {
    given:
    def transclusionResult = new TransclusionResult("", true, false)

    when:
    transclusionResult.addResolvedInclude(new Include("include#1", ["primary":""]).resolveWith(
      new Fragment(200, ""), 0, "fallback content"
    ))
    transclusionResult.addResolvedInclude(new Include("include#2", ["primary":""]).resolveWith(
      new Fragment(200, ""), 33, "fallback content"
    ))

    then:
    transclusionResult.getContent() ==
      "\n<!-- Ableron stats:\n" +
      "Processed 2 include(s) in 0ms\n\n" +
      "Time | Include | Resolved With | Fragment Cacheability\n" +
      "------------------------------------------------------\n" +
      "33ms | 1496920297 (primary) | fallback content | -\n" +
      "0ms | 1496920298 (primary) | fallback content | -\n" +
      "-->"
  }
}
