package io.github.ableron

import com.github.benmanes.caffeine.cache.Cache
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class IncludeSpec extends Specification {

  @Shared
  def config = AbleronConfig.builder()
    .requestTimeout(Duration.ofSeconds(1))
    .build()

  @Shared
  def httpClient = new TransclusionProcessor().getHttpClient()

  Cache<String, CachedResponse> cache = new TransclusionProcessor().getResponseCache()

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

  def "should follow redirects when resolving URLs"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setHeader("Location", "foo")
      .setResponseCode(302))
    mockWebServer.enqueue(new MockResponse()
      .setHeader("Location", "bar")
      .setResponseCode(302))
    mockWebServer.enqueue(new MockResponse()
      .setBody("response after redirect")
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == "response after redirect"

    cleanup:
    mockWebServer.shutdown()
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
    ), "fallback").resolve(httpClient, cache, config)

    then:
    resolvedInclude == "fallback"

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
    ), "fallback").resolve(httpClient, cache, config)

    then:
    resolvedInclude == "fallback"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should favor include tag specific request timeout over global one"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setBodyDelay(1500, TimeUnit.MILLISECONDS)
      .setResponseCode(200))

    when:
    def attributes = new HashMap()
    attributes.put(srcAttribute, mockWebServer.url("/").toString())
    attributes.putAll(localTimeout)

    then:
    new Include("...", attributes, null)
      .resolve(httpClient, cache, config) == expectedResolvedInclude

    cleanup:
    mockWebServer.shutdown()

    where:
    srcAttribute   | localTimeout                                  | expectedResolvedInclude
    "src"          | Map.of()                                      | ""
    "src"          | Map.of("src-timeout-millis", "2000")          | "response"
    "src"          | Map.of("fallback-src-timeout-millis", "2000") | ""
    "fallback-src" | Map.of()                                      | ""
    "fallback-src" | Map.of("fallback-src-timeout-millis", "2000") | "response"
    "fallback-src" | Map.of("src-timeout-millis", "2000")          | ""
  }

  def "should use cached http response if not expired"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response from src")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    cache.put(includeSrcUrl, new CachedResponse(200, "from cache", expirationTime))
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

  @Unroll
  def "should cache http response if status code is defined as cacheable in RFC 7231 - Status #responsStatus"() {
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
    resolvedInclude == expectedResolvedInclude
    if (expectedResponseCached) {
      assert cache.getIfPresent(includeSrcUrl) != null
      assert cache.getIfPresent(includeSrcUrl).body == expectedCachedBody
    } else {
      assert cache.getIfPresent(includeSrcUrl) == null
    }

    cleanup:
    mockWebServer.shutdown()

    where:
    responsStatus | responseBody | expectedResponseCached | expectedCachedBody | expectedResolvedInclude
    100           | "response"   | false                  | null               | ":("
    200           | "response"   | true                   | "response"         | "response"
    202           | "response"   | false                  | null               | ":("
    203           | "response"   | true                   | "response"         | "response"
    204           | ""           | true                   | ""                 | ""
    205           | "response"   | false                  | null               | ":("
    206           | "response"   | true                   | "response"         | "response"
    // TODO: Testing status code 300 does not work on Java 11 because HttpClient fails with "IOException: Invalid redirection"
    // 300           | "response"   | true                   | ""                 | ":("
    302           | "response"   | false                  | null               | ":("
    400           | "response"   | false                  | null               | ":("
    404           | "response"   | true                   | ""                 | ":("
    405           | "response"   | true                   | ""                 | ":("
    410           | "response"   | true                   | ""                 | ":("
    414           | "response"   | true                   | ""                 | ":("
    500           | "response"   | false                  | null               | ":("
    501           | "response"   | true                   | ""                 | ":("
    502           | "response"   | false                  | null               | ":("
    503           | "response"   | false                  | null               | ":("
    504           | "response"   | false                  | null               | ":("
    505           | "response"   | false                  | null               | ":("
    506           | "response"   | false                  | null               | ":("
    507           | "response"   | false                  | null               | ":("
    508           | "response"   | false                  | null               | ":("
    509           | "response"   | false                  | null               | ":("
    510           | "response"   | false                  | null               | ":("
    511           | "response"   | false                  | null               | ":("
  }

  def "should cache response for s-maxage seconds if directive is present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Cache-Control", "max-age=3600, s-maxage=604800 , public")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(604800).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(604800).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache response for max-age seconds if directive is present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3600).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3600).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should treat http header names as case insensitive"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("cache-control", "max-age=3600")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3600).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3600).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache response for max-age seconds minus Age seconds if directive is present and Age header is set"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Age", "600")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3000).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3000).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should use absolute value of Age header for cache expiration calculation"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Age", "-100")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3500).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3500).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache response based on Expires header and current time if Cache-Control header and Date header are not present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Cache-Control", "public")
      .setHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime == ZonedDateTime.parse("Wed, 12 Oct 2050 07:28:00 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()

    cleanup:
    mockWebServer.shutdown()
  }

  def "should handle Expires header with value 0"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Expires", "0")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == "response"
    cache.getIfPresent(includeSrcUrl) == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache response based on Expires and Date header if Cache-Control header is not present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Date", "Wed, 05 Oct 2050 07:28:00 GMT")
      .setHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime.isBefore(Instant.now().plus(7, ChronoUnit.DAYS).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plus(7, ChronoUnit.DAYS).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not cache response if Cache-Control header is set but without max-age directives"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader("Cache-Control", "no-cache,no-store,must-revalidate")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == "response"
    cache.getIfPresent(includeSrcUrl) == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not crash when cache headers contain invalid values"() {
    given:
    def mockWebServer = new MockWebServer()

    when:
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setHeader(header1Name, header1Value)
      .setHeader(header2Name, header2Value)
      .setResponseCode(200))
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, cache, config)

    then:
    resolvedInclude == "response"

    cleanup:
    mockWebServer.shutdown()

    where:
    header1Name     | header1Value                    | header2Name | header2Value
    "Cache-Control" | "s-maxage=not-numeric"          | "X-Dummy"   | "dummy"
    "Cache-Control" | "max-age=not-numeric"           | "X-Dummy"   | "dummy"
    "Cache-Control" | "max-age=3600"                  | "Age"       | "600"
    "Expires"       | "not-numeric"                   | "X-Dummy"   | "dummy"
    "Expires"       | "Wed, 12 Oct 2050 07:28:00 GMT" | "Date"      | "not-a-date"
  }

  def "should cache response for a configurable duration if no expiration time is indicated via response header"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/").toString()
    def config = AbleronConfig.builder()
      .fallbackResponseCacheTime(Duration.ofSeconds(30))
      .build()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, cache, config)
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "response"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(30).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(30).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }
}
