package io.github.ableron

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.jetbrains.annotations.NotNull
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
    def include = new Include("<include>", ["primary": ""], "fallback")
    def fragment = new Fragment(null, 404, "not found", Instant.EPOCH, ["X-Test": ["Foo"]])

    when:
    transclusionResult.addResolvedInclude(include, fragment, 0L)

    then:
    transclusionResult.getContent() == "content: not found"
    transclusionResult.getContentExpirationTime() == Optional.of(Instant.EPOCH)
    transclusionResult.hasPrimaryInclude()
    transclusionResult.getStatusCodeOverride() == Optional.of(404)
    transclusionResult.getResponseHeadersToPass() == ["X-Test":["Foo"]]
    transclusionResult.getProcessedIncludesCount() == 1
    transclusionResult.getProcessingTimeMillis() == 0
  }

  def "should handle unresolvable include correctly"() {
    given:
    def transclusionResult = new TransclusionResult("content: <include>")

    when:
    transclusionResult.addUnresolvableInclude(new Include("<include>", null, "fallback"), "error")

    then:
    transclusionResult.getContent() == "content: fallback"
    transclusionResult.getContentExpirationTime() == Optional.of(Instant.EPOCH)
    !transclusionResult.hasPrimaryInclude()
    transclusionResult.getStatusCodeOverride() == Optional.empty()
    transclusionResult.getResponseHeadersToPass() == [:]
    transclusionResult.getProcessedIncludesCount() == 1
    transclusionResult.getProcessingTimeMillis() == 0
  }

  def "should calculate cache control header value"() {
    given:
    def transclusionResult = new TransclusionResult("content")
    transclusionResult.addResolvedInclude(
      new Include(""),
      new Fragment(null, 200, "", fragmentExpirationTime, [:]),
      0L
    )

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
    transclusionResult.addResolvedInclude(
      new Include(""),
      new Fragment(null, 200, "", fragmentExpirationTime, [:]),
      0L
    )

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
    transclusionResult.addResolvedInclude(
      new Include(""),
      new Fragment(null, 200, "", fragmentExpirationTime, [:]),
      0L
    )

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
    new TransclusionResult("content", true, true).getContent() == "content\n" +
      "<!-- Ableron stats:\n" +
      "Processed 0 include(s) in 0ms\n" +
      "-->"
  }

  def "should append stats to content"() {
    given:
    def mockWebServer = new MockWebServer()
    def baseUrl = mockWebServer.url("/").toString()
    mockWebServer.setDispatcher(new Dispatcher() {
      @Override
      MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
        switch (recordedRequest.getPath()) {
          case "/uncacheable-fragment":
            return new MockResponse()
              .setResponseCode(200)
              .setHeader("Cache-Control", "no-store")
              .setBody("uncacheable-fragment")
          case "/cacheable-fragment-1":
            return new MockResponse()
              .setResponseCode(200)
              .setHeader("Expires", "Wed, 21 Oct 2050 00:00:00 GMT")
              .setBody("cacheable-fragment-1")
        }
        return new MockResponse().setResponseCode(404)
      }
    })
    def transclusionProcessor = new TransclusionProcessor(AbleronConfig.builder()
      .statsAppendToContent(true)
      .statsExposeFragmentUrl(true)
      .build())

    when:
    def result = transclusionProcessor.resolveIncludes("""
      <ableron-include id="1">static content</ableron-include>
      ableron-include id="2" src="${baseUrl}uncacheable-fragment" />
      ableron-include id="3" src="${baseUrl}cacheable-fragment-1" />
      ableron-include id="4" src="${baseUrl}cacheable-fragment-2" />
    """, [:])

    then:
    result.content.matches(".+static content.+")

    cleanup:
    mockWebServer.shutdown()
  }

  def "should append stats for primary include"() {
    given:
    def transclusionResult = new TransclusionResult("", true, false)

    when:
    transclusionResult.addResolvedInclude(
      new Include("include#1", ["primary":""]),
      new Fragment(200, ""),
      0L
    )

    then:
    transclusionResult.getContent() == "\n<!-- Ableron stats:\n" +
      "Processed 1 include(s) in 0ms\n" +
      "Primary include with status code 200\n" +
      "Resolved include '1496920298' with fallback content in 0ms\n" +
      "-->"
  }

  def "should append stats for primary include - multiple primary includes"() {
    given:
    def transclusionResult = new TransclusionResult("", true, false)

    when:
    transclusionResult.addResolvedInclude(
      new Include("include#1", ["primary":""]),
      new Fragment(200, ""),
      0L
    )
    transclusionResult.addResolvedInclude(
      new Include("include#2", ["primary":""]),
      new Fragment(200, ""),
      33L
    )

    then:
    transclusionResult.getContent() == "\n<!-- Ableron stats:\n" +
      "Processed 2 include(s) in 0ms\n" +
      "Primary include with status code 200\n" +
      "Resolved include '1496920298' with fallback content in 0ms\n" +
      "Ignoring status code and response headers of primary include with status code 200 because there is already another primary include\n" +
      "Resolved include '1496920297' with fallback content in 33ms\n" +
      "-->"
  }
}
