package io.github.ableron

import spock.lang.Specification

import java.time.Instant

class FragmentSpec extends Specification {

  def "should create expired fragment if expiration time is not provided"() {
    expect:
    new Fragment(200, "").expirationTime.isBefore(Instant.now())
  }

  def "should provide isRemote() based on existence of fragment url"() {
    expect:
    new Fragment(url, 200, "", Instant.EPOCH, Map.of()).isRemote() == expectedIsRemote

    where:
    url                  | expectedIsRemote
    null                 | false
    ""                   | true
    "http://example.com" | true
  }
}
