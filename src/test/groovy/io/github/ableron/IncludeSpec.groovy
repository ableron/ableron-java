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

  Stats stats = new Stats()

  @Shared
  def supplyPool = Executors.newFixedThreadPool(4)

  def "constructor should set raw attributes"() {
    given:
    def rawAttributes = ["src": "https://example.com"]

    expect:
    new Include("", rawAttributes).rawAttributes == rawAttributes
  }

  def "constructor should set fallback content"() {
    expect:
    include.fallbackContent == expectedFallbackContent

    where:
    include                             | expectedFallbackContent
    new Include(null)                   | ""
    new Include(null, null, "fallback") | "fallback"
  }

  def "constructor should set raw include tag"() {
    given:
    def rawIncludeTag = '<ableron-include src="https://example.com"/>'

    expect:
    new Include(rawIncludeTag).rawIncludeTag == rawIncludeTag
  }

  def "should handle include id"() {
    expect:
    include.id == expectedId

    where:
    include                                    | expectedId
    new Include("")                            | "0"
    new Include("", ["id": "foo-bar"])         | "foo-bar"
    new Include("", ["id": "FOO-bar%baz__/5"]) | "FOO-barbaz__5"
    new Include("", ["id": "//"])              | "0"
    new Include("zzzzz")                       | "116425210"
    new Include("zzzzzz")                      | "685785664"
  }

  def "constructor should set src attribute"() {
    expect:
    include.src == expectedSrc

    where:
    include                                         | expectedSrc
    new Include("")                                 | null
    new Include("", ["src": "https://example.com"]) | "https://example.com"
    new Include("", ["SRC": "https://example.com"]) | "https://example.com"
  }

  def "constructor should set src timeout attribute"() {
    expect:
    include.srcTimeout == expectedSrcTimeout

    where:
    include                                         | expectedSrcTimeout
    new Include("")                                 | null
    new Include("", ["src-timeout-millis": "2000"]) | Duration.ofMillis(2000)
  }

  def "constructor should set fallback-src attribute"() {
    expect:
    include.fallbackSrc == expectedFallbackSrc

    where:
    include                                                    | expectedFallbackSrc
    new Include(null)                                          | null
    new Include(null, ["fallback-src": "https://example.com"]) | "https://example.com"
  }

  def "constructor should set fallback src timeout attribute"() {
    expect:
    include.fallbackSrcTimeout == expectedFallbackSrcTimeout

    where:
    include                                                  | expectedFallbackSrcTimeout
    new Include("")                                          | null
    new Include("", ["fallback-src-timeout-millis": "2000"]) | Duration.ofMillis(2000)
  }

  def "constructor should set primary attribute"() {
    expect:
    include.primary == expectedPrimary

    where:
    include                                   | expectedPrimary
    new Include(null)                         | false
    new Include(null, ["primary": ""])        | true
    new Include(null, ["PRIMARY": ""])        | true
    new Include(null, ["primary": "primary"]) | true
    new Include(null, ["primary": "PRIMARY"]) | true
    new Include(null, ["priMARY": "PRImary"]) | true
    new Include(null, ["primary": "nope"])    | false
  }

  def "should consider include objects with identical include string as equal"() {
    when:
    def include1 = new Include('<ableron-include src="..."></ableron-include>')
    def include2 = new Include('<ableron-include src="..."></ableron-include>', ["foo": "bar"])
    def include3 = new Include('<ableron-include src="..."/>', ["test": "test"], "fallback")

    then:
    include1 == include2
    include1 != include3
    include2 != include3
  }

  def "should resolve with src"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(206)
      .setBody("response"))

    when:
    def include = new Include("", ["src": mockWebServer.url("/fragment").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "response"
    include.resolvedFragment.statusCode == 206
    include.resolvedFragmentSource == "remote src"
    include.resolveTimeMillis > 0
    mockWebServer.takeRequest().getPath() == "/fragment"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve with fallback-src if src could not be loaded"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("fragment from src"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment from fallback-src"))

    when:
    def include = new Include("", [
      "src": mockWebServer.url("/src").toString(),
      "fallback-src": mockWebServer.url("/fallback-src").toString()
    ]).resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fragment from fallback-src"
    include.resolvedFragment.statusCode == 200
    include.resolvedFragmentSource == "remote fallback-src"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve with fallback content if src and fallback-src could not be loaded"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("fragment from src"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("fragment from fallback-src"))

    when:
    def include = new Include("", [
      "src": mockWebServer.url("/src").toString(),
      "fallback-src": mockWebServer.url("/fallback-src").toString()
    ], "fallback content").resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fallback content"
    include.resolvedFragment.statusCode == 200
    include.resolvedFragmentSource == "fallback content"
    include.resolveTimeMillis > 0
    mockWebServer.takeRequest().getPath() == "/src"
    mockWebServer.takeRequest().getPath() == "/fallback-src"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should resolve to empty string if src, fallback src and fallback content are not present"() {
    when:
    def include = new Include(null).resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == ""
    include.resolvedFragment.statusCode == 200
    include.resolvedFragmentSource == "fallback content"
  }

  def "should handle primary include with errored src"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(503)
      .setBody("fragment from src"))

    when:
    def include = new Include("", [
      "src": mockWebServer.url("/").toString(),
      "primary": "primary"
    ]).resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fragment from src"
    include.resolvedFragment.statusCode == 503
    include.resolvedFragmentSource == "remote src"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should handle primary include without src and with errored fallback-src"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(503)
      .setBody("503"))

    when:
    def include = new Include("", [
      "fallback-src": mockWebServer.url("/").toString(),
      "primary": "primary"
    ]).resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "503"
    include.resolvedFragment.statusCode == 503
    include.resolvedFragmentSource == "remote fallback-src"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should handle primary include with errored src and successfully resolved fallback-src"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("src-500"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(206)
      .setBody("fallback-src-206"))

    when:
    def include = new Include("", [
      "src": mockWebServer.url("/").toString(),
      "fallback-src": mockWebServer.url("/").toString(),
      "primary": ""
    ]).resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fallback-src-206"
    include.resolvedFragment.statusCode == 206
    include.resolvedFragmentSource == "remote fallback-src"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should handle primary include with errored src and errored fallback-src"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(503)
      .setBody("src"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("fallback"))

    when:
    def include = new Include("", [
      "src": mockWebServer.url("/").toString(),
      "fallback-src": mockWebServer.url("/").toString(),
      "primary": ""
    ]).resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "src"
    include.resolvedFragment.statusCode == 503
    include.resolvedFragmentSource == "remote src"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should reset errored fragment of primary include for consecutive resolving"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(503)
      .setBody("src"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("fallback"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(504)
      .setBody("src 2nd call"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("fallback 2nd call"))
    def include = new Include("", [
      "src": mockWebServer.url("/").toString(),
      "fallback-src": mockWebServer.url("/").toString(),
      "primary": ""
    ])

    when:
    include.resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "src"
    include.resolvedFragment.statusCode == 503
    include.resolvedFragmentSource == "remote src"
    include.resolveTimeMillis > 0

    when:
    include.resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "src 2nd call"
    include.resolvedFragment.statusCode == 504
    include.resolvedFragmentSource == "remote src"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should ignore fallback content and set fragment status code and body of errored src if primary"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(503)
      .setBody("response"))

    when:
    def include = new Include("", [
      "src": mockWebServer.url("/").toString(),
      "primary": ""
    ], "fallback content").resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "response"
    include.resolvedFragment.statusCode == 503
    include.resolvedFragmentSource == "remote src"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not follow redirects when resolving URLs"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(302)
      .setHeader("Location", "foo"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment after redirect"))

    when:
    def include = new Include("", ["src": mockWebServer.url("/test-redirect").toString()], "fallback")
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fallback"
    include.resolvedFragment.statusCode == 200
    include.resolvedFragmentSource == "fallback content"
    include.resolveTimeMillis > 0

    cleanup:
    mockWebServer.shutdown()
  }

  def "should use cached fragment if not expired"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment from src"))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    cache.put(includeSrcUrl, new Fragment(null, 200, "from cache", expirationTime, [:]))
    sleep(2000)
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == expectedFragment
    include.resolvedFragment.statusCode == 200
    include.resolvedFragmentSource == expectedFragmentSource

    cleanup:
    mockWebServer.shutdown()

    where:
    expirationTime                | expectedFragment    | expectedFragmentSource
    Instant.now().plusSeconds(5)  | "from cache"        | "cached src"
    Instant.now().minusSeconds(5) | "fragment from src" | "remote src"
  }

  @Unroll
  def "should cache fragment if status code is defined as cacheable in RFC 7231 - Status #responseStatus"() {
    given:
    def mockWebServer = new MockWebServer()

    when:
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(responseStatus)
      .setHeader("Cache-Control", "max-age=7200")
      .setBody(srcFragment))
    def includeSrcUrl = mockWebServer.url("/test-caching-" + UUID.randomUUID().toString()).toString()
    def include = new Include("", ["src": includeSrcUrl], ":(")
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == expectedFragment
    include.resolvedFragment.statusCode == expectedFragmentStatusCode
    include.resolvedFragmentSource == (expectedFragment == ':(' ? 'fallback content' : 'remote src')

    if (expectedFragmentCached) {
      assert cache.getIfPresent(includeSrcUrl) != null
    } else {
      assert cache.getIfPresent(includeSrcUrl) == null
    }

    cleanup:
    mockWebServer.shutdown()

    where:
    responseStatus | srcFragment | expectedFragmentCached | expectedFragment | expectedFragmentStatusCode
    100            | "fragment"  | false                  | ":("             | 200
    200            | "fragment"  | true                   | "fragment"       | 200
    202            | "fragment"  | false                  | ":("             | 200
    203            | "fragment"  | true                   | "fragment"       | 203
    204            | ""          | true                   | ""               | 204
    205            | "fragment"  | false                  | ":("             | 200
    206            | "fragment"  | true                   | "fragment"       | 206
    // TODO: Testing status code 300 does not work on Java 11 because HttpClient fails with "IOException: Invalid redirection"
    // 300           | "fragment"  | true                   | ":("             | 200
    302            | "fragment"  | false                  | ":("             | 200
    400            | "fragment"  | false                  | ":("             | 200
    404            | "fragment"  | true                   | ":("             | 200
    405            | "fragment"  | true                   | ":("             | 200
    410            | "fragment"  | true                   | ":("             | 200
    414            | "fragment"  | true                   | ":("             | 200
    500            | "fragment"  | false                  | ":("             | 200
    501            | "fragment"  | true                   | ":("             | 200
    502            | "fragment"  | false                  | ":("             | 200
    503            | "fragment"  | false                  | ":("             | 200
    504            | "fragment"  | false                  | ":("             | 200
    505            | "fragment"  | false                  | ":("             | 200
    506            | "fragment"  | false                  | ":("             | 200
    507            | "fragment"  | false                  | ":("             | 200
    508            | "fragment"  | false                  | ":("             | 200
    509            | "fragment"  | false                  | ":("             | 200
    510            | "fragment"  | false                  | ":("             | 200
    511            | "fragment"  | false                  | ":("             | 200
  }

  def "should cache fragment for s-maxage seconds if directive is present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600, s-maxage=604800 , public")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
    def includeSrcUrl = mockWebServer.url("/test-s-maxage").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(604800).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(604800).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment for max-age seconds if directive is present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
    def includeSrcUrl = mockWebServer.url("/-test-max-age").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3600).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3600).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should treat http header names as case insensitive"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("cache-control", "max-age=3600"))
    def includeSrcUrl = mockWebServer.url("/").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3600).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3600).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment for max-age seconds minus Age seconds if directive is present and Age header is set"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Age", "600")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
    def includeSrcUrl = mockWebServer.url("/test-age-header").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3000).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3000).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should use absolute value of Age header for cache expiration calculation"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Cache-Control", "max-age=3600")
      .setHeader("Age", "-100")
      .setHeader("Expires", "Wed, 21 Oct 2015 07:28:00 GMT"))
    def includeSrcUrl = mockWebServer.url("/test-age-header").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plusSeconds(3500).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plusSeconds(3500).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment based on Expires header and current time if Cache-Control header and Date header are not present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Cache-Control", "public")
      .setHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT"))
    def includeSrcUrl = mockWebServer.url("/test-expires-header").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cacheExpirationTime == ZonedDateTime.parse("Wed, 12 Oct 2050 07:28:00 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()

    cleanup:
    mockWebServer.shutdown()
  }

  def "should handle Expires header with value 0"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Expires", "0"))
    def includeSrcUrl = mockWebServer.url("/test-expires-header").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cache.getIfPresent(includeSrcUrl) == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should cache fragment based on Expires and Date header if Cache-Control header is not present"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Date", "Wed, 05 Oct 2050 07:28:00 GMT")
      .setHeader("Expires", "Wed, 12 Oct 2050 07:28:00 GMT"))
    def includeSrcUrl = mockWebServer.url("/test-expires-header").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def cacheExpirationTime = cache.getIfPresent(includeSrcUrl).expirationTime

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cacheExpirationTime.isBefore(Instant.now().plus(7, ChronoUnit.DAYS).plusSeconds(1))
    cacheExpirationTime.isAfter(Instant.now().plus(7, ChronoUnit.DAYS).minusSeconds(1))

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not cache fragment if Cache-Control header is set but without max-age directives"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader("Cache-Control", "no-cache,no-store,must-revalidate"))
    def includeSrcUrl = mockWebServer.url("/test-caching").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cache.getIfPresent(includeSrcUrl) == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not crash when cache headers contain invalid values"() {
    given:
    def mockWebServer = new MockWebServer()

    when:
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setHeader(header1Name, header1Value)
      .setHeader(header2Name, header2Value))
    def include = new Include("", ["src": mockWebServer.url("/test-should-not-crash").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolvedFragment.content == "fragment"

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
      .setResponseCode(200)
      .setBody("fragment"))
    def includeSrcUrl = mockWebServer.url("/test-default-cache-duration").toString()

    when:
    def include = new Include("", ["src": includeSrcUrl])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fragment"
    cache.getIfPresent(includeSrcUrl) == null

    cleanup:
    mockWebServer.shutdown()
  }

  def "should apply request timeout for delayed header"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment from src")
      .setHeadersDelay(2, TimeUnit.SECONDS))

    when:
    def include = new Include("", ["src": mockWebServer.url("/test-timeout-handling").toString()], "fallback")
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fallback"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should apply request timeout for delayed body"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment from src")
      .setBodyDelay(2, TimeUnit.SECONDS))

    when:
    def include = new Include("", ["src": mockWebServer.url("/test-timeout-handling").toString()], "fallback")
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolved
    include.resolvedFragment.content == "fallback"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should favor include tag specific request timeout over global one"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody("fragment")
      .setBodyDelay(1500, TimeUnit.MILLISECONDS))

    when:
    def attributes = new HashMap()
    attributes.put(srcAttributeName, mockWebServer.url("/test-timeout-handling").toString())
    attributes.putAll(timeoutAttribute)

    then:
    new Include("", attributes).resolve(httpClient, [:], cache, config, supplyPool, stats).get()
      .resolvedFragment.content == expectedFragment

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
      .fragmentRequestHeadersToPass(["X-Default"])
      .fragmentAdditionalRequestHeadersToPass(["X-Additional"])
      .build()

    when:
    new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-Default": ["Foo"], "X-Additional": ["Bar"]], cache, config, supplyPool, stats).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeader("X-default") == "Foo"
    fragmentRequest.getHeader("X-additional") == "Bar"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should extend default allowed fragment request headers with additional allowed fragment request headers"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(204))
    def config = AbleronConfig.builder()
      .fragmentAdditionalRequestHeadersToPass(["X-Additional"])
      .build()

    when:
    new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, [
        "X-Correlation-ID": ["Foo"],
        "X-Additional": ["Bar"],
        "X-Test": ["Baz"]
      ], cache, config, supplyPool, stats).get()
    def fragmentRequest = mockWebServer.takeRequest()

    then:
    fragmentRequest.getHeader("X-Correlation-ID") == "Foo"
    fragmentRequest.getHeader("X-Additional") == "Bar"
    fragmentRequest.getHeader("X-Test") == null

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
    new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["x-tEsT":["Foo"]], cache, config, supplyPool, stats).get()
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
    new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-Test":["Foo"]], cache, config, supplyPool, stats).get()
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
    new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
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
    new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["user-agent":["test"]], cache, config, supplyPool, stats).get()
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
    new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-Test":["Foo", "Bar", "Baz"]], cache, config, supplyPool, stats).get()
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
    def include = new Include("", ["src": mockWebServer.url("/").toString(), "primary": ""])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolvedFragment.responseHeaders == ["x-test": ["Test"]]

    cleanup:
    mockWebServer.shutdown()
  }

  def "should not pass allowed response headers of non-primary fragment to transclusion result"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse().setResponseCode(200)
      .addHeader("X-Test", "Test"))

    when:
    def include = new Include("", ["src": mockWebServer.url("/").toString(), "primary": "false"])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolvedFragment.responseHeaders.isEmpty()

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
    def include = new Include("", ["src": mockWebServer.url("/").toString(), "primary": ""])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolvedFragment.responseHeaders == ["x-test": ["Test"]]

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
    def include = new Include("", ["src": mockWebServer.url("/").toString(), "primary": ""])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()

    then:
    include.resolvedFragment.responseHeaders.get("x-test") == ["Test", "Test2"]

    cleanup:
    mockWebServer.shutdown()
  }

  def "should consider cacheVaryByRequestHeaders"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Cache-Control", "max-age=30")
      .setBody("X-AB-Test=A"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Cache-Control", "max-age=30")
      .setBody("X-AB-Test=B"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Cache-Control", "max-age=30")
      .setBody("X-AB-Test=omitted"))
    def config = AbleronConfig.builder()
      .fragmentRequestHeadersToPass(["x-ab-TEST"])
      .cacheVaryByRequestHeaders(["x-AB-test"])
      .build()

    when:
    def include1 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-AB-TEST": ["A"]], cache, config, supplyPool, stats).get()
    def include2 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-AB-TEST": ["A"]], cache, config, supplyPool, stats).get()
    def include3 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-AB-TEST": ["B"]], cache, config, supplyPool, stats).get()
    def include4 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-AB-TEST": ["B"], "X-Foo": ["Bar"]], cache, config, supplyPool, stats).get()
    def include5 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, [:], cache, config, supplyPool, stats).get()
    def include6 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["x-ab-test": ["A"]], cache, config, supplyPool, stats).get()

    then:
    include1.resolvedFragment.content == "X-AB-Test=A"
    include2.resolvedFragment.content == "X-AB-Test=A"
    include3.resolvedFragment.content == "X-AB-Test=B"
    include4.resolvedFragment.content == "X-AB-Test=B"
    include5.resolvedFragment.content == "X-AB-Test=omitted"
    include6.resolvedFragment.content == "X-AB-Test=A"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should use consistent order of cacheVaryByRequestHeaders for cache key generation"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Cache-Control", "max-age=30")
      .setBody("A,B,C"))
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Cache-Control", "max-age=30")
      .setBody("A,B,B"))
    def config = AbleronConfig.builder()
      .fragmentRequestHeadersToPass(["x-test-A", "x-test-B", "x-test-C"])
      .cacheVaryByRequestHeaders(["X-Test-A", "X-Test-B", "X-Test-C"])
      .build()

    when:
    def include1 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-TEST-A": ["A"], "X-Test-B": ["B"], "X-Test-C": ["C"]], cache, config, supplyPool, stats).get()
    def include2 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-TEST-B": ["B"], "X-TEST-A": ["A"], "X-Test-C": ["C"]], cache, config, supplyPool, stats).get()
    def include3 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["X-TEST-C": ["C"], "X-test-B": ["B"], "X-Test-A": ["A"]], cache, config, supplyPool, stats).get()
    def include4 = new Include("", ["src": mockWebServer.url("/").toString()])
      .resolve(httpClient, ["x-test-c": ["B"], "x-test-b": ["B"], "x-test-a": ["A"]], cache, config, supplyPool, stats).get()

    then:
    include1.resolvedFragment.content == "A,B,C"
    include2.resolvedFragment.content == "A,B,C"
    include3.resolvedFragment.content == "A,B,C"
    include4.resolvedFragment.content == "A,B,B"

    cleanup:
    mockWebServer.shutdown()
  }
}
