package io.github.ableron

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient

class IncludeSpec extends Specification {

  @Shared
  def httpClient = HttpClient.newHttpClient()

  def "should throw exception if rawInclude is not provided"() {
    when:
    new Include(null, Map.of(), "", httpClient)

    then:
    def exception = thrown(NullPointerException)
    exception.message == "rawInclude must not be null"
  }

  def "should throw exception if attributes are not provided"() {
    when:
    new Include("", null, "", httpClient)

    then:
    def exception = thrown(NullPointerException)
    exception.message == "attributes must not be null"
  }

  def "should throw exception if httpClient is not provided"() {
    when:
    new Include("", Map.of(), "", null)

    then:
    def exception = thrown(NullPointerException)
    exception.message == "httpClient must not be null"
  }

  def "constructor should set raw include"() {
    when:
    def include = new Include("<ableron-include src=\"https://example.com\"/>", Map.of(), null, httpClient)

    then:
    include.rawInclude == "<ableron-include src=\"https://example.com\"/>"
  }

  def "constructor should set src attribute"() {
    expect:
    include.src == expectedSrc

    where:
    include                                                                                               | expectedSrc
    new Include("<ableron-include src=\"...\"/>", Map.of(), null, httpClient)                             | null
    new Include("<ableron-include src=\"...\"/>", Map.of("src", "https://example.com"), null, httpClient) | "https://example.com"
  }

  def "constructor should set fallback-src attribute"() {
    expect:
    include.fallbackSrc == expectedFallbackSrc

    where:
    include                                                                                                        | expectedFallbackSrc
    new Include("<ableron-include src=\"...\"/>", Map.of(), null, httpClient)                                      | null
    new Include("<ableron-include src=\"...\"/>", Map.of("fallback-src", "https://example.com"), null, httpClient) | "https://example.com"
  }

  def "constructor should set fallback content"() {
    expect:
    include.fallbackContent == expectedFallbackContent

    where:
    include                                                                         | expectedFallbackContent
    new Include("<ableron-include src=\"...\"/>", Map.of(), null, httpClient)       | null
    new Include("<ableron-include src=\"...\"/>", Map.of(), "fallback", httpClient) | "fallback"
  }

  def "should consider include objects with identical include string as equal"() {
    when:
    def include1 = new Include("<ableron-include src=\"...\"></ableron-include>", Map.of(), null, httpClient)
    def include2 = new Include("<ableron-include src=\"...\"></ableron-include>", Map.of("foo", "bar"), null, httpClient)
    def include3 = new Include("<ableron-include src=\"...\"/>", Map.of("test", "test"), null, httpClient)

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
    def resolvedInclude = new Include("<ableron-include />", Map.of("src", mockWebServer.url("/fragment").toString()), null, httpClient).resolve()

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
    def resolvedInclude = new Include("<ableron-include />", Map.of("src", mockWebServer.url("/fragment").toString(), "fallback-src", mockWebServer.url("/fallback-fragment").toString()), null, httpClient).resolve()

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
    def resolvedInclude = new Include("<ableron-include />", Map.of("src", mockWebServer.url("/fragment").toString(), "fallback-src", mockWebServer.url("/fallback-fragment").toString()), "fallback content", httpClient).resolve()

    then:
    resolvedInclude == "fallback content"
    mockWebServer.takeRequest().getPath() == "/fragment"
    mockWebServer.takeRequest().getPath() == "/fallback-fragment"

    cleanup:
    mockWebServer.shutdown()
  }
}
