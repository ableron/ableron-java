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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IncludeSpec extends Specification {

  @Shared
  def config = AbleronConfig.builder()
    .fragmentRequestTimeout(Duration.ofSeconds(1))
    .build()

  @Shared
  def httpClient = new TransclusionProcessor().getHttpClient()

  Cache<String, Fragment> cache = new TransclusionProcessor().getFragmentCache()

  @Shared
  def supplyPool = Executors.newFixedThreadPool(64)

  def "should throw exception if rawIncludeTag is not provided"() {
    when:
    new Include(null, Map.of(), "")

    then:
    def exception = thrown(NullPointerException)
    exception.message == "rawIncludeTag must not be null"
  }

  def "should throw exception if attributes are not provided"() {
    when:
    new Include("", null, "")

    then:
    def exception = thrown(NullPointerException)
    exception.message == "attributes must not be null"
  }

  def "constructor should set raw include tag"() {
    when:
    def include = new Include("<ableron-include src=\"https://example.com\"/>", Map.of(), null)

    then:
    include.rawIncludeTag == "<ableron-include src=\"https://example.com\"/>"
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

  def "constructor should set primary attribute"() {
    expect:
    include.primary == expectedPrimary

    where:
    include                                                | expectedPrimary
    new Include("...", Map.of(), null)                     | false
    new Include("...", Map.of("primary", ""), null)        | true
    new Include("...", Map.of("primary", "primary"), null) | true
    new Include("...", Map.of("primary", "PRIMARY"), null) | true
    new Include("...", Map.of("primary", "false"), null)   | false
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
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()

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
      .setBody("fragment from src")
      .setResponseCode(500))
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment from fallback-src")
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/src").toString(),
      "fallback-src", mockWebServer.url("/fallback-src").toString()
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fragment from fallback-src"
    mockWebServer.takeRequest().getPath() == "/src"
    mockWebServer.takeRequest().getPath() == "/fallback-src"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve include with fallback content if src and fallback-src could not be loaded"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment from src")
      .setResponseCode(500))
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment from fallback-src")
      .setResponseCode(500))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/src").toString(),
      "fallback-src", mockWebServer.url("/fallback-src").toString()
    ), "fallback content").resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fallback content"
    mockWebServer.takeRequest().getPath() == "/src"
    mockWebServer.takeRequest().getPath() == "/fallback-src"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve include to empty string if src, fallback src and fallback content are not present"() {
    expect:
    new Include("...", Map.of(), null).resolve(httpClient, [:], cache, config, supplyPool).get() == ""
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
      .setBody("fragment after redirect")
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/test-redirect").toString()
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fragment after redirect"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should use cached fragment if not expired"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment from src")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-caching").toString()

    when:
    cache.put(includeSrcUrl, new Fragment(200, "from cache", expirationTime))
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == expectedResolvedInclude

    cleanup:
    mockWebServer.shutdown()

    where:
    expirationTime                | expectedResolvedInclude
    Instant.now().plusSeconds(5)  | "from cache"
    Instant.now().minusSeconds(5) | "fragment from src"
  }

  @Unroll
  def "should cache fragment if status code is defined as cacheable in RFC 7231 - Status #responsStatus"() {
    given:
    def mockWebServer = new MockWebServer()

    when:
    mockWebServer.enqueue(new MockResponse()
      .setBody(fragmentContent)
      .setResponseCode(responsStatus))
    def includeSrcUrl = mockWebServer.url("/test-caching-" + UUID.randomUUID().toString()).toString()
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), ":(").resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == expectedResolvedInclude
    if (expectedFragmentCached) {
      assert cache.getIfPresent(includeSrcUrl) != null
      assert cache.getIfPresent(includeSrcUrl).content == expectedCachedBody
    } else {
      assert cache.getIfPresent(includeSrcUrl) == null
    }

    cleanup:
    mockWebServer.shutdown()

    where:
    responsStatus | fragmentContent | expectedFragmentCached | expectedCachedBody | expectedResolvedInclude
    100           | "fragment"      | false                  | null               | ":("
    200           | "fragment"      | true                   | "fragment"         | "fragment"
    202           | "fragment"      | false                  | null               | ":("
    203           | "fragment"      | true                   | "fragment"         | "fragment"
    204           | ""              | true                   | ""                 | ""
    205           | "fragment"      | false                  | null               | ":("
    206           | "fragment"      | true                   | "fragment"         | "fragment"
    // TODO: Testing status code 300 does not work on Java 11 because HttpClient fails with "IOException: Invalid redirection"
    // 300           | "fragment"   | true                   | ""                 | ":("
    302           | "fragment"   | false                  | null               | ":("
    400           | "fragment"   | false                  | null               | ":("
    404           | "fragment"   | true                   | ""                 | ":("
    405           | "fragment"   | true                   | ""                 | ":("
    410           | "fragment"   | true                   | ""                 | ":("
    414           | "fragment"   | true                   | ""                 | ":("
    500           | "fragment"   | false                  | null               | ":("
    501           | "fragment"   | true                   | ""                 | ":("
    502           | "fragment"   | false                  | null               | ":("
    503           | "fragment"   | false                  | null               | ":("
    504           | "fragment"   | false                  | null               | ":("
    505           | "fragment"   | false                  | null               | ":("
    506           | "fragment"   | false                  | null               | ":("
    507           | "fragment"   | false                  | null               | ":("
    508           | "fragment"   | false                  | null               | ":("
    509           | "fragment"   | false                  | null               | ":("
    510           | "fragment"   | false                  | null               | ":("
    511           | "fragment"   | false                  | null               | ":("
  }

  def "should cache fragment for s-maxage seconds if directive is present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600, s-maxage=604800 , public")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-s-maxage").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(604800).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(604800).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment for max-age seconds if directive is present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/-test-max-age").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3600).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3600).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should treat http header names as case insensitive"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("cache-control", "max-age=3600")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-cache-headers-case-insensitive").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3600).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3600).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment for max-age seconds minus Age seconds if directive is present and Age header is set"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Age", "600")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-age-header").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3000).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3000).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should use absolute value of Age header for cache expiration calculation"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Age", "-100")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-age-header").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3500).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3500).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment based on Expires header and current time if Cache-Control header and Date header are not present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Cache-Control", "public")
      .setHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-expires-header").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime == ZonedDateTime.parse("Wed, 12 Oct 2050 07:28:00 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()

    cleanup:
    mockWebServer.shutdown()
  }

  def "should handle Expires header with value 0"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Expires", "0")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-expires-header").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fragment"
    cache.getIfPresent(includeSrcUrl) == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment based on Expires and Date header if Cache-Control header is not present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Date", "Wed, 05 Oct 2050 07:28:00 GMT")
      .setHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-expires-header").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plus(7, ChronoUnit.DAYS).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plus(7, ChronoUnit.DAYS).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not cache fragment if Cache-Control header is set but without max-age directives"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader("Cache-Control", "no-cache,no-store,must-revalidate")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-caching").toString()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fragment"
    cache.getIfPresent(includeSrcUrl) == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not crash when cache headers contain invalid values"() {
    given:
    def mockWebServer = new MockWebServer()

    when:
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setHeader(header1Name, header1Value)
      .setHeader(header2Name, header2Value)
      .setResponseCode(200))
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/test-should-not-crash").toString()
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fragment"

    cleanup:
    mockWebServer.shutdown()

    where:
    header1Name     | header1Value                    | header2Name | header2Value
    "Cache-Control" | "s-maxage=not-numeric"          | "X-Dummy"   | "dummy"
    "Cache-Control" | "max-age=not-numeric"           | "X-Dummy"   | "dummy"
    "Cache-Control" | "max-age=3600"                  | "Age"       | "not-numeric"
    "Expires"       | "not-numeric"                   | "X-Dummy"   | "dummy"
    "Expires"       | "Wed, 12 Oct 2050 07:28:00 GMT" | "Date"      | "not-a-date"
  }

  def "should cache fragment for a configurable duration if no expiration time is indicated via response header"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-default-cache-duration").toString()
    def config = AbleronConfig.builder()
      .fragmentDefaultCacheDuration(Duration.ofSeconds(30))
      .build()

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", includeSrcUrl
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    resolvedInclude == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(30).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(30).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should apply request timeout for delayed header"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment from src")
      .setHeadersDelay(2, TimeUnit.SECONDS)
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/test-timeout-handling").toString()
    ), "fallback").resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fallback"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should apply request timeout for delayed body"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment from src")
      .setBodyDelay(2, TimeUnit.SECONDS)
      .setResponseCode(200))

    when:
    def resolvedInclude = new Include("...", Map.of(
      "src", mockWebServer.url("/test-timeout-handling").toString()
    ), "fallback").resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    resolvedInclude == "fallback"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should favor include tag specific request timeout over global one"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setBodyDelay(1500, TimeUnit.MILLISECONDS)
      .setResponseCode(200))

    when:
    def attributes = new HashMap()
    attributes.put(srcAttribute, mockWebServer.url("/test-timeout-handling").toString())
    attributes.putAll(localTimeout)

    then:
    new Include("...", attributes, null)
      .resolve(httpClient, [:], cache, config, supplyPool).get() == expectedResolvedInclude

    cleanup:
    mockWebServer.shutdown()

    where:
    srcAttribute   | localTimeout                                  | expectedResolvedInclude
    "src"          | Map.of()                                      | ""
    "src"          | Map.of("src-timeout-millis", "2000")          | "fragment"
    "src"          | Map.of("fallback-src-timeout-millis", "2000") | ""
    "fallback-src" | Map.of()                                      | ""
    "fallback-src" | Map.of("fallback-src-timeout-millis", "2000") | "fragment"
    "fallback-src" | Map.of("src-timeout-millis", "2000")          | ""
  }

  def "should pass allowed request headers to fragment requests"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))
    def config = AbleronConfig.builder()
      .fragmentRequestHeadersToPass(["X-Test"])
      .build()

    when:
    new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, ["X-Test":["Foo"]], cache, config, supplyPool).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeader("X-Test") == "Foo"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should treat fragment request headers allow list as case insensitive"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))
    def config = AbleronConfig.builder()
      .fragmentRequestHeadersToPass(["X-TeSt"])
      .build()

    when:
    new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, ["x-tEsT":["Foo"]], cache, config, supplyPool).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeader("X-Test") == "Foo"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not pass non-allowed request headers to fragment requests"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))
    def config = AbleronConfig.builder()
      .fragmentRequestHeadersToPass([])
      .build()

    when:
    new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, ["X-Test":["Foo"]], cache, config, supplyPool).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeader("X-Test") == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should pass default User-Agent header to fragment requests"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))

    when:
    new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, [:], cache, config, supplyPool).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeader("User-Agent").startsWith("Java-http-client/")

    cleanup:
    mockWebServer.shutdown()
  }

  def "should pass provided User-Agent header to fragment requests by default"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))

    when:
    new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, ["user-agent":["test"]], cache, config, supplyPool).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeader("User-Agent") == "test"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should pass header with multiple values to fragment requests"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))
    def config = AbleronConfig.builder()
      .fragmentRequestHeadersToPass(["X-Test"])
      .build()

    when:
    new Include("...", Map.of(
      "src", mockWebServer.url("/").toString()
    ), null).resolve(httpClient, ["X-Test":["Foo", "Bar", "Baz"]], cache, config, supplyPool).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeaders().values("X-Test") == ["Foo", "Bar", "Baz"]

    cleanup:
    mockWebServer.shutdown()
  }
}
