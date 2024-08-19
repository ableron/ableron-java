package io.github.ableron

import spock.lang.Specification

import java.time.Duration

class AbleronConfigSpec extends Specification {

  def "should have default value for each property"() {
    when:
    def config = AbleronConfig.builder().build()

    then:
    with(config) {
      enabled
      fragmentRequestTimeout == Duration.ofSeconds(3)
      fragmentRequestHeadersToPass as List == [
        "Accept-Language",
        "Correlation-ID",
        "Forwarded",
        "Referer",
        "User-Agent",
        "X-Correlation-ID",
        "X-Forwarded-For",
        "X-Forwarded-Proto",
        "X-Forwarded-Host",
        "X-Real-IP",
        "X-Request-ID"
      ]
      fragmentAdditionalRequestHeadersToPass == []
      primaryFragmentResponseHeadersToPass == [
        "Content-Language",
        "Location",
        "Refresh"
      ]
      cacheMaxSizeInBytes == 1024 * 1024 * 50
      cacheVaryByRequestHeaders == []
      !cacheAutoRefreshEnabled()
      cacheAutoRefreshMaxAttempts == 3
      cacheAutoRefreshInactiveFragmentsMaxRefreshs == 2
      !statsAppendToContent()
      !statsExposeFragmentUrl()
    }
  }

  def "should use values provided via builder"() {
    when:
    def config = AbleronConfig.builder()
      .enabled(false)
      .fragmentRequestTimeout(Duration.ofMillis(200))
      .fragmentRequestHeadersToPass(["X-Test-Request-Header", "X-Test-Request-Header-2"])
      .fragmentAdditionalRequestHeadersToPass(["X-Additional-Req-Header", "X-Additional-Req-Header-2"])
      .primaryFragmentResponseHeadersToPass(["X-Test-Response-Header", "X-Test-Response-Header-2"])
      .cacheMaxSizeInBytes(1024 * 100)
      .cacheVaryByRequestHeaders(["X-Test-Groups", "X-ACME-Country"])
      .cacheAutoRefreshEnabled(true)
      .cacheAutoRefreshMaxAttempts(5)
      .cacheAutoRefreshInactiveFragmentsMaxRefreshs(4)
      .statsAppendToContent(true)
      .statsExposeFragmentUrl(true)
      .build()

    then:
    with(config) {
      !enabled
      fragmentRequestTimeout == Duration.ofMillis(200)
      fragmentRequestHeadersToPass == ["X-Test-Request-Header", "X-Test-Request-Header-2"]
      fragmentAdditionalRequestHeadersToPass == ["X-Additional-Req-Header", "X-Additional-Req-Header-2"]
      primaryFragmentResponseHeadersToPass == ["X-Test-Response-Header", "X-Test-Response-Header-2"]
      cacheMaxSizeInBytes == 1024 * 100
      cacheVaryByRequestHeaders == ["X-Test-Groups", "X-ACME-Country"]
      cacheAutoRefreshEnabled()
      cacheAutoRefreshMaxAttempts == 5
      cacheAutoRefreshInactiveFragmentsMaxRefreshs == 4
      statsAppendToContent()
      statsExposeFragmentUrl()
    }
  }

  def "should append fragmentAdditionalRequestHeadersToPass to default fragmentsRequestHeadersToPass"() {
    when:
    def config = AbleronConfig.builder()
      .fragmentAdditionalRequestHeadersToPass(Set.of("Additional-A", "Additional-B", "x-correlation-id", "x-request-id"))
      .build()

    then:
    config.fragmentRequestHeadersToPass as List == [
      "Accept-Language",
      "Correlation-ID",
      "Forwarded",
      "Referer",
      "User-Agent",
      "X-Correlation-ID",
      "X-Forwarded-For",
      "X-Forwarded-Proto",
      "X-Forwarded-Host",
      "X-Real-IP",
      "X-Request-ID",
    ]
  }

  def "should throw exception if fragmentRequestTimeout is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .fragmentRequestTimeout(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "fragmentRequestTimeout must not be null"
  }

  def "should throw exception if fragmentRequestHeadersToPass is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .fragmentRequestHeadersToPass(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "fragmentRequestHeadersToPass must not be null"
  }

  def "should throw exception if fragmentAdditionalRequestHeadersToPass is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .fragmentAdditionalRequestHeadersToPass(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "fragmentAdditionalRequestHeadersToPass must not be null"
  }

  def "should throw exception if primaryFragmentResponseHeadersToPass is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .primaryFragmentResponseHeadersToPass(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "primaryFragmentResponseHeadersToPass must not be null"
  }

  def "should throw exception if cacheVaryByRequestHeaders is tried to be set to null"() {
    when:
    AbleronConfig.builder()
      .cacheVaryByRequestHeaders(null)
      .build()

    then:
    def exception = thrown(NullPointerException)
    exception.message == "cacheVaryByRequestHeaders must not be null"
  }

  def "should expose only immutable collections"() {
    given:
    def config = AbleronConfig.builder().build()

    when:
    config.getFragmentRequestHeadersToPass().add("Not-Allowed")

    then:
    thrown(UnsupportedOperationException)

    when:
    config.getPrimaryFragmentResponseHeadersToPass().add("Not-Allowed")

    then:
    thrown(UnsupportedOperationException)

    when:
    config.getCacheVaryByRequestHeaders().add("Not-Allowed")

    then:
    thrown(UnsupportedOperationException)
  }
}
