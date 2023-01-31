package io.github.ableron

import com.github.benmanes.caffeine.cache.Cache
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class IncludeSpec extends Specification {

  @Shared
  def httpClient = HttpClient.newHttpClient()

  Cache<String, HttpResponse> cache = new TransclusionProcessor().getResponseCache()

  @Shared
  def config = AbleronConfig.builder().requestTimeout(Duration.ofSeconds(1)).build()

  def "should throw exception if rawInclude is not provided"() {
    when:
    new Include(null, Map.of(), "")

    then:
    def exception = thrown(NullPointerException)
    exception.message == "rawInclude must not be null"
  }

  def "should throw exception if attributes are not provided"() {
    when:
    new Include("", null, "")

    then:
    def exception = thrown(NullPointerException)
    exception.message == "attributes must not be null"
  }

  def "constructor should set raw include"() {
    when:
    def include = new Include("<ableron-include src=\"https://example.com\"/>", Map.of(), null)

    then:
    include.rawInclude == "<ableron-include src=\"https://example.com\"/>"
  }

  def "constructor should set src attribute"() {
    expect:
    include.src == expectedSrc

    where:
    include                                                        | expectedSrc
    new Include("...", Map.of(), null)                             | null
    new Include("...", Map.of("src", "https://example.com"), null) | "https://example.com"
  }

  def "constructor should set fallback-src attribute"() {
    expect:
    include.fallbackSrc == expectedFallbackSrc

    where:
    include                                                                 | expectedFallbackSrc
    new Include("...", Map.of(), null)                                      | null
    new Include("...", Map.of("fallback-src", "https://example.com"), null) | "https://example.com"
  }

  def "constructor should set fallback content"() {
    expect:
    include.fallbackContent == expectedFallbackContent

    where:
    include                                  | expectedFallbackContent
    new Include("...", Map.of(), null)       | null
    new Include("...", Map.of(), "fallback") | "fallback"
  }

  def "should consider include objects with identical include string as equal"() {
    when:
    def include1 = new Include("<ableron-include src=\"...\"></ableron-include>", Map.of(), null)
    def include2 = new Include("<ableron-include src=\"...\"></ableron-include>", Map.of("foo", "bar"), null)
    def include3 = new Include("<ableron-include src=\"...\"/>", Map.of("test", "test"), null)

    then:
    include1 == include2
    include1 != include3
    include2 != include3
  }

  def "should resolve include with URL provided via src attribute"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/fragment").toString()
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == "response"
    mockWebServer.takeRequest().getPath() == "/fragment"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve include with URL provided via fallback-src attribute if src could not be loaded"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from src")
      .setResponseCode(500))
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from fallback-src")
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/fragment").toString(),
      "fallback-src", mockWebServer.url("/fallback-fragment").toString()
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == "response from fallback-src"
    mockWebServer.takeRequest().getPath() == "/fragment"
    mockWebServer.takeRequest().getPath() == "/fallback-fragment"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve include with fallback content if src and fallback-src could not be loaded"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from src")
      .setResponseCode(500))
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from fallback-src")
      .setResponseCode(500))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/fragment").toString(),
      "fallback-src", mockWebServer.url("/fallback-fragment").toString()
    ), "fallback content").resolve(httpClient, cache, config)

    then:
    resolvedInclude == "fallback content"
    mockWebServer.takeRequest().getPath() == "/fragment"
    mockWebServer.takeRequest().getPath() == "/fallback-fragment"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve include to empty string if src, fallback src and fallback content are not present"() {
    expect:
    new Include("...", Map.of(), null).resolve(httpClient, cache, config) == ""
  }

  def "should apply request timeout for delayed header"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from src")
      .setHeadersDelay(2, TimeUnit.SECONDS)
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == ""

    cleanup:
    mockWebServer.shutdown()
  }

  def "should apply request timeout for delayed body"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from src")
      .setBodyDelay(2, TimeUnit.SECONDS)
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == ""

    cleanup:
    mockWebServer.shutdown()
  }

  def "should use cached http response if not expired"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from src")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    cache.put(includeSrcUrl, new HttpResponse("from cache", expirationTime))
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == expectedResolvedInclude

    cleanup:
    mockWebServer.shutdown()

    where:
    expirationTime                | expectedResolvedInclude
    Instant.now().plusSeconds(5)  | "from cache"
    Instant.now().minusSeconds(5) | "response from src"
  }

  def "should cache http response only if status code is 200"() {
    given:
    def mockWebServer = new MockWebServer()

    when:
    mockWebServer.enqueue(new MockResponse()
      .setBody(responseBody)
      .setResponseCode(responsStatus))
    def includeSrcUrl = mockWebServer.url(UUID.randomUUID().toString()).toString()
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), ":(").resolve(httpClient, cache, config)

    then:
    resolvedInclude == expectedResolvedIncludeContent
    if (expectedResponseCached) {
      cache.getIfPresent(includeSrcUrl) != null
      cache.getIfPresent(includeSrcUrl).responseBody == responseBody
    } else {
      cache.getIfPresent(includeSrcUrl) == null
    }

    cleanup:
    mockWebServer.shutdown()

    where:
    responsStatus | responseBody | expectedResponseCached | expectedResolvedIncludeContent
    100           | "..."        | false                  | ":("
    200           | "response"   | true                   | "response"
    202           | "..."        | false                  | ":("
    204           | "..."        | false                  | ":("
    302           | "..."        | false                  | ":("
    400           | "..."        | false                  | ":("
    500           | "..."        | false                  | ":("
  }
}
