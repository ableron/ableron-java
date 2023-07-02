package io.github.ableron

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
    def include = new Include(["primary":""], "fallback", "<include>")
    def fragment = new Fragment(404, "not found", Instant.EPOCH, ["X-Test":["Foo"]])

    when:
    transclusionResult.addResolvedInclude(include, fragment)

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
    transclusionResult.addResolvedInclude(
      new Include([:]),
      new Fragment(200, "", fragmentExpirationTime, [:])
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
      new Include([:]),
      new Fragment(200, "", fragmentExpirationTime, [:])
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
    Instant.now().plusSeconds(300) | Duration.ofSeconds(300) | "max-age=299"
    Instant.now().plusSeconds(300) | Duration.ofSeconds(600) | "max-age=299"
  }

  def "should calculate cache control header value based on given response headers"() {
    given:
    def transclusionResult = new TransclusionResult("content")
    transclusionResult.addResolvedInclude(
      new Include([:]),
      new Fragment(200, "", fragmentExpirationTime, [:])
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
    Instant.now().plusSeconds(300) | ["Cache-Control":["max-age=120"]] | "max-age=119"
    Instant.now().plusSeconds(300) | ["Cache-Control":["max-age=300"]] | "max-age=299"
    Instant.now().plusSeconds(300) | ["Cache-Control":["max-age=600"]] | "max-age=299"
  }
}
