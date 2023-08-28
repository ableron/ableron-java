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

  def "constructor should set raw attributes"() {
    given:
    def rawAttributes = ["src": "https://example.com"]

    expect:
    new Include(rawAttributes).rawAttributes == rawAttributes
  }

  def "constructor should set fallback content"() {
    expect:
    include.fallbackContent == expectedFallbackContent

    where:
    include                       | expectedFallbackContent
    new Include(null, null)       | null
    new Include(null, "fallback") | "fallback"
  }

  def "constructor should set raw include tag"() {
    given:
    def rawIncludeTag = '<ableron-include src="https://example.com"/>'

    expect:
    new Include(null, null, rawIncludeTag).rawIncludeTag == rawIncludeTag
  }

  def "should handle include id"() {
    expect:
    include.id == expectedId

    where:
    include                                | expectedId
    new Include([:])                       | "0"
    new Include(["id": "foo-bar"])         | "foo-bar"
    new Include(["id": "FOO-bar%baz__/5"]) | "FOO-barbaz__5"
    new Include(["id": "//"])              | "0"
    new Include([:], "", "zzzzz")          | "116425210"
    new Include([:], "", "zzzzzz")         | "685785664"
  }

  def "constructor should set src attribute"() {
    expect:
    include.src == expectedSrc

    where:
    include                                     | expectedSrc
    new Include([:])                            | null
    new Include(["src": "https://example.com"]) | "https://example.com"
  }

  def "constructor should set src timeout attribute"() {
    expect:
    include.srcTimeout == expectedSrcTimeout

    where:
    include                                     | expectedSrcTimeout
    new Include([:])                            | null
    new Include(["src-timeout-millis": "2000"]) | Duration.ofMillis(2000)
  }

  def "constructor should set fallback-src attribute"() {
    expect:
    include.fallbackSrc == expectedFallbackSrc

    where:
    include                                              | expectedFallbackSrc
    new Include(null)                                    | null
    new Include(["fallback-src": "https://example.com"]) | "https://example.com"
  }

  def "constructor should set fallback src timeout attribute"() {
    expect:
    include.fallbackSrcTimeout == expectedFallbackSrcTimeout

    where:
    include                                              | expectedFallbackSrcTimeout
    new Include([:])                                     | null
    new Include(["fallback-src-timeout-millis": "2000"]) | Duration.ofMillis(2000)
  }

  def "constructor should set primary attribute"() {
    expect:
    include.primary == expectedPrimary

    where:
    include                             | expectedPrimary
    new Include([:])                    | false
    new Include(["primary": ""])        | true
    new Include(["primary": "primary"]) | true
    new Include(["primary": "PRIMARY"]) | true
    new Include(["primary": "nope"])    | false
  }

  def "should consider include objects with identical include string as equal"() {
    when:
    def include1 = new Include(null, null, '<ableron-include src="..."></ableron-include>')
    def include2 = new Include(["foo": "bar"], null, '<ableron-include src="..."></ableron-include>')
    def include3 = new Include(["test": "test"], "fallback", '<ableron-include src="..."/>')

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
    def fragment = new Include(["src": mockWebServer.url("/fragment").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "response"
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
    def fragment = new Include([
      "src": mockWebServer.url("/src").toString(),
      "fallback-src": mockWebServer.url("/fallback-src").toString()
    ]).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fragment from fallback-src"
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
    def fragment = new Include([
      "src": mockWebServer.url("/src").toString(),
      "fallback-src": mockWebServer.url("/fallback-src").toString()
    ], "fallback content").resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fallback content"
    mockWebServer.takeRequest().getPath() == "/src"
    mockWebServer.takeRequest().getPath() == "/fallback-src"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve include to empty string if src, fallback src and fallback content are not present"() {
    expect:
    new Include(null).resolve(httpClient, [:], cache, config, supplyPool).get().content == ""
  }

  def "should set fragment status code for successfully resolved src"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setResponseCode(206))

    when:
    def fragment = new Include(["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "response"
    fragment.statusCode.get() == 206

    cleanup:
    mockWebServer.shutdown()
  }

  def "should set fragment status code for successfully resolved fallback-src of primary include"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("src")
      .setResponseCode(500))
    mockWebServer.enqueue(new MockResponse()
      .setBody("fallback")
      .setResponseCode(206))

    when:
    def fragment = new Include([
      "src": mockWebServer.url("/").toString(),
      "fallback-src": mockWebServer.url("/").toString(),
      "primary": ""
    ]).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fallback"
    fragment.statusCode.get() == 206

    cleanup:
    mockWebServer.shutdown()
  }

  def "should set fragment status code and body of errored src"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setResponseCode(503))

    when:
    def fragment = new Include([
      "src": mockWebServer.url("/").toString(),
      "primary": "primary"
    ]).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "response"
    fragment.statusCode.get() == 503

    cleanup:
    mockWebServer.shutdown()
  }

  def "should set fragment status code of errored src also if fallback-src errored for primary include"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("src")
      .setResponseCode(503))
    mockWebServer.enqueue(new MockResponse()
      .setBody("fallback")
      .setResponseCode(500))

    when:
    def fragment = new Include([
      "src": mockWebServer.url("/").toString(),
      "fallback-src": mockWebServer.url("/").toString(),
      "primary": ""
    ]).resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "src"
    fragment.statusCode.get() == 503

    cleanup:
    mockWebServer.shutdown()
  }

  def "should reset errored fragment of primary include for consecutive resolving"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("src")
      .setResponseCode(503))
    mockWebServer.enqueue(new MockResponse()
      .setBody("fallback")
      .setResponseCode(500))
    mockWebServer.enqueue(new MockResponse()
      .setBody("src 2nd call")
      .setResponseCode(504))
    mockWebServer.enqueue(new MockResponse()
      .setBody("fallback 2nd call")
      .setResponseCode(500))
    def include = new Include([
      "src": mockWebServer.url("/").toString(),
      "fallback-src": mockWebServer.url("/").toString(),
      "primary": ""
    ])

    when:
    def fragment = include.resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "src"
    fragment.statusCode.get() == 503

    when:
    def fragment2 = include.resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment2.content == "src 2nd call"
    fragment2.statusCode.get() == 504

    cleanup:
    mockWebServer.shutdown()
  }

  def "should ignore fallback content and set fragment status code and body of errored src if primary"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("response")
      .setResponseCode(503))

    when:
    def fragment = new Include([
      "src": mockWebServer.url("/").toString(),
      "primary": ""
    ], "fallback content").resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "response"
    fragment.statusCode.get() == 503

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not follow redirects when resolving URLs"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setHeader("Location", "foo")
      .setResponseCode(302))
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment after redirect")
      .setResponseCode(200))

    when:
    def fragment = new Include(["src": mockWebServer.url("/test-redirect").toString()], "fallback")
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fallback"

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
    cache.put(includeSrcUrl, new Fragment(null, 200, "from cache", expirationTime, [:]))
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == expectedFragment

    cleanup:
    mockWebServer.shutdown()

    where:
    expirationTime                | expectedFragment
    Instant.now().plusSeconds(5)  | "from cache"
    Instant.now().minusSeconds(5) | "fragment from src"
  }

  @Unroll
  def "should cache fragment if status code is defined as cacheable in RFC 7231 - Status #responsStatus"() {
    given:
    def mockWebServer = new MockWebServer()

    when:
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(responsStatus)
      .setHeader("Cache-Control", "max-age=7200")
      .setBody(srcFragment))
    def includeSrcUrl = mockWebServer.url("/test-caching-" + UUID.randomUUID().toString()).toString()
    def fragment = new Include(["src": includeSrcUrl], ":(")
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == expectedFragment
    if (expectedFragmentCached) {
      assert cache.getIfPresent(includeSrcUrl) != null
    } else {
      assert cache.getIfPresent(includeSrcUrl) == null
    }

    cleanup:
    mockWebServer.shutdown()

    where:
    responsStatus | srcFragment | expectedFragmentCached | expectedFragment
    100           | "fragment"  | false                  | ":("
    200           | "fragment"  | true                   | "fragment"
    202           | "fragment"  | false                  | ":("
    203           | "fragment"  | true                   | "fragment"
    204           | ""          | true                   | ""
    205           | "fragment"  | false                  | ":("
    206           | "fragment"  | true                   | "fragment"
    // TODO: Testing status code 300 does not work on Java 11 because HttpClient fails with "IOException: Invalid redirection"
    // 300           | "fragment"  | true                   | ":("
    302           | "fragment"  | false                  | ":("
    400           | "fragment"  | false                  | ":("
    404           | "fragment"  | true                   | ":("
    405           | "fragment"  | true                   | ":("
    410           | "fragment"  | true                   | ":("
    414           | "fragment"  | true                   | ":("
    500           | "fragment"  | false                  | ":("
    501           | "fragment"  | true                   | ":("
    502           | "fragment"  | false                  | ":("
    503           | "fragment"  | false                  | ":("
    504           | "fragment"  | false                  | ":("
    505           | "fragment"  | false                  | ":("
    506           | "fragment"  | false                  | ":("
    507           | "fragment"  | false                  | ":("
    508           | "fragment"  | false                  | ":("
    509           | "fragment"  | false                  | ":("
    510           | "fragment"  | false                  | ":("
    511           | "fragment"  | false                  | ":("
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fragment"
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
    def fragment = new Include(["src": mockWebServer.url("/test-should-not-crash").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fragment"

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

  def "should not cache fragment if no expiration time is indicated via response header"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("fragment")
      .setResponseCode(200))
    def includeSrcUrl = mockWebServer.url("/test-default-cache-duration").toString()

    when:
    def fragment = new Include(["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fragment"
    cache.getIfPresent(includeSrcUrl) == null

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
    def fragment = new Include(["src": mockWebServer.url("/test-timeout-handling").toString()], "fallback")
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fallback"

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
    def fragment = new Include(["src": mockWebServer.url("/test-timeout-handling").toString()], "fallback")
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    fragment.content == "fallback"

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
    attributes.put(srcAttributeName, mockWebServer.url("/test-timeout-handling").toString())
    attributes.putAll(timeoutAttribute)

    then:
    new Include(attributes).resolve(httpClient, [:], cache, config, supplyPool).get().content == expectedFragment

    cleanup:
    mockWebServer.shutdown()

    where:
    srcAttributeName | timeoutAttribute                        | expectedFragment
    "src"            | [:]                                     | ""
    "src"            | ["src-timeout-millis": "2000"]          | "fragment"
    "src"            | ["fallback-src-timeout-millis": "2000"] | ""
    "fallback-src"   | [:]                                     | ""
    "fallback-src"   | ["fallback-src-timeout-millis": "2000"] | "fragment"
    "fallback-src"   | ["src-timeout-millis": "2000"]          | ""
  }

  def "should pass allowed request headers to fragment requests"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))
    def config = AbleronConfig.builder()
      .fragmentRequestHeadersToPass(["X-Test"])
      .build()

    when:
    new Include(["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-Test":["Foo"]], cache, config, supplyPool).get()
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
    new Include(["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["x-tEsT":["Foo"]], cache, config, supplyPool).get()
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
    new Include(["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-Test":["Foo"]], cache, config, supplyPool).get()
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
    new Include(["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool).get()
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
    new Include(["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["user-agent":["test"]], cache, config, supplyPool).get()
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
    new Include(["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-Test":["Foo", "Bar", "Baz"]], cache, config, supplyPool).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeaders().values("X-Test") == ["Foo", "Bar", "Baz"]

    cleanup:
    mockWebServer.shutdown()
  }

  def "should pass allowed response headers of primary fragment to transclusion result"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(200)
      .addHeader("X-Test", "Test"))
    def config = AbleronConfig.builder()
      .primaryFragmentResponseHeadersToPass(["X-Test"])
      .build()

    when:
    def result = new Include(["src": mockWebServer.url("/").toString(), "primary": ""])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    result.responseHeaders == ["x-test": ["Test"]]

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not pass allowed response headers of non-primary fragment to transclusion result"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(200)
      .addHeader("X-Test", "Test"))

    when:
    def result = new Include(["src": mockWebServer.url("/").toString(), "primary": "false"])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    result.responseHeaders.isEmpty()

    cleanup:
    mockWebServer.shutdown()
  }

  def "should treat fragment response headers allow list as case insensitive"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(200)
      .addHeader("x-test", "Test"))
    def config = AbleronConfig.builder()
      .primaryFragmentResponseHeadersToPass(["X-TeSt"])
      .build()

    when:
    def result = new Include(["src": mockWebServer.url("/").toString(), "primary": ""])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    result.responseHeaders == ["x-test": ["Test"]]

    cleanup:
    mockWebServer.shutdown()
  }

  def "should pass primary fragment response header with multiple values to transclusion result"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(200)
      .addHeader("X-Test", "Test")
      .addHeader("X-Test", "Test2"))
    def config = AbleronConfig.builder()
      .primaryFragmentResponseHeadersToPass(["X-TEST"])
      .build()

    when:
    def result = new Include(["src": mockWebServer.url("/").toString(), "primary": ""])
      .resolve(httpClient, [:], cache, config, supplyPool).get()

    then:
    result.responseHeaders.get("x-test") == ["Test", "Test2"]

    cleanup:
    mockWebServer.shutdown()
  }
}
