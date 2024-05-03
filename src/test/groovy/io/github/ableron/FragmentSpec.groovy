package io.github.ableron

import spock.lang.Specification

import java.time.Instant

class FragmentSpec extends Specification {

  def "should create expired fragment if expiration time is not provided"() {
    expect:
    new Fragment(200, "").expirationTime.isBefore(Instant.now())
  }
}
