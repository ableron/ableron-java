package io.github.ableron

import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient

class IncludeSpec extends Specification {

  @Shared
  def httpClient = HttpClient.newHttpClient()

  def "provided include must not be null"() {
    when:
    new Include(null, Map.of(), "", httpClient)

    then:
    def exception = thrown(NullPointerException)
    exception.message == "include must not be null"
  }

  def "provided include attributes must not be null"() {
    when:
    new Include("", null, "", httpClient)

    then:
    def exception = thrown(NullPointerException)
    exception.message == "attributes must not be null"
  }

  def "provided HTTP client must not be null"() {
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
    include                                                                               | expectedFallbackContent
    new Include("<ableron-include src=\"...\"/>", Map.of(), null, httpClient)             | null
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
}
