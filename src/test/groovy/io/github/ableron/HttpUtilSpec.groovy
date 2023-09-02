package io.github.ableron

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPOutputStream

class HttpUtilSpec extends Specification {

  def "should calculate response expiration time based on s-maxage"() {
    when:
    def expirationTime = HttpUtil.calculateResponseExpirationTime([
      "Cache-Control": ["max-age=3600, s-maxage=604800 , public"],
      "Expires": ["Wed, 21 Oct 2015 07:28:00 GMT"]
    ])

    then:
    expirationTime.isBefore(Instant.now().plusSeconds(604800).plusSeconds(1))
    expirationTime.isAfter(Instant.now().plusSeconds(604800).minusSeconds(1))
  }

  def "should calculate response expiration time based on max-age"() {
    when:
    def expirationTime = HttpUtil.calculateResponseExpirationTime(responseHeaders)

    then:
    expirationTime.isBefore(Instant.now().plusSeconds(expectedExpirationTimeSeconds).plusSeconds(1))
    expirationTime.isAfter(Instant.now().plusSeconds(expectedExpirationTimeSeconds).minusSeconds(1))

    where:
    responseHeaders                                                                   | expectedExpirationTimeSeconds
    ["Cache-Control": ["max-age=3600"], "Expires": ["Wed, 21 Oct 2015 07:28:00 GMT"]] | 3600
    ["cache-control": ["MAX-AGE=3600"], "Expires": ["Wed, 21 Oct 2015 07:28:00 GMT"]] | 3600
  }

  def "should calculate response expiration time based on max-age and Age"() {
    when:
    def expirationTime = HttpUtil.calculateResponseExpirationTime(responseHeaders)

    then:
    expirationTime.isBefore(Instant.now().plusSeconds(expectedExpirationTimeSeconds).plusSeconds(1))
    expirationTime.isAfter(Instant.now().plusSeconds(expectedExpirationTimeSeconds).minusSeconds(1))

    where:
    responseHeaders                                                                                    | expectedExpirationTimeSeconds
    ["Cache-Control": ["max-age=3600"], "Age": ["600"], "Expires": ["Wed, 21 Oct 2015 07:28:00 GMT"]]  | 3000
    ["cache-control": ["MAX-AGE=3600"], "age": ["600"], "Expires": ["Wed, 21 Oct 2015 07:28:00 GMT"]]  | 3000
    ["Cache-Control": ["max-age=3600"], "Age": ["-100"], "Expires": ["Wed, 21 Oct 2015 07:28:00 GMT"]] | 3500
  }

  def "should calculate response expiration time based on Expires header and current time if Cache-Control header and Date header are not present"() {
    when:
    def expirationTime = HttpUtil.calculateResponseExpirationTime([
      "Cache-Control": ["public"],
      "Expires": ["Wed, 12 Oct 2050 07:28:00 GMT"]
    ])

    then:
    expirationTime == ZonedDateTime.parse("Wed, 12 Oct 2050 07:28:00 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
  }

  def "should calculate response expiration time based on Expires and Date header if Cache-Control header is not present"() {
    when:
    def expirationTime = HttpUtil.calculateResponseExpirationTime([
      "Date": ["Wed, 05 Oct 2050 07:28:00 GMT"],
      "Expires": ["Wed, 12 Oct 2050 07:28:00 GMT"]
    ])

    then:
    expirationTime.isBefore(Instant.now().plus(7, ChronoUnit.DAYS).plusSeconds(1))
    expirationTime.isAfter(Instant.now().plus(7, ChronoUnit.DAYS).minusSeconds(1))
  }

  def "should calculate response expiration time based on Expires=0"() {
    expect:
    HttpUtil.calculateResponseExpirationTime(["Expires": ["0"]]) == Instant.EPOCH
  }

  def "should calculate response expiration time if Cache-Control header is set but without max-age directives"() {
    expect:
    HttpUtil.calculateResponseExpirationTime(["Cache-Control": ["no-cache,no-store,must-revalidate"]]) == Instant.EPOCH
  }

  def "should not crash on invalid header values"() {
    expect:
    HttpUtil.calculateResponseExpirationTime(responseHeaders) == Instant.EPOCH

    where:
    responseHeaders                                                        | _
    ["Cache-Control": ["s-maxage=not-numeric"]]                            | _
    ["Cache-Control": ["max-age=not-numeric"]]                             | _
    ["Cache-Control": ["max-age=3600"], "Age": ["not-numeric"]]            | _
    ["Expires": ["not-a-date"]]                                            | _
    ["Expires": ["Wed, 12 Oct 2050 07:28:00 GMT"], "Date": ["not-a-date"]] | _
  }

  def "should calculate expiration time in the past if no expiration time is indicated via response header"() {
    expect:
    HttpUtil.calculateResponseExpirationTime([:]) == Instant.EPOCH
  }

  def "should get plain text response body as string from http response"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setBody("plain text body")
      .setResponseCode(200))

    when:
    def httpResponse = HttpClient.newHttpClient()
      .send(HttpRequest.newBuilder()
        .uri(mockWebServer.url("/").uri())
        .build(), HttpResponse.BodyHandlers.ofByteArray())

    then:
    HttpUtil.getResponseBodyAsString(httpResponse) == "plain text body"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should get gzipped response body as string from http response"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .addHeader("Content-Encoding", "gzip")
      .setBody(new Buffer().write(gzip("gzipped body"))))

    when:
    def httpResponse = HttpClient.newHttpClient()
      .send(HttpRequest.newBuilder()
        .uri(mockWebServer.url("/").uri())
        .build(), HttpResponse.BodyHandlers.ofByteArray())

    then:
    HttpUtil.getResponseBodyAsString(httpResponse) == "gzipped body"

    cleanup:
    mockWebServer.shutdown()
  }

  def "should return empty response body from http response if gzip decoding failed"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .addHeader("Content-Encoding", "gzip")
      .setBody(new Buffer().write(Arrays.copyOfRange(gzip("gzipped body"), 4, 10))))

    when:
    def httpResponse = HttpClient.newHttpClient()
      .send(HttpRequest.newBuilder()
        .uri(mockWebServer.url("/").uri())
        .build(), HttpResponse.BodyHandlers.ofByteArray())

    then:
    HttpUtil.getResponseBodyAsString(httpResponse) == ""

    cleanup:
    mockWebServer.shutdown()
  }

  def "should return empty response body from http response if content encoding is unknown"() {
    given:
    def mockWebServer = new MockWebServer()
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(200)
      .addHeader("Content-Encoding", "br")
      .setBody("plain text body but with wrong content-encoding"))

    when:
    def httpResponse = HttpClient.newHttpClient()
      .send(HttpRequest.newBuilder()
        .uri(mockWebServer.url("/").uri())
        .build(), HttpResponse.BodyHandlers.ofByteArray())

    then:
    HttpUtil.getResponseBodyAsString(httpResponse) == ""

    cleanup:
    mockWebServer.shutdown()
  }

  private byte[] gzip(String data) {
    def bos = new ByteArrayOutputStream(data.length())
    def gzipOutputStream = new GZIPOutputStream(bos)
    gzipOutputStream.write(data.getBytes())
    gzipOutputStream.close()
    byte[] gzipped = bos.toByteArray()
    bos.close()
    return gzipped
  }
}
